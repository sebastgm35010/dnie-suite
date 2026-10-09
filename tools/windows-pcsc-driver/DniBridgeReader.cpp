// Original MIT implementation.
#define WIN32_NO_STATUS
#include <windows.h>
#undef WIN32_NO_STATUS
#include <ntstatus.h>
#include <wdf.h>
#include <winscard.h>
#include <winsmcrd.h>
#include "BridgeAbi.h"
#include <initguid.h>

DEFINE_GUID(GUID_DEVINTERFACE_SMARTCARD_READER, 0x50dd5230, 0xba8a, 0x11d1, 0xbf, 0x5d, 0x00, 0x00, 0xf8, 0x05, 0xf5, 0x30);
DEFINE_GUID(GUID_DNI_BRIDGE_BACKEND, 0x6d735823, 0x37a5, 0x4df8, 0x98, 0xc4, 0x0e, 0x51, 0x2b, 0x45, 0xd8, 0xf1);

struct ReaderContext {
    bool attached;
    bool powered;
    BYTE atr[33];
    ULONG atrLength;
    ULONG supported;
    ULONG protocol;
    ULONG state;
    WDFREQUEST pending;
    WDFFILEOBJECT backendOwner;
    uint64_t nextId;
    uint64_t pendingId;
    ULONG opcode;
    ULONG payloadLength;
    ULONG outputCapacity;
    ULONG pciLength;
    BYTE payload[BRIDGE_MAX_APDU];
    bool delivered;
    ULONGLONG deadline;
    WDFQUEUE presentWaiters;
    WDFQUEUE absentWaiters;
    WDFWAITLOCK lock;
    WDFTIMER timer;
};
WDF_DECLARE_CONTEXT_TYPE_WITH_NAME(ReaderContext, Reader);
EVT_WDF_DRIVER_DEVICE_ADD DeviceAdd;
EVT_WDF_IO_QUEUE_IO_DEVICE_CONTROL DeviceControl;
EVT_WDF_REQUEST_CANCEL CancelPending;
EVT_WDF_IO_QUEUE_IO_STOP IoStop;
EVT_WDF_TIMER TimerTick;
EVT_WDF_FILE_CLEANUP FileCleanup;
EVT_WDF_DEVICE_D0_EXIT D0Exit;
EVT_WDF_DEVICE_D0_ENTRY D0Entry;
EVT_WDF_OBJECT_CONTEXT_CLEANUP DeviceCleanup;
struct Guard {
    WDFWAITLOCK lock;
    explicit Guard(WDFWAITLOCK value) : lock(value) { WdfWaitLockAcquire(lock, nullptr); }
    ~Guard() { WdfWaitLockRelease(lock); }
};

static void Notify(WDFQUEUE queue) {
    WDFREQUEST request;
    while (NT_SUCCESS(WdfIoQueueRetrieveNextRequest(queue, &request))) WdfRequestComplete(request, STATUS_SUCCESS);
}

static WDFREQUEST TakePending(ReaderContext* context) {
    WDFREQUEST pending = context->pending;
    if (!pending) return nullptr;
    // Never access a request's output until the cancel routine no longer owns it.
    if (WdfRequestUnmarkCancelable(pending) == STATUS_CANCELLED) return nullptr;
    context->pending = nullptr;
    context->delivered = false;
    SecureZeroMemory(context->payload, context->payloadLength);
    context->payloadLength = 0;
    return pending;
}

static void FinishPending(ReaderContext* context, NTSTATUS status, size_t information = 0) {
    WDFREQUEST pending = TakePending(context);
    if (!pending) return;
    WdfRequestCompleteWithInformation(pending, status, information);
}

static void Detach(ReaderContext* context, NTSTATUS pendingStatus = STATUS_DEVICE_NOT_CONNECTED) {
    context->attached = false;
    context->backendOwner = nullptr;
    context->state = SCARD_ABSENT;
    context->protocol = 0;
    context->supported = 0;
    context->atrLength = 0;
    SecureZeroMemory(context->atr, sizeof(context->atr));
    FinishPending(context, pendingStatus);
    Notify(context->absentWaiters);
}

