using System.Text.Json;
using DniBridge.Control;
using DniBridge.Agent;

if(!OperatingSystem.IsWindows())throw new PlatformNotSupportedException();
// SCM passes no untrusted configuration paths; service config resides beside protected binaries.
new WindowsServiceLifetime().Run(async stop=> {
    string root=Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"..",".."));
    var enrollment=JsonSerializer.Deserialize<Enrollment>(await File.ReadAllTextAsync(Path.Combine(root,"state","enrollment.json"),stop)) ?? throw new IOException("No enrolled owner.");
    _=new System.Security.Principal.SecurityIdentifier(enrollment.OwnerSid);
    var engine=new BridgeHostRuntime(Path.Combine(root,"state","identity"));
    await using var controller=new HostController(engine.RunAsync,engine.PairingAsync,DniBridge.UsbAccessoryStream.ListPhones);
    await ControlServer.RunAsync(enrollment.OwnerSid,controller,stop,fault:failure=> {
        try {File.WriteAllText(Path.Combine(root,"state","control-fault.txt"),$"{DateTimeOffset.UtcNow:O} {failure}");}catch{ }
    });
});
public sealed record Enrollment(string OwnerSid);
