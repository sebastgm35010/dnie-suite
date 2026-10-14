using DniBridge.Control;
using QRCoder;
namespace DniBridge.Manager;
public sealed class PairingForm : Form {
    private readonly PictureBox image=new(){Dock=DockStyle.Fill,SizeMode=PictureBoxSizeMode.Zoom};
    public PairingForm(PairingView pairing) {
        Text="Emparejar esta PC";Size=new Size(550,680);StartPosition=FormStartPosition.CenterParent;Font=new Font("Segoe UI",11);BackColor=Aether.Background;ForeColor=Aether.Text;
        var layout=new TableLayoutPanel{Dock=DockStyle.Fill,RowCount=4,Padding=new Padding(16)};
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));layout.RowStyles.Add(new RowStyle(SizeType.Percent,100));layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.Controls.Add(new Label{Text="Escanea con Android y compara la huella. Este QR autoriza tu PC; mantenlo privado.",AutoSize=true,MaximumSize=new Size(490,0)},0,0);
        using var qr=QRCodeGenerator.GenerateQrCode(pairing.Payload,QRCodeGenerator.ECCLevel.M);using var renderer=new PngByteQRCode(qr);
        byte[] bytes=renderer.GetGraphic(6);try {using var stream=new MemoryStream(bytes);using var bitmap=Image.FromStream(stream);image.Image=new Bitmap(bitmap);}finally{Array.Clear(bytes);}
        layout.Controls.Add(image,0,1);
        layout.Controls.Add(new TextBox{Text=pairing.Fingerprint,ReadOnly=true,Multiline=true,Height=70,Dock=DockStyle.Fill,Font=new Font(FontFamily.GenericMonospace,10)},0,2);
        var close=new AetherButton{Text="Ocultar y cerrar",AutoSize=true};Aether.Style(close,true);close.Click+=(_,_)=>Close();layout.Controls.Add(close,0,3);Controls.Add(layout);
    }
    protected override void Dispose(bool disposing){if(disposing)image.Image?.Dispose();base.Dispose(disposing);}
}
