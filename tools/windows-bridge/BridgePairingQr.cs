using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace DniBridge;

/// <summary>Wire format only; QR rendering and platform presentation live elsewhere.</summary>
public static class BridgePairingQr
{
    public const string Prefix = "DNIEBRIDGE1:";
    public static string Encode(string host, int port, string certificateSha256, byte[] token, DateTimeOffset expires, DateTimeOffset now, string transport = "tls")
    {
        bool validHost = transport == "tailscale_tcp" ? TailnetTransport.IsHost(host) :
            IPAddress.TryParse(host, out var ip) && !ip.Equals(IPAddress.Any) && !ip.Equals(IPAddress.IPv6Any);
        if (!validHost || transport is not ("tls" or "tailscale_tcp") ||
            port is < 1024 or > 65535 || token.Length != 32 ||
            certificateSha256.Length != 64 || !certificateSha256.All(Uri.IsHexDigit) ||
            expires <= now || expires > now.AddMinutes(15)) throw new ArgumentException("Invalid QR pairing.");
        byte[] json = JsonSerializer.SerializeToUtf8Bytes(new { version = 1, host, port, certificateSha256, transport,
            pairingTokenBase64 = Convert.ToBase64String(token), expiresAt = expires.UtcDateTime.ToString("O") });
        try { return Prefix + Convert.ToBase64String(json).TrimEnd('=').Replace('+', '-').Replace('/', '_'); }
        finally { CryptographicOperations.ZeroMemory(json); }
    }
}
