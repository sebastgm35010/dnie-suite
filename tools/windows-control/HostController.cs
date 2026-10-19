using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
namespace DniBridge.Control;

/// <summary>One cancellable host, serialized mutations, independent from desktop/SCM APIs.</summary>
public sealed class HostController : IAsyncDisposable {
    private readonly SemaphoreSlim mutations=new(1,1);
    private readonly object stateLock=new();
    private readonly Func<ConnectionProfile,Action<string>,CancellationToken,Task> runner;
    private readonly Func<ConnectionProfile,Task<PairingView>> pairing;
    private readonly Func<string[]> usbDevices;
    private readonly Func<bool?>? readerPublication;
    private Task? running;private CancellationTokenSource? lifetime;
    private BridgeStatus state=new();
#if DEBUG
    private readonly Queue<string> diagnostics=new();
#endif
    public HostController(Func<ConnectionProfile,Action<string>,CancellationToken,Task> runner,
        Func<ConnectionProfile,Task<PairingView>> pairing,Func<string[]> usbDevices,Func<bool?>? readerPublication=null) {
        this.runner=runner;this.pairing=pairing;this.usbDevices=usbDevices;this.readerPublication=readerPublication;
    }
    public BridgeStatus Snapshot {get {
        BridgeStatus current;lock(stateLock)current=state;
        bool? published=null;
        try {published=readerPublication?.Invoke();}catch { /* An unavailable query is not absence. */ }
        return current with {ReaderPublished=published};
    }}
    private void Update(Func<BridgeStatus,BridgeStatus> change){lock(stateLock)state=change(state);}
    private void Phase(string phase) {
        // Production retains coarse state only; never APDU, PIN/CAN, payloads or tokens.
        string name=phase.Split(' ',2)[0];
        if(name.StartsWith("pcsc_apdu_"))return;
        bool resetCard = name is "pcsc_card_removed" or "bridge_ready_for_next_card" or
            "tailnet_ready_for_next_session" or "transport_retry" or "usb_retry" or
            "host_ready" or "tailnet_sync_listening" ||
            name.Contains("closed") || name.Contains("failed");
        Update(s=>s with {Phase=name,CardPresent=name=="pcsc_card_present" || (s.CardPresent && !resetCard)});
#if DEBUG
        lock(stateLock){diagnostics.Enqueue($"{DateTimeOffset.UtcNow:O} {name}");while(diagnostics.Count>100)diagnostics.Dequeue();}
#endif
    }
    public async Task<ControlResponse> HandleAsync(ControlRequest request) {
        if(!ControlRules.Allowed(request.Command))return new(false,Error:"Operacion no permitida.");
        try {
            switch(request.Command) {
                case "status":return new(true,Status:Snapshot);
                case "start":
                    var profile=request.Profile ?? throw new ArgumentException("Falta la configuracion.");ControlRules.Validate(profile);
                    await StartAsync(profile);return new(true,Status:Snapshot);
                case "stop":await StopAsync();return new(true,Status:Snapshot);
                case "pairing":
                    var selected=request.Profile ?? Snapshot.Profile ?? throw new ArgumentException("Selecciona LAN o USB.");ControlRules.Validate(selected);
                    if(selected.Method=="Tailscale")throw new ArgumentException("Tailscale sincroniza sin QR.");
                    return new(true,Pairing:await pairing(selected));
                case "usb-devices":return new(true,Devices:usbDevices());
#if DEBUG
                case "diagnostics":lock(stateLock)return new(true,Devices:diagnostics.ToArray());
#endif
                default:return new(false,Error:"Operacion no permitida.");
            }
        } catch(Exception failure) {return new(false,Status:Snapshot,Error:failure.Message);}
    }
    public async Task StartAsync(ConnectionProfile profile) {
        await mutations.WaitAsync();try {
            if(running is {IsCompleted:false}) {
                if(Snapshot.Profile==profile && Snapshot.State=="Running")return;
                throw new InvalidOperationException("Ya hay un puente activo. Detenlo antes de cambiar la conexion.");
            }
            lifetime?.Dispose();lifetime=new CancellationTokenSource();
            Update(_=>new("Starting","preparing",false,null,profile));
            var ready=new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            var stop=lifetime.Token;
            running=Task.Run(async()=> {
                try {
                    await runner(profile,phase=> {
                        Phase(phase);
                        if(phase is "host_ready" or "tailnet_sync_listening") {Update(s=>s with {State="Running"});ready.TrySetResult();}
                    },stop);
                    if(!stop.IsCancellationRequested)throw new IOException("El motor finalizo inesperadamente.");
                } catch(OperationCanceledException) when(stop.IsCancellationRequested) {ready.TrySetCanceled(stop);}
                catch(IOException) when(stop.IsCancellationRequested) {ready.TrySetCanceled(stop);}
                catch(Exception failure) {Update(s=>s with {State="Failed",CardPresent=false,Error=failure.Message});ready.TrySetException(failure);}
                finally {if(stop.IsCancellationRequested)Update(s=>s with {State="Stopped",Phase="idle",CardPresent=false});}
            });
            try {await ready.Task.WaitAsync(TimeSpan.FromSeconds(15));}
            catch {lifetime.Cancel();await running;throw;}
        }finally{mutations.Release();}
    }
    public async Task StopAsync() {
        await mutations.WaitAsync();try {
            if(running is {IsCompleted:false}) {
                Update(s=>s with {State="Stopping"});lifetime!.Cancel();
                await running.WaitAsync(TimeSpan.FromSeconds(25));
            }
            Update(s=>s with {State="Stopped",Phase="idle",CardPresent=false});
        }finally{mutations.Release();}
    }
    public async ValueTask DisposeAsync(){await StopAsync();lifetime?.Dispose();mutations.Dispose();}
}

