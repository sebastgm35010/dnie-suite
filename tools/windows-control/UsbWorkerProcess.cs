using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
namespace DniBridge.Control;

/// <summary>Native USB may block inside a driver call; never run it in the broker.</summary>
public static class UsbWorkerProcess {
    private static readonly object pendingLock=new();
    private static Process? pendingDriver;
    private static void EnsureAvailable() {
        lock(pendingLock) {
            if(pendingDriver is null)return;
            if(IsListed(pendingDriver.Id))throw new IOException("La conexion USB anterior sigue pendiente en UsbDk. Desconecta el cable antes de reintentar; no se abrira otro auxiliar.");
            pendingDriver.Dispose();pendingDriver=null;
        }
    }
    private static bool IsListed(int id) {
        var processes=Process.GetProcesses();
        try {return processes.Any(process=>process.Id==id);}
        finally {foreach(var process in processes)process.Dispose();}
    }
    private static void Quarantine(Process child) {
        lock(pendingLock) {
            if(pendingDriver is not null)return;
            try {pendingDriver=Process.GetProcessById(child.Id);}
            catch(ArgumentException) { }
        }
    }
    public static ProcessStartInfo StartInfo(ConnectionProfile profile) {
        ControlRules.Validate(profile);
        if(profile.Method!="USB")throw new ArgumentException("USB worker requires USB profile.");
        var start=new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"DNIeBridge.Agent.exe")) {
            UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardInput=true
        };
        start.ArgumentList.Add("--usb-worker");start.ArgumentList.Add(profile.Phone);
        return start;
    }
    public static string? SafePhase(string line) {
        string name=line.Split(' ',2)[0];
        return name.Length is >0 and <=64 && name.All(c=>c is >= 'a' and <= 'z' or >= '0' and <= '9' or '_') &&
            !name.StartsWith("pcsc_apdu_") ? name : null;
    }
    public static async Task RunAsync(ProcessStartInfo start,Action<string> log,CancellationToken stop,TimeSpan? driverTimeout=null) {
        stop.ThrowIfCancellationRequested();
        EnsureAvailable();
        if(Path.GetFileName(start.FileName).Equals("DNIeBridge.Agent.exe",StringComparison.OrdinalIgnoreCase)) {
            // Also retain exclusion after a broker update/restart: kernel teardown
            // can leave an old worker listed even after its process handle signals exit.
            var agents=Process.GetProcessesByName("DNIeBridge.Agent");
            try {
                if(agents.Any(agent=>agent.Id!=Environment.ProcessId))
                    throw new IOException("Un auxiliar USB anterior sigue listado por Windows. Desconecta el cable y espera a que se libere antes de reintentar.");
            } finally {foreach(var agent in agents)agent.Dispose();}
        }
        using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);
        using var outputStop=new CancellationTokenSource();
        int active=1;
        string currentPhase="usb_worker_starting";
        var timeout=driverTimeout ?? TimeSpan.FromSeconds(30);
        deadline.CancelAfter(timeout);
        using var child=Process.Start(start) ?? throw new IOException("No se pudo iniciar el transporte USB.");
        try {
            using var job=new ChildJob();
            job.Assign(child);
            var output=PumpAsync(child.StandardOutput,phase=> {
                if(Volatile.Read(ref active)==0)return;
                currentPhase=phase;
                deadline.CancelAfter(IsDriverPhase(phase)?timeout:Timeout.InfiniteTimeSpan);
                log(phase);
            },outputStop.Token);
            _=output.ContinueWith(task=>{_=task.Exception;},TaskContinuationOptions.OnlyOnFaulted);
            try {
                await child.WaitForExitAsync(deadline.Token);
                await output;
                throw new IOException("El transporte USB finalizo. Reconecta el telefono e inicia otra vez.");
            } catch(OperationCanceledException) when(deadline.IsCancellationRequested) {
                // Request normal cleanup first; a stuck driver cannot acknowledge it.
                try {await child.StandardInput.WriteLineAsync("stop");await child.StandardInput.FlushAsync();}
                catch(IOException) { }
                try {await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(3));}
                catch(TimeoutException) {
                    log("usb_worker_cancelled");
                    if(!child.HasExited)child.Kill();
                    try {await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));}
                    catch(TimeoutException) {
                        Quarantine(child);
                        throw new IOException("UsbDk sigue bloqueado en "+currentPhase+". Desconecta el cable para liberar el auxiliar; el servicio permanece disponible.");
                    }
                }
                // Phase reporting must not delay cleanup after the child has exited.
                outputStop.Cancel();
                try {await output.WaitAsync(TimeSpan.FromSeconds(1));}
                catch(OperationCanceledException) when(outputStop.IsCancellationRequested) { }
                catch(TimeoutException) { }
                if(!stop.IsCancellationRequested)throw new IOException("USB no respondio durante "+currentPhase+". Reconecta el cable antes de reintentar.");
                throw;
            }
        } finally {
            Volatile.Write(ref active,0);outputStop.Cancel();
            if(!child.HasExited)child.Kill();
            // A terminated process can still retain a kernel thread waiting in UsbDk.
            if(IsListed(child.Id))Quarantine(child);
        }
    }
    private static bool IsDriverPhase(string phase)=>phase is "usb_redirect_initial" or "usb_query_protocol" or
        "usb_send_identity" or "usb_start_accessory" or "usb_release_initial" or "usb_read_configuration" or "usb_redirect_accessory";
    private static async Task PumpAsync(StreamReader reader,Action<string> log,CancellationToken stop) {
        while(await reader.ReadLineAsync(stop) is { } line) {
            if(SafePhase(line) is { } phase)log(phase);
        }
    }
    // Kill only this worker when its broker exits, including an unexpected crash.
    private sealed class ChildJob : IDisposable {
        private readonly SafeFileHandle handle;
        public ChildJob() {
            handle=CreateJobObjectW(IntPtr.Zero,null);
            if(handle.IsInvalid)throw new IOException("No se pudo aislar el transporte USB.");
            var limits=new ExtendedLimits {Basic=new BasicLimits {Flags=0x2000}};
            if(!SetInformationJobObject(handle,9,ref limits,(uint)Marshal.SizeOf<ExtendedLimits>())) {
                handle.Dispose();throw new IOException("No se pudo limitar el proceso USB.");
            }
        }
        public void Assign(Process child) {
            if(!AssignProcessToJobObject(handle,child.Handle))throw new IOException("No se pudo asociar el proceso USB al servicio.");
        }
        public void Dispose()=>handle.Dispose();
        [StructLayout(LayoutKind.Sequential)]private struct BasicLimits {
            public long ProcessTime,JobTime;public uint Flags;public nuint Minimum,Maximum;
            public uint Processes;public nuint Affinity;public uint Priority,Scheduling;
        }
        [StructLayout(LayoutKind.Sequential)]private struct IoCounters {public ulong Read,Write,Other,ReadBytes,WriteBytes,OtherBytes;}
        [StructLayout(LayoutKind.Sequential)]private struct ExtendedLimits {public BasicLimits Basic;public IoCounters Io;public nuint ProcessMemory,JobMemory,PeakProcess,PeakJob;}
        [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]private static extern SafeFileHandle CreateJobObjectW(IntPtr attributes,string? name);
        [DllImport("kernel32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool SetInformationJobObject(SafeFileHandle job,int type,ref ExtendedLimits limits,uint size);
        [DllImport("kernel32.dll",SetLastError=true)][return:MarshalAs(UnmanagedType.Bool)]private static extern bool AssignProcessToJobObject(SafeFileHandle job,IntPtr process);
    }
}
