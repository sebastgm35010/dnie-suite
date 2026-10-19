#pragma once
// Test-only WDF seam. Windows/PCSC/ABI are real SDK headers; no device installed.
#include <winternl.h>
#ifndef NT_SUCCESS
#define NT_SUCCESS(status) (((NTSTATUS)(status)) >= 0)
#endif
#include <deque>
#include <vector>
#include <mutex>
#include <stdexcept>
#include <cstdlib>
struct FakeDevice; struct FakeQueue; struct FakeRequest; struct FakeTimer; struct FakeFile;
using WDFOBJECT = void*; using WDFDRIVER = void*; using WDFDEVICE = FakeDevice*;
using WDFQUEUE = FakeQueue*; using WDFREQUEST = FakeRequest*; using WDFTIMER = FakeTimer*;
using WDFFILEOBJECT = FakeFile*; using WDFWAITLOCK = std::mutex*;
using WDF_POWER_DEVICE_STATE = int;
using EVT_WDF_DRIVER_DEVICE_ADD = NTSTATUS(WDFDRIVER, struct FakeInit*);
using EVT_WDF_IO_QUEUE_IO_DEVICE_CONTROL = void(WDFQUEUE,WDFREQUEST,size_t,size_t,ULONG);
using EVT_WDF_IO_QUEUE_IO_STOP = void(WDFQUEUE,WDFREQUEST,ULONG);
using EVT_WDF_REQUEST_CANCEL = void(WDFREQUEST);
using EVT_WDF_TIMER = void(WDFTIMER); using EVT_WDF_FILE_CLEANUP = void(WDFFILEOBJECT);
using EVT_WDF_DEVICE_D0_EXIT = NTSTATUS(WDFDEVICE,WDF_POWER_DEVICE_STATE);
using EVT_WDF_DEVICE_D0_ENTRY = NTSTATUS(WDFDEVICE,WDF_POWER_DEVICE_STATE);
using EVT_WDF_DEVICE_SELF_MANAGED_IO_FLUSH = void(WDFDEVICE);
using EVT_WDF_OBJECT_CONTEXT_CLEANUP = void(WDFOBJECT);
constexpr int WdfFalse=0, WdfTrue=1, WdfSynchronizationScopeNone=0, WdfSynchronizationScopeDevice=1,
 WdfExecutionLevelPassive=1,WdfIoQueueDispatchManual=1,WdfIoQueueDispatchParallel=2,WdfDeviceIoBuffered=1;
