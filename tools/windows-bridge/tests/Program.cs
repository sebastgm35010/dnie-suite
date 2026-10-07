using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using DniBridge;

if (args.Length == 2 && args[0] == "--interop-fixture")
{
    await InteropFixture.RunAsync(args[1]);
    return;
}

using var rsa = RSA.Create(2048);
using var generated = new CertificateRequest("CN=bridge-test", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1).CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddHours(1));
// Windows Schannel cannot use an ephemeral private key; default temporary user container
// is deleted on disposal (PersistKeySet is deliberately absent).
using var certificate = new X509Certificate2(generated.Export(X509ContentType.Pfx), (string?)null, X509KeyStorageFlags.UserKeySet);
byte[] token = RandomNumberGenerator.GetBytes(32);
byte[] pin = SHA256.HashData(certificate.RawData);
int passed = 0;
passed += BridgePairingQrTests.Run();
foreach (var ip in new[] { "192.168.1.5", "10.0.0.2", "172.16.0.3" }) {
    var address = IPAddress.Parse(ip);
    Assert(LocalNetworkPolicy.IsBindingAllowed(address, [address]), "Assigned LAN/USB accepted.");
    Assert(!LocalNetworkPolicy.IsBindingAllowed(address, []), "Unassigned interface rejected.");
}
foreach (var ip in new[] { "0.0.0.0", "8.8.8.8", "100.104.106.125", "169.254.1.1", "::", "::1" })
    Assert(!LocalNetworkPolicy.IsBindingAllowed(IPAddress.Parse(ip), [IPAddress.Parse(ip)]), "Public, wildcard or unsupported binding rejected.");
BridgeSession.VerifyDriverIsolation([new IPEndPoint(IPAddress.Loopback, 35963)], 35963);
Assert(true, "Loopback-only driver accepted.");
foreach (var address in new[] { IPAddress.Any, IPAddress.IPv6Any, IPAddress.Parse("192.0.2.1") })
{
    bool rejected = false;
    try { BridgeSession.VerifyDriverIsolation([new IPEndPoint(address, 35963)], 35963); }
    catch (InvalidOperationException) { rejected = true; }
    Assert(rejected, "Exposed driver must be rejected.");
}
bool absentRejected = false;
try { BridgeSession.VerifyDriverIsolation([], 35963); }
catch (InvalidOperationException) { absentRejected = true; }
Assert(absentRejected, "Absent driver must be rejected.");
await TestRejectedToken();
await TestWrongPin();
await TestFramesAndDisconnect();
await TestMalformedFrame();
await TestTimeout();
await TestIdleDoesNotExpire();
passed += await UsbTransportTests.RunAsync();
passed += await PcIdentityTests.RunAsync();
await TestReverseTls();
passed += await PrivateCardPipeTests.RunAsync();
passed += await PcscBackendTests.RunAsync();
await TestPrivatePipeRelay();
await TestPrivatePipeAuthentication();
await TestIntegratedPcsc();
await TestIntegratedDirect();
await TestRepeatedSynchronization();
Console.WriteLine($"PASS {passed} checks (loopback TLS integration and isolation policy, synthetic bytes only).");

