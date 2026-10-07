using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

namespace DniBridge;

public static class BridgeSession
{
    public static void VerifyDriverIsolation(IEnumerable<System.Net.IPEndPoint> listeners, int port)
    {
        var endpoints = listeners.Where(endpoint => endpoint.Port == port).ToArray();
        if (endpoints.Length == 0 || endpoints.Any(endpoint => !System.Net.IPAddress.IsLoopback(endpoint.Address)))
            throw new InvalidOperationException("Virtual driver must listen on loopback only before using this bridge.");
    }

    public static Task RunAsync(TcpClient android, X509Certificate2 certificate,
        byte[] token, int vpcdPort, TimeSpan idleTimeout, CancellationToken cancellation)
        => RunAsync(android, certificate, token, ct => ConnectVpcdAsync(vpcdPort, ct), idleTimeout, cancellation);

    private static async Task<Stream> ConnectVpcdAsync(int port, CancellationToken cancellation)
    {
        var socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
        try
        {
            using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
            deadline.CancelAfter(TimeSpan.FromSeconds(5));
            await socket.ConnectAsync(new System.Net.IPEndPoint(System.Net.IPAddress.Loopback, port), deadline.Token);
            return new NetworkStream(socket, ownsSocket: true);
        }
        catch { socket.Dispose(); throw; }
    }

    public static async Task RunAsync(TcpClient android, X509Certificate2? certificate,
        byte[] token, Func<CancellationToken, Task<Stream>> connectLocal,
        TimeSpan idleTimeout, CancellationToken cancellation)
    {
        using (android)
            await RunAsync(android.GetStream(), certificate, token, connectLocal, idleTimeout, cancellation);
    }

    public static async Task RunAsync(Stream transport, X509Certificate2? certificate,
        byte[] token, Func<CancellationToken, Task<Stream>> connectLocal,
        TimeSpan idleTimeout, CancellationToken cancellation)
    {
        if (token.Length != 32) throw new ArgumentException("Token must be 32 bytes.");
        using (Stream tls = certificate == null ? transport : new SslStream(transport, false))
        using (var session = CancellationTokenSource.CreateLinkedTokenSource(cancellation))
        {
            using (var handshake = CancellationTokenSource.CreateLinkedTokenSource(session.Token))
            {
                handshake.CancelAfter(TimeSpan.FromSeconds(15));
                if (tls is SslStream secure) await secure.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = certificate,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                    ClientCertificateRequired = false,
                    AllowRenegotiation = false
                }, handshake.Token);
                var hello = new byte[40];
                await tls.ReadExactlyAsync(hello, handshake.Token);
                bool authenticated = hello.AsSpan(0, 8).SequenceEqual("DNIBRDG1"u8)
                    & CryptographicOperations.FixedTimeEquals(hello.AsSpan(8, 32), token);
                CryptographicOperations.ZeroMemory(hello);
                if (!authenticated) throw new AuthenticationException("Pairing rejected.");
                await tls.WriteAsync(new byte[] { 1 }, handshake.Token);
                await tls.FlushAsync(handshake.Token);
            }

            // The local endpoint is reached only after TLS and pairing have succeeded.
            Stream connected;
            using (var connect = CancellationTokenSource.CreateLinkedTokenSource(session.Token))
            {
                connect.CancelAfter(TimeSpan.FromSeconds(15));
                connected = await connectLocal(connect.Token);
            }
            using var local = connected;
            var toAndroid = PumpAsync(local, tls, idleTimeout, session.Token);
            var toHost = PumpAsync(tls, local, idleTimeout, session.Token);
            var ended = await Task.WhenAny(toAndroid, toHost);
            session.Cancel();
            // Observe both pumps; no background task survives a session.
            try { await Task.WhenAll(toAndroid, toHost); }
            catch { await ended; throw; }
        }
    }

    public static async Task PumpAsync(Stream source, Stream destination, TimeSpan timeout,
        CancellationToken cancellation)
    {
        byte[] header = new byte[2];
        while (true)
        {
            using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
            // An authenticated, idle card is allowed to wait indefinitely. A partial
            // frame remains bounded; stopping the host always cancels this read.
            await source.ReadExactlyAsync(header.AsMemory(0, 1), cancellation);
            deadline.CancelAfter(timeout);
            await source.ReadExactlyAsync(header.AsMemory(1, 1), deadline.Token);
            int length = (header[0] << 8) | header[1];
            if (length == 0) throw new InvalidDataException("Empty VPCD frame.");
            byte[] payload = new byte[length];
            try
            {
                await source.ReadExactlyAsync(payload, deadline.Token);
                await destination.WriteAsync(header, deadline.Token);
                await destination.WriteAsync(payload, deadline.Token);
                await destination.FlushAsync(deadline.Token);
            }
            finally { CryptographicOperations.ZeroMemory(payload); }
        }
    }
}
