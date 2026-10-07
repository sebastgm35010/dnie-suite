using System.Diagnostics;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Principal;
using DniBridge.Control;
namespace DniBridge.Manager;

public sealed partial class MainForm {
    private readonly AgentClient client=new();private readonly ManagerPreferences preferences=new();
    private readonly ReniecSelectorAdapter selector=new();
    private readonly System.Windows.Forms.Timer timer=new(){Interval=1500};
    private bool busy,polling,loaded;
    private BridgeStatus last=new();
    public MainForm() {
        BuildLayout();var saved=preferences.Load();method.SelectedItem=saved.Method;pc.Text=saved.Pc;phone.Text=saved.Phone;
        ReloadInterfaces();loaded=true;ShowMethod();
        method.SelectedIndexChanged+=(_,_)=>ShowMethod();
        install.Click+=async(_,_)=>await ActionAsync(InstallAsync);
        start.Click+=async(_,_)=>await ActionAsync(async()=> {
            var profile=Profile();var response=await client.SendAsync(new("start",profile));Check(response);preferences.Save(profile);
            last=response.Status ?? new();Render();
            if(profile.Method!="Tailscale")await ShowPairingAsync();
        });
        stop.Click+=async(_,_)=>await ActionAsync(async()=>{var response=await client.SendAsync(new("stop"));Check(response);last=response.Status??new();Render();});
        pair.Click+=async(_,_)=>await ActionAsync(ShowPairingAsync);
        refresh.Click+=async(_,_)=>await ActionAsync(async()=>{ReloadInterfaces();if(method.Text=="USB")await ReloadUsbAsync();});
        selectorStart.Click+=(_,_)=>{try{selector.Start(preferences.Directory);error.Text="";}catch(Exception failure){error.Text=failure.Message;}};
        selectorStop.Click+=(_,_)=>selector.Stop();
        timer.Tick+=async(_,_)=>await PollAsync();
        Shown+=async(_,_)=>{timer.Start();await PollAsync();};
        FormClosed+=(_,_)=>{timer.Stop();timer.Dispose();selector.Dispose();};
    }
    private void ReloadInterfaces() {
        string value=pc.Text;pc.Items.Clear();
        foreach(var adapter in NetworkInterface.GetAllNetworkInterfaces().Where(n=>n.OperationalStatus==OperationalStatus.Up))
            foreach(var address in adapter.GetIPProperties().UnicastAddresses.Select(a=>a.Address).Where(a=>a.AddressFamily==AddressFamily.InterNetwork && DniBridge.LocalNetworkPolicy.IsPrivateAddress(a) && !System.Net.IPAddress.IsLoopback(a)))pc.Items.Add(address.ToString());
        pc.Text=value;
    }
    private void ShowMethod() {
        if(!loaded)return;
        bool isUsb=method.Text=="USB";pc.Enabled=!isUsb && !busy;phone.Enabled=!isUsb && !busy;usb.Enabled=isUsb && !busy;pair.Visible=method.Text!="Tailscale";
        help.Text=method.Text switch {
            "LAN"=>"Selecciona la IP LAN de esta PC e introduce la IP Wi-Fi que muestra Android. La PC inicia la conexión; TLS conserva su identidad.",
            "USB"=>"Selecciona el teléfono conectado con un cable de datos. Android Open Accessory no requiere ADB ni compartir Internet. Si UsbDk acaba de instalarse, puede requerir reiniciar Windows.",
            _=>"Introduce los nombres Tailscale de ambos equipos. Android sincroniza automáticamente al acercar el DNI; no necesita QR."
        };
        Render();
    }
    private ConnectionProfile Profile()=>new(method.Text,pc.Text.Trim(),method.Text=="USB"?(usb.SelectedItem as UsbItem)?.Serial??"":phone.Text.Trim());
    private async Task ReloadUsbAsync() {
        var response=await client.SendAsync(new("usb-devices"));Check(response);usb.Items.Clear();
        foreach(string device in response.Devices??[]) {int at=device.IndexOf(' ');if(at>0)usb.Items.Add(new UsbItem(device,device[(at+1)..]));}
        if(usb.Items.Count>0)usb.SelectedIndex=0;else error.Text="UsbDk todavía no enumera el teléfono. Reconecta el cable; si PnP pidió reiniciar Windows, completa ese reinicio.";
    }
    private async Task ShowPairingAsync() {
        var response=await client.SendAsync(new("pairing",Profile()));Check(response);
        using var view=new PairingForm(response.Pairing??throw new IOException("No se pudo generar el emparejamiento."));view.ShowDialog(this);
    }
    private async Task InstallAsync() {
        string exe=Environment.ProcessPath??throw new IOException("No se encontro el ejecutable del gestor.");
        var options=new ProcessStartInfo(exe){UseShellExecute=true,Verb="runas",WindowStyle=ProcessWindowStyle.Hidden};
        options.ArgumentList.Add("--install");options.ArgumentList.Add(WindowsIdentity.GetCurrent().User!.Value);
        using var process=Process.Start(options)??throw new IOException("No se pudo abrir el instalador elevado.");await process.WaitForExitAsync();
        if(process.ExitCode!=0)throw new IOException("No se completo la instalacion. El instalador muestra la causa del error.");
        await PollAsync();
    }
    private async Task ActionAsync(Func<Task> action) {
        if(busy)return;busy=true;error.Text="";Render();
        try {await action();}catch(Exception failure){error.Text=failure is System.ComponentModel.Win32Exception {NativeErrorCode:1223}?"Se canceló la elevación. El puente no cambió.":failure.Message;}
        finally{busy=false;Render();}
    }
    private async Task PollAsync() {
        if(polling || busy)return;polling=true;
        try {
            var service=ServiceControl.Query();serviceStatus.Text=service==null?"Servicio: no instalado":service.Value.State==4?"Servicio: disponible · permisos SYSTEM conservados":"Servicio: detenido";
            if(service?.State==1){ServiceControl.Start();service=ServiceControl.Query();}
            if(service?.State==4){var response=await client.SendAsync(new("status"));Check(response);last=response.Status??new();
                if(last.State is "Running" or "Starting" && last.Profile is {} active) {
                    method.SelectedItem=active.Method;pc.Text=active.Pc;
                    if(active.Method!="USB")phone.Text=active.Phone;
                }
            }
            else last=new("Unavailable","idle");
            Render();selectorStatus.Text="Selector: "+selector.Status;selectorStart.Enabled=!selector.Running;selectorStop.Enabled=selector.Running;
        }catch(Exception failure){serviceStatus.Text="Servicio: no disponible";last=new("Unavailable","idle");error.Text=failure.Message;Render();}
        finally{polling=false;}
    }
    private void Render() {
        bool running=last.State is "Running" or "Starting" or "Stopping";
        start.Enabled=!busy && !running && last.State!="Unavailable";stop.Enabled=!busy && running;
        install.Enabled=!busy && !running;pair.Enabled=!busy && last.State!="Unavailable";
        method.Enabled=!busy && !running;pc.Enabled=!busy && !running && method.Text!="USB";phone.Enabled=pc.Enabled;usb.Enabled=!busy && !running && method.Text=="USB";refresh.Enabled=!busy && !running;
        hostStatus.Text=busy?"Procesando…":last.CardPresent?"DNI disponible para aplicaciones de Windows":last.State switch {
            "Running"=>last.Phase=="transport_retry"?"Esperando al teléfono · revisa la conexión":"Puente activo · esperando DNI",
            "Starting"=>"Preparando puente…","Stopping"=>"Cerrando conexión…","Failed"=>"El puente se detuvo por un error","Unavailable"=>"Instala el servicio para comenzar",_=>"Puente detenido"
        };
        if(last.Error!=null && !busy)error.Text=last.Error;
    }
    private static void Check(ControlResponse response){if(!response.Ok)throw new IOException(response.Error??"La operacion no se pudo completar.");}
    private sealed record UsbItem(string Label,string Serial){public override string ToString()=>Label;}
}
