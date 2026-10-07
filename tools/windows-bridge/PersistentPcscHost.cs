using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography.X509Certificates;
namespace DniBridge;

public static class PersistentPcscHost
{
    public static Task RunSessionAsync(Stream stream, ServerConfig config, X509Certificate2 certificate,
        byte[] token, DateTimeOffset expires, CancellationToken stop) =>
        PcscHostSession.RunAsync(stream, config.Transport == "tailscale_tcp" ? null : certificate, token, expires,
            TimeSpan.FromSeconds(config.IdleTimeoutSeconds), stop, () => {
                var devices=WindowsPcscDevice.Enumerate();
                if (devices.Length != 1) throw new InvalidOperationException("Exactly one DNIe reader required.");
                return new WindowsPcscDevice(devices[0]);
            }, phase => Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_phase={phase}"));
    public static async Task RunAsync(TcpListener listener, ServerConfig config, X509Certificate2 certificate,
        byte[] token, DateTimeOffset expires, CancellationToken stop)
    {
        while (!stop.IsCancellationRequested) {
            try {
                if (config.Transport == "usb_accessory") {
                    using var stream=await UsbAccessoryStream.OpenAsync(stop,config.PhoneHost);
                    await TransportReadiness.WaitAsync(stream,stop);
                    await RunSessionAsync(stream,config,certificate,token,expires,stop);
                } else {
                    using var phone=config.Transport == "tls_reverse" ? new TcpClient() : await listener.AcceptTcpClientAsync(stop);
                    if (config.Transport == "tls_reverse") {
                        var address=IPAddress.Parse(config.PhoneHost ?? throw new InvalidDataException("Missing phone IP."));
                        if (!LocalNetworkPolicy.IsPrivateAddress(address)) throw new InvalidDataException("Phone outside LAN.");
                        phone.Client.Bind(new IPEndPoint(IPAddress.Parse(config.Host),0));
                        using var connect=CancellationTokenSource.CreateLinkedTokenSource(stop);
                        connect.CancelAfter(TimeSpan.FromSeconds(5));
                        await phone.ConnectAsync(address,39444,connect.Token);
                    }
                    if (config.Transport == "tailscale_tcp") TailnetTransport.VerifyPeer(phone,config.PeerHost!);
                    if(config.Transport == "tls_reverse") await TransportReadiness.WaitAsync(phone.GetStream(),stop);
                    await RunSessionAsync(phone.GetStream(),config,certificate,token,expires,stop);
                }
            } catch (Exception failure) when (failure is PlatformNotSupportedException or DllNotFoundException or TypeInitializationException) {
                throw new InvalidOperationException("USB support unavailable; stop and fix the selected device or UsbDk installation.",failure);
            } catch (Exception failure) when (!stop.IsCancellationRequested) {
                Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_closed type={failure.GetType().Name} message={failure.Message}");
                await Task.Delay(1000,stop);
            }
            Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_ready_for_next_card");
        }
    }
}
