using System.Drawing.Drawing2D;
namespace DniBridge.Manager;

internal static class Aether {
    public static bool Dark {get;private set;}
    public static Color Primary=>C(0x004AC6,0x6B9EFF);
    public static Color PrimaryText=>C(0xFFFFFF,0x00174B);
    public static Color Outline=>C(0x737686,0x8D94A6);
    private static Color C(int light,int dark)=>Color.FromArgb(unchecked((int)0xFF000000)|(Dark?dark:light));
    public static Color Background=>C(0xF8F9FF,0x0B1326);
    public static Color Surface=>C(0xFFFFFF,0x131B2E);
    public static Color Container=>C(0xEFF4FF,0x1D2840);
    public static Color Border=>C(0xDCE9FF,0x2E3C56);
    public static Color Text=>C(0x0B1C30,0xEAF1FF);
    public static Color Muted=>C(0x434655,0xA9B5CB);
    public static Color Success=>C(0x006A63,0x67D9C8);
    public static Color Error=>C(0xBA1A1A,0xFFB4AB);
    public static Color Amber=>C(0xD97706,0xFBBF24);
    public static Color SecondaryFill=>C(0xE5EEFF,0x243554);
    public static Color SecondaryText=>C(0x00174B,0xEAF1FF);
    public static void UpdateThemeButton(Button button){button.Text=Dark?"Modo claro":"Modo oscuro";button.AccessibleName=button.Text;
        if(button is AetherButton a){a.ThemeAction=true;button.Padding=new Padding(34,7,12,7);}button.Invalidate();}
    private static Color[] Palette()=>[Primary,Background,Surface,Container,Border,Text,Muted,Success,Error,Amber,SecondaryFill,SecondaryText];
    public static void Apply(System.Windows.Forms.Control root,bool dark) {
        var before=Palette();Dark=dark;var after=Palette();
        void Refresh(System.Windows.Forms.Control c) {
            int background=Array.IndexOf(before,c.BackColor),foreground=Array.IndexOf(before,c.ForeColor);
            if(background>=0)c.BackColor=after[background];if(foreground>=0)c.ForeColor=after[foreground];
            if(c is AetherButton b && (b.PrimaryAction || b.IsSelectedSegment)){b.BackColor=Primary;b.ForeColor=PrimaryText;}
            if(c is ComboBox or TextBoxBase or ListBox) {c.BackColor=Surface;c.ForeColor=Text;}
            foreach(System.Windows.Forms.Control child in c.Controls)Refresh(child);c.Invalidate();
        }
        Refresh(root);
    }
    public static GraphicsPath Rounded(RectangleF r,float radius) {
        float d=Math.Min(radius*2,Math.Min(r.Width,r.Height));var path=new GraphicsPath();
        path.AddArc(r.X,r.Y,d,d,180,90);path.AddArc(r.Right-d,r.Y,d,d,270,90);
        path.AddArc(r.Right-d,r.Bottom-d,d,d,0,90);path.AddArc(r.X,r.Bottom-d,d,d,90,90);path.CloseFigure();return path;
    }
    public static void Style(Button button,bool primary=false) {
        if(button is AetherButton a)a.PrimaryAction=primary;button.MinimumSize=new Size(36,36);
        button.FlatStyle=FlatStyle.Flat;button.FlatAppearance.BorderSize=0;
        button.BackColor=primary?Primary:SecondaryFill;button.ForeColor=primary?PrimaryText:SecondaryText;
        button.Font=new Font("Segoe UI",10,FontStyle.Bold);button.Padding=new Padding(12,7,12,7);button.AutoSize=true;button.Cursor=Cursors.Hand;
    }
    public static Color SurfaceBehind(System.Windows.Forms.Control control) {
        for(var parent=control.Parent;parent!=null;parent=parent.Parent)
            if(parent.BackColor.A==255)return parent.BackColor;
        return Background;
    }
}
internal sealed class AetherButton : Button {
    public bool PrimaryAction {get;set;}
    public bool ThemeAction {get;set;}
    private float hoverAmount,hoverFrom,hoverTarget;
    private long hoverStarted;
    private readonly System.Windows.Forms.Timer hoverTimer=new(){Interval=16};
    private void HoverTo(float target){hoverTarget=target;hoverFrom=hoverAmount;hoverStarted=System.Diagnostics.Stopwatch.GetTimestamp();
        if(!Visible || !Enabled || !SystemInformation.IsMenuAnimationEnabled){hoverAmount=target;hoverTimer.Stop();Invalidate();return;}hoverTimer.Start();}
    internal bool HoverAnimating=>hoverTimer.Enabled;
    protected override void Dispose(bool disposing){if(disposing){hoverTimer.Stop();hoverTimer.Dispose();}base.Dispose(disposing);}
    protected override void OnVisibleChanged(EventArgs e){base.OnVisibleChanged(e);if(!Visible){hoverTimer.Stop();hoverAmount=hoverTarget;}}
    protected override void OnEnabledChanged(EventArgs e){base.OnEnabledChanged(e);if(!Enabled)HoverTo(0);}

