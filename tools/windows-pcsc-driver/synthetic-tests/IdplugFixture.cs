using System.Security.Principal;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using DniBridge;

// Detection-only model. Never uses NFC, a production private key, PIN or CAN.
static class IdplugFixture
{
    sealed class Model
    {
        public byte[] Atr { get; }
        readonly byte[] access, fci, uid;
        readonly string mode;
        bool selected;
        bool mseReady, nonceSent;
        int paceStep;
        public Model(string directory) {
            using var doc=JsonDocument.Parse(File.ReadAllText(Path.Combine(directory,"bootstrap.json")));
            var p=doc.RootElement;
            Atr=Convert.FromHexString(p.GetProperty("atr").GetString()!);
            access=Convert.FromHexString(p.GetProperty("cardAccess").GetString()!);
            fci=Convert.FromHexString(p.GetProperty("fci").GetString()!);
            uid=Convert.FromHexString(p.GetProperty("syntheticUid").GetString()!);
            mode=p.GetProperty("mode").GetString()!;
            if(Atr.Length is <2 or >33 || access.Length is <1 or >4096 || uid.Length!=4)
                throw new InvalidDataException("fixture bounds");
        }
        public void Reset() { selected=false;mseReady=false;nonceSent=false;paceStep=0; }
        public byte[] Reply(byte[] c) {
            if(c.Length<4)return [0x67,0];
            if(c[0]==0xFF && c[1]==0xCA) {
                if(c.Length!=5 || c[2]!=0 || c[3]!=0)return [0x6A,0x81];
                return mode=="uid-off" ? [0x6A,0x81] : [..uid,0x90,0];
            }
            // Explicit experiment: model only MSE and encrypted challenge, never mutual auth.
            if((mode=="nonce-gate" || mode=="auth-failure-gate") && c.Length==23 && Convert.ToHexString(c)=="0022C1A412800A04007F0007020204040483010284010D") {
                mseReady=true;nonceSent=false;paceStep=0;return [0x90,0];
            }
            if((mode=="nonce-gate" || mode=="auth-failure-gate") && mseReady && !nonceSent && Convert.ToHexString(c)=="10860000027C0000") {
                nonceSent=true;paceStep=1;
                byte[] key=SHA256.HashData([..Encoding.ASCII.GetBytes("000000"),0,0,0,3]);
                using var aes=Aes.Create();aes.Key=key;aes.IV=new byte[16];aes.Mode=CipherMode.CBC;aes.Padding=PaddingMode.None;
                byte[] encrypted=aes.CreateEncryptor().TransformFinalBlock(new byte[32],0,32);
                CryptographicOperations.ZeroMemory(key);
                return [0x7C,0x22,0x80,0x20,..encrypted,0x90,0];
            }
            // Structural emulation only: RFC 5639 public generator, no shared secret.
            // Deliberately fail final mutual authentication; never claim a secure session.
            if(mode=="auth-failure-gate" && c.Length>=9 && c[1]==0x86 && c[2]==0 && c[3]==0) {
                if(paceStep==1 && c.Length==42 && c[0]==0x10 && c[4]==0x24 && c[5]==0x7C && c[6]==0x22 && c[7]==0x81 && c[8]==0x20) {
                    paceStep=2;return [0x7C,2,0x82,0,0x90,0];
                }
                if(paceStep==2 && c.Length==75 && c[0]==0x10 && c[4]==0x45 && c[5]==0x7C && c[6]==0x43 && c[7]==0x83 && c[8]==0x41 && c[9]==4) {
                    paceStep=3;
                    byte[] point=Convert.FromHexString("048BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997");
                    return [0x7C,0x43,0x84,0x41,..point,0x90,0];
                }
                if(paceStep==3 && c.Length==18 && c[0]==0 && c[4]==12 && c[5]==0x7C && c[6]==10 && c[7]==0x85 && c[8]==8) {
                    mseReady=false;paceStep=0;return [0x63,0];
                }
            }
            if(c[0]!=0)return [0x69,0x85];
            // Do not implement PACE or PIN authentication. Record the first attempt only.
            if(c[1] is 0x22 or 0x86 or 0x20 or 0x24 or 0x26 or 0x28 or 0x2A or 0x82 or 0x88)return [0x69,0x85];
            if(c[1]==0xA4) {
                selected=false;
                if(c.Length<5 || c.Length<5+c[4])return [0x67,0];
                var data=Convert.ToHexString(c.AsSpan(5,c[4]));
                if(c[2]==4 && data=="A000000077030C60000000FE00000500")return [0x90,0];
                if(c[2]==2 && data=="011C" && mode!="access-off") {
                    selected=true;return c[3]==4 ? [..fci,0x90,0] : [0x90,0];
                }
                return [0x6A,0x82];
            }
            if(c[1]==0xB0) {
                if(!selected)return [0x69,0x85];
                int offset=(c[2]<<8)|c[3];
                int le=c.Length==5 ? (c[4]==0?256:c[4]) : c.Length==7 && c[4]==0 ? (c[5]<<8)|c[6] : -1;
                if(le<1)return [0x67,0];
                if(offset>=access.Length)return [0x6B,0];
                return [..access.AsSpan(offset,Math.Min(le,access.Length-offset)),0x90,0];
            }
            return [0x6D,0];
        }
    }
    public static void SelfTest(string directory) {
        var model=new Model(directory);
        void Check(bool ok){if(!ok)throw new InvalidDataException("model test");}
        Check(model.Reply([0,0xB0,0,0,0]).SequenceEqual(new byte[]{0x69,0x85}));
        Check(model.Reply([0,0xA4,2,12,2,1,0x1C]).SequenceEqual(new byte[]{0x90,0}));
        Check(model.Reply([0,0xB0,0,0,0]).Length>2);
        model.Reset();Check(model.Reply([0,0xB0,0,0,0]).Length==2);
        foreach(byte ins in new byte[]{0x20,0x22,0x86,0x2A})Check(model.Reply([0,ins,0,0]).SequenceEqual(new byte[]{0x69,0x85}));
        Check(model.Reply([0xFF,0xCA,0,0,0]).Length==6);
        using var config=JsonDocument.Parse(File.ReadAllText(Path.Combine(directory,"bootstrap.json")));
        if(config.RootElement.GetProperty("mode").GetString()=="auth-failure-gate") {
            Check(model.Reply(Convert.FromHexString("10860000027C0000")).SequenceEqual(new byte[]{0x69,0x85}));
            Check(model.Reply(Convert.FromHexString("0022C1A412800A04007F0007020204040483010284010D")).SequenceEqual(new byte[]{0x90,0}));
            Check(model.Reply(Convert.FromHexString("10860000027C0000")).Length==38);
            Check(model.Reply([0x10,0x86,0,0,0x24,0x7C,0x22,0x81,0x20,..new byte[32],0]).SequenceEqual(new byte[]{0x7C,2,0x82,0,0x90,0}));
            Check(model.Reply([0x10,0x86,0,0,0x45,0x7C,0x43,0x83,0x41,4,..new byte[64],0]).Length==71);
            Check(model.Reply([0,0x86,0,0,12,0x7C,10,0x85,8,..new byte[8],0]).SequenceEqual(new byte[]{0x63,0}));
            Check(model.Reply([0,0x86,0,0,12,0x7C,10,0x85,8,..new byte[8],0]).SequenceEqual(new byte[]{0x69,0x85}));
            Console.WriteLine("PASS 7 additional synthetic PACE failure-stage checks.");
        }
        Console.WriteLine("PASS 9 detection-model checks; authentication deliberately unavailable.");
    }
    public static async Task RunAsync(string directory) {
        if(!OperatingSystem.IsWindows())throw new PlatformNotSupportedException();
        if(WindowsIdentity.GetCurrent().User?.Value!="S-1-5-18")throw new UnauthorizedAccessException("SYSTEM reader backend required");
        var model=new Model(directory);
        string log=Path.Combine(directory,"synthetic-apdu.jsonl");
        long sequence=0;
        void Event(string name,object fields) => File.AppendAllText(log,JsonSerializer.Serialize(new {
            time=DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),syntheticOnly=true,sequence,@event=name,fields})+"\n");
        using var deadline=new CancellationTokenSource(TimeSpan.FromMinutes(4));
        bool ok=false;
        try {
            using var device=new WindowsPcscDevice(WindowsPcscDevice.Enumerate().Single());
            device.Attach(model.Atr,2);
            Event("attached",new {profile="detection_only",uid="synthetic",authentication=false});
            await File.WriteAllTextAsync(Path.Combine(directory,"ready.json"),"{\"syntheticOnly\":true}");
            while(!deadline.IsCancellationRequested && !File.Exists(Path.Combine(directory,"stop.txt")) && sequence<10000) {
                var request=device.Poll();
                if(request==null){await Task.Delay(10);continue;}
                sequence++;
                byte[] answer;
                switch(request.Opcode) {
                    case PcscAbi.Reset: model.Reset();answer=model.Atr;Event("reset",new {});break;
                    case PcscAbi.PowerOff: model.Reset();answer=[];Event("power_off",new {});break;
                    case PcscAbi.Transmit:
                        var c=request.Payload;
                        bool pin=c.Length>1 && c[1] is 0x20 or 0x24 or 0x26 or 0x28 or 0x2C;
                        answer=model.Reply(c);
                        Event("apdu",new {command=Convert.ToHexString(pin?c.Take(4).ToArray():c),redacted=pin,
                            commandBytes=c.Length,response=Convert.ToHexString(answer),kind="synthetic_bootstrap"});break;
                    default: throw new InvalidDataException("unknown driver control");
                }
                device.Complete(request.Id,0,answer);
            }
            ok=true;
        } catch(Exception failure) {Event("failed",new {type=failure.GetType().Name});}
        finally {
            Event("detached",new {ok});
            await File.WriteAllTextAsync(Path.Combine(directory,"result.json"),JsonSerializer.Serialize(new {ok,syntheticOnly=true,sequence}));
        }
    }
}
