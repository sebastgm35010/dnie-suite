using System.Reflection;
using DniBridge.Manager;
using DniBridge.Control;
internal static class Program {
    private static int checks;
    private static void Check(bool condition,string message){if(!condition)throw new Exception(message);checks++;}
    [STAThread]private static void Main(string[] args) {
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);using var form=new MainForm(false);
        T Field<T>(string name)=>(T)typeof(MainForm).GetField(name,BindingFlags.Instance|BindingFlags.NonPublic)!.GetValue(form)!;
        void Set(string name,object value)=>typeof(MainForm).GetField(name,BindingFlags.Instance|BindingFlags.NonPublic)!.SetValue(form,value);
        void Render()=>typeof(MainForm).GetMethod("Render",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(form,null);
        form.Show();Application.DoEvents();
        var method=Field<ComboBox>("method");
        foreach(var transport in new[]{"Tailscale","LAN","USB"}) {
            method.SelectedItem=transport;Application.DoEvents();
            Check(Field<Panel>("pcRow").Visible==(transport!="USB"),"IP row matches transport");
            Check(Field<Panel>("phoneRow").Visible==(transport!="USB"),"Phone row matches transport");
            Check(Field<Panel>("usbRow").Visible==(transport=="USB"),"USB row matches transport");
            Check(Field<Button>("pair").Visible==(transport!="Tailscale"),"QR visibility matches transport");
        }
        Set("serviceState",(uint)4);
        foreach(bool? published in new bool?[]{null,false,true}) {
            Set("last",new BridgeStatus("Running","waiting",true,ReaderPublished:published));Render();
            Check(Field<System.Windows.Forms.Control>("cardHealth").AccessibleDescription=="DNI presente","Private card health independent from PCSC");
            var detail=Field<System.Windows.Forms.Control>("readerHealth").AccessibleDescription;
            Check(detail==(published switch {true=>"Publicado en WinSCard",false=>"Ausente de WinSCard",null=>"Sin verificar"}),"Reader publication is three-state");
            Check(Field<System.Windows.Forms.Control>("serviceHealth").AccessibleDescription=="Activo (SYSTEM)","Service health independent from card");
        }
        Check(!Field<Button>("install").Visible,"No repair prompt when service healthy");
        if(args.Length==1) {
            Directory.CreateDirectory(args[0]);Set("last",new BridgeStatus("Stopped","idle",ReaderPublished:true));Render();
            foreach(var transport in new[]{"Tailscale","LAN","USB"}) {
                method.SelectedItem=transport;Application.DoEvents();using var bitmap=new Bitmap(form.Width,form.Height);
                form.DrawToBitmap(bitmap,new Rectangle(Point.Empty,form.Size));bitmap.Save(Path.Combine(args[0],"manager-"+transport+".png"));
            }
        }
        Check(typeof(MainForm).Assembly.GetType("DniBridge.Manager.DebugForm")==null,"Release excludes DebugForm type");
        var probe=Path.Combine(Path.GetTempPath(),"dnie-forbidden-layout.png");
        using(var process=System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"DNIeBridge.exe")){UseShellExecute=false,ArgumentList={"--layout-test",probe}})!) {
            Check(process.WaitForExit(5000) && process.ExitCode==2,"Release rejects layout-test command");
        }
        form.Close();Console.WriteLine(System.Text.Json.JsonSerializer.Serialize(new{passed=true,checks,serviceCalls=0,cardOperations=0}));
    }
}