void Assert(bool condition, string description) { if (!condition) throw new Exception(description); passed++; }
TcpListener Listen() { var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start(); return listener; }
async Task<(TcpClient client, SslStream tls, Task server, TcpListener host)> Start(bool wrongToken = false, bool wrongPin = false, int timeoutSeconds = 2,
    Func<CancellationToken, Task<Stream>>? connectLocal = null, Func<IPcscDevice>? native = null)
{
    var host = Listen(); var remote = Listen();
    var accepted = remote.AcceptTcpClientAsync();
    var client = new TcpClient(); await client.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)remote.LocalEndpoint).Port);
    var serverClient = await accepted; remote.Stop();
    var server = native != null
        ? PcscHostSession.RunAsync(serverClient, certificate, token, DateTimeOffset.UtcNow.AddMinutes(1), TimeSpan.FromSeconds(timeoutSeconds), CancellationToken.None, native, _ => { })
        : connectLocal == null
        ? BridgeSession.RunAsync(serverClient, certificate, token, ((IPEndPoint)host.LocalEndpoint).Port, TimeSpan.FromSeconds(timeoutSeconds), CancellationToken.None)
        : BridgeSession.RunAsync(serverClient, certificate, token, connectLocal, TimeSpan.FromSeconds(timeoutSeconds), CancellationToken.None);
    var tls = new SslStream(client.GetStream(), false, (_, cert, _, _) => cert != null && CryptographicOperations.FixedTimeEquals(SHA256.HashData(cert.GetRawCertData()), wrongPin ? new byte[32] : pin));
    try
    {
        await tls.AuthenticateAsClientAsync(new SslClientAuthenticationOptions { TargetHost = "bridge-test", EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13 });
        byte[] hello = new byte[40]; "DNIBRDG1"u8.CopyTo(hello); token.CopyTo(hello, 8); if (wrongToken) hello[8] ^= 1;
        // Force fragmentation of the authentication prelude too.
        foreach (byte value in hello) await tls.WriteAsync(new byte[] { value });
    }
    catch when (wrongPin) { }
    catch
    {
        await server;
        throw;
    }
    return (client, tls, server, host);
}
async Task ExpectClosed(Task task)
{
    try { await task.WaitAsync(TimeSpan.FromSeconds(5)); throw new Exception("Expected session closure."); }
    catch (Exception error) when (error is not TimeoutException && error.Message != "Expected session closure.") { }
}
async Task TestRejectedToken()
{
    var fixture = await Start(wrongToken: true);
    using (fixture.client) using (fixture.tls)
    {
        await ExpectClosed(fixture.server);
        Assert(!fixture.host.Pending(), "Wrong token must never connect to virtual driver.");
    }
    fixture.host.Stop();
}
async Task TestWrongPin()
{
    var fixture = await Start(wrongPin: true);
    using (fixture.client) using (fixture.tls)
    {
        await ExpectClosed(fixture.server);
        Assert(!fixture.host.Pending(), "Wrong certificate pin must never reach virtual driver.");
    }
    fixture.host.Stop();
}
async Task<(TcpClient peer, NetworkStream stream)> HostConnection(TcpListener host, SslStream tls)
{
    byte[] ack = new byte[1]; await tls.ReadExactlyAsync(ack); Assert(ack[0] == 1, "Successful authentication ACK.");
    var peer = await host.AcceptTcpClientAsync().WaitAsync(TimeSpan.FromSeconds(3));
    return (peer, peer.GetStream());
}
async Task TestFramesAndDisconnect()
{
    var fixture = await Start();
    using (fixture.client) using (fixture.tls)
    {
        var host = await HostConnection(fixture.host, fixture.tls);
        using (host.peer) using (host.stream)
        {
            // Controls 0/1/2 forward with no fabricated response, followed by ATR request.
            byte[] request = [0, 1, 0, 0, 1, 1, 0, 1, 2, 0, 1, 4];
            foreach (byte value in request) await host.stream.WriteAsync(new byte[] { value });
            byte[] received = new byte[request.Length]; await fixture.tls.ReadExactlyAsync(received);
            Assert(received.SequenceEqual(request), "Fragmented controls forwarded byte for byte.");
            byte[] response = [0, 3, 0x3b, 0x80, 0];
            foreach (byte value in response) await fixture.tls.WriteAsync(new byte[] { value });
            received = new byte[response.Length]; await host.stream.ReadExactlyAsync(received);
            Assert(received.SequenceEqual(response), "Fragmented ATR response forwarded byte for byte.");
            // Maximum accepted length remains exactly uint16 bounded.
            response = new byte[65537]; response[0] = 255; response[1] = 255; response[^1] = 0x42;
            var writing = fixture.tls.WriteAsync(response).AsTask();
            received = new byte[response.Length]; await host.stream.ReadExactlyAsync(received); await writing;
            Assert(received.SequenceEqual(response), "Maximum length accepted intact.");
            fixture.client.Close(); await ExpectClosed(fixture.server);
            byte[] single = new byte[1];
            int read = await host.stream.ReadAsync(single).AsTask().WaitAsync(TimeSpan.FromSeconds(3));
            Assert(read == 0, "Android disconnect removes virtual card client.");
        }
    }
    fixture.host.Stop();
}
async Task TestMalformedFrame()
{
    var fixture = await Start();
    using (fixture.client) using (fixture.tls)
    {
        var host = await HostConnection(fixture.host, fixture.tls);
        using (host.peer) using (host.stream)
        {
            await fixture.tls.WriteAsync(new byte[] { 0, 0 }); await ExpectClosed(fixture.server);
            Assert(await host.stream.ReadAsync(new byte[1]) == 0, "Empty frame closes driver connection.");
        }
    }
    fixture.host.Stop();
}
async Task TestReverseTls()
{
    var phoneListener=Listen();var localListener=Listen();
    using var pc=new TcpClient();
    await pc.ConnectAsync(IPAddress.Loopback,((IPEndPoint)phoneListener.LocalEndpoint).Port);
    using var phone=await phoneListener.AcceptTcpClientAsync();phoneListener.Stop();
    using var stop=new CancellationTokenSource(TimeSpan.FromSeconds(10));
    var readiness=TransportReadiness.WaitAsync(pc.GetStream(),stop.Token);
    foreach(byte b in "DNIREADY1"u8.ToArray())await phone.GetStream().WriteAsync(new byte[]{b},stop.Token);
    await readiness;Assert(true,"Fragmented nine-byte readiness accepted.");
    var server=BridgeSession.RunAsync(pc.GetStream(),certificate,token,ct=>Task.FromResult<Stream>(new MemoryStream()),TimeSpan.FromSeconds(2),stop.Token);
    using var tls=new SslStream(phone.GetStream(),false,(_,cert,_,_)=>cert!=null && CryptographicOperations.FixedTimeEquals(SHA256.HashData(cert.GetRawCertData()),pin));
    await tls.AuthenticateAsClientAsync(new SslClientAuthenticationOptions{TargetHost="bridge-test",EnabledSslProtocols=SslProtocols.Tls12 | SslProtocols.Tls13},stop.Token);
    await tls.WriteAsync("DNIBRDG1"u8.ToArray(),stop.Token);await tls.WriteAsync(token,stop.Token);
    byte[] ack=new byte[1];await tls.ReadExactlyAsync(ack,stop.Token);
    Assert(ack[0]==1,"PC initiates TCP, Android remains the pinned TLS client.");
    await ExpectClosed(server);
    localListener.Stop();
    await AssertBadReadiness();
}
async Task AssertBadReadiness() {
    try {await TransportReadiness.WaitAsync(new MemoryStream("BADREADY1"u8.ToArray()),CancellationToken.None);throw new Exception("Invalid readiness accepted.");}
    catch(InvalidDataException){Assert(true,"Invalid readiness rejected before TLS/card.");}
}
async Task TestIdleDoesNotExpire()
{
    var fixture = await Start(timeoutSeconds: 1);
    using (fixture.client) using (fixture.tls) {
        var host=await HostConnection(fixture.host,fixture.tls);
        using(host.peer) using(host.stream) {
            await Task.Delay(1300);
            Assert(!fixture.server.IsCompleted,"Authenticated idle session stays alive beyond the frame timeout.");
            await fixture.tls.WriteAsync(new byte[]{0,1,2});
            var frame=new byte[3];await host.stream.ReadExactlyAsync(frame);
            Assert(frame.SequenceEqual(new byte[]{0,1,2}),"Command after idle arrives unchanged.");
        }
        await ExpectClosed(fixture.server);
    }
    fixture.host.Stop();
}
async Task TestTimeout()
{
    var fixture = await Start(timeoutSeconds: 1);
    using (fixture.client) using (fixture.tls)
    {
        var host = await HostConnection(fixture.host, fixture.tls);
        using (host.peer) using (host.stream)
        {
            await fixture.tls.WriteAsync(new byte[] { 0, 4, 1 });
            await ExpectClosed(fixture.server);
            Assert(await host.stream.ReadAsync(new byte[1]) == 0, "Truncated payload deadline closes driver.");
        }
    }
    fixture.host.Stop();
}

