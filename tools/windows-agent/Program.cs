using System.Text.Json;
using DniBridge.Control;
using DniBridge.Agent;

if(!OperatingSystem.IsWindows())throw new PlatformNotSupportedException();
if(args.Length==2 && args[0]=="--usb-worker") {
    var profile=new ConnectionProfile("USB","",args[1]);ControlRules.Validate(profile);
    string root=Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"..",".."));
    using var stop=new CancellationTokenSource();
    // No secrets in arguments or stdout. The same protected identity is read locally.
    _=Task.Run(async()=>{await Console.In.ReadLineAsync();stop.Cancel();});
    try {
        await new BridgeHostRuntime(Path.Combine(root,"state","identity")).RunLocalAsync(profile,phase=> {
            if(UsbWorkerProcess.SafePhase(phase) is { } name)Console.WriteLine(name);
        },stop.Token);
    } catch(OperationCanceledException) when(stop.IsCancellationRequested) { }
    catch(IOException) when(stop.IsCancellationRequested) { }
    catch(Exception) {Environment.ExitCode=1;}
    return;
}
// SCM passes no untrusted configuration paths; service config resides beside protected binaries.
new WindowsServiceLifetime().Run(async stop=> {
    string root=Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"..",".."));
    var enrollment=JsonSerializer.Deserialize<Enrollment>(await File.ReadAllTextAsync(Path.Combine(root,"state","enrollment.json"),stop)) ?? throw new IOException("No enrolled owner.");
    _=new System.Security.Principal.SecurityIdentifier(enrollment.OwnerSid);
    var engine=new BridgeHostRuntime(Path.Combine(root,"state","identity"));
    await using var controller=new HostController(engine.RunAsync,engine.PairingAsync,DniBridge.UsbAccessoryStream.ListPhones,ReaderPublication.Query);
    await ControlServer.RunAsync(enrollment.OwnerSid,controller,stop,fault:failure=> {
        try {File.WriteAllText(Path.Combine(root,"state","control-fault.txt"),$"{DateTimeOffset.UtcNow:O} {failure}");}catch{ }
    });
});
public sealed record Enrollment(string OwnerSid);
