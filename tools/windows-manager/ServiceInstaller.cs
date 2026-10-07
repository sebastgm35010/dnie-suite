using System.Security.AccessControl;
using System.Security.Principal;
using System.Security.Cryptography;
using System.Text.Json;
using DniBridge.Control;
namespace DniBridge.Manager;

/// <summary>One explicit elevation installs immutable binaries and a SYSTEM broker.</summary>
public static class ServiceInstaller {
    public const string InstallRoot="D:\\DNIeBridgeApp";
    public static async Task<string> InstallAsync(string source,string ownerSid) {
        if(!new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator))throw new UnauthorizedAccessException("La instalacion necesita permisos de administrador una sola vez.");
        var owner=new SecurityIdentifier(ownerSid);
        _=owner.Translate(typeof(NTAccount));
        if(ownerSid=="S-1-5-18")throw new ArgumentException("Selecciona al usuario del gestor, no SYSTEM.");
        source=Path.GetFullPath(source);
        var manifest=JsonSerializer.Deserialize<Distribution>(await File.ReadAllTextAsync(Path.Combine(source,"distribution.json")),ControlWire.Json) ?? throw new IOException("Falta el manifiesto del paquete.");
        if(manifest.Files.Length is < 6 or >100)throw new IOException("Manifiesto fuera de limites.");
        foreach(var entry in manifest.Files) {
            string input=SafePath(source,entry.Path);
            if(!Convert.ToHexString(SHA256.HashData(await File.ReadAllBytesAsync(input))).Equals(entry.Sha256,StringComparison.OrdinalIgnoreCase))throw new IOException("Paquete incompleto o modificado: "+entry.Path);
        }
        foreach(string required in new[]{"DNIeBridge.exe","DNIeBridge.dll","DNIeBridge.Agent.exe","DNIeBridge.Agent.dll","WindowsBridge.dll","WindowsControl.dll"})
            if(!manifest.Files.Any(f=>f.Path==required))throw new IOException("Falta componente: "+required);
        string existingEnrollment=Path.Combine(InstallRoot,"state","enrollment.json");
        if(File.Exists(existingEnrollment)) {
            var existing=JsonSerializer.Deserialize<Enrollment>(await File.ReadAllTextAsync(existingEnrollment));
            if(existing?.OwnerSid!=ownerSid)throw new UnauthorizedAccessException("El servicio pertenece a otro usuario. No se reasigno su identidad.");
        }
        if(ServiceControl.Query() is {State:4}) {
            // Preserve a live card. Repair/update requires explicitly stopping its bridge first.
            var status=await new AgentClient().SendAsync(new("status"));
            if(status.Status?.State is "Running" or "Starting" or "Stopping")throw new InvalidOperationException("Deten el puente antes de reparar o actualizar el servicio.");
        }
        await ServiceControl.StopAsync();
        CreateProtected(InstallRoot,ownerSid,true);
        string state=Path.Combine(InstallRoot,"state");CreateProtected(state,ownerSid,false);
        string enrollmentPath=Path.Combine(state,"enrollment.json");
        if(File.Exists(enrollmentPath)) {
            var enrollment=JsonSerializer.Deserialize<Enrollment>(await File.ReadAllTextAsync(enrollmentPath));
            if(enrollment?.OwnerSid!=ownerSid)throw new UnauthorizedAccessException("El servicio pertenece a otro usuario. No se reasigno su identidad.");
        }
        string versions=Path.Combine(InstallRoot,"versions");CreateProtected(versions,ownerSid,true);
        string target=Path.Combine(versions,Guid.NewGuid().ToString("N"));CreateProtected(target,ownerSid,true);
        foreach(var entry in manifest.Files) {
            string output=SafePath(target,entry.Path);Directory.CreateDirectory(Path.GetDirectoryName(output)!);
            File.Copy(SafePath(source,entry.Path),output,false);
        }
        File.Copy(Path.Combine(source,"distribution.json"),Path.Combine(target,"distribution.json"));
        await File.WriteAllTextAsync(enrollmentPath,JsonSerializer.Serialize(new Enrollment(ownerSid)));
        string identity=Path.Combine(state,"identity");
        if(!Directory.Exists(identity)) {
            string old=Path.Combine("D:/DNIeToolchain/PcIdentities",ownerSid);
            if(File.Exists(Path.Combine(old,"identity.json"))) {
                var metadata=JsonSerializer.Deserialize<DniBridge.PcIdentity>(await File.ReadAllTextAsync(Path.Combine(old,"identity.json"))) ?? throw new IOException("Identidad anterior invalida.");
                using var cert=new System.Security.Cryptography.X509Certificates.X509Certificate2(await File.ReadAllBytesAsync(Path.Combine(old,"bridge.pfx")),metadata.Password,System.Security.Cryptography.X509Certificates.X509KeyStorageFlags.EphemeralKeySet);
                if(!cert.HasPrivateKey)throw new IOException("Identidad anterior sin clave.");
                CreateProtected(identity,ownerSid,false);
                File.Copy(Path.Combine(old,"identity.json"),Path.Combine(identity,"identity.json"));File.Copy(Path.Combine(old,"bridge.pfx"),Path.Combine(identity,"bridge.pfx"));
            }
        }
        ServiceControl.Install(Path.Combine(target,"DNIeBridge.Agent.exe"),ownerSid);
        for(int attempt=0;attempt<75;attempt++){if(ServiceControl.Query()?.State==4)break;await Task.Delay(200);}
        if(ServiceControl.Query()?.State!=4)throw new IOException("El servicio no arranco. Revisa el registro de instalacion.");
        await new AgentClient().SendAsync(new("status"));
        await File.WriteAllTextAsync(Path.Combine(InstallRoot,"installed.json"),JsonSerializer.Serialize(new {directory=target,ownerSid,configuration=manifest.Configuration}));
        string manager=Path.Combine(target,"DNIeBridge.exe");
        if(WindowsIdentity.GetCurrent().User!.Value==ownerSid)CreateShortcut(manager);
        return manager;
    }
    private static void CreateShortcut(string executable) {
        string desktop=Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
        if(string.IsNullOrEmpty(desktop) || !Directory.Exists(desktop))return;
        object? shell=null,shortcut=null;
        try {
            shell=Activator.CreateInstance(Type.GetTypeFromProgID("WScript.Shell")!);
            dynamic automation=shell!;
            shortcut=automation.CreateShortcut(Path.Combine(desktop,"DNIe Bridge.lnk"));
            dynamic link=shortcut;link.TargetPath=executable;link.WorkingDirectory=Path.GetDirectoryName(executable);link.Description="Gestor nativo del lector DNIe Bridge";link.Save();
        } finally {
            if(shortcut!=null)System.Runtime.InteropServices.Marshal.FinalReleaseComObject(shortcut);
            if(shell!=null)System.Runtime.InteropServices.Marshal.FinalReleaseComObject(shell);
        }
    }
    private static void CreateProtected(string directory,string ownerSid,bool readUser) {
        Directory.CreateDirectory(directory);
        if((File.GetAttributes(directory)&FileAttributes.ReparsePoint)!=0)throw new IOException("No se permite instalar sobre enlaces.");
        var acl=new DirectorySecurity();acl.SetAccessRuleProtection(true,false);
        foreach(string sid in new[]{"S-1-5-18","S-1-5-32-544"})acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(sid),FileSystemRights.FullControl,InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
        if(readUser)acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(ownerSid),FileSystemRights.ReadAndExecute,InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
        acl.SetOwner(new SecurityIdentifier("S-1-5-32-544"));new DirectoryInfo(directory).SetAccessControl(acl);
    }
    public static string SafePath(string root,string relative) {
        if(string.IsNullOrWhiteSpace(relative) || Path.IsPathRooted(relative) || relative.Contains(':') || relative.Split('/','\\').Any(p=>p is ".." or "."))throw new IOException("Ruta del paquete invalida.");
        string path=Path.GetFullPath(Path.Combine(root,relative));
        if(!path.StartsWith(Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Ruta fuera del paquete.");
        return path;
    }
    public sealed record FileEntry(string Path,string Sha256);
    public sealed record Distribution(string Configuration,FileEntry[] Files);
    private sealed record Enrollment(string OwnerSid);
}
