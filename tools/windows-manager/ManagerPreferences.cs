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
    public void Save(ConnectionProfile profile) {
        System.IO.Directory.CreateDirectory(Directory);string path=Path.Combine(Directory,"settings.json");
        File.WriteAllText(path+".new",JsonSerializer.Serialize(profile,ControlWire.Json));File.Move(path+".new",path,true);
    }
}
