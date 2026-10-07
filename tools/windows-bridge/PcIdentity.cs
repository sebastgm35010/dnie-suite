using System.Diagnostics;
using System.Security.Principal;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
namespace DniBridge;

/// <summary>Private stable PC identity. Session directories receive copies, never generate new pins.</summary>
[System.Runtime.Versioning.SupportedOSPlatform("windows")]
public sealed record PcIdentity(string Password, string Token, string Id)
{
    public static async Task RestrictAsync(string directory)
    {
        Directory.CreateDirectory(directory);
        if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0)
            throw new IOException("Identity directory cannot be a link.");
        string sid = WindowsIdentity.GetCurrent().User!.Value;
        var command = new ProcessStartInfo("icacls.exe") { UseShellExecute=false, CreateNoWindow=true,
            RedirectStandardOutput=true, RedirectStandardError=true };
        foreach (string arg in new[] { directory, "/inheritance:r", "/grant:r", $"*{sid}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F" }) command.ArgumentList.Add(arg);
        using var process = Process.Start(command)!;
        await Task.WhenAll(process.StandardOutput.ReadToEndAsync(), process.StandardError.ReadToEndAsync());
        await process.WaitForExitAsync();
        if (process.ExitCode != 0) throw new IOException("Cannot protect PC identity.");
    }
    public static async Task<PcIdentity> LoadOrCreateAsync(string directory)
    {
        directory=Path.GetFullPath(directory);
        await RestrictAsync(directory);
        using var mutex = new Mutex(false, "Local\\DNIeBridgeIdentity-" + WindowsIdentity.GetCurrent().User!.Value);
        if (!mutex.WaitOne(TimeSpan.FromSeconds(10))) throw new IOException("Identity is busy.");
        // All operations while the named mutex is held are synchronous: mutex ownership is thread-affine.
        try {
            string metadata=Path.Combine(directory,"identity.json"), pfx=Path.Combine(directory,"bridge.pfx");
            if (File.Exists(metadata)) {
                var existing=JsonSerializer.Deserialize<PcIdentity>(File.ReadAllText(metadata)) ?? throw new IOException("Invalid PC identity.");
                using var cert=new X509Certificate2(File.ReadAllBytes(pfx),existing.Password,X509KeyStorageFlags.EphemeralKeySet);
                if (!cert.HasPrivateKey || cert.NotAfter.ToUniversalTime() <= DateTime.UtcNow) throw new IOException("PC identity expired; explicitly rotate it and re-pair.");
                return existing;
            }
            if (File.Exists(pfx)) throw new IOException("Incomplete identity; do not replace the key automatically.");
            using var key=RSA.Create(3072);
            var request=new CertificateRequest("CN=DNIe PC Bridge",key,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1);
            request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false,false,0,true));
            request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,true));
            request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") },true));
            using var certificate=request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5),DateTimeOffset.UtcNow.AddYears(10));
            var identity=new PcIdentity(Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)),Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)),Guid.NewGuid().ToString("N"));
            File.WriteAllBytes(pfx,certificate.Export(X509ContentType.Pfx,identity.Password));
            File.WriteAllText(metadata,JsonSerializer.Serialize(identity));
            return identity;
        } finally { mutex.ReleaseMutex(); }
    }
}