/// <summary>Only the established bridge engines, called in the SYSTEM service process.</summary>
public sealed class BridgeHostRuntime {
    private readonly string identityDirectory;
    private PcIdentity? identity;
    private readonly SemaphoreSlim keyLock=new(1,1);
    public BridgeHostRuntime(string directory){identityDirectory=directory;}
    private async Task<PcIdentity> IdentityAsync() {
        await keyLock.WaitAsync();try{return identity ??= await PcIdentity.LoadOrCreateAsync(identityDirectory);}finally{keyLock.Release();}
    }
    public async Task<PairingView> PairingAsync(ConnectionProfile profile) {
        var saved=await IdentityAsync();
        using var certificate=new X509Certificate2(Path.Combine(identityDirectory,"bridge.pfx"),saved.Password,X509KeyStorageFlags.EphemeralKeySet);
        byte[] token=Convert.FromBase64String(saved.Token);
        try {
            string pin=Convert.ToHexString(SHA256.HashData(certificate.RawData));
            string currentPc=profile.Method=="USB"?"127.0.0.1":LocalNetworkPolicy.ChooseLocalPc(profile.Pc,profile.Phone).ToString();
            string qr=BridgePairingQr.Encode(currentPc,39443,pin,token,certificate.NotAfter.ToUniversalTime(),DateTimeOffset.UtcNow,
                profile.Method=="USB"?"usb_accessory":"tls_reverse",true);
            return new(qr,string.Join(":",Enumerable.Range(0,32).Select(i=>pin.Substring(i*2,2))),certificate.NotAfter.ToUniversalTime().ToString("O"));
        }finally{CryptographicOperations.ZeroMemory(token);}
    }
    public Task RunAsync(ConnectionProfile profile,Action<string> log,CancellationToken stop) =>
        profile.Method=="USB" ? UsbWorkerProcess.RunAsync(UsbWorkerProcess.StartInfo(profile),log,stop) : RunLocalAsync(profile,log,stop);
    public async Task RunLocalAsync(ConnectionProfile profile,Action<string> log,CancellationToken stop) {
        ControlRules.Validate(profile);
        if(WindowsPcscDevice.Enumerate().Length!=1)throw new IOException("No se encontro un unico lector DNIe Bridge instalado.");
        _ = ReaderPublication.Query();
        if(profile.Method=="Tailscale") {
            await TailnetSyncServer.RunAsync(TailnetTransport.Resolve(profile.Pc),39443,profile.Phone,()=> {
                var devices=WindowsPcscDevice.Enumerate();if(devices.Length!=1)throw new IOException("El lector dejo de estar disponible.");return new WindowsPcscDevice(devices[0]);
            },log,stop);return;
        }
        var address=profile.Method=="USB"?IPAddress.Loopback:LocalNetworkPolicy.ChooseLocalPc(profile.Pc,profile.Phone);
        LocalNetworkPolicy.VerifyBinding(address);
        if(profile.Method=="USB" && !File.Exists(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"UsbDk Runtime Library","UsbDkHelper.dll")))
            throw new IOException("Instala el componente UsbDk x64 para USB directo. Puede requerir reiniciar Windows.");
        var saved=await IdentityAsync();
        string certificatePath=Path.Combine(identityDirectory,"bridge.pfx");
        using var certificate=new X509Certificate2(certificatePath,saved.Password,X509KeyStorageFlags.UserKeySet);
        byte[] token=Convert.FromBase64String(saved.Token);
        var config=new ServerConfig(address.ToString(),39443,35963,certificatePath,saved.Password,saved.Token,120,certificate.NotAfter.ToUniversalTime().ToString("O"),profile.Method=="USB"?"usb_accessory":"tls_reverse",address.ToString(),null,true,profile.Phone);
        try {await PersistentPcscHost.RunAsync(new TcpListener(address,39443),config,certificate,token,certificate.NotAfter.ToUniversalTime(),stop,log);}
        finally {CryptographicOperations.ZeroMemory(token);}
    }
}
