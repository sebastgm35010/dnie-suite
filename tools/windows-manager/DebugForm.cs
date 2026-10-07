#if DEBUG
using DniBridge.Control;
namespace DniBridge.Manager;
public sealed class DebugForm : Form {
    public DebugForm(AgentClient client) {
        Text="Diagnóstico de desarrollo";Size=new Size(700,450);
        var text=new TextBox{Multiline=true,ReadOnly=true,ScrollBars=ScrollBars.Both,Dock=DockStyle.Fill,Font=new Font(FontFamily.GenericMonospace,10)};
        var refresh=new Button{Text="Actualizar eventos del servicio",Dock=DockStyle.Top};
        refresh.Click+=async(_,_)=>{try{var response=await client.SendAsync(new("diagnostics"));text.Text=response.Ok?string.Join(Environment.NewLine,response.Devices??[]):response.Error;}catch(Exception error){text.Text=error.Message;}};
        Controls.Add(text);Controls.Add(refresh);
    }
}
#endif
