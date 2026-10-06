using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using DniBridge;

/// <summary>Fixed synthetic browser probes; never transmits to another reader or a real DNI.</summary>
static class BrowserInteropFixture
{
    public static async Task RunAsync(string directory)
    {
        using var deadline=new CancellationTokenSource(TimeSpan.FromMinutes(3));
        using var key=RSA.Create(2048);
        using var generated=new CertificateRequest("CN=cross-layer-synthetic",key,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1)
            .CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1),DateTimeOffset.UtcNow.AddMinutes(5));
        using var certificate=new X509Certificate2(generated.Export(X509ContentType.Pfx),(string?)null,X509KeyStorageFlags.UserKeySet);
        byte[] token=RandomNumberGenerator.GetBytes(32);
        using var tls=new TcpListener(IPAddress.Loopback,0); tls.Start();
        using var http=new TcpListener(IPAddress.Loopback,0); http.Start();
        Task? host=null;
        bool attached=false;
        string finalResult="";
        string trace=Path.Combine(directory,"controller.jsonl");
        void Event(string name,object? fields=null) { lock(trace) File.AppendAllText(trace,JsonSerializer.Serialize(new {time=DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),category="controller",@event=name,fields=fields??new {}})+"\n"); }
        try
        {
            await File.WriteAllTextAsync(Path.Combine(directory,"android-provision.json"),JsonSerializer.Serialize(new {
                version=1,host="127.0.0.1",port=((IPEndPoint)tls.LocalEndpoint).Port,
                certificateSha256=Convert.ToHexString(SHA256.HashData(certificate.RawData)),pairingTokenBase64=Convert.ToBase64String(token),
                expiresAt=DateTime.UtcNow.AddMinutes(3).ToString("O")}),deadline.Token);
            using var mobile=await tls.AcceptTcpClientAsync(deadline.Token); tls.Stop();
            host=PcscHostSession.RunAsync(mobile,certificate,token,DateTimeOffset.UtcNow.AddMinutes(3),TimeSpan.FromSeconds(45),deadline.Token,
                ()=>new WindowsPcscDevice(WindowsPcscDevice.Enumerate().Single()),phase=>{Event("bridge_phase",new {phase});if(phase=="pcsc_card_present")Volatile.Write(ref attached,true);});
            while(!Volatile.Read(ref attached)) { if(host.IsCompleted) await host; await Task.Delay(25,deadline.Token); }
            await File.WriteAllTextAsync(Path.Combine(directory,"browser-ready.json"),JsonSerializer.Serialize(new {port=((IPEndPoint)http.LocalEndpoint).Port}),deadline.Token);
            bool complete=false;
            while(!complete)
            {
                using var browser=await http.AcceptTcpClientAsync(deadline.Token);
                Event("http_connection_accepted");
                var stream=browser.GetStream();
                using var reader=new StreamReader(stream,Encoding.ASCII,false,1024,true);
                string line;
                try
                {
                    // Chromium may preconnect and leave a socket idle while sending HTTP on another.
                    using var readDeadline=CancellationTokenSource.CreateLinkedTokenSource(deadline.Token);
                    readDeadline.CancelAfter(TimeSpan.FromSeconds(1));
                    string? first=await reader.ReadLineAsync(readDeadline.Token);
                    if(first==null)continue;
                    line=first;
                    Event("http_request",new {kind=first=="GET / HTTP/1.1"?"page":first=="POST /probe HTTP/1.1"?"probe":"other"});
                    int bytes=line.Length;string? header;
                    do {header=await reader.ReadLineAsync(readDeadline.Token);bytes+=header?.Length??0;if(bytes>8192)throw new InvalidDataException("HTTP fixture bounds");}while(!string.IsNullOrEmpty(header));
                }
                catch(Exception failure) when(failure is IOException or OperationCanceledException && !deadline.IsCancellationRequested) {Event("http_idle_connection_retired");continue;}
                string body,content="text/html; charset=utf-8";
                if(line=="GET / HTTP/1.1")
                    body="<!doctype html><meta charset=utf-8><title>Prueba sintética DNIe</title><p>Prueba local de motor y controlador; sin DNI real ni servicios RENIEC.</p><script>fetch('/probe',{method:'POST'}).then(r=>r.json()).then(r=>{window.fixtureResult=r;document.body.append(r.success?'PASS':'FAIL');console.info('synthetic_completed')}).catch(()=>window.fixtureResult={success:false})</script>";
                else if(line=="POST /probe HTTP/1.1")
                {
                    Event("browser_probe_started");
                    var results=Probe(Event);
                    body=JsonSerializer.Serialize(new {success=true,syntheticOnly=true,results});content="application/json";complete=true;
                    finalResult=body;
                    Event("browser_probe_completed");
                }
                else { body="fixture route unavailable";content="text/plain"; }
                byte[] payload=Encoding.UTF8.GetBytes(body);
                await stream.WriteAsync(Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: {content}\r\nContent-Length: {payload.Length}\r\nConnection: close\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; connect-src 'self'\r\n\r\n"),deadline.Token);
                await stream.WriteAsync(payload,deadline.Token);
                Event("http_response_sent");
            }
            // Allow the browser to record response / DOM result, then retire the TLS/card session.
            await Task.Delay(1000,deadline.Token);
            deadline.Cancel();
        }
        catch(Exception failure)
        {
            Event("fixture_failed",new {type=failure.GetType().Name});
            finalResult=JsonSerializer.Serialize(new {success=false,error=failure.GetType().Name,syntheticOnly=true});
            Environment.ExitCode=1;
        }
        finally
        {
            deadline.Cancel(); tls.Stop(); http.Stop();
            if(host!=null){try{await host;}catch{}}
            CryptographicOperations.ZeroMemory(token);
        }
        await File.WriteAllTextAsync(Path.Combine(directory,"result.json"),finalResult);
    }
    static object Probe(Action<string,object?> trace)
    {
        IntPtr context=IntPtr.Zero,card=IntPtr.Zero;
        void Check(uint status,string name){trace(name,new {status=$"{status:X8}"});if(status!=0)throw new InvalidDataException(name);}
        try
        {
            Check(Native.SCardEstablishContext(2,IntPtr.Zero,IntPtr.Zero,out context),"pcsc_context");
            uint length=0;Check(Native.SCardListReadersW(context,null,null,ref length),"reader_list_size");
            char[] names=new char[length];Check(Native.SCardListReadersW(context,null,names,ref length),"reader_list");
            string[] all=new string(names).Split('\0',StringSplitOptions.RemoveEmptyEntries);
            var states=all.Select(name=>new Native.ReaderState {Reader=name,Atr=new byte[36]}).ToArray();
            Check(Native.SCardGetStatusChangeW(context,0,states,(uint)states.Length),"presence_snapshot");
            trace("reader_selection",new {dotnetFirst=all.First(),javaLastPresent=states.LastOrDefault(s=>(s.EventState&32)!=0).Reader,
                readers=states.Select(s=>new {name=s.Reader,present=(s.EventState&32)!=0})});
            string ours=all.Single(r=>r.StartsWith("DNIe Bridge Android NFC"));
            Check(Native.SCardConnectW(context,ours,2,2,out card,out uint protocol),"connect_own_reader");
            byte[] Send(byte[] command,string label)
            {
                var pci=new Native.Pci {Protocol=protocol,Length=8};byte[] result=new byte[258];uint size=258;
                Check(Native.SCardTransmit(card,ref pci,command,(uint)command.Length,IntPtr.Zero,result,ref size),"transmit_"+label);
                string status=Convert.ToHexString(result.AsSpan((int)size-2,2));trace("apdu_result",new {label,status});return result[..(int)size];
            }
            byte[] v2=[0,0xA4,4,12,7,0xA0,0,0,0,0,0x3F,0];
            byte[] v3=[0,0xA4,4,12,16,0xA0,0,0,0,0x77,3,12,0x60,0,0,0,0xFE,0,0,5,0];
            if(!Send(v2,"dotnet_v2").SequenceEqual(new byte[]{0x69,0x85}))throw new InvalidDataException("v2 rejection");
            if(!Send(v3,"dotnet_v3").SequenceEqual(new byte[]{0x90,0}))throw new InvalidDataException("v3 recognition");
            Check(Native.SCardReconnect(card,2,2,1,out protocol),"warm_reset");
            if(!Send(v3,"java_v3_correct_reader").SequenceEqual(new byte[]{0x90,0}))throw new InvalidDataException("java recognition");
            byte[] javaSelect=[0,0xA4,4,4,16,..v3[5..],0];
            if(!Send(javaSelect,"java_p2_04_profile_limit").SequenceEqual(new byte[]{0x69,0x85}))throw new InvalidDataException("profile boundary");
            Check(Native.SCardDisconnect(card,2),"power_off");card=IntPtr.Zero;
            return new {v2="6985",v3="9000",warmReset=true,javaOnCorrectReader="9000",nextJavaSelect="6985"};
        }
        finally {if(card!=IntPtr.Zero)Native.SCardDisconnect(card,0);if(context!=IntPtr.Zero)Native.SCardReleaseContext(context);}
    }
}
