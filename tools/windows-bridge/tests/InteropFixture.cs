using System.Net;
using System.Diagnostics;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.Principal;
using System.Text.Json;
using DniBridge;

internal static class InteropFixture
{
    public static async Task RunAsync(string provisionPath)
    {
        string path = Path.GetFullPath(provisionPath);
        string directory = Path.GetDirectoryName(path)!;
        if (Directory.Exists(directory)) throw new IOException("Use a new private fixture directory.");
        Directory.CreateDirectory(directory);
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Fixture uses Windows Schannel.");
        string sid = WindowsIdentity.GetCurrent().User!.Value;
        var acl = new ProcessStartInfo("icacls.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (string item in new[] { directory, "/inheritance:r", "/grant:r", $"*{sid}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F" }) acl.ArgumentList.Add(item);
        using (var process = Process.Start(acl)!)
        {
            await Task.WhenAll(process.StandardOutput.ReadToEndAsync(), process.StandardError.ReadToEndAsync());
            await process.WaitForExitAsync();
            if (process.ExitCode != 0) throw new IOException("Could not restrict fixture provisioning ACLs.");
        }
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(60));
        using var key = RSA.Create(2048);
        using var generated = new CertificateRequest("CN=bridge-interop-fixture", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1)
            .CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddMinutes(10));
        using var certificate = new X509Certificate2(generated.Export(X509ContentType.Pfx), (string?)null, X509KeyStorageFlags.UserKeySet);
        byte[] token = RandomNumberGenerator.GetBytes(32);
        var host = new TcpListener(IPAddress.Loopback, 0);
        var tls = new TcpListener(IPAddress.Loopback, 0);
        host.Start(); tls.Start();
        Task? session = null;
        try
        {
            await File.WriteAllTextAsync(path, JsonSerializer.Serialize(new
            {
                version = 1, host = "127.0.0.1", port = ((IPEndPoint)tls.LocalEndpoint).Port,
                certificateSha256 = Convert.ToHexString(SHA256.HashData(certificate.RawData)),
                pairingTokenBase64 = Convert.ToBase64String(token), expiresAt = certificate.NotAfter.ToUniversalTime().ToString("O")
            }), deadline.Token);
            Console.WriteLine("INTEROP_FIXTURE_READY");
            using var android = await tls.AcceptTcpClientAsync(deadline.Token);
            tls.Stop();
            session = BridgeSession.RunAsync(android, certificate, token, ((IPEndPoint)host.LocalEndpoint).Port,
                TimeSpan.FromSeconds(10), deadline.Token);
            using var peer = await host.AcceptTcpClientAsync(deadline.Token);
            using var stream = peer.GetStream();
            await Exchange(stream, [4], [0x3b, 0x80, 0x80, 1, 1], deadline.Token);
            await Exchange(stream, [0, 0xa4, 2, 0x0c, 2, 0xce, 0x81], [0x90, 0], deadline.Token);
            await Exchange(stream, [0, 0xb0, 0, 0, 3], [1, 2, 3, 0x90, 0], deadline.Token);
            await Exchange(stream, [0, 0x20, 0, 0x80, 1, 0], [0x69, 0x85], deadline.Token);
            // Closing the virtual peer must terminate the Android TLS connection too.
            peer.Close();
            try { await session.WaitAsync(TimeSpan.FromSeconds(5), deadline.Token); }
            catch (Exception error) when (error is EndOfStreamException or IOException or OperationCanceledException) { }
            Console.WriteLine("PASS Kotlin/.NET interoperability: ATR, SELECT, READ, rejected VERIFY, disconnect.");
        }
        finally
        {
            deadline.Cancel(); host.Stop(); tls.Stop();
            if (session != null) { try { await session; } catch { } }
            CryptographicOperations.ZeroMemory(token);
            if (File.Exists(path)) File.Delete(path);
        }
    }

    private static async Task Exchange(NetworkStream peer, byte[] request, byte[] expected, CancellationToken cancellation)
    {
        byte[] frame = new byte[request.Length + 2]; frame[1] = (byte)request.Length; request.CopyTo(frame, 2);
        // Fragment deliberately to exercise the other runtime's frame reader.
        foreach (byte value in frame) await peer.WriteAsync(new byte[] { value }, cancellation);
        byte[] header = new byte[2]; await peer.ReadExactlyAsync(header, cancellation);
        int length = (header[0] << 8) | header[1];
        if (length != expected.Length) throw new InvalidDataException("Unexpected fixture response length.");
        byte[] response = new byte[length]; await peer.ReadExactlyAsync(response, cancellation);
        if (!response.SequenceEqual(expected)) throw new InvalidDataException("Unexpected fixture response.");
    }
}