async Task TestPrivatePipeRelay()
{
    string name = "DNIeBridge-" + Guid.NewGuid().ToString("N");
    var fixture = await Start(timeoutSeconds: 5, connectLocal: async ct =>
    {
        var pipe = PrivateCardPipe.Create(name);
        try { await pipe.WaitForConnectionAsync(ct); return pipe; }
        catch { pipe.Dispose(); throw; }
    });
    using (fixture.client) using (fixture.tls)
    using (var desktop = new System.IO.Pipes.NamedPipeClientStream(".", name, System.IO.Pipes.PipeDirection.InOut, System.IO.Pipes.PipeOptions.Asynchronous))
    {
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        byte[] ack = new byte[1]; await fixture.tls.ReadExactlyAsync(ack, deadline.Token);
        await desktop.ConnectAsync(deadline.Token);
        await desktop.WriteAsync(new byte[] { 0, 1, 4 }, deadline.Token);
        byte[] request = new byte[3]; await fixture.tls.ReadExactlyAsync(request, deadline.Token);
        Assert(request.SequenceEqual(new byte[] { 0, 1, 4 }), "Private local command reaches authenticated TLS.");
        foreach (byte value in new byte[] { 0, 2, 0x90, 0 }) await fixture.tls.WriteAsync(new byte[] { value }, deadline.Token);
        byte[] response = new byte[4]; await desktop.ReadExactlyAsync(response, deadline.Token);
        Assert(response.SequenceEqual(new byte[] { 0, 2, 0x90, 0 }), "TLS response returns through private pipe.");
        desktop.Close(); await ExpectClosed(fixture.server);
        Assert(true, "Closing private endpoint closes TLS.");
    }
    fixture.host.Stop();
}

