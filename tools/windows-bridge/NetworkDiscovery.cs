using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Diagnostics;
using System.Text.Json;

namespace DniBridge;

/// <summary>Authenticated routing only. Discovery never enrolls a peer or replaces pinned TLS.</summary>
public static class NetworkDiscovery {
    public const int Port=39445;
    public const int QuerySize=105;
    public static byte[] Query(byte service,byte[] pin,byte[] nonce,byte[] token) {
        if(service is not (1 or 2) || pin.Length!=32 || nonce.Length!=32 || token.Length!=32)throw new ArgumentException("Discovery dimensions.");
        byte[] packet=new byte[105];"DNIFIND1"u8.CopyTo(packet);packet[8]=service;pin.CopyTo(packet,9);nonce.CopyTo(packet,41);
        HMACSHA256.HashData(token,packet.AsSpan(0,73)).CopyTo(packet,73);return packet;
    }
    public static IPAddress[]? ReadReply(byte[] packet,byte[] query,byte[] token) {
        if(query.Length!=105 || token.Length!=32 || packet.Length is <106 or >138 || !packet.AsSpan(0,8).SequenceEqual("DNIFNDR1"u8) ||
            !packet.AsSpan(8,65).SequenceEqual(query.AsSpan(8,65)))return null;
        int count=packet[73];if(count>8 || packet.Length!=106+4*count)return null;
        if(!CryptographicOperations.FixedTimeEquals(packet.AsSpan(packet.Length-32),HMACSHA256.HashData(token,packet.AsSpan(0,packet.Length-32))))return null;
        var addresses=Enumerable.Range(0,count).Select(i=>new IPAddress(packet.AsSpan(74+i*4,4))).ToArray();
        return addresses.All(Allowed)?addresses:null;
    }
    public static bool Allowed(IPAddress address)=>LocalNetworkPolicy.IsPrivateAddress(address) && !IPAddress.IsLoopback(address);
    public static async Task<IPAddress[]> ResolveHintAsync(string hint,CancellationToken stop) {
        if(string.IsNullOrWhiteSpace(hint))return [];
        if(IPAddress.TryParse(hint,out var ip))return Allowed(ip)?[ip]:[];
        if(!TailnetTransport.IsHost(hint))return [];
        try {return (await Dns.GetHostAddressesAsync(hint).WaitAsync(TimeSpan.FromSeconds(2),stop)).Where(TailnetTransport.IsAddress).Take(4).ToArray();}
        catch(Exception) when(!stop.IsCancellationRequested){return [];}
    }
    public static IPAddress[] Broadcasts() => NetworkInterface.GetAllNetworkInterfaces().Where(n=>n.OperationalStatus==OperationalStatus.Up)
        .SelectMany(n=>n.GetIPProperties().UnicastAddresses).Where(a=>Allowed(a.Address) && a.Address.AddressFamily==AddressFamily.InterNetwork)
        .Select(a=> {
            var ip=a.Address.GetAddressBytes();var mask=a.IPv4Mask.GetAddressBytes();
            return new IPAddress(ip.Select((b,i)=>(byte)(b | ~mask[i])).ToArray());
        }).Distinct().Take(16).ToArray();
    public static async Task<IPAddress[]> FindAsync(byte service,byte[] pin,byte[] token,string hint,CancellationToken stop) {
        var hintIps=await ResolveHintAsync(hint,stop);
        var targets=Broadcasts().Concat(hintIps).Concat(await TailnetPeers.AddressesAsync(stop)).Distinct().Take(64).ToArray();
        return await ProbeAsync(service,pin,token,targets,stop);
    }
    public static async Task<IPAddress[]> ProbeAsync(byte service,byte[] pin,byte[] token,IEnumerable<IPAddress> targets,
        CancellationToken stop,int port=Port,int timeoutMs=1200,bool allowLoopback=false) {
        using var socket=new UdpClient(AddressFamily.InterNetwork);socket.EnableBroadcast=true;
        if(OperatingSystem.IsWindows())socket.Client.IOControl(unchecked((int)0x9800000C),new byte[]{0,0,0,0},null);
        byte[] query=Query(service,pin,RandomNumberGenerator.GetBytes(32),token);
        using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);deadline.CancelAfter(timeoutMs);
        foreach(var address in targets.Distinct().Take(64))try {await socket.SendAsync(query,new IPEndPoint(address,port),deadline.Token);}
            catch(SocketException){}
        try {
            while(!deadline.IsCancellationRequested) {
                UdpReceiveResult reply;
                try {reply=await socket.ReceiveAsync(deadline.Token);}catch(SocketException){continue;}
                if(!Allowed(reply.RemoteEndPoint.Address) && !(allowLoopback && IPAddress.IsLoopback(reply.RemoteEndPoint.Address)))continue;
                var addresses=ReadReply(reply.Buffer,query,token);
                if(addresses!=null)return new[]{reply.RemoteEndPoint.Address}.Concat(addresses).Distinct().ToArray();
            }
        } catch(OperationCanceledException) when(!stop.IsCancellationRequested){}
        return [];
    }
}

