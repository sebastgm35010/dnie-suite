using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using DniBridge;

static class SessionRelayTest
{
    public static async Task RunAsync(string result)
    {
        var checks = new List<string>();
        void Check(bool ok, string name) { if (!ok) throw new Exception(name); checks.Add(name); }
        IntPtr context = IntPtr.Zero, card = IntPtr.Zero;
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(65));
        using var rsa = RSA.Create(2048);
        using var generated = new CertificateRequest("CN=synthetic-relay", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1)
            .CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddHours(1));
        using var certificate = new X509Certificate2(generated.Export(X509ContentType.Pfx), (string?)null, X509KeyStorageFlags.UserKeySet);
        byte[] token = RandomNumberGenerator.GetBytes(32), pin = SHA256.HashData(certificate.RawData);
        byte[] atr = [0x3B, 0x80, 1, 0x81]; // Explicitly simulated, never used in a real phone pairing.
        int resets = 0, pulses = 0, powerOffs = 0;
        bool attached = false;
        Task? host = null, mobile = null;
        using var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start();
        using var phone = new TcpClient();
        try
        {
            var accepted = listener.AcceptTcpClientAsync(deadline.Token);
            await phone.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port, deadline.Token);
            var peer = await accepted;
            host = PcscHostSession.RunAsync(peer, certificate, token, DateTimeOffset.UtcNow.AddMinutes(2), TimeSpan.FromSeconds(45), deadline.Token,
                () => new WindowsPcscDevice(WindowsPcscDevice.Enumerate().Single()), phase => { if (phase == "pcsc_card_present") attached = true; });
            using var tls = new SslStream(phone.GetStream(), false, (_, cert, _, _) => cert != null && CryptographicOperations.FixedTimeEquals(pin, SHA256.HashData(cert.GetRawCertData())));
            await tls.AuthenticateAsClientAsync(new SslClientAuthenticationOptions { TargetHost = "synthetic-relay", EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13 }, deadline.Token);
            byte[] hello = new byte[40]; "DNIBRDG1"u8.CopyTo(hello); token.CopyTo(hello, 8);
            await tls.WriteAsync(hello, deadline.Token); CryptographicOperations.ZeroMemory(hello);
            byte[] ack = new byte[1]; await tls.ReadExactlyAsync(ack, deadline.Token);
            Check(ack[0] == 1, "TLS and token accepted by integrated host");
            mobile = Task.Run(async () =>
            {
                while (!deadline.IsCancellationRequested)
                {
                    byte[] header = new byte[2]; await tls.ReadExactlyAsync(header, deadline.Token);
                    byte[] command = new byte[(header[0] << 8) | header[1]]; await tls.ReadExactlyAsync(command, deadline.Token);
                    byte[] response;
                    if (command.Length == 1) response = command[0] switch {
                        4 => atr, 2 => Reset(), 0 => Off(), 5 => Pulse(), _ => throw new Exception("Unknown simulated control")
                    };
                    else response = command.SequenceEqual(new byte[] { 0, 0xB0, 0, 0, 1 }) ? [0xDE, 0xAD, 0x90, 0] : [0x6A, 0x82];
                    await tls.WriteAsync(new byte[] { (byte)(response.Length >> 8), (byte)response.Length }, deadline.Token);
                    await tls.WriteAsync(response, deadline.Token);
                }
                byte[] Reset() { Interlocked.Increment(ref resets); return atr; }
                byte[] Off() { Interlocked.Increment(ref powerOffs); return [1]; }
                byte[] Pulse() { Interlocked.Increment(ref pulses); return [1]; }
            });
            while (!attached) await Task.Delay(10, deadline.Token);
            Check(Native.SCardEstablishContext(2, IntPtr.Zero, IntPtr.Zero, out context) == 0, "native PCSC context");
            uint size = 0; Check(Native.SCardListReadersW(context, null, null, ref size) == 0, "native reader list size");
            char[] names = new char[size]; Check(Native.SCardListReadersW(context, null, names, ref size) == 0, "native reader list");
            string reader = new string(names).Split('\0', StringSplitOptions.RemoveEmptyEntries).Single(x => x.StartsWith("DNIe Bridge Android NFC"));
            Check(Native.SCardConnectW(context, reader, 2, 2, out card, out uint protocol) == 0 && protocol == 2, "native connect through TLS reset");
            byte[] Send(byte[] command) {
                var pci = new Native.Pci { Protocol = protocol, Length = 8 }; byte[] output = new byte[258]; uint length = 258;
                Check(Native.SCardTransmit(card, ref pci, command, (uint)command.Length, IntPtr.Zero, output, ref length) == 0, "native transmit via TLS");
                return output[..(int)length];
            }
            Check(Send([0, 0xA4, 4, 0, 0]).SequenceEqual(new byte[] { 0x6A, 0x82 }), "unsuccessful status preserved end to end");
            await Task.Delay(TimeSpan.FromSeconds(35), deadline.Token);
            Check(pulses >= 3 && !host.IsCompleted, "35 seconds idle survive using transport heartbeats");
            Check(Send([0, 0xB0, 0, 0, 1]).SequenceEqual(new byte[] { 0xDE, 0xAD, 0x90, 0 }), "transmit works after prolonged idle");
            int resetsBefore = Volatile.Read(ref resets);
            uint resetStatus = Native.SCardReconnect(card, 2, 2, 1, out protocol);
            Check(resetStatus == 0 && Volatile.Read(ref resets) > resetsBefore, "native warm reset crosses TLS and completes");
            Check(Native.SCardDisconnect(card, 2) == 0 && powerOffs >= 1, "native power off crosses TLS and completes"); card = IntPtr.Zero;
            phone.Close();
            try { await host.WaitAsync(TimeSpan.FromSeconds(5)); } catch (Exception e) when (e is not TimeoutException) { }
            var state = new[] { new Native.ReaderState { Reader=reader, CurrentState=0x20, Atr=new byte[36] } };
            Check(Native.SCardGetStatusChangeW(context, 3000, state, 1) == 0 && (state[0].EventState & 0x10) != 0, "phone disconnect removes native card");
            await File.WriteAllTextAsync(result, JsonSerializer.Serialize(new { success=true, checks, resets, pulses, powerOffs, syntheticOnly=true }, new JsonSerializerOptions { WriteIndented=true }));
            Console.WriteLine($"PASS {checks.Count} native PCSC/TLS session checks");
        }
        catch (Exception e) {
            await File.WriteAllTextAsync(result, JsonSerializer.Serialize(new { success=false, checks, error=e.Message, resets, pulses, powerOffs, syntheticOnly=true }));
            Environment.ExitCode=1;
        }
        finally {
            deadline.Cancel(); phone.Close();
            if (card != IntPtr.Zero) Native.SCardDisconnect(card, 0);
            if (mobile != null) { try { await mobile; } catch { } }
            if (host != null) { try { await host; } catch { } }
            if (context != IntPtr.Zero) Native.SCardReleaseContext(context);
            CryptographicOperations.ZeroMemory(token);
        }
    }
}
