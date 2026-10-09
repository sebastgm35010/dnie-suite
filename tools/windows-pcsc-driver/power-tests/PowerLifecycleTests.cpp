// Executes production DniBridgeReader.cpp with test-only WDF ownership/queue stubs.
// This does not simulate a real Windows power transition or install a driver.
#include <iostream>
#include <memory>
#include <thread>
#include "../DniBridgeReader.cpp"
static int checks=0;
static void Check(bool condition,const char* message) { ++checks; if(!condition)throw std::runtime_error(message); }
struct Fixture {
    std::unique_ptr<FakeDevice> device; FakeFile backend{};
    Fixture() { FakeInit init; Check(NT_SUCCESS(DeviceAdd(nullptr,&init)),"DeviceAdd"); device.reset(init.device); backend.device=device.get(); Check(NT_SUCCESS(D0Entry(device.get(),0)),"D0Entry"); }
    ReaderContext* context() { return Reader(device.get()); }
    FakeRequest request(size_t output=0) { FakeRequest r; r.queue=device->defaultQueue; r.output.resize(output,0xCC); return r; }
    void ioctl(FakeRequest& r,ULONG code) { DeviceControl(device->defaultQueue,&r,r.output.size(),r.input.size(),code); }
    void attach() { auto r=request(); r.file=&backend; const BYTE atr[]={0x3b,0x00}; AttachHeader header{BRIDGE_VERSION,SCARD_PROTOCOL_T1,sizeof(atr),0}; r.input.resize(sizeof(header)+sizeof(atr)); memcpy(r.input.data(),&header,sizeof(header)); memcpy(r.input.data()+sizeof(header),atr,sizeof(atr)); ioctl(r,BRIDGE_ATTACH); Check(r.completed&&r.status==STATUS_SUCCESS,"attach succeeds"); }
    FakeRequest transmit() { auto r=request(sizeof(SCARD_IO_REQUEST)+16); SCARD_IO_REQUEST pci{SCARD_PROTOCOL_T1,sizeof(SCARD_IO_REQUEST)}; const BYTE apdu[]={0,0xA4,4,0}; r.input.resize(sizeof(pci)+sizeof(apdu)); memcpy(r.input.data(),&pci,sizeof(pci)); memcpy(r.input.data()+sizeof(pci),apdu,sizeof(apdu)); return r; }
    void deliver() { auto r=request(sizeof(RequestHeader)+8); r.file=&backend; ioctl(r,BRIDGE_GET_REQUEST); Check(r.status==STATUS_SUCCESS&&r.completed&&context()->delivered,"deliver once"); }
    void reply() { auto r=request(); r.file=&backend; CompleteHeader header{context()->pendingId,0,2}; r.input.resize(sizeof(header)+2); memcpy(r.input.data(),&header,sizeof(header)); r.input[sizeof(header)]=0x90; ioctl(r,BRIDGE_COMPLETE); Check(r.completed&&r.status==STATUS_SUCCESS,"backend reply"); }
};
static void Stop(bool delivered) {
    Fixture f; f.attach(); auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); auto id=f.context()->pendingId;
    if(delivered)f.deliver();
    IoStop(f.device->defaultQueue,&r,WdfRequestStopActionSuspend|WdfRequestStopRequestCancelable);
    Check(r.completed&&r.completions==1&&r.status==STATUS_CANCELLED,"suspend completes once");
    Check(!f.context()->pending&&!f.context()->attached&&!f.context()->backendOwner,"suspend invalidates session");
    Check(f.context()->payloadLength==0&&f.context()->payload[0]==0,"suspend wipes payload");
    D0Exit(f.device.get(),0); D0Entry(f.device.get(),0);
    Check(!f.context()->attached&&!f.context()->pending,"resume does not replay/attach");
    f.attach(); auto next=f.transmit(); f.ioctl(next,IOCTL_SMARTCARD_TRANSMIT);
    Check(f.context()->pendingId>id,"resume request id not reused"); f.deliver(); f.reply();
    Check(next.completions==1&&next.information==sizeof(SCARD_IO_REQUEST)+2,"fresh session works");
}
static void Cancellation(bool concurrent) {
    Fixture f; f.attach(); auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); f.deliver();
    r.cancelQueued=true; r.cancelRunning=true;
    if(concurrent) {
        std::thread stop([&] { IoStop(f.device->defaultQueue,&r,WdfRequestStopActionPurge|WdfRequestStopRequestCancelable); });
        std::thread cancel([&] { CancelPending(&r); }); stop.join(); cancel.join();
    } else {
        IoStop(f.device->defaultQueue,&r,WdfRequestStopActionSuspend|WdfRequestStopRequestCancelable);
        Check(!r.completed&&f.context()->pending==&r,"cancel routine owns completion after unmark loses");
        Check(!f.context()->attached&&!f.context()->backendOwner,"lost unmark still invalidates backend");
        CancelPending(&r);
    }
    Check(r.completions==1&&r.status==STATUS_CANCELLED,"race completes once");
    Check(!f.context()->pending&&!f.context()->attached&&f.context()->payloadLength==0,"cancel wipes and detaches");
    auto stale=f.request(); stale.file=&f.backend; f.ioctl(stale,BRIDGE_GET_REQUEST);
    Check(stale.status==STATUS_ACCESS_DENIED,"old backend cannot reuse session");
}
static void QueueLifecycle() {
    Fixture f;
    Check(f.device->defaultQueue->config.PowerManaged==WdfTrue&&f.device->defaultQueue->config.EvtIoStop==IoStop,"power queue registers IoStop");
    Check(f.context()->presentWaiters->config.PowerManaged==WdfFalse&&f.context()->absentWaiters->config.PowerManaged==WdfFalse,"manual notification queues not power managed");
    auto wait=f.request(); f.ioctl(wait,IOCTL_SMARTCARD_IS_PRESENT); Check(!wait.completed,"queued presence waiter");
    D0Exit(f.device.get(),0); Check(wait.completed&&wait.status==STATUS_CANCELLED,"D0Exit purges waiter to completion");
    Check(f.context()->presentWaiters->stopped&&f.context()->absentWaiters->stopped,"both queues purged");
    auto suspended=f.request(); f.ioctl(suspended,IOCTL_SMARTCARD_GET_STATE); Check(suspended.status==STATUS_DEVICE_NOT_READY,"no access outside D0");
    D0Entry(f.device.get(),0); auto next=f.request(); f.ioctl(next,IOCTL_SMARTCARD_IS_PRESENT); f.attach(); Check(next.completed&&next.status==STATUS_SUCCESS,"resume notification restored");
    auto absent=f.request(); f.ioctl(absent,IOCTL_SMARTCARD_IS_ABSENT); Check(!absent.completed,"absence waiter queued");
    auto transmit=f.transmit(); f.ioctl(transmit,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); FileCleanup(&f.backend);
    Check(absent.completed&&absent.status==STATUS_SUCCESS&&transmit.status==STATUS_DEVICE_NOT_CONNECTED,"file close detaches, retires, notifies");
    Check(!f.context()->timer->running||f.context()->powered,"timer only running in D0");
}
static void TimeoutAndCompletion() {
    Fixture f; f.attach(); auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); f.context()->deadline=0;
    TimerTick(f.context()->timer); Check(r.completions==1&&r.status==STATUS_IO_TIMEOUT&&!f.context()->attached,"timeout detaches once");
    f.attach(); auto valid=f.transmit(); f.ioctl(valid,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); f.reply();
    IoStop(f.device->defaultQueue,&valid,WdfRequestStopActionSuspend); Check(valid.completions==1&&valid.status==STATUS_SUCCESS,"completed request is never completed again");
    D0Exit(f.device.get(),0); Check(!f.context()->timer->running,"timer stopped after exit");
    TimerTick(f.context()->timer); Check(!f.context()->timer->running,"late timer cannot restart outside D0");
}
static void PowerOperations() {
    for(ULONG operation : {SCARD_COLD_RESET,SCARD_WARM_RESET,SCARD_POWER_DOWN}) {
        Fixture f; f.attach(); auto r=f.request(operation==SCARD_POWER_DOWN ? 0 : 33);
        r.input.resize(sizeof(operation)); memcpy(r.input.data(),&operation,sizeof(operation));
        f.ioctl(r,IOCTL_SMARTCARD_POWER); Check(!r.completed&&f.context()->pending==&r,"power operation retained");
        f.deliver(); IoStop(f.device->defaultQueue,&r,WdfRequestStopActionSuspend|WdfRequestStopRequestCancelable);
        Check(r.completions==1&&r.status==STATUS_CANCELLED&&!f.context()->attached,"reset/poweroff not replayed");
        D0Exit(f.device.get(),0); D0Entry(f.device.get(),0); Check(!f.context()->pending,"power operation gone after resume");
    }
}
static void StaleReplyAndEarlyCancel() {
    Fixture f; f.attach(); auto first=f.transmit(); f.ioctl(first,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); auto oldId=f.context()->pendingId;
    IoStop(f.device->defaultQueue,&first,WdfRequestStopActionSuspend|WdfRequestStopRequestCancelable);
    D0Exit(f.device.get(),0); D0Entry(f.device.get(),0); f.attach();
    auto next=f.transmit(); f.ioctl(next,IOCTL_SMARTCARD_TRANSMIT); f.deliver();
    auto stale=f.request(); stale.file=&f.backend; CompleteHeader header{oldId,0,2}; stale.input.resize(sizeof(header)+2); memcpy(stale.input.data(),&header,sizeof(header)); stale.input[sizeof(header)]=0x90; f.ioctl(stale,BRIDGE_COMPLETE);
    Check(stale.status==STATUS_INVALID_PARAMETER&&!next.completed,"late response cannot complete new request"); f.reply(); Check(next.completions==1,"new request completed exactly once");
    auto canceled=f.transmit(); canceled.cancelQueued=true; f.ioctl(canceled,IOCTL_SMARTCARD_TRANSMIT);
    Check(canceled.completions==1&&canceled.status==STATUS_CANCELLED&&!f.context()->pending,"mark cancellation failure does not retain request");
    Check(f.context()->payloadLength==0,"early cancellation wipes command");
    auto actual=f.transmit(); f.ioctl(actual,IOCTL_SMARTCARD_TRANSMIT); actual.cancelQueued=true; actual.cancelRunning=true; CancelPending(&actual);
    IoStop(f.device->defaultQueue,&actual,WdfRequestStopActionSuspend);
    Check(actual.completions==1&&!f.context()->attached,"cancel before stop invalidates session without double completion");
}
static void EmptyPollWithoutSystemClients() {
    Fixture f; f.attach(); auto empty=f.request(sizeof(RequestHeader)+8); empty.file=&f.backend;
    f.ioctl(empty,BRIDGE_GET_REQUEST);
    Check(empty.completed&&empty.status==STATUS_NO_MORE_ENTRIES&&empty.information==0,"empty owner poll returns no entries without external clients");
    auto unowned=f.request(sizeof(RequestHeader)+8); f.ioctl(unowned,BRIDGE_GET_REQUEST);
    Check(unowned.status==STATUS_ACCESS_DENIED,"unowned empty poll remains denied");
}
int main() {
    try {
        Stop(false); Stop(true); Cancellation(false); QueueLifecycle(); TimeoutAndCompletion(); PowerOperations(); StaleReplyAndEarlyCancel(); EmptyPollWithoutSystemClients();
        for(int i=0;i<200;++i) { Cancellation(true); Stop(i%2==0); QueueLifecycle(); }
        std::cout << "PASS production driver callbacks: " << checks << " checks; 200 concurrent cancellation/stop races and 200 power cycles. No PnP/power/DNI operations.\n";
        return 0;
    } catch(const std::exception& error) { std::cerr << "FAIL: " << error.what() << '\n'; return 1; }
}
