namespace DniBridge.Manager;
public sealed partial class MainForm : Form {
    private readonly ComboBox method=new(){DropDownStyle=ComboBoxStyle.DropDownList};
    private readonly ComboBox pc=new(){DropDownStyle=ComboBoxStyle.DropDown};
    private readonly TextBox phone=new();
    private readonly ComboBox usb=new(){DropDownStyle=ComboBoxStyle.DropDownList};
    private readonly Label serviceStatus=new(),hostStatus=new(),error=new(),help=new();
    private readonly Button install=new(){Text="Instalar / reparar servicio"},start=new(){Text="Iniciar puente"},stop=new(){Text="Detener puente"},pair=new(){Text="Mostrar QR"},refresh=new(){Text="Actualizar dispositivos"};
    private readonly Button selectorStart=new(){Text="Activar selector RENIEC"},selectorStop=new(){Text="Retirar selector"};
    private readonly Label selectorStatus=new();
    private void BuildLayout() {
        Text="DNIe Bridge 3.1 · Gestor Windows";StartPosition=FormStartPosition.CenterScreen;MinimumSize=new Size(720,740);Size=new Size(790,810);
        Font=new Font("Segoe UI",11);BackColor=Color.White;AutoScaleMode=AutoScaleMode.Dpi;
        var layout=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=2,RowCount=13,Padding=new Padding(24)};
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,190));layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
        for(int row=0;row<13;row++)layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        void Whole(System.Windows.Forms.Control control,int row){control.Dock=DockStyle.Fill;control.Margin=new Padding(0,8,0,8);layout.Controls.Add(control,0,row);layout.SetColumnSpan(control,2);}
        void Input(string label,System.Windows.Forms.Control control,int row){layout.Controls.Add(new Label{Text=label,AutoSize=true,Padding=new Padding(0,8,0,0)},0,row);control.Dock=DockStyle.Fill;control.Margin=new Padding(0,4,0,8);layout.Controls.Add(control,1,row);}
        Whole(new Label{Text="Tu DNI, conectado a Windows",Font=new Font("Segoe UI",19,FontStyle.Bold),AutoSize=true},0);
        Whole(serviceStatus,1);serviceStatus.AutoSize=true;
        var installRow=new FlowLayoutPanel{AutoSize=true};installRow.Controls.Add(install);Whole(installRow,2);install.AutoSize=true;
        method.Items.AddRange(new object[]{"Tailscale","LAN","USB"});method.SelectedIndex=0;Input("Conexión",method,3);
        Input("PC / interfaz LAN",pc,4);Input("Teléfono / IP Wi-Fi",phone,5);Input("Dispositivo USB",usb,6);
        Whole(help,7);help.AutoSize=true;help.MaximumSize=new Size(700,0);help.ForeColor=Color.DimGray;
        var actions=new FlowLayoutPanel{AutoSize=true};foreach(var button in new[]{start,stop,pair,refresh}){button.AutoSize=true;actions.Controls.Add(button);}Whole(actions,8);
        Whole(hostStatus,9);hostStatus.Font=new Font("Segoe UI",13,FontStyle.Bold);hostStatus.AutoSize=true;
        Whole(error,10);error.ForeColor=Color.DarkRed;error.AutoSize=true;error.MaximumSize=new Size(700,0);
        var selector=new FlowLayoutPanel{AutoSize=true};selectorStart.AutoSize=true;selectorStop.AutoSize=true;selector.Controls.Add(selectorStart);selector.Controls.Add(selectorStop);selectorStatus.AutoSize=true;selector.Controls.Add(selectorStatus);Whole(selector,11);
        var footer=new FlowLayoutPanel{AutoSize=true};footer.Controls.Add(new Label{Text="Sin cierre por inactividad. Cerrar el gestor conserva el puente en el servicio.",AutoSize=true});
#if DEBUG
        var debug=new Button{Text="Modo debug",AutoSize=true};debug.Click+=(_,_)=>new DebugForm(client).Show(this);footer.Controls.Add(debug);
#endif
        Whole(footer,12);Controls.Add(layout);
    }
}
