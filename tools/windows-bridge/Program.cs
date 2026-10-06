using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.Principal;
using System.Text.Json;
using DniBridge;

if (args.Length == 0) { Console.WriteLine("doctor | setup --output <private-directory> [--host IP] [--port 39443] | serve-local --config <server-private.json> | pcsc-devices | pcsc-relay --pipe <session-name> [--device <interface-path>] | serve --config <server-private.json>"); return; }
string Option(string name, string? fallback = null)
{
    int index = Array.IndexOf(args, name);
    return index >= 0 && index + 1 < args.Length ? args[index + 1] : fallback ?? throw new ArgumentException($"Missing {name}.");
}
var jsonOptions = new JsonSerializerOptions { WriteIndented = true, PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
if (args[0] == "doctor")
{
    var properties = System.Net.NetworkInformation.IPGlobalProperties.GetIPGlobalProperties();
    var listeners = properties.GetActiveTcpListeners();
    var driverListeners = listeners.Where(endpoint => endpoint.Port == 35963).ToArray();
    foreach (var endpoint in driverListeners) Console.WriteLine($"Virtual driver binding: {endpoint.Address}:{endpoint.Port}");
    bool isolated = true;
    try { BridgeSession.VerifyDriverIsolation(listeners, 35963); }
    catch (InvalidOperationException)
    {
        isolated = false;
        Console.WriteLine("BLOCKED: the virtual driver is absent or exposes raw card traffic beyond loopback. No real card session can be started.");
        Console.WriteLine("Current Bix VPCD source has no INI bind-address setting. This tool will not change its driver or firewall. Use an isolated driver before serve.");
    }
    foreach (var network in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
    {
        if (network.OperationalStatus != System.Net.NetworkInformation.OperationalStatus.Up || network.NetworkInterfaceType == System.Net.NetworkInformation.NetworkInterfaceType.Loopback) continue;
        foreach (var address in network.GetIPProperties().UnicastAddresses)
            if (address.Address.AddressFamily == AddressFamily.InterNetwork) Console.WriteLine($"Candidate local TLS address (--host): {address.Address}");
    }
    Console.WriteLine(isolated ? "Driver isolation preflight passed. Choose the interface reachable by Android for TLS provisioning." : "TLS provisioning may be prepared, but serve remains blocked. Diagnostic does not connect to any card.");
    Environment.ExitCode = isolated ? 0 : 2;
}
else if (args[0] == "pcsc-devices")
{
    var devices = WindowsPcscDevice.Enumerate();
    foreach (string path in devices) Console.WriteLine(path);
    if (devices.Length == 0) Console.WriteLine("No DNIe UMDF reader installed. This command does not install or register a reader.");
}
else if (args[0] == "pcsc-relay")
{
    string pipeName = Option("--pipe");
    if (!System.Text.RegularExpressions.Regex.IsMatch(pipeName, "^DNIeBridge-[a-f0-9]{32}$")) throw new ArgumentException("Invalid session pipe.");
    string[] devices = WindowsPcscDevice.Enumerate();
    string path = Option("--device", devices.Length == 1 ? devices[0] : "");
    if (path.Length == 0) throw new InvalidOperationException("Install the signed DNIe UMDF reader and select its interface first.");
    using var device = new WindowsPcscDevice(path);
    using var stop = new CancellationTokenSource();
    Console.CancelKeyPress += (_, e) => { e.Cancel = true; stop.Cancel(); };
    using var pipe = new System.IO.Pipes.NamedPipeClientStream(".", pipeName, System.IO.Pipes.PipeDirection.InOut, System.IO.Pipes.PipeOptions.Asynchronous);
    await pipe.ConnectAsync(10000, stop.Token);
    Console.WriteLine("PC/SC backend connected to session-local transport. Card presence requires a real NFC ATR.");
    await PcscBackend.RunAsync(device, pipe, stop.Token);
}
else if (args[0] == "serve-tailnet")
{
    var address = TailnetTransport.Resolve(Option("--host", "sebas-laptop.tail2752e9.ts.net"));
    string peer = Option("--peer", "sebas-poco.tail2752e9.ts.net");
    using var stop = new CancellationTokenSource();
    Console.CancelKeyPress += (_, e) => { e.Cancel = true; stop.Cancel(); };
    try {
        await TailnetSyncServer.RunAsync(address, int.Parse(Option("--port", "39443")), peer, () => {
            var devices = WindowsPcscDevice.Enumerate();
            if (devices.Length != 1) throw new InvalidOperationException("Exactly one DNIe reader required.");
            return new WindowsPcscDevice(devices[0]);
        }, phase => Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_phase={phase}"), stop.Token);
    } catch (OperationCanceledException) { Console.WriteLine("Tailnet listener stopped."); }
}
else if (args[0] == "setup")
{
    if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Private provisioning requires Windows ACLs.");
    string directory = Path.GetFullPath(Option("--output"));
    if (Directory.Exists(directory)) throw new IOException("Use a new private output directory.");
    string transport = Option("--transport", "tls");
    if (transport is not ("tls" or "tailscale_tcp")) throw new ArgumentException("Unknown transport.");
    bool tailnet = transport == "tailscale_tcp";
    string advertisedHost = Option("--host", "127.0.0.1");
    IPAddress host = tailnet ? TailnetTransport.Resolve(advertisedHost) : IPAddress.Parse(advertisedHost);
    string? peerHost = tailnet ? Option("--peer") : null;
    if (tailnet) TailnetTransport.Resolve(peerHost!);
    if (host.Equals(IPAddress.Any) || host.Equals(IPAddress.IPv6Any)) throw new ArgumentException("Specify one local interface address.");
    int port = int.Parse(Option("--port", "39443"));
    if (port is < 1024 or > 65535) throw new ArgumentException("Invalid TLS port.");
    Directory.CreateDirectory(directory);
    string sid = WindowsIdentity.GetCurrent().User!.Value;
    var acl = new ProcessStartInfo("icacls.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
    foreach (string item in new[] { directory, "/inheritance:r", "/grant:r", $"*{sid}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F" }) acl.ArgumentList.Add(item);
    using (var process = Process.Start(acl)!)
    {
        await Task.WhenAll(process.StandardOutput.ReadToEndAsync(), process.StandardError.ReadToEndAsync());
        await process.WaitForExitAsync();
        if (process.ExitCode != 0) throw new IOException("Could not restrict provisioning ACLs; no secrets generated.");
    }
    using var key = RSA.Create(3072);
    var request = new CertificateRequest("CN=DNI Android PC Bridge", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
    request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
    request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, true));
    var usages = new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") };
    request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(usages, true));
    var san = new SubjectAlternativeNameBuilder(); san.AddIpAddress(host); request.CertificateExtensions.Add(san.Build());
    using var certificate = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(90));
    byte[] token = RandomNumberGenerator.GetBytes(32);
    string password = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
    string certificatePath = Path.Combine(directory, "bridge.pfx");
    await File.WriteAllBytesAsync(certificatePath, certificate.Export(X509ContentType.Pfx, password));
    string pin = Convert.ToHexString(SHA256.HashData(certificate.RawData));
    string pairingExpiresAt = DateTime.UtcNow.AddMinutes(10).ToString("O");
    await File.WriteAllTextAsync(Path.Combine(directory, "server-private.json"), JsonSerializer.Serialize(new ServerConfig(host.ToString(), port, 35963, certificatePath, password, Convert.ToBase64String(token), 120, pairingExpiresAt, transport, advertisedHost, peerHost), jsonOptions));
    await File.WriteAllTextAsync(Path.Combine(directory, "android-provision.json"), JsonSerializer.Serialize(new { version = 1, host = advertisedHost, port, certificateSha256 = pin, transport, pairingTokenBase64 = Convert.ToBase64String(token), expiresAt = pairingExpiresAt }, jsonOptions));
    CryptographicOperations.ZeroMemory(token);
    Console.WriteLine("Private provisioning created. Import android-provision.json through a trusted channel; it is a credential.");
    Console.WriteLine($"Server SHA-256: {string.Join(":", Enumerable.Range(0, 32).Select(i => pin.Substring(i * 2, 2)))}");
}
else if (args[0] is "serve" or "serve-local" or "serve-pcsc")
{
    bool nativePcsc = args[0] == "serve-pcsc";
    bool privateLocal = args[0] is "serve-local" or "serve-pcsc";
    var config = JsonSerializer.Deserialize<ServerConfig>(await File.ReadAllTextAsync(Option("--config")), jsonOptions) ?? throw new InvalidDataException("Invalid config.");
    if (config.Transport is not ("tls" or "tailscale_tcp")) throw new InvalidDataException("Unknown transport.");
    bool tailnet = config.Transport == "tailscale_tcp";
    if (tailnet && (!nativePcsc || config.PeerHost == null || !TailnetTransport.IsHost(config.PeerHost))) throw new InvalidDataException("Tailscale requires PC/SC and an explicit peer.");
    DateTimeOffset expires = config.PairingExpiresAt == null ? DateTimeOffset.MaxValue : DateTimeOffset.Parse(config.PairingExpiresAt);
    if (expires <= DateTimeOffset.UtcNow) throw new InvalidDataException("Pairing expired; generate a new pairing.");
    byte[] token = Convert.FromBase64String(config.PairingTokenBase64);
    if (token.Length != 32 || config.IdleTimeoutSeconds is < 5 or > 300 || config.Port is < 1024 or > 65535 || config.VpcdPort != 35963) throw new InvalidDataException("Invalid config bounds.");
    // A remote raw VPCD listener would bypass TLS entirely. Refuse the current exposed
    // Bix configuration until it is isolated by its owner; no unsafe override exists.
    if (!privateLocal)
    {
        try { BridgeSession.VerifyDriverIsolation(System.Net.NetworkInformation.IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners(), config.VpcdPort); }
        catch (InvalidOperationException error) { Console.WriteLine(error.Message); CryptographicOperations.ZeroMemory(token); Environment.ExitCode = 1; return; }
    }
    // Windows Schannel needs a temporary user key container. Do not use PersistKeySet.
    using var certificate = new X509Certificate2(config.CertificatePath, config.CertificatePassword, X509KeyStorageFlags.UserKeySet);
    if (!certificate.HasPrivateKey || DateTime.UtcNow < certificate.NotBefore.ToUniversalTime() || DateTime.UtcNow >= certificate.NotAfter.ToUniversalTime()) throw new InvalidDataException("Certificate unavailable or expired; provision again.");
    IPAddress bindAddress = IPAddress.Parse(config.Host);
    if (tailnet ? !TailnetTransport.IsAddress(bindAddress) : privateLocal && !IPAddress.IsLoopback(bindAddress)) throw new InvalidDataException("Transport binding outside its profile.");
    if (bindAddress.Equals(IPAddress.Any) || bindAddress.Equals(IPAddress.IPv6Any)) throw new InvalidDataException("Specify one local interface address.");
    using var stop = new CancellationTokenSource();
    using var pairingDeadline = CancellationTokenSource.CreateLinkedTokenSource(stop.Token);
    if (config.PairingExpiresAt != null) pairingDeadline.CancelAfter(expires - DateTimeOffset.UtcNow);
    Console.CancelKeyPress += (_, e) => { e.Cancel = true; stop.Cancel(); };
    var listener = new TcpListener(bindAddress, config.Port);
    string pipeName = "DNIeBridge-" + Guid.NewGuid().ToString("N");
    try
    {
        listener.Start(1);
        Console.WriteLine($"{config.Transport} bridge waiting. Ctrl+C closes the bridge and card session.");
        if (privateLocal && !nativePcsc) Console.WriteLine($"Session-local provider endpoint: {pipeName}. This is not a system PC/SC reader.");
        if (nativePcsc) Console.WriteLine("PC/SC integrated mode: native backend attaches after authenticated NFC ATR.");
        // One approved card session per invocation. No remote cleartext socket.
        using var client = await listener.AcceptTcpClientAsync(pairingDeadline.Token);
        if (tailnet) TailnetTransport.VerifyPeer(client, config.PeerHost!);
        stop.CancelAfter(TimeSpan.FromMinutes(30)); // Hard cap independent of mobile or task registration.
        listener.Stop();
        if (nativePcsc)
        {
            await PcscHostSession.RunAsync(client, tailnet ? null : certificate, token, expires, TimeSpan.FromSeconds(config.IdleTimeoutSeconds), stop.Token,
                () => {
                    var devices = WindowsPcscDevice.Enumerate();
                    if (devices.Length != 1) throw new InvalidOperationException("Exactly one DNIe reader required.");
                    return new WindowsPcscDevice(devices[0]);
                }, phase => Console.WriteLine($"{DateTimeOffset.UtcNow:O} bridge_phase={phase}"));
        }
        else if (privateLocal)
        {
            await BridgeSession.RunAsync(client, certificate, token, async ct =>
            {
                if (DateTimeOffset.UtcNow >= expires) throw new InvalidDataException("Pairing expired before authentication.");
                var pipe = PrivateCardPipe.Create(pipeName);
                try { await pipe.WaitForConnectionAsync(ct); return pipe; }
                catch { pipe.Dispose(); throw; }
            }, TimeSpan.FromSeconds(config.IdleTimeoutSeconds), stop.Token);
        }
        else await BridgeSession.RunAsync(client, certificate, token, config.VpcdPort, TimeSpan.FromSeconds(config.IdleTimeoutSeconds), stop.Token);
    }
    catch (OperationCanceledException) { Console.WriteLine("Bridge stopped or session timed out."); }
    catch (Exception) { Console.WriteLine("Bridge session closed. Authentication, transport or framing failed."); Environment.ExitCode = 1; }
    finally { listener.Stop(); CryptographicOperations.ZeroMemory(token); }
}
else throw new ArgumentException("Unknown command.");

public sealed record ServerConfig(string Host, int Port, int VpcdPort, string CertificatePath, string CertificatePassword, string PairingTokenBase64, int IdleTimeoutSeconds, string? PairingExpiresAt = null, string Transport = "tls", string? AdvertisedHost = null, string? PeerHost = null);