constexpr ULONG WdfRequestStopActionSuspend=1, WdfRequestStopActionPurge=2, WdfRequestStopRequestCancelable=4;
#ifndef SCARD_PROTOCOL_DEFAULT
#define SCARD_PROTOCOL_DEFAULT 0x80000000
#endif
#ifndef SCARD_PROTOCOL_OPTIMAL
#define SCARD_PROTOCOL_OPTIMAL 0x00000000
#endif
#define WDF_NO_EVENT_CALLBACK nullptr
#define WDF_NO_CONTEXT nullptr
#define WDF_NO_HANDLE nullptr
#define WDF_NO_OBJECT_ATTRIBUTES nullptr
#define WDF_REL_TIMEOUT_IN_MS(value) (value)
struct WDF_OBJECT_ATTRIBUTES { size_t ContextSize{}; void* ParentObject{}; int SynchronizationScope{},ExecutionLevel{}; EVT_WDF_OBJECT_CONTEXT_CLEANUP* EvtCleanupCallback{}; };
#define WDF_OBJECT_ATTRIBUTES_INIT(value) (*(value)=WDF_OBJECT_ATTRIBUTES{})
#define WDF_OBJECT_ATTRIBUTES_INIT_CONTEXT_TYPE(value,type) do { WDF_OBJECT_ATTRIBUTES_INIT(value); (value)->ContextSize=sizeof(type); } while(0)
#define WDF_DECLARE_CONTEXT_TYPE_WITH_NAME(type,name) inline type* name(WDFDEVICE device) { return static_cast<type*>(device->context); }
struct WDF_IO_QUEUE_CONFIG { int dispatch{},PowerManaged{WdfTrue}; bool isDefault{}; EVT_WDF_IO_QUEUE_IO_DEVICE_CONTROL* EvtIoDeviceControl{}; EVT_WDF_IO_QUEUE_IO_STOP* EvtIoStop{}; };
#define WDF_IO_QUEUE_CONFIG_INIT(value,mode) do { *(value)=WDF_IO_QUEUE_CONFIG{}; (value)->dispatch=(mode); } while(0)
#define WDF_IO_QUEUE_CONFIG_INIT_DEFAULT_QUEUE(value,mode) do { WDF_IO_QUEUE_CONFIG_INIT(value,mode); (value)->isDefault=true; } while(0)
struct WDF_FILEOBJECT_CONFIG {};
#define WDF_FILEOBJECT_CONFIG_INIT(...) ((void)0)
struct WDF_PNPPOWER_EVENT_CALLBACKS { EVT_WDF_DEVICE_D0_EXIT* EvtDeviceD0Exit{}; EVT_WDF_DEVICE_D0_ENTRY* EvtDeviceD0Entry{}; EVT_WDF_DEVICE_SELF_MANAGED_IO_FLUSH* EvtDeviceSelfManagedIoFlush{}; };
#define WDF_PNPPOWER_EVENT_CALLBACKS_INIT(value) (*(value)=WDF_PNPPOWER_EVENT_CALLBACKS{})
struct WDF_TIMER_CONFIG { EVT_WDF_TIMER* callback{}; };
#define WDF_TIMER_CONFIG_INIT(value,fn) ((value)->callback=(fn))
struct WDF_DRIVER_CONFIG {};
#define WDF_DRIVER_CONFIG_INIT(...) ((void)0)
struct FakeInit { FakeDevice* device{}; };
using PWDFDEVICE_INIT = FakeInit*;
struct FakeQueue { FakeDevice* device{}; WDF_IO_QUEUE_CONFIG config; std::deque<FakeRequest*> requests; bool stopped{}; int purges{},starts{}; };
struct FakeRequest { FakeQueue* queue{}; FakeFile* file{}; std::vector<BYTE> input,output; bool marked{},cancelQueued{},cancelRunning{},completed{}; int completions{}; NTSTATUS status{}; size_t information{}; EVT_WDF_REQUEST_CANCEL* cancel{}; };
struct FakeTimer { FakeDevice* device{}; bool running{}; int starts{}; };
struct FakeFile { FakeDevice* device{}; };
struct FakeDevice { void* context{}; WDFQUEUE defaultQueue{}; std::vector<WDFQUEUE> queues; WDFWAITLOCK lock{}; WDFTIMER timer{}; int interfaceToggles{}; ~FakeDevice() { for(auto q:queues) delete q; delete timer; delete lock; free(context); } };
inline WDFDEVICE WdfIoQueueGetDevice(WDFQUEUE q) { return q->device; }
inline WDFQUEUE WdfRequestGetIoQueue(WDFREQUEST r) { return r->queue; }
inline WDFFILEOBJECT WdfRequestGetFileObject(WDFREQUEST r) { return r->file; }
inline WDFDEVICE WdfFileObjectGetDevice(WDFFILEOBJECT f) { return f->device; }
inline void WdfWaitLockAcquire(WDFWAITLOCK l,void*) { l->lock(); }
inline void WdfWaitLockRelease(WDFWAITLOCK l) { l->unlock(); }
inline NTSTATUS WdfWaitLockCreate(WDF_OBJECT_ATTRIBUTES* a,WDFWAITLOCK* l) { *l=new std::mutex; static_cast<FakeDevice*>(a->ParentObject)->lock=*l; return STATUS_SUCCESS; }
inline NTSTATUS WdfRequestMarkCancelableEx(WDFREQUEST r,EVT_WDF_REQUEST_CANCEL* c) { if(r->cancelQueued) return STATUS_CANCELLED; r->marked=true; r->cancel=c; return STATUS_SUCCESS; }
inline NTSTATUS WdfRequestUnmarkCancelable(WDFREQUEST r) { if(r->cancelQueued) return STATUS_CANCELLED; if(!r->marked) throw std::runtime_error("unmark unowned request"); r->marked=false; return STATUS_SUCCESS; }
inline void WdfRequestCompleteWithInformation(WDFREQUEST r,NTSTATUS status,size_t information) { if(r->completed || (r->marked&&!r->cancelRunning)) throw std::runtime_error("completion without exclusive ownership"); r->completed=true; ++r->completions; r->status=status; r->information=information; }
inline void WdfRequestComplete(WDFREQUEST r,NTSTATUS status) { WdfRequestCompleteWithInformation(r,status,0); }
inline NTSTATUS WdfRequestRetrieveInputBuffer(WDFREQUEST r,size_t length,void** p,size_t*) { if(r->input.size()<length)return STATUS_BUFFER_TOO_SMALL; *p=r->input.data(); return STATUS_SUCCESS; }
inline NTSTATUS WdfRequestRetrieveOutputBuffer(WDFREQUEST r,size_t length,void** p,size_t*) { if(r->output.size()<length)return STATUS_BUFFER_TOO_SMALL; *p=r->output.data(); return STATUS_SUCCESS; }
inline NTSTATUS WdfRequestForwardToIoQueue(WDFREQUEST r,WDFQUEUE q) { if(q->stopped) return STATUS_INVALID_DEVICE_STATE; r->queue=q; q->requests.push_back(r); return STATUS_SUCCESS; }
inline NTSTATUS WdfIoQueueRetrieveNextRequest(WDFQUEUE q,WDFREQUEST* r) { if(q->stopped||q->requests.empty()) return STATUS_NO_MORE_ENTRIES; *r=q->requests.front(); q->requests.pop_front(); return STATUS_SUCCESS; }
inline void WdfIoQueuePurgeSynchronously(WDFQUEUE q) { q->stopped=true; ++q->purges; while(!q->requests.empty()) { auto r=q->requests.front(); q->requests.pop_front(); WdfRequestComplete(r,STATUS_CANCELLED); } }
inline void WdfIoQueueStart(WDFQUEUE q) { q->stopped=false; ++q->starts; }
inline void WdfTimerStart(WDFTIMER t,LONGLONG) { t->running=true; ++t->starts; }
inline BOOLEAN WdfTimerStop(WDFTIMER t,BOOLEAN) { t->running=false; return TRUE; }
inline WDFOBJECT WdfTimerGetParentObject(WDFTIMER t) { return t->device; }
inline NTSTATUS WdfDeviceCreate(PWDFDEVICE_INIT* init,WDF_OBJECT_ATTRIBUTES* a,WDFDEVICE* d) { *d=new FakeDevice; (*d)->context=calloc(1,a->ContextSize); (*init)->device=*d; return STATUS_SUCCESS; }
inline NTSTATUS WdfDeviceCreateDeviceInterface(WDFDEVICE,const GUID*,void*) { return STATUS_SUCCESS; }
inline void WdfDeviceSetDeviceInterfaceState(WDFDEVICE d, const GUID*, void*, BOOLEAN) { ++d->interfaceToggles; }
inline NTSTATUS WdfIoQueueCreate(WDFDEVICE d,WDF_IO_QUEUE_CONFIG* c,WDF_OBJECT_ATTRIBUTES*,WDFQUEUE* q) { auto created=new FakeQueue; created->device=d; created->config=*c; d->queues.push_back(created); if(c->isDefault)d->defaultQueue=created; if(q)*q=created; return STATUS_SUCCESS; }
inline NTSTATUS WdfTimerCreate(WDF_TIMER_CONFIG*,WDF_OBJECT_ATTRIBUTES* a,WDFTIMER* t) { *t=new FakeTimer; (*t)->device=static_cast<FakeDevice*>(a->ParentObject); (*t)->device->timer=*t; return STATUS_SUCCESS; }
inline void WdfDeviceInitSetIoType(PWDFDEVICE_INIT,int) {}
inline void WdfDeviceInitSetFileObjectConfig(PWDFDEVICE_INIT,WDF_FILEOBJECT_CONFIG*,WDF_OBJECT_ATTRIBUTES*) {}
inline void WdfDeviceInitSetPnpPowerEventCallbacks(PWDFDEVICE_INIT,WDF_PNPPOWER_EVENT_CALLBACKS*) {}
using PDRIVER_OBJECT = void*; using DRIVER_INITIALIZE = NTSTATUS(PDRIVER_OBJECT,PUNICODE_STRING);
inline NTSTATUS WdfDriverCreate(PDRIVER_OBJECT,PUNICODE_STRING,WDF_OBJECT_ATTRIBUTES*,WDF_DRIVER_CONFIG*,void*) { return STATUS_SUCCESS; }