void CancelPending(WDFREQUEST request) {
    auto context = Reader(WdfIoQueueGetDevice(WdfRequestGetIoQueue(request)));
    {
        Guard guard(context->lock);
        if (context->pending == request) {
            context->pending = nullptr;
            context->delivered = false;
            SecureZeroMemory(context->payload, context->payloadLength);
            context->payloadLength = 0;
            // A canceled command may already have executed on the physical card.
            // Invalidate this backend session; never accept another APDU on it.
            Detach(context);
        }
    }
    WdfRequestComplete(request, STATUS_CANCELLED);
}

void IoStop(WDFQUEUE queue, WDFREQUEST request, ULONG) {
    auto context = Reader(WdfIoQueueGetDevice(queue));
    Guard guard(context->lock);
    // This queue only retains the single QueueBackend request. Synchronous IOCTLs
    // and requests forwarded to manual queues are no longer driver-owned here.
    // If cancellation won UnmarkCancelable, CancelPending owns completion.
    if (context->pending == request) Detach(context, STATUS_CANCELLED);
    // Complete, never StopAcknowledge(TRUE): replaying a transmit/reset after
    // resume can repeat a signature or corrupt the remote secure-messaging SSC.
}

void TimerTick(WDFTIMER timer) {
    auto context = Reader(reinterpret_cast<WDFDEVICE>(WdfTimerGetParentObject(timer)));
    Guard guard(context->lock);
    if (context->pending && GetTickCount64() >= context->deadline) {
        FinishPending(context, STATUS_IO_TIMEOUT);
        Detach(context);
    }
    if (context->powered) WdfTimerStart(timer, WDF_REL_TIMEOUT_IN_MS(1000));
}

void FileCleanup(WDFFILEOBJECT file) {
    auto context = Reader(WdfFileObjectGetDevice(file));
    Guard guard(context->lock);
    if (context->backendOwner == file) Detach(context);
}

NTSTATUS D0Exit(WDFDEVICE device, WDF_POWER_DEVICE_STATE) {
    auto context = Reader(device);
    {
        Guard guard(context->lock);
        context->powered = false;
        WdfTimerStop(context->timer, FALSE);
        Detach(context);
    }
    // These are non-power-managed manual queues containing only framework-owned
    // presence waits. Purge to completion outside our lock; D0Entry may then
    // safely restart them, without an asynchronous purge racing the new session.
    WdfIoQueuePurgeSynchronously(context->presentWaiters);
    WdfIoQueuePurgeSynchronously(context->absentWaiters);
    return STATUS_SUCCESS;
}

NTSTATUS D0Entry(WDFDEVICE device, WDF_POWER_DEVICE_STATE) {
    auto context = Reader(device);
    Guard guard(context->lock);
    context->powered = true;
    WdfIoQueueStart(context->presentWaiters);
    WdfIoQueueStart(context->absentWaiters);
    WdfTimerStart(context->timer, WDF_REL_TIMEOUT_IN_MS(1000));
    return STATUS_SUCCESS;
}

void DeviceCleanup(WDFOBJECT object) {
    auto context = Reader(reinterpret_cast<WDFDEVICE>(object));
    // D0Exit owns request retirement; WDF cancels remaining queues and removes child
    // objects during teardown. Do not reference queues/locks during final cleanup.
    SecureZeroMemory(context->atr, sizeof(context->atr));
    SecureZeroMemory(context->payload, sizeof(context->payload));
}

static NTSTATUS CopyOutput(WDFREQUEST request, const void* bytes, size_t size) {
    void* output = nullptr;
    NTSTATUS status = WdfRequestRetrieveOutputBuffer(request, size, &output, nullptr);
    if (NT_SUCCESS(status)) { memcpy(output, bytes, size); WdfRequestCompleteWithInformation(request, STATUS_SUCCESS, size); }
    return status;
}