    private bool selectedSegment;
    public bool IsSelectedSegment {get=>selectedSegment;set{if(selectedSegment==value)return;selectedSegment=value;Invalidate();}}
    public AetherButton(){FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;UseVisualStyleBackColor=false;
        hoverTimer.Tick+=(_,_)=>{float t=Math.Clamp((float)System.Diagnostics.Stopwatch.GetElapsedTime(hoverStarted).TotalMilliseconds/140f,0,1);float ease=t*t*(3-2*t);hoverAmount=hoverFrom+(hoverTarget-hoverFrom)*ease;Invalidate();if(t>=1)hoverTimer.Stop();};
        SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);}
    protected override void OnMouseEnter(EventArgs e){HoverTo(1);base.OnMouseEnter(e);}
    protected override void OnMouseLeave(EventArgs e){HoverTo(0);base.OnMouseLeave(e);}
    protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Aether.SurfaceBehind(this));}
    protected override void OnPaint(PaintEventArgs e){
        var g=e.Graphics;g.Clear(Aether.SurfaceBehind(this));g.SmoothingMode=SmoothingMode.AntiAlias;g.PixelOffsetMode=PixelOffsetMode.HighQuality;
        if(Width<3 || Height<3)return;
        using var path=Aether.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),7);
        var color=Enabled||IsSelectedSegment?BackColor:Aether.Container;if(Enabled){var light=ControlPaint.Light(color,.1f);color=Color.FromArgb((int)(color.R+(light.R-color.R)*hoverAmount),(int)(color.G+(light.G-color.G)*hoverAmount),(int)(color.B+(light.B-color.B)*hoverAmount));}
        using var fill=new SolidBrush(color);g.FillPath(fill,path);
        if(PrimaryAction && Enabled && hoverAmount>0){using var ring=new Pen(Color.FromArgb((int)(100*hoverAmount),Aether.PrimaryText),1.2f);g.DrawPath(ring,path);}
        if(Focused){using var border=new Pen(Aether.Primary,1.5f);g.DrawPath(border,path);}
        if(ThemeAction){using var icon=new Pen(Aether.Primary,1.5f);float y=Height/2f-7;
            if(Aether.Dark){g.DrawEllipse(icon,13,y+3,8,8);g.DrawLine(icon,17,y-1,17,y+1);g.DrawLine(icon,17,y+13,17,y+15);g.DrawLine(icon,9,y+7,11,y+7);g.DrawLine(icon,23,y+7,25,y+7);}
            else {g.DrawArc(icon,11,y,13,14,70,260);g.DrawArc(icon,16,y-2,10,12,150,160);}}
        int inset=ThemeAction?32:4;
        TextRenderer.DrawText(g,Text,Font,new Rectangle(inset,3,Width-inset-4,Height-6),Enabled||IsSelectedSegment?ForeColor:Aether.Outline,TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis|TextFormatFlags.NoPrefix);
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
        var color=tone switch {HealthTone.Good=>Aether.Success,HealthTone.Waiting=>Aether.Amber,HealthTone.Error=>Aether.Error,HealthTone.Ready=>Aether.Primary,_=>Aether.Border};
        using(var accent=new SolidBrush(color))g.FillRectangle(accent,14,1,Width-28,3);
        using(var medal=Aether.Rounded(new RectangleF(15,17,34,34),10))using(var tint=new SolidBrush(Color.FromArgb(28,color)))g.FillPath(tint,medal);
        using(var icon=new Pen(tone==HealthTone.Idle?Aether.Muted:color,1.5f)){
            if(heading.StartsWith("Servicio"))g.DrawPolygon(icon,new[]{new Point(32,23),new Point(41,27),new Point(39,39),new Point(32,46),new Point(25,39),new Point(23,27)});
            else if(heading.StartsWith("Lector")){g.DrawRectangle(icon,23,26,19,15);g.DrawLine(icon,26,31,39,31);g.DrawLine(icon,27,45,38,45);}
            else {g.DrawRectangle(icon,22,27,21,15);g.DrawRectangle(icon,26,31,6,7);g.DrawLine(icon,35,32,40,32);}
        }
        TextRenderer.DrawText(g,heading,titleFont,new Rectangle(58,20,Width-72,28),Aether.Muted,flags);
        using(var pill=Aether.Rounded(new RectangleF(15,61,Width-30,Height-74),8))using(var tint=new SolidBrush(Color.FromArgb(18,color)))g.FillPath(tint,pill);
        TextRenderer.DrawText(g,detail,valueFont,new Rectangle(24,65,Width-48,Height-78),Aether.Text,flags);

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
