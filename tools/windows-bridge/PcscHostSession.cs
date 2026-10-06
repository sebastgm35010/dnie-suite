using System.IO.Pipes;
using System.Net.Sockets;
using System.Security.Cryptography.X509Certificates;

namespace DniBridge;

/// <summary>Authenticated TLS and the native backend owned by one finite process.</summary>
public static class PcscHostSession
{
    public static async Task RunAsync(TcpClient phone, X509Certificate2? certificate, byte[] token,
        DateTimeOffset expires, TimeSpan idle, CancellationToken cancellation,
        Func<IPcscDevice> openDevice, Action<string> log)
    {
        using var lifetime = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
        NamedPipeClientStream? backendPipe = null;
        IPcscDevice? device = null;
        Task? backend = null;
        try
        {
            await BridgeSession.RunAsync(phone, certificate, token, async ct =>
            {
                if (DateTimeOffset.UtcNow >= expires) throw new InvalidDataException("Pairing expired before authentication.");
                log("peer_authorized");
                device = openDevice(); // Native private interface only after successful authentication.
                log("pcsc_backend_opened");
                string name = "DNIeBridge-" + Guid.NewGuid().ToString("N");
                var server = PrivateCardPipe.Create(name);
                try
                {
                    backendPipe = new NamedPipeClientStream(".", name, PipeDirection.InOut, PipeOptions.Asynchronous);
                    var accept = server.WaitForConnectionAsync(ct);
                    await backendPipe.ConnectAsync(ct);
                    await accept;
                    backend = PcscBackend.RunAsync(device, backendPipe, lifetime.Token, log);
                    // Observe a native/backend failure immediately; do not leave a waiting TLS pump.
                    _ = backend.ContinueWith(_ => lifetime.Cancel(), CancellationToken.None,
                        TaskContinuationOptions.ExecuteSynchronously, TaskScheduler.Default);
                    return server;
                }
                catch { server.Dispose(); throw; }
            }, idle, lifetime.Token);
        }
        catch (Exception failure) { log($"session_failed type={failure.GetType().Name}"); throw; }
        finally
        {
            lifetime.Cancel();
            backendPipe?.Dispose();
            try { if (backend != null) await backend; }
            catch (System.ComponentModel.Win32Exception failure) { log($"backend_shutdown_failed type=Win32Exception code={failure.NativeErrorCode}"); throw; }
            finally { device?.Dispose(); }
        }
    }
}
