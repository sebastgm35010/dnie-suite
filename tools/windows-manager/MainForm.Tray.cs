using DniBridge.Control;
namespace DniBridge.Manager;

public sealed partial class MainForm {
    private NotifyIcon? tray;
    private ContextMenuStrip? trayMenu;
    private bool exitManager;
    private readonly CheckBox closeToTray=new(){Text="Al cerrar, mantener en bandeja",AutoSize=true};
    private readonly Button inspectReader=new AetherButton(){Text="Probar lector en Windows"};
    private readonly Label inspection=new(){AutoSize=true,ForeColor=Aether.Muted,MaximumSize=new Size(820,0)};
    private void ConfigureTray(bool monitor) {
        inspectReader.Click+=async(_,_)=>await ActionAsync(async()=> {
            // Existing status only enumerates WinSCard. No connect, reset, APDU or auto-start.
            var service=ServiceControl.Query();serviceKnown=true;serviceState=service?.State;
            if(service?.State==4) {var response=await client.SendAsync(new("status"));Check(response);last=response.Status??new();}
            else last=new("Unavailable","idle");
            inspection.Text=service?.State!=4?"Comprobación: servicio detenido o no instalado.":last.ReaderPublished switch {
                true=>"Comprobación: servicio disponible y lector publicado en Windows.",
                false=>"Comprobación: servicio disponible; Windows no enumera el lector.",
                null=>"Comprobación: servicio disponible; publicación del lector indeterminada."
            };Render();
        });
        if(!monitor)return;
        trayMenu=new ContextMenuStrip();
        void Action(string text,Button button){var item=new ToolStripMenuItem(text);item.Click+=(_,_)=>button.PerformClick();trayMenu.Items.Add(item);}
        trayMenu.Items.Add("Mostrar gestor",null,(_,_)=>ShowManager());
        Action("Iniciar puente",start);Action("Detener puente",stop);Action("Mostrar QR",pair);Action("Alternar selector RENIEC",selectorToggle);
        trayMenu.Items.Add(new ToolStripSeparator());trayMenu.Items.Add("Salir del gestor (conservar puente)",null,(_,_)=>{exitManager=true;Close();});
        trayMenu.Opening+=(_,_)=>{foreach(var pair in new[]{(1,start),(2,stop),(3,this.pair),(4,selectorToggle)})trayMenu.Items[pair.Item1].Enabled=pair.Item2.Enabled && pair.Item2.Visible;};
        tray=new NotifyIcon{Icon=(Icon)SystemIcons.Shield.Clone(),Text="DNIe Bridge · gestor",ContextMenuStrip=trayMenu,Visible=true};
        tray.DoubleClick+=(_,_)=>ShowManager();
        Resize+=(_,_)=>{if(WindowState==FormWindowState.Minimized)Hide();};
        FormClosing+=(_,e)=>{if(!exitManager && closeToTray.Checked && e.CloseReason==CloseReason.UserClosing){e.Cancel=true;Hide();}};
        FormClosed+=(_,_)=>{tray.Visible=false;tray.Icon?.Dispose();tray.Dispose();trayMenu.Dispose();};
    }
    private void ShowManager(){Show();WindowState=FormWindowState.Normal;Activate();}
}