async Task TestPrivatePipeAuthentication()
{
    bool opened = false;
    var fixture = await Start(wrongToken: true, connectLocal: ct =>
    {
        opened = true;
        throw new Exception("Unauthenticated endpoint must not be opened.");
    });
    using (fixture.client) using (fixture.tls) { await ExpectClosed(fixture.server); }
    Assert(!opened, "Wrong token never opens the private local endpoint.");
    fixture.host.Stop();
}

async Task TestIntegratedPcsc()
{
    int opened = 0;
    var rejected = await Start(wrongToken: true, native: () => { opened++; throw new Exception("Forbidden backend"); });
    using (rejected.client) using (rejected.tls) { await ExpectClosed(rejected.server); }
    rejected.host.Stop();
    Assert(opened == 0, "Wrong token never opens integrated native backend.");
    var device = new PcscHostTestDevice();
    var fixture = await Start(native: () => device);
    using (fixture.client) using (fixture.tls)
    {
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        byte[] ack = new byte[1]; await fixture.tls.ReadExactlyAsync(ack, deadline.Token);
        Assert(ack[0] == 1, "Integrated mode authenticates phone first.");
        byte[] frame = new byte[3]; await fixture.tls.ReadExactlyAsync(frame, deadline.Token);
        Assert(frame.SequenceEqual(new byte[] { 0, 1, 4 }), "Integrated native backend asks authenticated mobile for ATR.");
        await fixture.tls.WriteAsync(new byte[] { 0, 5, 0x3B, 0x80, 0x80, 1, 1 }, deadline.Token);
        await fixture.tls.ReadExactlyAsync(frame, deadline.Token);
        Assert(device.Attached && frame.SequenceEqual(new byte[] { 0, 1, 2 }), "Real ATR attaches before PCSC reset is sent.");
        await fixture.tls.WriteAsync(new byte[] { 0, 5, 0x3B, 0x80, 0x80, 1, 1 }, deadline.Token);
        while (!device.Completed) await Task.Delay(10, deadline.Token);
        fixture.client.Close(); await ExpectClosed(fixture.server);
        Assert(device.Detached && device.Disposed, "Integrated transport closure detaches and disposes native backend.");
    }
    fixture.host.Stop();
}

async Task TestIntegratedDirect()
{
    Assert(TailnetTransport.IsAddress(IPAddress.Parse("100.104.106.125")), "Tailnet address accepted.");
    Assert(TailnetTransport.IsHost("sebas-laptop.tail2752e9.ts.net"), "MagicDNS accepted.");
    foreach (string address in new[] { "127.0.0.1", "192.168.1.5", "8.8.8.8", "100.63.255.255", "100.128.0.1" })
        Assert(!TailnetTransport.IsAddress(IPAddress.Parse(address)), "Non-tailnet rejected.");
    foreach (bool wrong in new[] { true, false })
    {
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        using var listener = Listen();
        using var phone = new TcpClient();
        var accept = listener.AcceptTcpClientAsync(deadline.Token);
        await phone.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port, deadline.Token);
        var peer = await accept;
        var device = new PcscHostTestDevice();
        bool opened = false;
        var server = PcscHostSession.RunAsync(peer, null, token, DateTimeOffset.UtcNow.AddMinutes(1), TimeSpan.FromSeconds(3), deadline.Token,
            () => { opened = true; return device; }, _ => {});
        var stream = phone.GetStream();
        byte[] hello = new byte[40]; "DNIBRDG1"u8.CopyTo(hello); token.CopyTo(hello,8);
        if (wrong) hello[8] ^= 1;
        foreach (byte value in hello) await stream.WriteAsync(new byte[] { value }, deadline.Token);
        CryptographicOperations.ZeroMemory(hello);
        if (wrong) {
            await ExpectClosed(server);
            Assert(!opened, "Direct wrong token cannot open PCSC.");
            continue;
        }
        byte[] ack = new byte[1]; await stream.ReadExactlyAsync(ack,deadline.Token);
        Assert(ack[0] == 1, "Direct mode authenticates without TLS.");
        byte[] frame = new byte[3]; await stream.ReadExactlyAsync(frame,deadline.Token);
        Assert(frame.SequenceEqual(new byte[] { 0,1,4 }), "Direct PCSC asks for ATR.");
        await stream.WriteAsync(new byte[] {0,5,0x3b,0x80,0x80,1,1},deadline.Token);
        await stream.ReadExactlyAsync(frame,deadline.Token);
        Assert(device.Attached && frame.SequenceEqual(new byte[] {0,1,2}), "Direct real ATR attaches and warm resets.");
        await stream.WriteAsync(new byte[] {0,5,0x3b,0x80,0x80,1,1},deadline.Token);
        while (!device.Completed) await Task.Delay(10,deadline.Token);
        phone.Close(); await ExpectClosed(server);
        Assert(device.Detached && device.Disposed, "Direct disconnect closes native device.");
    }
}

