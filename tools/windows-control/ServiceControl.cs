using System.Runtime.InteropServices;
using System.ComponentModel;
namespace DniBridge.Control;

/// <summary>SCM adapter for this application's service only. Never controls system card services.</summary>
public static class ServiceControl {
    public const string Name="DNIeBridgeAgent";
    [StructLayout(LayoutKind.Sequential)] public struct Status {public uint Type,State,Accepted,ExitCode,SpecificExitCode,CheckPoint,WaitHint,ProcessId,Flags;}
    public static Status? Query() {
        IntPtr manager=OpenSCManagerW(null,null,1);if(manager==IntPtr.Zero)throw Error();
        try {IntPtr service=OpenServiceW(manager,Name,4);if(service==IntPtr.Zero){if(Marshal.GetLastWin32Error()==1060)return null;throw Error();}
            try {if(!QueryServiceStatusEx(service,0,out var status,Marshal.SizeOf<Status>(),out _))throw Error();return status;}
            finally {CloseServiceHandle(service);}
        } finally {CloseServiceHandle(manager);}
    }
    public static void Install(string executable,string ownerSid) {
        IntPtr manager=OpenSCManagerW(null,null,3);if(manager==IntPtr.Zero)throw Error();
        try {IntPtr service=OpenServiceW(manager,Name,0xF01FF);
            if(service==IntPtr.Zero) {
                if(Marshal.GetLastWin32Error()!=1060)throw Error();
                service=CreateServiceW(manager,Name,"DNIe Bridge - lector NFC",0xF01FF,0x10,2,1,"\""+Path.GetFullPath(executable)+"\"",null,IntPtr.Zero,null,null,null);
                if(service==IntPtr.Zero)throw Error();
            } else if(!ChangeServiceConfigW(service,0xFFFFFFFF,2,0xFFFFFFFF,"\""+Path.GetFullPath(executable)+"\"",null,IntPtr.Zero,null,null,null,null)) {CloseServiceHandle(service);throw Error();}
            try {
                if(!ConvertStringSecurityDescriptorToSecurityDescriptorW($"D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;0x15;;;{ownerSid})",1,out var acl,out _))throw Error();
                try {if(!SetServiceObjectSecurity(service,4,acl))throw Error();}finally{LocalFree(acl);}
                if(!StartServiceW(service,0,IntPtr.Zero) && Marshal.GetLastWin32Error()!=1056)throw Error();
            }
            finally {CloseServiceHandle(service);}
        } finally {CloseServiceHandle(manager);}
    }
    public static async Task StopAsync(bool uninstall=false) {
        IntPtr manager=OpenSCManagerW(null,null,1);if(manager==IntPtr.Zero)throw Error();
        try {IntPtr service=OpenServiceW(manager,Name,0x10024);if(service==IntPtr.Zero){if(Marshal.GetLastWin32Error()==1060)return;throw Error();}
            try {
                if(!ControlService(service,1,out _) && Marshal.GetLastWin32Error()!=1062)throw Error();
                for(int attempt=0;attempt<100;attempt++){if(Query()?.State is null or 1)break;await Task.Delay(200);}
                if(Query()?.State is not (null or 1))throw new IOException("El servicio aun se esta deteniendo; no se reemplazaron sus archivos.");
                if(uninstall && !DeleteService(service))throw Error();
            } finally {CloseServiceHandle(service);}
        } finally {CloseServiceHandle(manager);}
    }
    public static void Start() {
        IntPtr manager=OpenSCManagerW(null,null,1);if(manager==IntPtr.Zero)throw Error();
        try {IntPtr service=OpenServiceW(manager,Name,0x10);if(service==IntPtr.Zero)throw Error();
            try {if(!StartServiceW(service,0,IntPtr.Zero) && Marshal.GetLastWin32Error()!=1056)throw Error();}finally{CloseServiceHandle(service);}
        }finally{CloseServiceHandle(manager);}
    }
    private static Win32Exception Error()=>new(Marshal.GetLastWin32Error());
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]private static extern IntPtr OpenSCManagerW(string? machine,string? database,uint access);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]private static extern IntPtr OpenServiceW(IntPtr manager,string name,uint access);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]private static extern IntPtr CreateServiceW(IntPtr manager,string name,string display,uint access,uint type,uint start,uint error,string path,string? group,IntPtr tag,string? dependencies,string? account,string? password);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool ChangeServiceConfigW(IntPtr service,uint type,uint start,uint error,string path,string? group,IntPtr tag,string? dependencies,string? account,string? password,string? display);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool QueryServiceStatusEx(IntPtr service,int level,out Status status,int size,out int needed);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool StartServiceW(IntPtr service,int argc,IntPtr argv);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool ControlService(IntPtr service,uint control,out Status status);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool DeleteService(IntPtr service);
    [DllImport("advapi32.dll")][return:MarshalAs(UnmanagedType.Bool)]private static extern bool CloseServiceHandle(IntPtr handle);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool ConvertStringSecurityDescriptorToSecurityDescriptorW(string text,uint revision,out IntPtr descriptor,out uint size);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool SetServiceObjectSecurity(IntPtr service,uint info,IntPtr descriptor);
    [DllImport("kernel32.dll")]private static extern IntPtr LocalFree(IntPtr memory);

}
