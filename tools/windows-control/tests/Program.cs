using DniBridge.Control;
using System.IO.Pipes;
using System.Security.Principal;
[assembly:System.Runtime.Versioning.SupportedOSPlatform("windows")]

int passed=0;
void Check(bool value,string message){if(!value)throw new Exception(message);passed++;}
var profile=new ConnectionProfile("Tailscale","100.104.106.125","100.68.70.85");
#if DEBUG
Check(ControlRules.Allowed("diagnostics"),"Debug configuration exposes bounded diagnostics");
#else
Check(!ControlRules.Allowed("diagnostics"),"Production excludes debug command");
#endif
if(args.Contains("--installed-agent-check")) {
    Check(!new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator),"Smoke runs without admin");
    uint pid=ServiceControl.Query()?.ProcessId ?? throw new Exception("Missing service");
    var client=new AgentClient();
    Check((await client.SendAsync(new("status"))).Status?.State=="Stopped","Boot starts broker only");
    try {
        var first=await client.SendAsync(new("start",profile));Check(first.Ok && first.Status?.State=="Running","Installed engine starts from unprivileged control");
        var again=await client.SendAsync(new("start",profile));Check(again.Ok && ServiceControl.Query()?.ProcessId==pid,"Repeated start keeps service and host");
        await Task.Delay(1200);
        Check((await new AgentClient().SendAsync(new("status"))).Status?.State=="Running","Host survives client disconnect");
        Check((await client.SendAsync(new("stop"))).Status?.State=="Stopped","Unprivileged stop cleans host");
        Check((await client.SendAsync(new("start",profile))).Ok,"Restart requires no UAC");
    } finally {await client.SendAsync(new("stop"));}
    Check(ServiceControl.Query()?.ProcessId==pid,"SYSTEM broker remains alive");
#if !DEBUG
    using(var pipe=new NamedPipeClientStream(".",ControlPipe.Name,PipeDirection.InOut,PipeOptions.Asynchronous,TokenImpersonationLevel.Identification)) {
        await pipe.ConnectAsync(3000);ControlPipe.VerifyServer(pipe,pid);
        await ControlWire.WriteAsync(pipe,new ControlRequest("diagnostics"),CancellationToken.None);
        var denied=await ControlWire.ReadAsync<ControlResponse>(pipe,CancellationToken.None);await pipe.WriteAsync(new byte[]{1});
        Check(!denied.Ok,"Installed production broker refuses direct debug request");
    }
