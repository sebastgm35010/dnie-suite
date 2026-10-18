using DniBridge.Control;
using QRCoder;
namespace DniBridge.Manager;
public sealed class PairingForm : Form {
    private readonly PictureBox image=new(){Dock=DockStyle.Fill,SizeMode=PictureBoxSizeMode.Zoom};
    private readonly System.Windows.Forms.Timer copyResetTimer=new(){Interval=2500};
    public PairingForm(PairingView pairing) {
        DoubleBuffered=true;
        Text="Emparejar esta PC";Size=new Size(550,680);StartPosition=FormStartPosition.CenterParent;Font=new Font("Segoe UI",11);BackColor=Aether.Background;ForeColor=Aether.Text;
        var layout=new TableLayoutPanel{Dock=DockStyle.Fill,RowCount=4,Padding=new Padding(24)};
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));layout.RowStyles.Add(new RowStyle(SizeType.Percent,100));layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.Controls.Add(new Label{Text="Vincular teléfono Android\n\n1 · Escanea el QR en DNIe Evolución\n2 · Compara la huella antes de confirmar",AutoSize=true,MaximumSize=new Size(490,0),Font=new Font("Segoe UI",12,FontStyle.Bold)},0,0);
        using var qr=QRCodeGenerator.GenerateQrCode(pairing.Payload,QRCodeGenerator.ECCLevel.M);using var renderer=new PngByteQRCode(qr);
        byte[] bytes=renderer.GetGraphic(6);try {using var stream=new MemoryStream(bytes);using var bitmap=Image.FromStream(stream);image.Image=new Bitmap(bitmap);}finally{Array.Clear(bytes);}
        layout.Controls.Add(image,0,1);
        var fingerprint=new TextBox{Text=string.Join(" ",pairing.Fingerprint.Replace(":","").Chunk(4).Select(x=>new string(x))),ReadOnly=true,Multiline=true,Height=70,Dock=DockStyle.Fill,Font=new Font(FontFamily.GenericMonospace,10)};layout.Controls.Add(fingerprint,0,2);
        var actions=new FlowLayoutPanel{Dock=DockStyle.Fill,FlowDirection=FlowDirection.RightToLeft,AutoSize=true};
        var close=new AetherButton{Text="Ocultar y cerrar",AutoSize=true};Aether.Style(close,true);close.Click+=(_,_)=>Close();
        var copy=new AetherButton{Text="Copiar huella",AutoSize=true};Aether.Style(copy,false);
        copyResetTimer.Tick+=(_,_)=>{copyResetTimer.Stop();if(!copy.IsDisposed)copy.Text="Copiar huella";};
        copy.Click+=(_,_)=>{Clipboard.SetText(pairing.Fingerprint);copy.Text="✓ Copiada";copyResetTimer.Stop();copyResetTimer.Start();};
        actions.Controls.Add(close);actions.Controls.Add(copy);layout.Controls.Add(actions,0,3);Controls.Add(layout);
    }
    protected override void Dispose(bool disposing){if(disposing){copyResetTimer.Stop();copyResetTimer.Dispose();image.Image?.Dispose();}base.Dispose(disposing);}
}
