using System.Text.Json;
using DniBridge.Control;
namespace DniBridge.Manager;
public sealed class ManagerPreferences {
    public string Directory {get;}=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"DNIeBridge","Manager");
    public ConnectionProfile Load() {
        try {
            string path=Path.Combine(Directory,"settings.json");
            if(File.Exists(path))return JsonSerializer.Deserialize<ConnectionProfile>(File.ReadAllText(path),ControlWire.Json) ?? new();
            // Public preferences only; pairing credentials remain in the privileged service.
            string legacy="D:/DNIeToolchain/Desktop/settings.json";
            if(File.Exists(legacy)) {
                using var doc=JsonDocument.Parse(File.ReadAllText(legacy));var json=doc.RootElement;
                string Get(string key)=>json.TryGetProperty(key,out var value)?value.GetString()??"":"";
                return new(Get("Method") switch {"LAN TLS"=>"LAN","USB directo"=>"USB",_=>"Tailscale"},Get("Method")=="LAN TLS"?Get("Address"):Get("Pc"),Get("Phone"));
            }
        }catch(Exception){ }
        return new();
    }
    public bool PairingShown=>File.Exists(Path.Combine(Directory,"pairing-shown"));
    public void MarkPairingShown(){System.IO.Directory.CreateDirectory(Directory);File.WriteAllText(Path.Combine(Directory,"pairing-shown"),"1");}
    public string[] History(){try {return System.Text.Json.JsonSerializer.Deserialize<string[]>(File.ReadAllText(Path.Combine(Directory,"phone-history.json")))?.Where(ValidHint).Distinct().Take(4).ToArray()??[];}catch{return [];}}
    private static bool ValidHint(string value)=>value.Length<=253 && (TailnetTransport.IsHost(value) || System.Net.IPAddress.TryParse(value,out var ip) && NetworkDiscovery.Allowed(ip));
    public void Save(ConnectionProfile profile) {
        System.IO.Directory.CreateDirectory(Directory);string path=Path.Combine(Directory,"settings.json");
        if(profile.Method=="LAN" && ValidHint(profile.Phone)) {
            var history=new[]{profile.Phone}.Concat(History()).Distinct().Take(4).ToArray();
            File.WriteAllText(Path.Combine(Directory,"phone-history.json"),JsonSerializer.Serialize(history));
        }
        File.WriteAllText(path+".new",JsonSerializer.Serialize(profile,ControlWire.Json));File.Move(path+".new",path,true);
    }
}
