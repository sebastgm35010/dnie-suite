using System.Drawing.Drawing2D;
namespace DniBridge.Manager;

internal static class Aether {
    public static readonly Color Primary=Color.FromArgb(0,74,198), Background=Color.FromArgb(248,249,255),
        Surface=Color.White, Container=Color.FromArgb(239,244,255), Border=Color.FromArgb(220,233,255),
        Text=Color.FromArgb(11,28,48), Muted=Color.FromArgb(67,70,85), Success=Color.FromArgb(0,106,99),
        Error=Color.FromArgb(186,26,26), Amber=Color.FromArgb(217,119,6);
    public static GraphicsPath Rounded(RectangleF r,float radius) {
        float d=Math.Min(radius*2,Math.Min(r.Width,r.Height));var path=new GraphicsPath();
        path.AddArc(r.X,r.Y,d,d,180,90);path.AddArc(r.Right-d,r.Y,d,d,270,90);
        path.AddArc(r.Right-d,r.Bottom-d,d,d,0,90);path.AddArc(r.X,r.Bottom-d,d,d,90,90);path.CloseFigure();return path;
    }
    public static void Style(Button button,bool primary=false) {
        button.FlatStyle=FlatStyle.Flat;button.FlatAppearance.BorderSize=0;
        button.BackColor=primary?Primary:Color.FromArgb(229,238,255);button.ForeColor=primary?Color.White:Color.FromArgb(0,23,75);
        button.Font=new Font("Segoe UI",10,FontStyle.Bold);button.Padding=new Padding(12,7,12,7);button.AutoSize=true;button.Cursor=Cursors.Hand;
    }
    public static Color SurfaceBehind(System.Windows.Forms.Control control) {
        for(var parent=control.Parent;parent!=null;parent=parent.Parent)
            if(parent.BackColor.A==255)return parent.BackColor;
        return Background;
    }
}
internal sealed class AetherButton : Button {
    private bool hover;
    private bool selectedSegment;
    public bool IsSelectedSegment {get=>selectedSegment;set{if(selectedSegment==value)return;selectedSegment=value;Invalidate();}}
    public AetherButton(){FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;UseVisualStyleBackColor=false;
        SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);}
    protected override void OnMouseEnter(EventArgs e){hover=true;Invalidate();base.OnMouseEnter(e);}
    protected override void OnMouseLeave(EventArgs e){hover=false;Invalidate();base.OnMouseLeave(e);}
    protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Aether.SurfaceBehind(this));}
    protected override void OnPaint(PaintEventArgs e){
        var g=e.Graphics;g.Clear(Aether.SurfaceBehind(this));g.SmoothingMode=SmoothingMode.AntiAlias;g.PixelOffsetMode=PixelOffsetMode.HighQuality;
        if(Width<3 || Height<3)return;
        using var path=Aether.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),7);
        var color=Enabled||IsSelectedSegment?BackColor:Aether.Container;if(hover && Enabled)color=ControlPaint.Light(color,.1f);
        using var fill=new SolidBrush(color);g.FillPath(fill,path);
        if(Focused){using var border=new Pen(Aether.Primary,1.5f);g.DrawPath(border,path);}
        TextRenderer.DrawText(g,Text,Font,new Rectangle(4,3,Width-8,Height-6),Enabled||IsSelectedSegment?ForeColor:Color.FromArgb(115,118,134),TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis|TextFormatFlags.NoPrefix);
    }
}
internal sealed class AetherCard : Panel {
    public AetherCard(){DoubleBuffered=true;BackColor=Aether.Surface;Padding=new Padding(18);}
    protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Aether.SurfaceBehind(this));}
    protected override void OnPaint(PaintEventArgs e) {
        e.Graphics.Clear(Aether.SurfaceBehind(this));if(Width<3 || Height<3)return;
        e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;using var path=Aether.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),12);
        using var fill=new SolidBrush(Aether.Surface);using var border=new Pen(Aether.Border);e.Graphics.FillPath(fill,path);e.Graphics.DrawPath(border,path);base.OnPaint(e);
    }
}
internal enum HealthTone { Idle, Waiting, Good, Error, Ready }
internal sealed class HealthCard : System.Windows.Forms.Control {
    private string heading="",detail="";private HealthTone tone;
    public HealthCard(){DoubleBuffered=true;BackColor=Aether.Background;MinimumSize=new Size(180,104);Height=112;}
    public void ShowState(string title,string value,HealthTone state){if(heading==title && detail==value && tone==state)return;heading=title;detail=value;tone=state;AccessibleName=title;AccessibleDescription=value;Invalidate();}
    protected override void OnPaint(PaintEventArgs e) {
        base.OnPaint(e);var g=e.Graphics;g.Clear(Aether.SurfaceBehind(this));if(Width<3 || Height<3)return;g.SmoothingMode=SmoothingMode.AntiAlias;
        using var path=Aether.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),14);using var white=new SolidBrush(Aether.Surface);
        using var border=new Pen(Aether.Border);g.FillPath(white,path);g.DrawPath(border,path);
        using var titleFont=new Font("Segoe UI",10);using var valueFont=new Font("Segoe UI",11,FontStyle.Bold);
        var flags=TextFormatFlags.WordBreak|TextFormatFlags.EndEllipsis|TextFormatFlags.NoPadding;
        TextRenderer.DrawText(g,heading,titleFont,new Rectangle(17,15,Width-34,25),Aether.Muted,flags);
        var color=tone switch {HealthTone.Good=>Aether.Success,HealthTone.Waiting=>Aether.Amber,HealthTone.Error=>Aether.Error,HealthTone.Ready=>Aether.Primary,_=>Color.FromArgb(115,118,134)};
        using var point=new SolidBrush(color);g.FillEllipse(point,17,55,8,8);
        TextRenderer.DrawText(g,detail,valueFont,new Rectangle(32,48,Width-46,Height-54),Aether.Text,flags);
    }
}

/** Keeps native input/keyboard/accessibility behavior inside a painted frame. */
internal sealed class AetherInputBox : Panel {
    private readonly System.Windows.Forms.Control input;
    public AetherInputBox(System.Windows.Forms.Control field) {
        input=field;DoubleBuffered=true;BackColor=Aether.Surface;Height=42;MinimumSize=new Size(80,42);
        Padding=new Padding(10,7,10,6);field.Dock=DockStyle.Fill;field.BackColor=Aether.Surface;field.ForeColor=Aether.Text;
        field.Font=new Font("Segoe UI",10.5f);if(field is TextBox text)text.BorderStyle=BorderStyle.None;
        if(field is ComboBox combo)combo.FlatStyle=FlatStyle.Flat;
        field.GotFocus+=(_,_)=>Invalidate();field.LostFocus+=(_,_)=>Invalidate();Controls.Add(field);
    }
    protected override void OnPaint(PaintEventArgs e) {
        base.OnPaint(e);e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
        if(Width<3 || Height<3)return;
        using var path=Aether.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),8);
        using var pen=new Pen(input.ContainsFocus?Aether.Primary:Aether.Border,input.ContainsFocus?1.5f:1f);e.Graphics.DrawPath(pen,path);
    }
}