static NTSTATUS QueueBackend(ReaderContext* context, WDFREQUEST request, ULONG opcode,
    const BYTE* bytes, ULONG length, ULONG capacity, ULONG pciLength = 0) {
    if (!context->attached) return STATUS_DEVICE_NOT_CONNECTED;
    if (context->pending) return STATUS_DEVICE_BUSY;
    if (length > BRIDGE_MAX_APDU || capacity > BRIDGE_MAX_APDU) return STATUS_INVALID_BUFFER_SIZE;
    context->pending = request;
    context->pendingId = ++context->nextId;
    context->opcode = opcode;
    context->payloadLength = length;
    context->outputCapacity = capacity;
    context->pciLength = pciLength;
    context->delivered = false;
    context->deadline = GetTickCount64() + 30000;
    if (length) memcpy(context->payload, bytes, length);
    NTSTATUS status = WdfRequestMarkCancelableEx(request, CancelPending);
    if (!NT_SUCCESS(status)) { context->pending = nullptr; SecureZeroMemory(context->payload, length); context->payloadLength = 0; }
    return status;
}

void DeviceControl(WDFQUEUE queue, WDFREQUEST request, size_t outputLength, size_t inputLength, ULONG code) {
    auto context = Reader(WdfIoQueueGetDevice(queue));
    Guard guard(context->lock);
    if (!context->powered) { WdfRequestComplete(request, STATUS_DEVICE_NOT_READY); return; }
    NTSTATUS status = STATUS_NOT_SUPPORTED;
    void* input = nullptr;
    if (inputLength) {
        status = WdfRequestRetrieveInputBuffer(request, inputLength, &input, nullptr);
        if (!NT_SUCCESS(status)) { WdfRequestComplete(request, status); return; }
    }
    status = STATUS_NOT_SUPPORTED;
    if (code >= BRIDGE_ATTACH && code <= BRIDGE_COMPLETE) {
        auto owner = WdfRequestGetFileObject(request);
        if (!owner || (code != BRIDGE_ATTACH && owner != context->backendOwner)) { WdfRequestComplete(request, STATUS_ACCESS_DENIED); return; }
        if (code == BRIDGE_ATTACH) {
            if (context->attached || context->pending) status = STATUS_DEVICE_BUSY;
            else if (inputLength < sizeof(AttachHeader)) status = STATUS_INVALID_PARAMETER;
            else {
                auto header = static_cast<AttachHeader*>(input);
                if (header->version != BRIDGE_VERSION || header->reserved || header->atrLength < 2 || header->atrLength > 33 ||
                    (header->protocols != SCARD_PROTOCOL_T0 && header->protocols != SCARD_PROTOCOL_T1) || inputLength != sizeof(*header) + header->atrLength ||
                    (static_cast<BYTE*>(input)[sizeof(*header)] != 0x3b && static_cast<BYTE*>(input)[sizeof(*header)] != 0x3f)) status = STATUS_INVALID_PARAMETER;
                else {
                    context->attached = true; context->backendOwner = owner;
                    context->supported = header->protocols; context->protocol = header->protocols;
                    context->atrLength = header->atrLength;
                    memcpy(context->atr, static_cast<BYTE*>(input) + sizeof(*header), header->atrLength);
                    context->state = SCARD_SPECIFIC;
                    Notify(context->presentWaiters); status = STATUS_SUCCESS;
                }
            }
        } else if (code == BRIDGE_DETACH) { if (inputLength) status = STATUS_INVALID_PARAMETER; else { Detach(context); status = STATUS_SUCCESS; } }
        else if (code == BRIDGE_GET_REQUEST) {
            if (inputLength) status = STATUS_INVALID_PARAMETER;
            else if (!context->pending || context->delivered) status = STATUS_NO_MORE_ENTRIES;
            else {
                void* output = nullptr;
                status = WdfRequestRetrieveOutputBuffer(request, sizeof(RequestHeader) + context->payloadLength, &output, nullptr);
                if (NT_SUCCESS(status)) {
                    RequestHeader header{context->pendingId, context->opcode, context->payloadLength, context->outputCapacity, 0};
                    memcpy(output, &header, sizeof(header));
                    memcpy(static_cast<BYTE*>(output) + sizeof(header), context->payload, context->payloadLength);
                    context->delivered = true;
                    WdfRequestCompleteWithInformation(request, STATUS_SUCCESS, sizeof(header) + context->payloadLength); return;
                }
            }
        } else if (code == BRIDGE_COMPLETE) {
            if (!context->pending || !context->delivered || inputLength < sizeof(CompleteHeader)) status = STATUS_INVALID_PARAMETER;
            else {
                auto header = static_cast<CompleteHeader*>(input);
                if (header->id != context->pendingId || header->length > BRIDGE_MAX_APDU || inputLength != sizeof(*header) + header->length ||
                    header->length > context->outputCapacity || (header->status && NT_SUCCESS(static_cast<NTSTATUS>(header->status))) || (!NT_SUCCESS(static_cast<NTSTATUS>(header->status)) && header->length)) status = STATUS_INVALID_PARAMETER;
                else if (!NT_SUCCESS(static_cast<NTSTATUS>(header->status))) { FinishPending(context, static_cast<NTSTATUS>(header->status)); Detach(context); status = STATUS_SUCCESS; }
                else {
                    auto payload = static_cast<BYTE*>(input) + sizeof(*header);
                    size_t prefix = context->opcode == OP_TRANSMIT ? sizeof(SCARD_IO_REQUEST) : 0;
                    bool valid = context->opcode == OP_TRANSMIT ? header->length >= 2 :
                        context->opcode == OP_RESET ? header->length >= 2 && header->length <= 33 && (payload[0] == 0x3b || payload[0] == 0x3f) : header->length == 0;
                    if (!valid) status = STATUS_INVALID_PARAMETER;
                    else {
                        // Snapshot metadata before TakePending clears the pending payload;
                        // unmark cancellation before retrieving or writing the output buffer.
                        ULONG opcode = context->opcode;
                        WDFREQUEST pending = TakePending(context);
                        if (!pending) { WdfRequestComplete(request, STATUS_CANCELLED); return; }
                        void* output = nullptr;
                        status = prefix + header->length ? WdfRequestRetrieveOutputBuffer(pending, prefix + header->length, &output, nullptr) : STATUS_SUCCESS;
                        if (NT_SUCCESS(status)) {
                            if (prefix) { SCARD_IO_REQUEST pci{context->protocol, sizeof(SCARD_IO_REQUEST)}; memcpy(output, &pci, sizeof(pci)); }
                            if (header->length) memcpy(static_cast<BYTE*>(output) + prefix, payload, header->length);
                            if (opcode == OP_RESET) { memcpy(context->atr, payload, header->length); context->atrLength = header->length; context->protocol = context->supported; context->state = SCARD_SPECIFIC; }
                            if (opcode == OP_POWER_OFF) { context->state = SCARD_PRESENT; context->protocol = 0; }
                            WdfRequestCompleteWithInformation(pending, STATUS_SUCCESS, prefix + header->length);
                        } else {
                            WdfRequestComplete(pending, status);
                            Detach(context);
                        }
                    }
                }
            }
        }
    } else if (code == IOCTL_SMARTCARD_GET_STATE) {
        ULONG state = context->state; status = CopyOutput(request, &state, sizeof(state)); if (NT_SUCCESS(status)) return;
    } else if (code == IOCTL_SMARTCARD_IS_PRESENT || code == IOCTL_SMARTCARD_IS_ABSENT) {
        bool satisfied = code == IOCTL_SMARTCARD_IS_PRESENT ? context->attached : !context->attached;
        if (satisfied) status = STATUS_SUCCESS;
        else { status = WdfRequestForwardToIoQueue(request, code == IOCTL_SMARTCARD_IS_PRESENT ? context->presentWaiters : context->absentWaiters); if (NT_SUCCESS(status)) return; }
    } else if (code == IOCTL_SMARTCARD_GET_ATTRIBUTE && inputLength == sizeof(ULONG)) {
        ULONG attribute = *static_cast<ULONG*>(input); ULONG value = 0;
        static const char vendor[] = "DNIe Bridge"; static const char type[] = "Android NFC Private Reader";
        const void* bytes = &value; size_t size = sizeof(value);
        switch (attribute) {
        case SCARD_ATTR_VENDOR_NAME: bytes = vendor; size = sizeof(vendor); break;
        case SCARD_ATTR_VENDOR_IFD_TYPE: bytes = type; size = sizeof(type); break;
        case SCARD_ATTR_VENDOR_IFD_VERSION: value = 0x00020000; break;
        case SCARD_ATTR_CHANNEL_ID: value = 0xF0000000; break;
        case SCARD_ATTR_PROTOCOL_TYPES: value = SCARD_PROTOCOL_T0 | SCARD_PROTOCOL_T1; break;
        case SCARD_ATTR_CURRENT_PROTOCOL_TYPE: value = context->protocol; break;
        case SCARD_ATTR_ATR_STRING: if (!context->attached) { WdfRequestComplete(request, STATUS_DEVICE_NOT_CONNECTED); return; } bytes = context->atr; size = context->atrLength; break;
        case SCARD_ATTR_ICC_PRESENCE: value = context->attached ? 2 : 0; bytes = &value; size = 1; break;
        case SCARD_ATTR_DEVICE_UNIT: value = 0; break;
        default: WdfRequestComplete(request, STATUS_NOT_SUPPORTED); return;
        }
        status = CopyOutput(request, bytes, size); if (NT_SUCCESS(status)) return;
    } else if (code == IOCTL_SMARTCARD_SET_PROTOCOL && inputLength == sizeof(ULONG)) {
        ULONG protocols = *static_cast<ULONG*>(input);
        if (!context->attached) status = STATUS_DEVICE_NOT_CONNECTED;
        else if (!context->protocol) status = STATUS_INVALID_DEVICE_STATE;
        else if (!(protocols & context->supported)) status = STATUS_NOT_SUPPORTED;
        else { context->protocol = context->supported; context->state = SCARD_SPECIFIC; status = CopyOutput(request, &context->protocol, sizeof(ULONG)); if (NT_SUCCESS(status)) return; }
    } else if (code == IOCTL_SMARTCARD_POWER && inputLength == sizeof(ULONG)) {
        ULONG power = *static_cast<ULONG*>(input);
        ULONG operation = power == SCARD_POWER_DOWN ? OP_POWER_OFF : power == SCARD_COLD_RESET || power == SCARD_WARM_RESET ? OP_RESET : 0;
        status = operation ? QueueBackend(context, request, operation, static_cast<BYTE*>(input), sizeof(ULONG), operation == OP_RESET ? 33 : 0) : STATUS_NOT_SUPPORTED;
        if (NT_SUCCESS(status)) return;
    } else if (code == IOCTL_SMARTCARD_TRANSMIT && inputLength >= sizeof(SCARD_IO_REQUEST) && outputLength >= sizeof(SCARD_IO_REQUEST) + 2) {
        auto pci = static_cast<SCARD_IO_REQUEST*>(input);
        if (!context->protocol || context->state != SCARD_SPECIFIC) status = STATUS_INVALID_DEVICE_STATE;
        else if (pci->cbPciLength != sizeof(SCARD_IO_REQUEST) || pci->dwProtocol != context->protocol || inputLength - pci->cbPciLength < 4 || inputLength - pci->cbPciLength > BRIDGE_MAX_APDU) status = STATUS_INVALID_PARAMETER;
        else { status = QueueBackend(context, request, OP_TRANSMIT, static_cast<BYTE*>(input) + pci->cbPciLength,
            static_cast<ULONG>(inputLength - pci->cbPciLength), static_cast<ULONG>(min(outputLength - sizeof(SCARD_IO_REQUEST), static_cast<size_t>(BRIDGE_MAX_APDU))), pci->cbPciLength); if (NT_SUCCESS(status)) return; }
    }
    WdfRequestComplete(request, status);
}

