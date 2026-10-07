using System.Runtime.InteropServices;
using System.ComponentModel;
using DniBridge.Control;
namespace DniBridge.Agent;

/// <summary>SCM owns the process; the control callback only signals cancellation.</summary>
public sealed class WindowsServiceLifetime {
    private readonly CancellationTokenSource stop=new();
    private IntPtr statusHandle;private uint checkpoint;
    private MainCallback? main;private HandlerCallback? handler;
    [UnmanagedFunctionPointer(CallingConvention.Winapi)]private delegate void MainCallback(uint count,IntPtr args);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)]private delegate uint HandlerCallback(uint control,uint type,IntPtr data,IntPtr context);
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)]private struct Entry {public string? Name;public MainCallback? Main;}
    [StructLayout(LayoutKind.Sequential)]private struct Status {public uint Type,State,Accepted,Exit,Specific,Checkpoint,Hint;}
    public void Run(Func<CancellationToken,Task> work) {
        main=(_,_)=> {
            handler=(control,_,_,_)=>{if(control is 1 or 5){Set(3);stop.Cancel();}else if(control==4)Set(stop.IsCancellationRequested?3u:4u);return 0;};
            statusHandle=RegisterServiceCtrlHandlerExW(ServiceControl.Name,handler,IntPtr.Zero);
            if(statusHandle==IntPtr.Zero)return;
            Set(2);try {var task=work(stop.Token);Set(4);task.GetAwaiter().GetResult();Set(1);}
            catch(OperationCanceledException) when(stop.IsCancellationRequested){Set(1);}
            catch(Exception){Set(1,1066,1);}
        };
        if(!StartServiceCtrlDispatcherW(new[]{new Entry{Name=ServiceControl.Name,Main=main},new Entry()}))throw new Win32Exception(Marshal.GetLastWin32Error());
        GC.KeepAlive(main);GC.KeepAlive(handler);
    }
    private void Set(uint state,uint exit=0,uint specific=0) {
        var value=new Status {Type=0x10,State=state,Accepted=state==4?5u:0u,Exit=exit,Specific=specific,
            Checkpoint=state is 2 or 3?++checkpoint:0,Hint=state is 2 or 3?30000u:0u};
        SetServiceStatus(statusHandle,ref value);
    }
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool StartServiceCtrlDispatcherW([In]Entry[] entries);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]private static extern IntPtr RegisterServiceCtrlHandlerExW(string name,HandlerCallback handler,IntPtr context);
    [DllImport("advapi32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool SetServiceStatus(IntPtr handle,ref Status status);
}
