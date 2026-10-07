using DniBridge;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
static class PcIdentityTests {
    public static async Task<int> RunAsync() {
        if(!OperatingSystem.IsWindows())return 0;
        string directory=Path.Combine("D:/DNIeToolchain/Temp","identity-test-"+Guid.NewGuid().ToString("N"));
        try {
            var first=await PcIdentity.LoadOrCreateAsync(directory);
            byte[] pfx=File.ReadAllBytes(Path.Combine(directory,"bridge.pfx"));
            var second=await PcIdentity.LoadOrCreateAsync(directory);
            if(first!=second || !pfx.SequenceEqual(File.ReadAllBytes(Path.Combine(directory,"bridge.pfx"))))throw new Exception("PC identity changed between sessions.");
            using var certificate=new X509Certificate2(pfx,first.Password,X509KeyStorageFlags.EphemeralKeySet);
            string pin=Convert.ToHexString(SHA256.HashData(certificate.RawData));
            var token=Convert.FromBase64String(first.Token);
            foreach(string transport in new[]{"tls_reverse","usb_accessory"})
                if(!BridgePairingQr.Encode("127.0.0.1",39443,pin,token,certificate.NotAfter.ToUniversalTime(),DateTimeOffset.UtcNow,transport,true).StartsWith(BridgePairingQr.Prefix))throw new Exception("Persistent identity QR failed.");
            CryptographicOperations.ZeroMemory(pfx);CryptographicOperations.ZeroMemory(token);
            return 3;
        } finally {
            if(Path.GetFullPath(directory).StartsWith("D:\\DNIeToolchain\\Temp\\identity-test-",StringComparison.OrdinalIgnoreCase)) {
                foreach(var file in Directory.GetFiles(directory))File.Delete(file);
                Directory.Delete(directory);
            }
        }
    }
}