NTSTATUS DeviceAdd(WDFDRIVER, PWDFDEVICE_INIT init) {
    WdfDeviceInitSetIoType(init, WdfDeviceIoBuffered);
    WDF_FILEOBJECT_CONFIG files; WDF_FILEOBJECT_CONFIG_INIT(&files, WDF_NO_EVENT_CALLBACK, WDF_NO_EVENT_CALLBACK, FileCleanup);
    WdfDeviceInitSetFileObjectConfig(init, &files, WDF_NO_OBJECT_ATTRIBUTES);
    WDF_PNPPOWER_EVENT_CALLBACKS power; WDF_PNPPOWER_EVENT_CALLBACKS_INIT(&power); power.EvtDeviceD0Exit = D0Exit; power.EvtDeviceD0Entry = D0Entry;
    WdfDeviceInitSetPnpPowerEventCallbacks(init, &power);
    WDF_OBJECT_ATTRIBUTES attributes; WDF_OBJECT_ATTRIBUTES_INIT_CONTEXT_TYPE(&attributes, ReaderContext);
    attributes.SynchronizationScope = WdfSynchronizationScopeDevice;
    attributes.ExecutionLevel = WdfExecutionLevelPassive;
    attributes.EvtCleanupCallback = DeviceCleanup;
    WDFDEVICE device;
    NTSTATUS status = WdfDeviceCreate(&init, &attributes, &device);
    if (!NT_SUCCESS(status)) return status;
    auto context = Reader(device); context->state = SCARD_ABSENT;
    WDF_OBJECT_ATTRIBUTES lockAttributes; WDF_OBJECT_ATTRIBUTES_INIT(&lockAttributes); lockAttributes.ParentObject = device;
    status = WdfWaitLockCreate(&lockAttributes, &context->lock); if (!NT_SUCCESS(status)) return status;
    status = WdfDeviceCreateDeviceInterface(device, &GUID_DEVINTERFACE_SMARTCARD_READER, nullptr); if (!NT_SUCCESS(status)) return status;
    status = WdfDeviceCreateDeviceInterface(device, &GUID_DNI_BRIDGE_BACKEND, nullptr); if (!NT_SUCCESS(status)) return status;
    WDF_IO_QUEUE_CONFIG queue; WDF_IO_QUEUE_CONFIG_INIT(&queue, WdfIoQueueDispatchManual);
    queue.PowerManaged = WdfFalse;
    status = WdfIoQueueCreate(device, &queue, WDF_NO_OBJECT_ATTRIBUTES, &context->presentWaiters); if (!NT_SUCCESS(status)) return status;
    status = WdfIoQueueCreate(device, &queue, WDF_NO_OBJECT_ATTRIBUTES, &context->absentWaiters); if (!NT_SUCCESS(status)) return status;
    WDF_IO_QUEUE_CONFIG_INIT_DEFAULT_QUEUE(&queue, WdfIoQueueDispatchParallel); queue.EvtIoDeviceControl = DeviceControl;
    queue.PowerManaged = WdfTrue; queue.EvtIoStop = IoStop;
    status = WdfIoQueueCreate(device, &queue, WDF_NO_OBJECT_ATTRIBUTES, nullptr); if (!NT_SUCCESS(status)) return status;
    WDF_TIMER_CONFIG timerConfig; WDF_TIMER_CONFIG_INIT(&timerConfig, TimerTick);
    WDF_OBJECT_ATTRIBUTES timerAttributes; WDF_OBJECT_ATTRIBUTES_INIT(&timerAttributes); timerAttributes.ParentObject = device;
    timerAttributes.ExecutionLevel = WdfExecutionLevelPassive;
    status = WdfTimerCreate(&timerConfig, &timerAttributes, &context->timer);
    return status;
}

extern "C" DRIVER_INITIALIZE DriverEntry;
extern "C" NTSTATUS DriverEntry(PDRIVER_OBJECT object, PUNICODE_STRING registry) {
    WDF_DRIVER_CONFIG config; WDF_DRIVER_CONFIG_INIT(&config, DeviceAdd);
    return WdfDriverCreate(object, registry, WDF_NO_OBJECT_ATTRIBUTES, &config, WDF_NO_HANDLE);
}
