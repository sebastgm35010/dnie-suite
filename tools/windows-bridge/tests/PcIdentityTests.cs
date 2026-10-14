using DniBridge;
using System.Linq;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.Principal;
static class PcIdentityTests {
    private static void RestrictFileToTrusted(string filePath, string sid) {
        var acl = new FileSecurity();
        acl.SetAccessRuleProtection(true, false);
        foreach (string allowed in new[] { sid, "S-1-5-18", "S-1-5-32-544" }.Distinct())
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(allowed), FileSystemRights.FullControl, AccessControlType.Allow));
        new FileInfo(filePath).SetAccessControl(acl);
    }
    public static async Task<int> RunAsync() {
        if(!OperatingSystem.IsWindows())return 0;
        string directory=Path.Combine("D:/DNIeToolchain/Temp","identity-test-"+Guid.NewGuid().ToString("N"));
        try {
            var first=await PcIdentity.LoadOrCreateAsync(directory);
            string metadataPath=Path.Combine(directory,"identity.json");
            string pfxPath=Path.Combine(directory,"bridge.pfx");
            string sid=WindowsIdentity.GetCurrent().User!.Value;
            byte[] pfx=File.ReadAllBytes(pfxPath);
            var second=await PcIdentity.LoadOrCreateAsync(directory);
            if(first!=second || !pfx.SequenceEqual(File.ReadAllBytes(pfxPath)))throw new Exception("PC identity changed between sessions.");
            using var certificate=new X509Certificate2(pfx,first.Password,X509KeyStorageFlags.EphemeralKeySet);
            string pin=Convert.ToHexString(SHA256.HashData(certificate.RawData));
            var token=Convert.FromBase64String(first.Token);
            foreach(string transport in new[]{"tls_reverse","usb_accessory"})
                if(!BridgePairingQr.Encode("127.0.0.1",39443,pin,token,certificate.NotAfter.ToUniversalTime(),DateTimeOffset.UtcNow,transport,true).StartsWith(BridgePairingQr.Prefix))throw new Exception("Persistent identity QR failed.");
            // 1. Default restricted directory and secret files pass provenance verification.
            PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid);
            // 2. Non-inherited directory ReadAndExecute for Users (S-1-5-32-545) is allowed when secret files remain protected.
            var users=new SecurityIdentifier("S-1-5-32-545");
            var dirInfo=new DirectoryInfo(directory);
            var dirTraverseAcl=dirInfo.GetAccessControl();
            dirTraverseAcl.AddAccessRule(new FileSystemAccessRule(users,FileSystemRights.ReadAndExecute,InheritanceFlags.None,PropagationFlags.None,AccessControlType.Allow));
            dirInfo.SetAccessControl(dirTraverseAcl);
            PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid);
            await PcIdentity.RestrictAsync(directory);
            // 3. Explicit Read for Users on secret files (identity.json and bridge.pfx) must be rejected.
            foreach(string secretFile in new[]{metadataPath,pfxPath}) {
                var fInfo=new FileInfo(secretFile);
                var fAcl=fInfo.GetAccessControl();
                fAcl.AddAccessRule(new FileSystemAccessRule(users,FileSystemRights.Read,AccessControlType.Allow));
                fInfo.SetAccessControl(fAcl);
                bool secretReadRejected=false;
                try { PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid); }
                catch(UnauthorizedAccessException) { secretReadRejected=true; }
                if(!secretReadRejected)throw new Exception($"Untrusted Read ACE on secret file {Path.GetFileName(secretFile)} was accepted.");
                RestrictFileToTrusted(secretFile,sid);
            }
            // 4. Inherited ReadAndExecute from directory onto secret files must also be rejected.
            // Re-enable ACL inheritance on both secret files after case 3 before testing inherited exposure.
            foreach(string secretFile in new[]{metadataPath,pfxPath}) {
                var inheritableAcl=new FileInfo(secretFile).GetAccessControl();
                inheritableAcl.SetAccessRuleProtection(false,true);
                new FileInfo(secretFile).SetAccessControl(inheritableAcl);
            }
            var inheritedReadAcl=dirInfo.GetAccessControl();
            inheritedReadAcl.AddAccessRule(new FileSystemAccessRule(users,FileSystemRights.ReadAndExecute,InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
            dirInfo.SetAccessControl(inheritedReadAcl);
            foreach(string secretFile in new[]{metadataPath,pfxPath}) {
                bool hasInheritedUsersRead=new FileInfo(secretFile)
                    .GetAccessControl(AccessControlSections.Access)
                    .GetAccessRules(false,true,typeof(SecurityIdentifier))
                    .Cast<FileSystemAccessRule>()
                    .Any(r => r.IsInherited && r.IdentityReference.Value == users.Value && (r.FileSystemRights & FileSystemRights.ReadData) != 0);
                if(!hasInheritedUsersRead)throw new Exception($"Expected inherited Read ACE on {Path.GetFileName(secretFile)} before provenance check.");
            }
            bool inheritedSecretReadRejected=false;
            try { PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid); }
            catch(UnauthorizedAccessException) { inheritedSecretReadRejected=true; }
            if(!inheritedSecretReadRejected)throw new Exception("Inherited ReadAndExecute exposing secret files was accepted.");
            await PcIdentity.RestrictAsync(directory);
            // 5. Untrusted Write permission on directory must be rejected.
            var writeAcl=dirInfo.GetAccessControl();
            writeAcl.AddAccessRule(new FileSystemAccessRule(users,FileSystemRights.Write,InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
            dirInfo.SetAccessControl(writeAcl);
            bool writeRejected=false;
            try { PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid); }
            catch(UnauthorizedAccessException) { writeRejected=true; }
            if(!writeRejected)throw new Exception("Untrusted write ACE was accepted on legacy identity.");
            await PcIdentity.RestrictAsync(directory);
            // 6. Independent check: Untrusted FullControl permission on directory must be rejected.
            var fullControlAcl=dirInfo.GetAccessControl();
            fullControlAcl.AddAccessRule(new FileSystemAccessRule(users,FileSystemRights.FullControl,InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
            dirInfo.SetAccessControl(fullControlAcl);
            bool fullControlRejected=false;
            try { PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid); }
            catch(UnauthorizedAccessException) { fullControlRejected=true; }
            if(!fullControlRejected)throw new Exception("Untrusted FullControl ACE was accepted on legacy identity.");
            await PcIdentity.RestrictAsync(directory);
            // 7. Incomplete legacy identity (missing bridge.pfx) must fail closed without auto-replacing.
            File.Delete(pfxPath);
            bool incompleteRejected=false;
            try { PcIdentity.VerifyLegacyProvenance(directory,metadataPath,pfxPath,sid); }
            catch(IOException) { incompleteRejected=true; }
            if(!incompleteRejected)throw new Exception("Incomplete legacy identity was accepted.");
            CryptographicOperations.ZeroMemory(pfx);CryptographicOperations.ZeroMemory(token);
            return 7;
        } finally {
            if(Path.GetFullPath(directory).StartsWith("D:\\DNIeToolchain\\Temp\\identity-test-",StringComparison.OrdinalIgnoreCase)) {
                foreach(var file in Directory.GetFiles(directory))File.Delete(file);
                Directory.Delete(directory);
            }
        }
    }
}
