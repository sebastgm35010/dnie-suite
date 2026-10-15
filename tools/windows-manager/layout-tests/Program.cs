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
        foreach(var transport in new[]{"LAN","USB"}) {
            method.SelectedItem=transport;Application.DoEvents();
            Check(!Field<Panel>("pcRow").Visible,"IP row matches transport");
            Check(Field<Panel>("phoneRow").Visible==(transport!="USB"),"Phone row matches transport");
            Check(Field<Panel>("usbRow").Visible==(transport=="USB"),"USB row matches transport");
            Check(Field<Button>("pair").Visible,"QR visibility matches transport");
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
        var segments=Field<Dictionary<string,Button>>("transportButtons");
        foreach(var transport in new[]{"LAN","USB"}) {
            method.SelectedItem=transport;Render();
            foreach(var (key,button) in segments) {
                Check(!button.Enabled,"Transport cannot change during a session");
                using var bitmap=new Bitmap(button.Width,button.Height);using var graphics=Graphics.FromImage(bitmap);graphics.Clear(Color.Black);
                button.GetType().GetMethod("OnPaint",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(button,new object[]{new PaintEventArgs(graphics,button.ClientRectangle)});
                Check(bitmap.GetPixel(0,0).ToArgb()==Color.White.ToArgb(),"Opaque button explicitly paints its outer corner white");
                Check(bitmap.GetPixel(button.Width/2,6).ToArgb()==(key==transport?Color.FromArgb(0,74,198):Color.FromArgb(239,244,255)).ToArgb(),"Selected transport stays blue while disabled");
            }
        }
        foreach(var name in new[]{"serviceHealth","readerHealth","cardHealth"}) {
            var control=Field<System.Windows.Forms.Control>(name);using var bitmap=new Bitmap(control.Width,control.Height);using var graphics=Graphics.FromImage(bitmap);graphics.Clear(Color.Black);
            control.GetType().GetMethod("OnPaint",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(control,new object[]{new PaintEventArgs(graphics,control.ClientRectangle)});
            Check(bitmap.GetPixel(0,0).ToArgb()==control.Parent!.BackColor.ToArgb(),"Health card clears its outer corner");
        }
        Check(Field<ComboBox>("pc").Parent!.GetType().Name=="AetherInputBox","PC input uses rounded wrapper");
        Check(Field<ComboBox>("phone").DropDownStyle==ComboBoxStyle.DropDown,"Phone input has no sunken border");

        if(args.Length==1) {
            Directory.CreateDirectory(args[0]);Set("last",new BridgeStatus("Stopped","idle",ReaderPublished:true));Render();
            foreach(var transport in new[]{"LAN","USB"}) {
                method.SelectedItem=transport;Application.DoEvents();using var bitmap=new Bitmap(form.Width,form.Height);
                form.DrawToBitmap(bitmap,new Rectangle(Point.Empty,form.Size));bitmap.Save(Path.Combine(args[0],"manager-"+transport+".png"));
            }
        }
        Check(typeof(MainForm).Assembly.GetType("DniBridge.Manager.DebugForm")==null,"Release excludes DebugForm type");
        var probe=Path.Combine(Path.GetTempPath(),"dnie-forbidden-layout.png");
        using(var process=System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"DNIeBridge.exe")){UseShellExecute=false,ArgumentList={"--layout-test",probe}})!) {
            Check(process.WaitForExit(5000) && process.ExitCode==2,"Release rejects layout-test command");
        }
        typeof(MainForm).GetMethod("ConfigureTray",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(form,new object[]{true});
        Check(Field<NotifyIcon>("tray").Visible,"Tray icon available without starting host");
        form.WindowState=FormWindowState.Minimized;Application.DoEvents();Check(!form.Visible,"Minimize moves manager to tray");
        typeof(MainForm).GetMethod("ShowManager",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(form,null);Application.DoEvents();Check(form.Visible,"Tray can restore manager");
        Field<CheckBox>("closeToTray").Checked=true;form.Close();Application.DoEvents();Check(!form.IsDisposed && !form.Visible,"Optional close keeps manager in tray");
        Aether.Apply(form,true);Aether.UpdateThemeButton(Field<Button>("themeToggle"));Application.DoEvents();
        Check(form.BackColor==Color.FromArgb(11,19,38),"Dark background matches Android palette");
        Check(Aether.Surface==Color.FromArgb(19,27,46),"Dark surface matches Android palette");
        Check(Aether.Primary.ToArgb()==unchecked((int)0xFF6B9EFF),"Dark primary R9");
        Check(Aether.PrimaryText.ToArgb()==unchecked((int)0xFF00174B),"Dark primary foreground R9");
        Check(Field<Button>("themeToggle").Text=="Modo claro","Readable theme action");
        Check(Field<Button>("start").ForeColor==Aether.PrimaryText,"Primary action keeps semantic foreground after theme change");
        if(args.Length>0) {using var darkImage=new Bitmap(form.Width,form.Height);form.DrawToBitmap(darkImage,new Rectangle(Point.Empty,form.Size));darkImage.Save(Path.Combine(args[0],"bridge-dark-r7.png"));}
        Aether.Apply(form,false);Application.DoEvents();Check(form.BackColor==Aether.Background,"Light theme restored without changing service state");
        Field<CheckBox>("closeToTray").Checked=false;form.Close();Console.WriteLine(System.Text.Json.JsonSerializer.Serialize(new{passed=true,checks,serviceCalls=0,cardOperations=0}));
    }
}
