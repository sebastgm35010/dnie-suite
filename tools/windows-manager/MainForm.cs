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
    private uint? serviceState;
    private bool serviceKnown;
    private BridgeStatus last=new();
    public MainForm(): this(true) {}
    internal MainForm(bool monitor) {
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
        selectorToggle.Click+=(_,_)=>{try{if(selector.Running)selector.Stop();else selector.Start(preferences.Directory);error.Text="";Render();}catch(Exception failure){error.Text=failure.Message;}};
        ConfigureTray(monitor);timer.Tick+=async(_,_)=>await PollAsync();
        if(monitor)Shown+=async(_,_)=>{timer.Start();await PollAsync();};
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
        bool isUsb=method.Text=="USB";pcRow.Visible=!isUsb;phoneRow.Visible=!isUsb;usbRow.Visible=isUsb;pair.Visible=method.Text!="Tailscale";
        pcLabel.Text=method.Text=="LAN"?"Interfaz LAN PC":"Host/IP Tailscale PC";
        phoneLabel.Text=method.Text=="LAN"?"IP Wi-Fi teléfono":"Host/IP Tailscale teléfono";
        refresh.Text=isUsb?"Actualizar USB":"Actualizar interfaces";
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
        finally{busy=false;if(!IsDisposed)Render();}
    }
    private async Task PollAsync() {
        if(polling || busy)return;polling=true;
        try {
            var service=ServiceControl.Query();serviceKnown=true;serviceState=service?.State;serviceStatus.Text=service==null?"Servicio: no instalado":service.Value.State==4?"Servicio: disponible · permisos SYSTEM conservados":"Servicio: detenido";
            if(service?.State==1){ServiceControl.Start();service=ServiceControl.Query();serviceState=service?.State;}
            if(service?.State==4){var response=await client.SendAsync(new("status"));
                if(busy || IsDisposed)return;
                Check(response);last=response.Status??new();
                if(last.State is "Running" or "Starting" && last.Profile is {} active) {
                    method.SelectedItem=active.Method;pc.Text=active.Pc;
                    if(active.Method!="USB")phone.Text=active.Phone;
                    else if((usb.SelectedItem as UsbItem)?.Serial!=active.Phone) {
                        var selected=new UsbItem("Telefono seleccionado: "+active.Phone,active.Phone);
                        usb.Items.Add(selected);usb.SelectedItem=selected;
                    }
                }
            }
            else last=new("Unavailable","idle");
            Render();
        }catch(Exception failure){if(!busy && !IsDisposed){serviceKnown=false;serviceState=null;serviceStatus.Text="Servicio: no disponible";last=new("Unavailable","idle");error.Text=failure.Message;Render();}}
        finally{polling=false;}
    }
    private void Render() {
        if(IsDisposed)return;
        inspectReader.Enabled=!busy;if(tray!=null)tray.Text=last.State=="Running"?"DNIe Bridge · puente activo":"DNIe Bridge · puente detenido";
        bool running=last.State is "Running" or "Starting" or "Stopping";
        start.Enabled=!busy && !running && last.State!="Unavailable";stop.Enabled=!busy && running;
        install.Enabled=!busy && !running;pair.Enabled=!busy && last.State!="Unavailable";
        method.Enabled=!busy && !running;
        foreach(var (key,button) in transportButtons){button.Enabled=method.Enabled;if(button is AetherButton segment)segment.IsSelectedSegment=method.Text==key;button.BackColor=method.Text==key?Aether.Primary:Color.FromArgb(229,238,255);button.ForeColor=method.Text==key?Color.White:Aether.Text;}
        pcRow.Visible=method.Text!="USB";phoneRow.Visible=method.Text!="USB";usbRow.Visible=method.Text=="USB";
        install.Visible=serviceState!=4;
        serviceHealth.ShowState("Servicio base",serviceState==4?"Activo (SYSTEM)":serviceState==1?"Detenido":serviceKnown?"No instalado":"Sin verificar",serviceState==4?HealthTone.Good:HealthTone.Idle);
        readerHealth.ShowState("Lector Windows (PC/SC)",last.ReaderPublished switch {true=>"Publicado en WinSCard",false=>"Ausente de WinSCard",null=>"Sin verificar"},last.ReaderPublished switch {true=>HealthTone.Good,false=>HealthTone.Waiting,null=>HealthTone.Idle});
        cardHealth.ShowState("Tarjeta DNIe",last.CardPresent?"DNI presente":running?"Esperando DNI":"Puente detenido",last.CardPresent?HealthTone.Good:running?HealthTone.Ready:HealthTone.Idle);
        selectorStatus.Text=selector.Running?"Activo":"Inactivo";selectorStatus.ForeColor=selector.Running?Aether.Success:Aether.Muted;
        selectorToggle.Text=selector.Running?"Detener selector":"Activar selector";selectorToggle.Enabled=!busy;pc.Enabled=!busy && !running && method.Text!="USB";phone.Enabled=pc.Enabled;usb.Enabled=!busy && !running && method.Text=="USB";refresh.Enabled=!busy && !running;
        hostStatus.Text=busy?"Procesando…":last.CardPresent?last.ReaderPublished switch {
            true=>"DNI conectado · lector publicado en Windows",
            false=>"DNI conectado · lector no publicado en Windows",
            null=>"DNI conectado · publicación del lector sin verificar"
        }:last.State switch {
            "Running"=>last.Phase switch {
                "usb_waiting_phone"=>"USB: esperando el teléfono conectado",
                "usb_switching_accessory"=>"USB: preparando modo accesorio Android",
                "usb_redirect_initial"=>"USB: tomando control del teléfono",
                "usb_query_protocol"=>"USB: comprobando protocolo del accesorio",
                "usb_send_identity"=>"USB: anunciando aplicación Android",
                "usb_start_accessory"=>"USB: solicitando cambio a accesorio",
                "usb_release_initial"=>"USB: liberando conexión anterior",
                "usb_read_configuration"=>"USB: leyendo configuración del accesorio",
                "usb_redirect_accessory"=>"USB: abriendo conexión con el accesorio",
                "usb_waiting_accessory"=>"USB: esperando que el teléfono reaparezca",
                "usb_accessory_open" or "usb_waiting_android"=>"USB: abre DNIe Evolución y autoriza el puente",
                "usb_retry"=>"USB: reconectando · comprueba el permiso y la app Android",
                "transport_retry"=>"Esperando al teléfono · revisa la conexión",
                _=>"Puente activo · esperando DNI"
            },
            "Starting"=>"Preparando puente…","Stopping"=>"Cerrando conexión…","Failed"=>"El puente se detuvo por un error","Unavailable"=>"Instala el servicio para comenzar",_=>"Puente detenido"
        };
        if(last.Error!=null && !busy)error.Text=last.Error;
    }
    private static void Check(ControlResponse response){if(!response.Ok)throw new IOException(response.Error??"La operacion no se pudo completar.");}
    private sealed record UsbItem(string Label,string Serial){public override string ToString()=>Label;}
}
