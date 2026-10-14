using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography.X509Certificates;
namespace DniBridge;

public static class PersistentPcscHost
{
    public static Task RunSessionAsync(Stream stream, ServerConfig config, X509Certificate2 certificate,
        byte[] token, DateTimeOffset expires, CancellationToken stop, Action<string>? log=null) =>
        PcscHostSession.RunAsync(stream, config.Transport == "tailscale_tcp" ? null : certificate, token, expires,
            TimeSpan.FromSeconds(config.IdleTimeoutSeconds), stop, () => {
                var devices=WindowsPcscDevice.Enumerate();
                if (devices.Length != 1) throw new InvalidOperationException("Exactly one DNIe reader required.");
                return new WindowsPcscDevice(devices[0]);
            }, log ?? (phase => Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_phase={phase}")));
    public static async Task RunAsync(TcpListener listener, ServerConfig config, X509Certificate2 certificate,
        byte[] token, DateTimeOffset expires, CancellationToken stop, Action<string>? log=null)
    {
        log?.Invoke("host_ready");
        while (!stop.IsCancellationRequested) {
            try {
                if (config.Transport == "usb_accessory") {
                    using var stream=await UsbAccessoryStream.OpenAsync(stop,config.PhoneHost,log);
                    log?.Invoke("usb_waiting_android");
                    await TransportReadiness.WaitAsync(stream,stop);
                    await RunSessionAsync(stream,config,certificate,token,expires,stop,log);
                } else {
                    using var phone=config.Transport == "tls_reverse" ? new TcpClient() : await listener.AcceptTcpClientAsync(stop);
                    if (config.Transport == "tls_reverse") {
                        byte[] pin=System.Security.Cryptography.SHA256.HashData(certificate.RawData);
                        var candidates=await NetworkDiscovery.FindAsync(1,pin,token,config.PhoneHost??"",stop);
                        if(candidates.Length==0)candidates=await NetworkDiscovery.ResolveHintAsync(config.PhoneHost??"",stop);
                        if(candidates.Length==0)throw new IOException("Esperando teléfono vinculado en la red.");
                        using var connect=CancellationTokenSource.CreateLinkedTokenSource(stop);
                        connect.CancelAfter(TimeSpan.FromSeconds(5));
                        await phone.ConnectAsync(candidates,39444,connect.Token);
                        log?.Invoke("network_peer_found");
                    }
                    if (config.Transport == "tailscale_tcp") TailnetTransport.VerifyPeer(phone,config.PeerHost!);
                    if(config.Transport == "tls_reverse") await TransportReadiness.WaitAsync(phone.GetStream(),stop);
                    await RunSessionAsync(phone.GetStream(),config,certificate,token,expires,stop,log);
                }
            } catch (Exception failure) when (failure is PlatformNotSupportedException or DllNotFoundException or TypeInitializationException) {
                throw new InvalidOperationException("USB support unavailable; stop and fix the selected device or UsbDk installation.",failure);
            } catch (Exception failure) when (!stop.IsCancellationRequested) {
                if(log!=null)log($"{(config.Transport=="usb_accessory"?"usb_retry":"transport_retry")} type={failure.GetType().Name}");
                else Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_closed type={failure.GetType().Name} message={failure.Message}");
                await Task.Delay(1000,stop);
            }
            if(log!=null)log("bridge_ready_for_next_card");
            else Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_ready_for_next_card");
        }
    }
}
