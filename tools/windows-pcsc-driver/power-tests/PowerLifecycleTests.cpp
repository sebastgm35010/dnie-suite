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
    D0Exit(f.device.get(),0);
    Check(!wait.completed,"present waiter survives D0Exit without cancellation");
    auto suspended=f.request(sizeof(ULONG)); f.ioctl(suspended,IOCTL_SMARTCARD_GET_STATE);
    ULONG suspendedState=0; memcpy(&suspendedState,suspended.output.data(),sizeof(suspendedState));
    Check(suspended.completed&&suspended.status==STATUS_SUCCESS&&suspendedState==SCARD_ABSENT,"get_state succeeds during suspension without dropping SCardSvr");
    auto nonPassive=f.request(); nonPassive.file=&f.backend; f.ioctl(nonPassive,BRIDGE_ATTACH);
    Check(nonPassive.status==STATUS_DEVICE_NOT_READY,"active operation rejected outside D0");
    D0Entry(f.device.get(),0);
    Check(!wait.completed,"present waiter still waiting after D0Entry");
    f.attach();
    Check(wait.completed&&wait.status==STATUS_SUCCESS,"resume notification restored");

    auto republishBusy=f.request(); republishBusy.file=&f.backend; f.ioctl(republishBusy,BRIDGE_REPUBLISH);
    Check(republishBusy.status==STATUS_DEVICE_BUSY,"republish rejected while card attached");
    auto detach=f.request(); detach.file=&f.backend; f.ioctl(detach,BRIDGE_DETACH);
    int initialToggles=f.device->interfaceToggles;
    auto republishOk=f.request(); republishOk.file=&f.backend; f.ioctl(republishOk,BRIDGE_REPUBLISH);
    Check(republishOk.completed&&republishOk.status==STATUS_SUCCESS&&f.device->interfaceToggles==initialToggles+2,"BRIDGE_REPUBLISH toggles interface state");

    auto waitFlush=f.request(); f.ioctl(waitFlush,IOCTL_SMARTCARD_IS_PRESENT); Check(!waitFlush.completed,"queued presence waiter for flush");
    SelfManagedIoFlush(f.device.get());
    Check(waitFlush.completed&&waitFlush.status==STATUS_CANCELLED,"SelfManagedIoFlush purges present waiter");
    Check(f.context()->presentWaiters->stopped&&f.context()->absentWaiters->stopped,"both queues stopped after flush");
    D0Entry(f.device.get(),0);

    f.attach();
    ULONG power=SCARD_WARM_RESET; auto resetZero=f.request(0); resetZero.input.resize(sizeof(power)); memcpy(resetZero.input.data(),&power,sizeof(power));
    f.ioctl(resetZero,IOCTL_SMARTCARD_POWER);
    Check(!resetZero.completed&&f.context()->pending==&resetZero,"power reset with 0 output accepted");
    f.deliver();
    auto replyReset=f.request(); replyReset.file=&f.backend;
    const BYTE atrBytes[]={0x3b,0x00};
    CompleteHeader replyHdr{f.context()->pendingId,0,sizeof(atrBytes)};
    replyReset.input.resize(sizeof(replyHdr)+sizeof(atrBytes));
    memcpy(replyReset.input.data(),&replyHdr,sizeof(replyHdr));
    memcpy(replyReset.input.data()+sizeof(replyHdr),atrBytes,sizeof(atrBytes));
    f.ioctl(replyReset,BRIDGE_COMPLETE);
    Check(replyReset.completed&&replyReset.status==STATUS_SUCCESS,"reset backend reply accepted");
    Check(resetZero.completed&&resetZero.status==STATUS_SUCCESS&&resetZero.information==0,"power reset with 0 output completes with 0 bytes");

    ULONG defaultProto=SCARD_PROTOCOL_DEFAULT; auto setProto=f.request(sizeof(defaultProto)); setProto.input.resize(sizeof(defaultProto)); memcpy(setProto.input.data(),&defaultProto,sizeof(defaultProto));
    f.ioctl(setProto,IOCTL_SMARTCARD_SET_PROTOCOL);
    Check(setProto.completed&&setProto.status==STATUS_SUCCESS&&f.context()->protocol==SCARD_PROTOCOL_T1,"SCARD_PROTOCOL_DEFAULT negotiated successfully");

    ULONG optimalProto=SCARD_PROTOCOL_OPTIMAL; auto setOpt=f.request(sizeof(optimalProto)); setOpt.input.resize(sizeof(optimalProto)); memcpy(setOpt.input.data(),&optimalProto,sizeof(optimalProto));
    f.ioctl(setOpt,IOCTL_SMARTCARD_SET_PROTOCOL);
    Check(setOpt.completed&&setOpt.status==STATUS_SUCCESS&&f.context()->protocol==SCARD_PROTOCOL_T1,"SCARD_PROTOCOL_OPTIMAL negotiated successfully");

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
    Check(actual.completions==1&&f.context()->attached,"undelivered cancel preserves session without double completion");
}
static void RecoverableCompletion() {
    for(NTSTATUS failure : {STATUS_BUFFER_TOO_SMALL, STATUS_NOT_SUPPORTED}) {
        Fixture f; f.attach(); auto absent=f.request(); f.ioctl(absent,IOCTL_SMARTCARD_IS_ABSENT);
        auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); auto id=f.context()->pendingId;
        auto complete=f.request(); complete.file=&f.backend; CompleteHeader header{id,static_cast<uint32_t>(failure),0};
        complete.input.resize(sizeof(header)); memcpy(complete.input.data(),&header,sizeof(header)); f.ioctl(complete,BRIDGE_COMPLETE);
        Check(complete.status==STATUS_SUCCESS&&r.completions==1&&r.status==failure,"request-local error reaches original caller");
        Check(f.context()->attached&&f.context()->backendOwner==&f.backend&&!absent.completed,"local error does not remove card or revoke owner");
        Check(!f.context()->pending&&f.context()->payloadLength==0,"local error retires payload without replay");
        auto empty=f.request(sizeof(RequestHeader)+8); empty.file=&f.backend; f.ioctl(empty,BRIDGE_GET_REQUEST);
        Check(empty.status==STATUS_NO_MORE_ENTRIES,"owner can poll after local error");
        auto next=f.transmit(); f.ioctl(next,IOCTL_SMARTCARD_TRANSMIT); Check(f.context()->pendingId>id,"next operation uses a new id");
        f.deliver(); f.reply(); Check(next.status==STATUS_SUCCESS&&next.completions==1,"subsequent operation completes normally");
        // Transport loss still removes the card, with no second completion.
        auto fatal=f.transmit(); f.ioctl(fatal,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); header={f.context()->pendingId,static_cast<uint32_t>(STATUS_DEVICE_NOT_CONNECTED),0};
        auto lost=f.request(); lost.file=&f.backend; lost.input.resize(sizeof(header)); memcpy(lost.input.data(),&header,sizeof(header)); f.ioctl(lost,BRIDGE_COMPLETE);
        Check(fatal.completions==1&&!f.context()->attached&&absent.completed,"transport failure still detaches and notifies");
    }
}
static void CompletionBoundaries() {
    Fixture f; f.attach(); auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); f.deliver();
    // Exercise failure to retrieve the caller buffer after a successful response.
    r.output.resize(1); f.reply();
    Check(r.status==STATUS_BUFFER_TOO_SMALL&&r.completions==1&&f.context()->attached,"local output-copy failure preserves synchronized transmit session");
    ULONG protocol=SCARD_PROTOCOL_T0; auto unsupported=f.request(sizeof(protocol)); unsupported.input.resize(sizeof(protocol)); memcpy(unsupported.input.data(),&protocol,sizeof(protocol)); f.ioctl(unsupported,IOCTL_SMARTCARD_SET_PROTOCOL);
    Check(unsupported.status==STATUS_NOT_SUPPORTED&&f.context()->protocol==SCARD_PROTOCOL_T1,"unsupported protocol leaves selected protocol unchanged");
    auto pending=f.transmit(); f.ioctl(pending,IOCTL_SMARTCARD_TRANSMIT);
    auto other=f.transmit(); f.ioctl(other,IOCTL_SMARTCARD_TRANSMIT);
    Check(other.status==STATUS_DEVICE_BUSY&&f.context()->pending==&pending,"overlapping transmit cannot replace an in-flight command");
    protocol=SCARD_PROTOCOL_T1; memcpy(unsupported.input.data(),&protocol,sizeof(protocol)); auto select=f.request(sizeof(protocol)); select.input=unsupported.input; f.ioctl(select,IOCTL_SMARTCARD_SET_PROTOCOL);
    Check(select.status==STATUS_DEVICE_BUSY&&f.context()->pending==&pending,"negotiation cannot race a pending operation");
    f.deliver(); f.reply(); Check(pending.completions==1&&pending.status==STATUS_SUCCESS,"parallel queue still accepts backend completion");
    // A reset failure is not covered by the transmit-only recovery allowlist.
    ULONG power=SCARD_WARM_RESET; auto reset=f.request(33); reset.input.resize(sizeof(power)); memcpy(reset.input.data(),&power,sizeof(power)); f.ioctl(reset,IOCTL_SMARTCARD_POWER); f.deliver();
    auto failed=f.request(); failed.file=&f.backend; CompleteHeader header{f.context()->pendingId,static_cast<uint32_t>(STATUS_NOT_SUPPORTED),0}; failed.input.resize(sizeof(header)); memcpy(failed.input.data(),&header,sizeof(header)); f.ioctl(failed,BRIDGE_COMPLETE);
    Check(reset.status==STATUS_NOT_SUPPORTED&&!f.context()->attached,"reset failure still invalidates the session");
}
static void CancelDeliveryRace() {
    Fixture f; f.attach(); auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); r.cancelQueued=true; r.cancelRunning=true;
    auto poll=f.request(sizeof(RequestHeader)+8); poll.file=&f.backend;
    std::thread backend([&] { f.ioctl(poll,BRIDGE_GET_REQUEST); });
    std::thread cancel([&] { CancelPending(&r); }); backend.join(); cancel.join();
    Check(r.completions==1&&r.status==STATUS_CANCELLED&&!f.context()->pending,"delivery/cancel race completes once without replay");
    Check(poll.status==STATUS_SUCCESS ? !f.context()->attached : poll.status==STATUS_NO_MORE_ENTRIES&&f.context()->attached,"only a delivered canceled command invalidates session");
}
static void PowerDownReconnect() {
    Fixture f; f.attach(); ULONG power=SCARD_POWER_DOWN;
    auto down=f.request(); down.input.resize(sizeof(power)); memcpy(down.input.data(),&power,sizeof(power)); f.ioctl(down,IOCTL_SMARTCARD_POWER); f.deliver();
    auto complete=f.request(); complete.file=&f.backend; CompleteHeader header{f.context()->pendingId,0,0}; complete.input.resize(sizeof(header)); memcpy(complete.input.data(),&header,sizeof(header)); f.ioctl(complete,BRIDGE_COMPLETE);
    Check(down.status==STATUS_SUCCESS&&f.context()->attached&&f.context()->state==SCARD_PRESENT&&f.context()->protocol==0,"powerdown retains present card with no selected protocol");
    auto invalid=f.transmit(); f.ioctl(invalid,IOCTL_SMARTCARD_TRANSMIT); Check(invalid.status==STATUS_INVALID_DEVICE_STATE,"transmit requires negotiation after powerdown");
    ULONG protocol=SCARD_PROTOCOL_T1;
    auto shortOutput=f.request(); shortOutput.input.resize(sizeof(protocol)); memcpy(shortOutput.input.data(),&protocol,sizeof(protocol)); f.ioctl(shortOutput,IOCTL_SMARTCARD_SET_PROTOCOL);
    Check(shortOutput.status==STATUS_BUFFER_TOO_SMALL&&f.context()->protocol==0,"invalid output cannot change negotiated state");
    auto negotiate=f.request(sizeof(protocol)); negotiate.input=shortOutput.input; f.ioctl(negotiate,IOCTL_SMARTCARD_SET_PROTOCOL);
    ULONG selected=0; memcpy(&selected,negotiate.output.data(),sizeof(selected));
    Check(negotiate.status==STATUS_SUCCESS&&selected==SCARD_PROTOCOL_T1&&f.context()->state==SCARD_SPECIFIC,"contactless protocol can be selected again without cached success for reset");
    auto next=f.transmit(); f.ioctl(next,IOCTL_SMARTCARD_TRANSMIT); f.deliver(); f.reply(); Check(next.status==STATUS_SUCCESS,"next APDU reaches backend after logical reconnect");
    // A short reset buffer is rejected before any physical reset is sent.
    power=SCARD_WARM_RESET; auto reset=f.request(1); reset.input.resize(sizeof(power)); memcpy(reset.input.data(),&power,sizeof(power)); f.ioctl(reset,IOCTL_SMARTCARD_POWER);
    Check(reset.status==STATUS_BUFFER_TOO_SMALL&&!f.context()->pending&&f.context()->attached,"undersized reset does not reach card or expel it");
}
static void CancelBeforeDelivery() {
    Fixture f; f.attach(); auto r=f.transmit(); f.ioctl(r,IOCTL_SMARTCARD_TRANSMIT); auto id=f.context()->pendingId;
    r.cancelQueued=true; r.cancelRunning=true; CancelPending(&r);
    Check(r.status==STATUS_CANCELLED&&r.completions==1&&!f.context()->pending,"undelivered cancellation completes once");
    Check(f.context()->attached&&f.context()->backendOwner==&f.backend&&f.context()->payloadLength==0,"undelivered cancellation preserves session and clears command");
    auto next=f.transmit(); f.ioctl(next,IOCTL_SMARTCARD_TRANSMIT); Check(f.context()->pendingId>id,"canceled command is never reused"); f.deliver(); f.reply();
    Check(next.status==STATUS_SUCCESS,"owner remains usable after undelivered cancellation");
}
static void EmptyPollWithoutSystemClients() {
    Fixture f; f.attach(); auto empty=f.request(sizeof(RequestHeader)+8); empty.file=&f.backend;
    f.ioctl(empty,BRIDGE_GET_REQUEST);
    Check(empty.completed&&empty.status==STATUS_NO_MORE_ENTRIES&&empty.information==0,"empty owner poll returns no entries without external clients");
    auto unowned=f.request(sizeof(RequestHeader)+8); f.ioctl(unowned,BRIDGE_GET_REQUEST);
    Check(unowned.status==STATUS_ACCESS_DENIED,"unowned empty poll remains denied");
}
int main(int argc, char** argv) {
    try {
        if(argc==2) {
            std::string name=argv[1];
            if(name=="completion") RecoverableCompletion();
            else if(name=="protocol") PowerDownReconnect();
            else if(name=="cancel") CancelBeforeDelivery();
            else throw std::runtime_error("Unknown test group");
            std::cout << "PASS regression group " << name << ": " << checks << " checks\n"; return 0;
        }
        Stop(false); Stop(true); Cancellation(false); QueueLifecycle(); TimeoutAndCompletion(); PowerOperations(); StaleReplyAndEarlyCancel(); EmptyPollWithoutSystemClients();
        RecoverableCompletion(); PowerDownReconnect(); CancelBeforeDelivery(); CompletionBoundaries();
        for(int i=0;i<200;++i) { Cancellation(true); Stop(i%2==0); QueueLifecycle(); }
        for(int i=0;i<200;++i) CancelDeliveryRace();
        std::cout << "PASS production driver callbacks: " << checks << " checks; 200 concurrent cancellation/stop races and 200 power cycles. No PnP/power/DNI operations.\n";
        return 0;
    } catch(const std::exception& error) { std::cerr << "FAIL: " << error.what() << '\n'; return 1; }
}