/// <summary>Local daemon metadata only, bounded/cached; never uploads status or logs it.</summary>
public static class TailnetPeers {
    private static readonly SemaphoreSlim gate=new(1,1);
    private static DateTime cachedAt=DateTime.MinValue;
    private static IPAddress[] cached=[];
    public static async Task<IPAddress[]> AddressesAsync(CancellationToken stop) {
        await gate.WaitAsync(stop);
        try {
            if(DateTime.UtcNow-cachedAt<TimeSpan.FromSeconds(30))return cached;
            cachedAt=DateTime.UtcNow;cached=[];
            string executable=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"Tailscale","tailscale.exe");
            if(!File.Exists(executable))return cached;
            using var deadline=CancellationTokenSource.CreateLinkedTokenSource(stop);deadline.CancelAfter(2500);
            using var process=new Process {StartInfo=new(executable) {UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true}};
            process.StartInfo.ArgumentList.Add("status");process.StartInfo.ArgumentList.Add("--json");
            try {
                process.Start();var errors=process.StandardError.ReadToEndAsync(deadline.Token);
                var bytes=new MemoryStream();byte[] buffer=new byte[4096];int count;
                while((count=await process.StandardOutput.BaseStream.ReadAsync(buffer,deadline.Token))>0) {
                    if(bytes.Length+count>1024*1024)throw new InvalidDataException("Tailnet status outside bounds.");bytes.Write(buffer,0,count);
                }
                await process.WaitForExitAsync(deadline.Token);await errors;
                if(process.ExitCode==0)cached=Parse(bytes.ToArray());
            } catch(Exception) when(!stop.IsCancellationRequested) {try {if(!process.HasExited)process.Kill(true);}catch{} }
            finally {if(stop.IsCancellationRequested)try {if(!process.HasExited)process.Kill(true);}catch{} }
            return cached;
        } finally {gate.Release();}
    }
    public static IPAddress[] Parse(byte[] bytes) {
        if(bytes.Length>1024*1024)throw new InvalidDataException();
        using var json=JsonDocument.Parse(bytes);if(!json.RootElement.TryGetProperty("Peer",out var peers) || peers.ValueKind!=JsonValueKind.Object)return [];
        return peers.EnumerateObject().Where(p=>!p.Value.TryGetProperty("Online",out var online) || online.ValueKind==JsonValueKind.True)
            .SelectMany(p=>p.Value.TryGetProperty("TailscaleIPs",out var ips)&&ips.ValueKind==JsonValueKind.Array?ips.EnumerateArray().Select(i=>i.GetString()??""):[])
            .Select(s=>IPAddress.TryParse(s,out var ip)?ip:null).OfType<IPAddress>().Where(TailnetTransport.IsAddress).Distinct().Take(32).ToArray();
    }
}
