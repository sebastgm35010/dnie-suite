using System.Security.Principal;
using DniBridge.Control;
using DniBridge.Manager;

internal static class Program {
    [STAThread]private static void Main(string[] args) {
        ApplicationConfiguration.Initialize();
        if(args.Length==2 && args[0]=="--install") {
            try {
                string target=ServiceInstaller.InstallAsync(AppContext.BaseDirectory,args[1]).GetAwaiter().GetResult();
                File.WriteAllText(Path.Combine(ServiceInstaller.InstallRoot,"install-result.json"),System.Text.Json.JsonSerializer.Serialize(new {ok=true,executable=target}));
            }catch(Exception error){MessageBox.Show(error.Message,"Instalacion del puente",MessageBoxButtons.OK,MessageBoxIcon.Error);Environment.ExitCode=1;}
            return;
        }
        if(args.Length==1 && args[0]=="--uninstall") {
            try {if(!new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator))throw new UnauthorizedAccessException("Desinstalar el servicio requiere administrador.");ServiceControl.StopAsync(true).GetAwaiter().GetResult();}
            catch(Exception error){MessageBox.Show(error.Message,"Desinstalar servicio");Environment.ExitCode=1;}return;
        }
#if DEBUG
        if(args.Length==2 && args[0]=="--layout-test") {using var form=new MainForm();form.Show();Application.DoEvents();using var image=new Bitmap(form.Width,form.Height);form.DrawToBitmap(image,new Rectangle(Point.Empty,form.Size));image.Save(args[1]);form.Close();return;}
#endif
        if(args.Length!=0){Environment.ExitCode=2;return;}
        string sid=WindowsIdentity.GetCurrent().User!.Value;
        using var mutex=new Mutex(true,"Local\\DNIeBridge.Manager."+sid,out bool first);
        if(!first){try {using var activate=EventWaitHandle.OpenExisting("Local\\DNIeBridge.Manager.Activate."+sid);activate.Set();}catch{ }return;}
        using var signal=new EventWaitHandle(false,EventResetMode.AutoReset,"Local\\DNIeBridge.Manager.Activate."+sid);
        using var main=new MainForm();
        var registration=ThreadPool.RegisterWaitForSingleObject(signal,(_,_)=> {if(main.IsHandleCreated)main.BeginInvoke(new Action(()=>{main.Show();main.WindowState=FormWindowState.Normal;main.Activate();}));},null,-1,false);
        try {Application.Run(main);}finally{registration.Unregister(null);mutex.ReleaseMutex();}
    }
}