#endif
    Console.WriteLine($"PASS {passed} installed service checks; no card connected, all calls from regular user.");
    return;
}
ControlRules.Validate(profile);Check(true,"Tailnet config accepted");
foreach(var invalid in new[]{new ConnectionProfile("Shell","cmd","run"),new ConnectionProfile("LAN","0.0.0.0","8.8.8.8"),new ConnectionProfile("USB","","bad / serial"),new ConnectionProfile("Tailscale","192.168.1.5","100.68.70.85")}) {
    try {ControlRules.Validate(invalid);throw new Exception("Unsupported profile accepted");}catch(ArgumentException){Check(true,"Invalid profile refused");}
}
foreach(string command in new[]{"exec","read-file","install-driver","delete","shell","STATUS"})Check(!ControlRules.Allowed(command),"Control channel has no generic privileged operations");
using(var stream=new MemoryStream()) {
    await ControlWire.WriteAsync(stream,new ControlRequest("start",profile),CancellationToken.None);stream.Position=0;
    var copy=await ControlWire.ReadAsync<ControlRequest>(stream,CancellationToken.None);Check(copy.Profile==profile,"Wire round trip");
}
foreach(byte[] header in new[]{new byte[4],new byte[]{0,1,0,1},new byte[]{255,255,255,255}}) {
    try {await ControlWire.ReadAsync<ControlRequest>(new MemoryStream(header),CancellationToken.None);throw new Exception("Unbounded frame accepted");}catch(InvalidDataException){Check(true,"Invalid frame rejected before allocating payload");}
}
int starts=0,stops=0;
await using(var controller=new HostController(async(_,log,token)=> {
    Interlocked.Increment(ref starts);log("host_ready");log("pcsc_card_present");log("pcsc_apdu_requested requestId=99 header=00200000");
    try {await Task.Delay(Timeout.Infinite,token);}finally{Interlocked.Increment(ref stops);log("pcsc_card_removed");}
},_=>Task.FromResult(new PairingView("synthetic-only","AA","2030")),()=>["2717:FF88 synthetic-serial"])) {
    var results=await Task.WhenAll(Enumerable.Range(0,8).Select(_=>controller.HandleAsync(new("start",profile))));
    Check(results.All(r=>r.Ok) && starts==1,"Repeated Start never duplicates the host");
    Check(controller.Snapshot.CardPresent && controller.Snapshot.State=="Running","Status comes from engine events");
    Check(!controller.Snapshot.Phase.Contains("00200000"),"No APDU header in status");
    var rejected=await controller.HandleAsync(new("start",profile with {Phone="100.68.70.86"}));Check(!rejected.Ok && starts==1,"Transport change cannot interrupt the active host");
    var bad=await controller.HandleAsync(new("exec"));Check(!bad.Ok,"Unknown operation rejected");
    Check((await controller.HandleAsync(new("pairing",new("LAN","192.168.1.5","192.168.1.11")))).Pairing?.Payload=="synthetic-only","Pairing adapter stays separate");
    await controller.StopAsync();Check(stops==1 && !controller.Snapshot.CardPresent && controller.Snapshot.State=="Stopped","Stop awaits cleanup");
    await controller.StartAsync(profile);Check(starts==2,"Host can be started again without new privileges");
    await controller.StopAsync();
}
await using(var failed=new HostController((_,_,_)=>throw new IOException("synthetic startup failure"),_=>throw new Exception(),()=>[])) {
    var response=await failed.HandleAsync(new("start",profile));Check(!response.Ok && response.Status?.State=="Failed","Startup error surfaced in structured response");
}
await using(var cancelledUsb=new HostController(async(_,log,token)=> {
    log("host_ready");
    try {await Task.Delay(Timeout.Infinite,token);}
    catch(OperationCanceledException) {throw new IOException("USB transfer interrupted during stop");}
},_=>throw new Exception(),()=>[])) {
    await cancelledUsb.StartAsync(profile);
    await cancelledUsb.StopAsync();
    Check(cancelledUsb.Snapshot.State=="Stopped" && cancelledUsb.Snapshot.Error==null,"Cancelled USB I/O does not become a spurious failure");
}
if(OperatingSystem.IsWindows()) {
    string owner=WindowsIdentity.GetCurrent().User!.Value;
    string name="DNIeBridge.Test."+Guid.NewGuid().ToString("N");
    await using var controller=new HostController((_,_,_)=>throw new Exception("No real host in IPC test"),_=>throw new Exception(),()=>[]);
    using var stop=new CancellationTokenSource();
    var server=ControlServer.RunAsync(owner,controller,stop.Token,name);
    for(int round=0;round<3;round++) {
        using var pipe=new NamedPipeClientStream(".",name,PipeDirection.InOut,PipeOptions.Asynchronous,TokenImpersonationLevel.Identification);
        await pipe.ConnectAsync(3000);ControlPipe.VerifyServer(pipe,(uint)Environment.ProcessId);
        await ControlWire.WriteAsync(pipe,new ControlRequest(round==1?"shell":"status"),CancellationToken.None);
        var answer=await ControlWire.ReadAsync<ControlResponse>(pipe,CancellationToken.None);
        await pipe.WriteAsync(new byte[]{1});
        Check(answer.Ok==(round!=1),"Persistent local broker survives malformed/unrecognized commands");
        try {ControlPipe.VerifyServer(pipe,(uint)Environment.ProcessId+1);throw new Exception("Wrong server accepted");}catch(UnauthorizedAccessException){Check(true,"Client verifies server PID");}
    }
    stop.Cancel();try{await server;}catch(OperationCanceledException){}
}
Console.WriteLine($"PASS {passed} checks (lifecycle, bounded authenticated local control, synthetic bytes; no card opened).");
