namespace DniBridge.Manager;
public sealed partial class MainForm : Form {
    private readonly ComboBox method=new(){DropDownStyle=ComboBoxStyle.DropDownList};
    private readonly ComboBox pc=new(){DropDownStyle=ComboBoxStyle.DropDown};
    private readonly ComboBox phone=new(){DropDownStyle=ComboBoxStyle.DropDown};
    private readonly ComboBox usb=new(){DropDownStyle=ComboBoxStyle.DropDownList};
    private readonly Label serviceStatus=new(),hostStatus=new(),error=new(),help=new();
    private readonly Button install=new AetherButton(){Text="Instalar / reparar servicio"},start=new AetherButton(){Text="Iniciar puente"},stop=new AetherButton(){Text="Detener puente"},pair=new AetherButton(){Text="Mostrar QR"},refresh=new AetherButton(){Text="Actualizar dispositivos"};
    private readonly Button selectorToggle=new AetherButton(){Text="Activar selector"};
    private readonly Label selectorStatus=new();
    private readonly HealthCard serviceHealth=new(),readerHealth=new(),cardHealth=new();
    private readonly Panel pcRow=new(){AutoSize=true},phoneRow=new(){AutoSize=true},usbRow=new(){AutoSize=true};
    private readonly Label pcLabel=new(),phoneLabel=new();
    private readonly Dictionary<string,Button> transportButtons=new();
    private void BuildLayout() {
        Text="DNIe Bridge 3.1 · Gestor Windows";StartPosition=FormStartPosition.CenterScreen;MinimumSize=new Size(840,790);Size=new Size(960,880);
        Font=new Font("Segoe UI",10);BackColor=Aether.Background;ForeColor=Aether.Text;AutoScaleMode=AutoScaleMode.Dpi;
        var scroll=new Panel{Dock=DockStyle.Fill,AutoScroll=true,Padding=new Padding(22)};
        var layout=new TableLayoutPanel{Dock=DockStyle.Top,AutoSize=true,ColumnCount=1,RowCount=7};
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
        void Whole(System.Windows.Forms.Control control,int row){control.Dock=DockStyle.Fill;control.Margin=new Padding(0,0,0,16);layout.Controls.Add(control,0,row);}
        var header=new FlowLayoutPanel{AutoSize=true,FlowDirection=FlowDirection.TopDown,WrapContents=false};
        header.Controls.Add(new Label{Text="◈ DNIe Bridge",Font=new Font("Segoe UI",23,FontStyle.Bold),AutoSize=true});
        header.Controls.Add(new Label{Text="Tu DNI, conectado a las aplicaciones de Windows",ForeColor=Aether.Muted,AutoSize=true});Whole(header,0);
        var health=new TableLayoutPanel{ColumnCount=3,RowCount=1,Height=114};
        foreach(var card in new[]{serviceHealth,readerHealth,cardHealth}){health.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,33.33f));card.Dock=DockStyle.Fill;card.Margin=new Padding(0,0,10,0);health.Controls.Add(card);}
        Whole(health,1);
        var configuration=new AetherCard{AutoSize=true};
        var config=new TableLayoutPanel{BackColor=Aether.Surface,Dock=DockStyle.Top,AutoSize=true,ColumnCount=1,RowCount=7};config.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
        var segments=new TableLayoutPanel{BackColor=Aether.Surface,Dock=DockStyle.Top,ColumnCount=2,Height=48,Margin=new Padding(0,0,0,15)};
        foreach(var (key,label) in new[]{("LAN","Red (Wi-Fi / Tailscale)"),("USB","Cable USB")}) {
            segments.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,50f));var segment=new AetherButton{Text=label,Dock=DockStyle.Fill,Margin=new Padding(0,0,8,0)};
            Aether.Style(segment);segment.Click+=(_,_)=>method.SelectedItem=key;transportButtons.Add(key,segment);segments.Controls.Add(segment);
        }
        method.Items.AddRange(new object[]{"LAN","USB"});method.SelectedIndex=0;config.Controls.Add(segments);
        void Input(Panel row,Label label,System.Windows.Forms.Control field,string text) {
            row.BackColor=Aether.Surface;row.Dock=DockStyle.Top;row.Margin=new Padding(0,0,0,10);
            var table=new TableLayoutPanel{BackColor=Aether.Surface,Dock=DockStyle.Top,AutoSize=true,ColumnCount=2};table.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,205));table.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            label.Text=text;label.AutoSize=true;label.Padding=new Padding(0,8,0,0);table.Controls.Add(label,0,0);
            var input=new AetherInputBox(field){Dock=DockStyle.Fill,Margin=new Padding(0,4,0,5)};table.Controls.Add(input,1,0);row.Controls.Add(table);config.Controls.Add(row);
        }
        Input(pcRow,pcLabel,pc,"Host/IP Tailscale PC");Input(phoneRow,phoneLabel,phone,"Host/IP Tailscale teléfono");Input(usbRow,new Label(),usb,"Dispositivo USB");
        help.AutoSize=true;help.MaximumSize=new Size(820,0);help.ForeColor=Aether.Muted;help.Margin=new Padding(0,4,0,14);config.Controls.Add(help);
        var actions=new FlowLayoutPanel{BackColor=Aether.Surface,AutoSize=true,Dock=DockStyle.Top};foreach(var button in new[]{start,stop,pair,refresh,inspectReader}){Aether.Style(button,button==start);actions.Controls.Add(button);}config.Controls.Add(actions);
        config.Controls.Add(inspection);
        hostStatus.AutoSize=true;hostStatus.ForeColor=Aether.Muted;hostStatus.MaximumSize=new Size(820,0);hostStatus.Margin=new Padding(0,10,0,0);config.Controls.Add(hostStatus);
        configuration.Controls.Add(config);Whole(configuration,2);
        error.ForeColor=Aether.Error;error.AutoSize=true;error.MaximumSize=new Size(820,0);Whole(error,3);
        var selectorCard=new AetherCard{AutoSize=true};var selectorLayout=new FlowLayoutPanel{BackColor=Aether.Surface,AutoSize=true,Dock=DockStyle.Top,FlowDirection=FlowDirection.TopDown,WrapContents=false};
        selectorLayout.Controls.Add(new Label{Text="Integración RENIEC / Ciudadano Digital",AutoSize=true,Font=new Font("Segoe UI",12,FontStyle.Bold)});
        selectorLayout.Controls.Add(new Label{Text="Dirige el flujo compatible al lector DNIe Bridge.",AutoSize=true,ForeColor=Aether.Muted});
        var selectorActions=new FlowLayoutPanel{BackColor=Aether.Surface,AutoSize=true};selectorStatus.AutoSize=true;selectorStatus.Padding=new Padding(0,10,12,0);Aether.Style(selectorToggle);selectorActions.Controls.Add(selectorStatus);selectorActions.Controls.Add(selectorToggle);selectorLayout.Controls.Add(selectorActions);
        selectorCard.Controls.Add(selectorLayout);Whole(selectorCard,4);
        var footer=new FlowLayoutPanel{AutoSize=true};Aether.Style(install);footer.Controls.Add(install);footer.Controls.Add(closeToTray);
        footer.Controls.Add(new Label{Text="Sin cierre por inactividad. Cerrar esta ventana conserva el puente activo.",AutoSize=true,ForeColor=Aether.Muted,Padding=new Padding(0,10,0,0)});
#if DEBUG
        var debug=new AetherButton{Text="Modo debug",AutoSize=true};Aether.Style(debug);debug.Click+=(_,_)=>new DebugForm(client).Show(this);footer.Controls.Add(debug);
#endif
        Whole(footer,5);scroll.Controls.Add(layout);Controls.Add(scroll);
    }
}
