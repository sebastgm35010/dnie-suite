using System.Text;
using System.Text.Json;
using DniBridge;

static class BridgePairingQrTests
{
    public static int Run()
    {
        var now = DateTimeOffset.Parse("2030-01-01T00:00:00Z");
        byte[] token = Enumerable.Range(0, 32).Select(x => (byte)x).ToArray();
        string pin = new('A', 64);
        string qr = BridgePairingQr.Encode("127.0.0.1", 39443, pin, token, now.AddMinutes(10), now);
        string encoded = qr[BridgePairingQr.Prefix.Length..].Replace('-', '+').Replace('_', '/');
        encoded += new string('=', (4 - encoded.Length % 4) % 4);
        using var json = JsonDocument.Parse(Convert.FromBase64String(encoded));
        if (json.RootElement.GetProperty("host").GetString() != "127.0.0.1" ||
            json.RootElement.GetProperty("pairingTokenBase64").GetString() != Convert.ToBase64String(token) ||
            !qr.StartsWith(BridgePairingQr.Prefix) || qr.Length > 2048) throw new Exception("QR roundtrip failed.");
        int passed = 1;
        string direct = BridgePairingQr.Encode("sebas-laptop.tail2752e9.ts.net",39443,pin,token,now.AddMinutes(10),now,"tailscale_tcp");
        string content = direct[BridgePairingQr.Prefix.Length..].Replace('-','+').Replace('_','/');
        using var directJson = JsonDocument.Parse(Convert.FromBase64String(content + new string('=',(4-content.Length%4)%4)));
        if (directJson.RootElement.GetProperty("transport").GetString() != "tailscale_tcp") throw new Exception("Direct QR transport lost.");
        passed++;
        try { BridgePairingQr.Encode("192.168.1.5",39443,pin,token,now.AddMinutes(10),now,"tailscale_tcp"); throw new Exception("LAN direct accepted."); }
        catch (ArgumentException) { passed++; }
        foreach (var expiry in new[] { now, now.AddMinutes(-1), now.AddMinutes(16) })
        {
            try { BridgePairingQr.Encode("127.0.0.1", 39443, pin, token, expiry, now); throw new Exception("Expiry accepted."); }
            catch (ArgumentException) { passed++; }
        }
        foreach (var host in new[] { "0.0.0.0", "::", "https://example.test" })
        {
            try { BridgePairingQr.Encode(host, 39443, pin, token, now.AddMinutes(10), now); throw new Exception("Host accepted."); }
            catch (ArgumentException) { passed++; }
        }
        return passed;
    }
}
