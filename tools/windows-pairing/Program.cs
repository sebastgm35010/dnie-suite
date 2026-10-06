using System.Security.AccessControl;
using System.Security.Principal;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using System.Net;
using DniBridge;
using QRCoder;

if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException();
if (args.Length != 2 || args[0] != "--config") throw new ArgumentException("Usage: WindowsPairing --config <private server-private.json>");
string configPath = Path.GetFullPath(args[1]);
string directory = Path.GetDirectoryName(configPath)!;
string sid = WindowsIdentity.GetCurrent().User!.Value;
var rules = new DirectoryInfo(directory).GetAccessControl().GetAccessRules(true, true, typeof(SecurityIdentifier));
foreach (FileSystemAccessRule rule in rules)
    if (rule.AccessControlType == AccessControlType.Allow && rule.IdentityReference.Value != sid && rule.IdentityReference.Value != "S-1-5-18")
        throw new IOException("Pairing directory must be private to the current user and SYSTEM.");
var config = JsonSerializer.Deserialize<ServerConfig>(await File.ReadAllTextAsync(configPath), new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
    ?? throw new InvalidDataException("Invalid configuration.");
if (config.PairingExpiresAt == null) throw new InvalidDataException("Generate a new short-lived pairing with setup.");
using var certificate = new X509Certificate2(await File.ReadAllBytesAsync(config.CertificatePath), config.CertificatePassword, X509KeyStorageFlags.EphemeralKeySet);
var expires = DateTimeOffset.Parse(config.PairingExpiresAt);
if (DateTimeOffset.UtcNow >= certificate.NotAfter.ToUniversalTime() || DateTimeOffset.UtcNow < certificate.NotBefore.ToUniversalTime()) throw new InvalidDataException("TLS certificate expired.");
string pin = Convert.ToHexString(SHA256.HashData(certificate.RawData));
byte[] token = Convert.FromBase64String(config.PairingTokenBase64);
byte[]? png = null;
try
{
    string payload = BridgePairingQr.Encode(config.AdvertisedHost ?? config.Host, config.Port, pin, token, expires, DateTimeOffset.UtcNow, config.Transport);
    using var data = QRCodeGenerator.GenerateQrCode(payload, QRCodeGenerator.ECCLevel.M);
    using var renderer = new PngByteQRCode(data);
    png = renderer.GetGraphic(6);
    string fingerprint = string.Join(":", Enumerable.Range(0, 32).Select(i => pin.Substring(i * 2, 2)));
    // No payload text, remote assets, analytics or browser/search handoff.
    string html = $$"""
    <!doctype html><html lang="es"><meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
    <meta name="referrer" content="no-referrer"><title>Emparejar DNIe · PC</title>
    <style>body{font-family:system-ui;background:#f5f5f5;color:#171717;max-width:720px;margin:32px auto;padding:24px}article{background:white;border:1px solid #222;padding:24px;border-radius:16px}img{display:block;width:min(100%,480px);margin:auto}code{display:block;overflow-wrap:anywhere;font-size:14px}button{padding:12px;margin-top:16px}</style>
    <article><h1>Emparejar el teléfono</h1><p>En la app abre <b>Puente NFC · PC → Escanear QR de la PC</b>.</p>
    <img id="qr" alt="QR privado de emparejamiento" src="data:image/png;base64,{{Convert.ToBase64String(png)}}">
    <p>Equipo: {{WebUtility.HtmlEncode(config.AdvertisedHost ?? config.Host)}}:{{config.Port}}</p><p>Transporte: {{WebUtility.HtmlEncode(config.Transport)}}. {{(config.Transport == "tailscale_tcp" ? "Tailscale directo, sin TLS adicional." : "Compara esta huella con la mostrada en el teléfono:")}}</p><code>{{(config.Transport == "tailscale_tcp" ? "Autorización por token de sesión" : fingerprint)}}</code>
    <p id="expiry">Válido hasta {{expires.LocalDateTime:HH:mm:ss}}. Este QR autoriza el emparejamiento; muéstralo solo a tu teléfono.</p>
    <button onclick="hide()">Ocultar QR</button><p>Escanear configura el cliente. El DNI se autoriza después desde la app.</p></article>
    <script>function hide(){document.getElementById('qr').removeAttribute('src');document.getElementById('qr').hidden=true}setTimeout(()=>{hide();document.getElementById('expiry').textContent='QR vencido. Genera otro emparejamiento.'},{{Math.Max(0, (long)(expires-DateTimeOffset.UtcNow).TotalMilliseconds)}});</script></html>
    """;
    string page = Path.Combine(directory, "pairing-qr.html");
    await File.WriteAllTextAsync(page, html);
    Console.WriteLine("QR local creado en la carpeta privada. No publique este archivo.");
    Console.WriteLine(page);
}
finally { CryptographicOperations.ZeroMemory(token); if (png != null) CryptographicOperations.ZeroMemory(png); }