async Task TestRepeatedSynchronization()
{
    using var stop = new CancellationTokenSource(TimeSpan.FromSeconds(10));
    using var listener = new TcpListener(IPAddress.Loopback,0);
    listener.Start();
    int port = ((IPEndPoint)listener.LocalEndpoint).Port;
    var devices = new List<PcscHostTestDevice>();
    var server = TailnetSyncServer.ServeAsync(listener, c => {
        Assert(IPAddress.IsLoopback(((IPEndPoint)c.Client.RemoteEndPoint!).Address),"Test peer checked before bootstrap.");
    }, () => { var d = new PcscHostTestDevice(); devices.Add(d); return d; }, _ => {}, stop.Token);
    byte[]? previous = null;
    try {
        using (var malformed = new TcpClient()) {
            await malformed.ConnectAsync(IPAddress.Loopback,port,stop.Token);
            await malformed.GetStream().WriteAsync("BADSYNC1"u8.ToArray(),stop.Token);
            Assert(await malformed.GetStream().ReadAsync(new byte[1],stop.Token) == 0,"Invalid sync closes before backend.");
        }
        Assert(devices.Count == 0,"Malformed sync cannot open reader.");
        for (int round=0; round<2; round++) {
            using var phone = new TcpClient();
            await phone.ConnectAsync(IPAddress.Loopback,port,stop.Token);
            var stream = phone.GetStream();
            foreach (byte b in "DNISYNC1"u8.ToArray()) await stream.WriteAsync(new byte[] {b},stop.Token);
            byte[] sync = new byte[40]; await stream.ReadExactlyAsync(sync,stop.Token);
            Assert(sync.AsSpan(0,8).SequenceEqual("DNISYNC1"u8),"Sync reply version verified.");
            byte[] fresh = sync[8..];
            if (previous != null) Assert(!fresh.SequenceEqual(previous),"Reconnect gets independent token.");
            previous = fresh;
            byte[] hello = new byte[40]; "DNIBRDG1"u8.CopyTo(hello); fresh.CopyTo(hello,8);
            await stream.WriteAsync(hello,stop.Token);
            byte[] ack = new byte[1]; await stream.ReadExactlyAsync(ack,stop.Token);
            Assert(ack[0] == 1,"Synced session authenticated.");
            byte[] frame = new byte[3]; await stream.ReadExactlyAsync(frame,stop.Token);
            Assert(frame.SequenceEqual(new byte[] {0,1,4}),"Synced reader requests ATR.");
            await stream.WriteAsync(new byte[] {0,5,0x3b,0x80,0x80,1,1},stop.Token);
            await stream.ReadExactlyAsync(frame,stop.Token);
            Assert(frame.SequenceEqual(new byte[] {0,1,2}),"Synced reader warm resets.");
            await stream.WriteAsync(new byte[] {0,5,0x3b,0x80,0x80,1,1},stop.Token);
            while (!devices[round].Completed) await Task.Delay(10,stop.Token);
            phone.Close();
            while (!devices[round].Disposed) await Task.Delay(10,stop.Token);
            Assert(devices[round].Detached,"Old reader detached before next session.");
        }
    } finally {
        stop.Cancel();
        try { await server; } catch (OperationCanceledException) {}
    }
}
