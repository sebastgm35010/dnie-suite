using System.Text.Json;
using System.Net;
using System.Text.RegularExpressions;
namespace DniBridge.Control;

public sealed record ConnectionProfile(string Method="Tailscale",string Pc="",string Phone="");
public sealed record ControlRequest(string Command,ConnectionProfile? Profile=null);
public sealed record BridgeStatus(string State="Stopped",string Phase="idle",bool CardPresent=false,string? Error=null,ConnectionProfile? Profile=null);
public sealed record PairingView(string Payload,string Fingerprint,string Expires);
public sealed record ControlResponse(bool Ok,BridgeStatus? Status=null,PairingView? Pairing=null,string[]? Devices=null,string? Error=null);
public static class ControlRules {
    public static void Validate(ConnectionProfile profile) {
        if(profile.Pc is null || profile.Phone is null)throw new ArgumentException("Faltan datos de conexion.");
        if(profile.Pc.Length>253 || profile.Phone.Length>253)throw new ArgumentException("Nombre o direccion demasiado largo.");
        switch(profile.Method) {
            case "Tailscale":
                if(!TailnetTransport.IsHost(profile.Pc) || !TailnetTransport.IsHost(profile.Phone))throw new ArgumentException("Introduce los nombres Tailscale o direcciones 100.x de ambos equipos.");
                break;
            case "LAN":
                if(!IPAddress.TryParse(profile.Pc,out var pc) || !IPAddress.TryParse(profile.Phone,out var phone) ||
                    !LocalNetworkPolicy.IsPrivateAddress(pc) || !LocalNetworkPolicy.IsPrivateAddress(phone) || IPAddress.IsLoopback(pc) || IPAddress.IsLoopback(phone))
                    throw new ArgumentException("LAN requiere la IP privada de la PC y la IP Wi-Fi del telefono.");
                break;
            case "USB":
                if(!Regex.IsMatch(profile.Phone,"^[a-zA-Z0-9_&.-]{1,200}$"))throw new ArgumentException("Selecciona el dispositivo USB del telefono.");
                break;
            default:throw new ArgumentException("Transporte no compatible.");
        }
    }
    public static bool Allowed(string command) => command is "status" or "start" or "stop" or "pairing" or "usb-devices"
#if DEBUG
        or "diagnostics"
#endif
        ;
}
public static class ControlWire {
    public const int Limit=65536;
    public static readonly JsonSerializerOptions Json=new() {PropertyNamingPolicy=JsonNamingPolicy.CamelCase,PropertyNameCaseInsensitive=true};
    public static async Task WriteAsync<T>(Stream stream,T value,CancellationToken stop) {
        byte[] bytes=JsonSerializer.SerializeToUtf8Bytes(value,Json);
        try {if(bytes.Length>Limit)throw new InvalidDataException("Control response too large.");
            byte[] header=new byte[4];System.Buffers.Binary.BinaryPrimitives.WriteInt32BigEndian(header,bytes.Length);
            await stream.WriteAsync(header,stop);await stream.WriteAsync(bytes,stop);await stream.FlushAsync(stop);
        } finally {System.Security.Cryptography.CryptographicOperations.ZeroMemory(bytes);}
    }
    public static async Task<T> ReadAsync<T>(Stream stream,CancellationToken stop) {
        byte[] header=new byte[4];await stream.ReadExactlyAsync(header,stop);
        int size=System.Buffers.Binary.BinaryPrimitives.ReadInt32BigEndian(header);
        if(size is <=0 or >Limit)throw new InvalidDataException("Control frame outside bounds.");
        byte[] bytes=new byte[size];try {await stream.ReadExactlyAsync(bytes,stop);return JsonSerializer.Deserialize<T>(bytes,Json) ?? throw new InvalidDataException("Invalid control message.");}
        finally {System.Security.Cryptography.CryptographicOperations.ZeroMemory(bytes);}
    }
}
