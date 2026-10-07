using System.Diagnostics;
using System.Text.Json;
namespace DniBridge.Manager;

/// <summary>Optional RENIEC selector stays unelevated and separate from the privileged service.</summary>
public sealed class ReniecSelectorAdapter : IDisposable {
    private Process? process;private string? directory;
    public bool Running=>process is {HasExited:false};
    public string Status {
        get {if(directory!=null && File.Exists(Path.Combine(directory,"status.json"))) {
            try {using var doc=JsonDocument.Parse(File.ReadAllText(Path.Combine(directory,"status.json")));return doc.RootElement.GetProperty("phase").GetString()??"";}catch{}}
            return Running?"starting":"stopped";
        }
    }
    public void Start(string storage) {
        if(Running)return;
        string source=Path.Combine(AppContext.BaseDirectory,"selector","selector_service.py");
        string dependencies="D:/DNIeToolchain/Instrumentation/python";
        string java=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),".dcdelivery-2.0","files","customjre","bin","java.exe");
        if(!File.Exists(source) || !System.IO.Directory.Exists(Path.Combine(dependencies,"frida")) || !File.Exists(java))
            throw new IOException("El selector opcional requiere el runtime RENIEC y Frida. El lector PC/SC funciona sin este selector para otras aplicaciones.");
        directory=Path.Combine(storage,"selector-"+Guid.NewGuid().ToString("N"));System.IO.Directory.CreateDirectory(directory);
        var options=new ProcessStartInfo("python.exe") {UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true};
        foreach(string arg in new[]{"-u",source,"--directory",directory,"--dependency-dir",dependencies,"--runtime",java})options.ArgumentList.Add(arg);
        process?.Dispose();process=Process.Start(options) ?? throw new IOException("No se pudo iniciar el selector.");
        _=process.StandardOutput.ReadToEndAsync();_=process.StandardError.ReadToEndAsync();
    }
    public void Stop(){if(Running && directory!=null)File.WriteAllText(Path.Combine(directory,"stop.txt"),"manager_stop");}
    public void Dispose(){Stop();process?.Dispose();}
}
