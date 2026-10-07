using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;

namespace DniBridge;

/// <summary>Reusable tailnet listener; bootstrap belongs to the accepted, authorized socket.</summary>
public static class TailnetSyncServer
{
    public static async Task SynchronizeAsync(Stream stream, byte[] token, CancellationToken stop)
    {
        if (token.Length != 32) throw new ArgumentException("Invalid synchronization token.");
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(stop);
        deadline.CancelAfter(TimeSpan.FromSeconds(15));
        byte[] request = new byte[8];
        await stream.ReadExactlyAsync(request, deadline.Token);
        if (!request.AsSpan().SequenceEqual("DNISYNC1"u8)) throw new InvalidDataException("Unknown synchronization request.");
        await stream.WriteAsync("DNISYNC1"u8.ToArray(), deadline.Token);
        await stream.WriteAsync(token, deadline.Token);
        await stream.FlushAsync(deadline.Token);
    }

    public static async Task RunAsync(IPAddress address, int port, string peerHost,
        Func<IPcscDevice> openDevice, Action<string> log, CancellationToken stop)
    {
        if (!TailnetTransport.IsAddress(address) || !TailnetTransport.IsHost(peerHost) || port is < 1024 or > 65535)
            throw new ArgumentException("Invalid tailnet listener.");
        var listener = new TcpListener(address, port);
        await ServeAsync(listener, phone => TailnetTransport.VerifyPeer(phone, peerHost), openDevice, log, stop);
    }

    // Transport policy is supplied before synchronization; the CLI uses VerifyPeer exclusively.
    public static async Task ServeAsync(TcpListener listener, Action<TcpClient> verifyPeer,
        Func<IPcscDevice> openDevice, Action<string> log, CancellationToken stop)
    {
        try {
            listener.Start(1);
            log("tailnet_sync_listening");
            while (!stop.IsCancellationRequested) {
                using var phone = await listener.AcceptTcpClientAsync(stop);
                byte[] token = RandomNumberGenerator.GetBytes(32);
                using var session = CancellationTokenSource.CreateLinkedTokenSource(stop);
                try {
                    verifyPeer(phone);
                    log("tailnet_peer_verified");
                    await SynchronizeAsync(phone.GetStream(), token, session.Token);
                    log("tailnet_synchronized");
                    await PcscHostSession.RunAsync(phone, null, token, DateTimeOffset.UtcNow.AddMinutes(2),
                        TimeSpan.FromSeconds(120), session.Token, openDevice, log);
                } catch (Exception failure) when (!stop.IsCancellationRequested) {
                    log($"tailnet_session_closed type={failure.GetType().Name}");
                } finally { CryptographicOperations.ZeroMemory(token); }
                log("tailnet_ready_for_next_session");
            }
        } finally { listener.Stop(); }
    }
}
