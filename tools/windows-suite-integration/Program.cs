using System.Diagnostics;
using System.Security.Principal;
using System.Text.Json;
using System.Text.RegularExpressions;
using DniBridge.Control;

// Adapter exports only public availability, host state and card presence. Never pairing/profile/error data.
internal static class Program {
    private const string Root="D:\\DNIeBridgeApp";
    private static async Task<int> Main(string[] args) {
        Console.OutputEncoding=new System.Text.UTF8Encoding(false);
        if(!OperatingSystem.IsWindows() || args.Length!=1 || args[0] is not ("status" or "stop" or "open-manager"))return 2;
        try {
            if(args[0]=="open-manager") {OpenManager();Console.WriteLine("DNIE1\tOPENED");return 0;}
            var service=ServiceControl.Query();
            if(service is null){Print("NOT_INSTALLED","UNKNOWN",false);return 0;}
            if(service.Value.State!=4){Print(service.Value.State==1?"SERVICE_STOPPED":"UNAVAILABLE",service.Value.State==1?"STOPPED":"UNKNOWN",false);return 0;}
            using var deadline=new CancellationTokenSource(TimeSpan.FromSeconds(args[0]=="status"?6:35));
            var response=await new AgentClient().SendAsync(new(args[0]),deadline.Token);
            if(!response.Ok || response.Status is null)throw new IOException();
            var status=response.Status;
            string state=status.State switch {"Stopped"=>"STOPPED","Starting"=>"STARTING","Running"=>"RUNNING","Stopping"=>"STOPPING","Failed"=>"FAILED",_=>"UNKNOWN"};
            Print("AVAILABLE",state,status.CardPresent);return 0;
        }catch { if(args[0]=="status"){Print("UNAVAILABLE","UNKNOWN",false);return 0;}return 1; }
    }
    private static void Print(string availability,string host,bool card)=>Console.WriteLine($"DNIE1\t{availability}\t{host}\t{(card?1:0)}");
    [System.Runtime.Versioning.SupportedOSPlatform("windows")]
    private static void OpenManager() {
        string manifest=Path.Combine(Root,"installed.json");
        if(new FileInfo(manifest).Length>8192)throw new IOException();
        using var document=JsonDocument.Parse(File.ReadAllText(manifest));
        string owner=document.RootElement.GetProperty("ownerSid").GetString()??"";
        if(owner!=WindowsIdentity.GetCurrent().User?.Value)throw new UnauthorizedAccessException();
        string directory=Path.GetFullPath(document.RootElement.GetProperty("directory").GetString()??"");
        string versions=Path.Combine(Root,"versions");
        if(!string.Equals(Path.GetDirectoryName(directory),versions,StringComparison.OrdinalIgnoreCase) ||
            !Regex.IsMatch(Path.GetFileName(directory),"^[a-f0-9]{32}$"))throw new IOException();
        string executable=Path.Combine(directory,"DNIeBridge.exe");
        foreach(string path in new[]{Root,versions,directory,executable})
            if((File.GetAttributes(path)&FileAttributes.ReparsePoint)!=0)throw new IOException();
        Process.Start(new ProcessStartInfo(executable){UseShellExecute=false,WorkingDirectory=directory});
    }
}
