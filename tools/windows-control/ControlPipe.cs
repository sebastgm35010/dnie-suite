using System.IO.Pipes;
using System.Security.Principal;
using System.Runtime.InteropServices;
using System.ComponentModel;
using Microsoft.Win32.SafeHandles;
namespace DniBridge.Control;
public static class ControlPipe {
    public const string Name="DNIeBridge.Agent.v2";
    [StructLayout(LayoutKind.Sequential)]private struct Attributes {public int Length;public IntPtr Descriptor;public int Inherit;}
    public static NamedPipeServerStream Create(string owner,string name=Name) {
        if(!OperatingSystem.IsWindows())throw new PlatformNotSupportedException();
        _=new SecurityIdentifier(owner);
        if(!name.StartsWith("DNIeBridge.") || name.Length>128 || name.Contains('\\') || name.Contains('/'))throw new ArgumentException("Invalid pipe.");
        if(!ConvertStringSecurityDescriptorToSecurityDescriptorW($"D:P(A;;GA;;;SY)(A;;GRGW;;;{owner})",1,out var descriptor,out _))throw new Win32Exception(Marshal.GetLastWin32Error());
        try {var attributes=new Attributes {Length=Marshal.SizeOf<Attributes>(),Descriptor=descriptor};
            var handle=CreateNamedPipeW($"\\\\.\\pipe\\{name}",0x40080003,8,1,ControlWire.Limit,ControlWire.Limit,0,ref attributes);
            if(handle.IsInvalid){int error=Marshal.GetLastWin32Error();handle.Dispose();throw new Win32Exception(error);}
            try{return new NamedPipeServerStream(PipeDirection.InOut,true,false,handle);}catch{handle.Dispose();throw;}
        }finally{LocalFree(descriptor);}
    }
    public static string CallerSid(NamedPipeServerStream pipe) {
        // Identification tokens cannot open files. Keep CLR identity/Claims assembly
        // loading outside impersonation; query TOKEN_USER using native APIs only.
        string? sid=null;pipe.RunAsClient(()=> {
            if(!OpenThreadToken(new IntPtr(-2),8,true,out var token))throw new Win32Exception(Marshal.GetLastWin32Error());
            try {
                GetTokenInformation(token,1,IntPtr.Zero,0,out int length);
                if(length is < 8 or >65536)throw new UnauthorizedAccessException("Invalid identity token.");
                IntPtr information=Marshal.AllocHGlobal(length);
                try {
                    if(!GetTokenInformation(token,1,information,length,out _))throw new Win32Exception(Marshal.GetLastWin32Error());
                    if(!ConvertSidToStringSidW(Marshal.ReadIntPtr(information),out var text))throw new Win32Exception(Marshal.GetLastWin32Error());
                    try {sid=Marshal.PtrToStringUni(text);}finally{LocalFree(text);}
                }finally{Marshal.FreeHGlobal(information);}
            }finally{CloseHandle(token);}
        });
        return sid ?? throw new UnauthorizedAccessException("No caller identity.");
    }
    public static void VerifyServer(NamedPipeClientStream pipe,uint expectedPid) {
        if(expectedPid==0 || !GetNamedPipeServerProcessId(pipe.SafePipeHandle,out uint actual) || actual!=expectedPid)
            throw new UnauthorizedAccessException("El canal local no pertenece al servicio instalado.");
    }
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]private static extern SafePipeHandle CreateNamedPipeW(string name,uint mode,uint pipeMode,uint instances,uint output,uint input,uint timeout,ref Attributes attributes);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool ConvertStringSecurityDescriptorToSecurityDescriptorW(string text,uint revision,out IntPtr descriptor,out uint size);
    [DllImport("kernel32.dll")]private static extern IntPtr LocalFree(IntPtr ptr);
    [DllImport("kernel32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool GetNamedPipeServerProcessId(SafePipeHandle pipe,out uint pid);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool OpenThreadToken(IntPtr thread,uint access,[MarshalAs(UnmanagedType.Bool)]bool self,out IntPtr token);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool GetTokenInformation(IntPtr token,int kind,IntPtr data,int size,out int needed);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool ConvertSidToStringSidW(IntPtr sid,out IntPtr text);
    [DllImport("kernel32.dll")][return:MarshalAs(UnmanagedType.Bool)]private static extern bool CloseHandle(IntPtr handle);
}
public sealed class AgentClient {
    public async Task<ControlResponse> SendAsync(ControlRequest request,CancellationToken stop=default) {
        if(!ControlRules.Allowed(request.Command))throw new ArgumentException("Unsupported control command.");
        var service=ServiceControl.Query();
        if(service?.State!=4)throw new IOException("El servicio no esta iniciado. Usa Instalar / reparar servicio.");
        using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);deadline.CancelAfter(TimeSpan.FromSeconds(35));
        using var pipe=new NamedPipeClientStream(".",ControlPipe.Name,PipeDirection.InOut,PipeOptions.Asynchronous,TokenImpersonationLevel.Identification);
        await pipe.ConnectAsync(3000,deadline.Token);
        ControlPipe.VerifyServer(pipe,service.Value.ProcessId);
        await ControlWire.WriteAsync(pipe,request,deadline.Token);
        var response=await ControlWire.ReadAsync<ControlResponse>(pipe,deadline.Token);
        await pipe.WriteAsync(new byte[]{1},deadline.Token);
        return response;
    }
}
