using System.Security.AccessControl;
using System.Security.Principal;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
namespace DniBridge;

/// <summary>Private stable PC identity. Session directories receive copies, never generate new pins.</summary>
[System.Runtime.Versioning.SupportedOSPlatform("windows")]
public sealed record PcIdentity(string Password, string Token, string Id)
{
    public static Task RestrictAsync(string directory)
    {
        Directory.CreateDirectory(directory);
        if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0)
            throw new IOException("Identity directory cannot be a link.");
        string sid = WindowsIdentity.GetCurrent().User!.Value;
        var acl = new DirectorySecurity();
        acl.SetAccessRuleProtection(true, false);
        foreach (string allowed in new[] {sid, "S-1-5-18", "S-1-5-32-544"}.Distinct())
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(allowed), FileSystemRights.FullControl,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
        new DirectoryInfo(directory).SetAccessControl(acl);
        return Task.CompletedTask;
    }
    public static void VerifyLegacyProvenance(string directory, string metadataPath, string pfxPath, string ownerSid)
    {
        if (!File.Exists(metadataPath) || !File.Exists(pfxPath))
            throw new IOException("Identidad anterior incompleta; no se migro ni reemplazo automaticamente.");
        string? parent = Path.GetDirectoryName(directory);
        foreach (string? path in new[] { parent, directory, metadataPath, pfxPath }) {
            if (!string.IsNullOrEmpty(path) && (File.Exists(path) || Directory.Exists(path)) &&
                (File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("No se permite migrar una identidad anterior a traves de enlaces.");
        }
        var trusted = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "S-1-5-18", "S-1-5-32-544", ownerSid };
        // Elementary write/delete/ACL/ownership bits for directory provenance; exclude FullControl/Modify composites
        // whose masks also include Read/Execute/Synchronize bits so non-inherited directory traversal is not misclassified as write.
        const FileSystemRights directoryMutationMask =
            FileSystemRights.Write |
            FileSystemRights.Delete |
            FileSystemRights.DeleteSubdirectoriesAndFiles |
            FileSystemRights.ChangePermissions |
            FileSystemRights.TakeOwnership;
        // Secret files (identity.json with PFX password + token, and bridge.pfx with private key) must additionally
        // reject any read/execute exposure to untrusted principals.
        const FileSystemRights secretFileExposureMask =
            directoryMutationMask |
            FileSystemRights.ReadData |
            FileSystemRights.Read |
            FileSystemRights.ReadExtendedAttributes |
            FileSystemRights.ExecuteFile;
        void CheckSecurity(FileSystemSecurity security, string label, FileSystemRights forbiddenMask)
        {
            string? fileOwner = security.GetOwner(typeof(SecurityIdentifier))?.Value;
            if (fileOwner == null || !trusted.Contains(fileOwner))
                throw new UnauthorizedAccessException($"Propietario no confiable en identidad anterior ({label}): {fileOwner}");
            foreach (FileSystemAccessRule rule in security.GetAccessRules(true, true, typeof(SecurityIdentifier))) {
                if (rule.AccessControlType == AccessControlType.Allow &&
                    (rule.FileSystemRights & forbiddenMask) != 0 &&
                    !trusted.Contains(rule.IdentityReference.Value))
                    throw new UnauthorizedAccessException($"Permisos no confiables o secreto expuesto en identidad anterior ({label}): {rule.IdentityReference.Value}");
            }
        }
        CheckSecurity(new DirectoryInfo(directory).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access), "directorio", directoryMutationMask);
        CheckSecurity(new FileInfo(metadataPath).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access), "identity.json", secretFileExposureMask);
        CheckSecurity(new FileInfo(pfxPath).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access), "bridge.pfx", secretFileExposureMask);
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
