package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.pdf.StampPlacementGeometry.Handle

/** Pointer events only; PDF adapter supplies page and stamp images. */
class PreviewCanvas : JPanel(), Scrollable {
    var dropHighlight=false
        set(value) {field=value;repaint()}
    var zoom=1.0
        set(value) { field=value.coerceIn(.5,3.0);revalidate();repaint() }
    private fun extent()=(parent as? JViewport)?.extentSize?.takeIf { it.width>40 && it.height>40 }?:size.takeIf { it.width>40 && it.height>40 }?:Dimension(650,720)
    private fun pageSize()=image?.let { Dimension(it.width,it.height) }?:Dimension(595,842)
    override fun getPreferredSize(): Dimension {
        val view=extent();val page=pageSize();val fit=minOf((view.width-40).toDouble()/page.width,(view.height-40).toDouble()/page.height).coerceAtLeast(.01)
        return Dimension(maxOf(view.width,(page.width*fit*zoom+40).toInt()),maxOf(view.height,(page.height*fit*zoom+40).toInt()))
    }
    override fun getPreferredScrollableViewportSize()=Dimension(650,720)
    override fun getScrollableTracksViewportWidth()=zoom<=1.0
    override fun getScrollableTracksViewportHeight()=zoom<=1.0
    override fun getScrollableUnitIncrement(r: Rectangle,orientation: Int,direction: Int)=24
    override fun getScrollableBlockIncrement(r: Rectangle,orientation: Int,direction: Int)=if(orientation==SwingConstants.VERTICAL) (r.height-24).coerceAtLeast(24) else (r.width-24).coerceAtLeast(24)
    var image: BufferedImage?=null
        set(value) { field?.takeIf {it!==value}?.flush();field=value;revalidate();repaint() }
    var stampImage: BufferedImage?=null
        set(value) { field?.takeIf { it!==value }?.flush();field=value;repaint() }
    var stampError: String?=null
        set(value) { field=value;repaint() }
    var placement=NormalizedStampPlacement(.36f,.80f,.60f,.16f)
        set(value) { val sizeChanged=field.width!=value.width || field.height!=value.height;field=value;repaint();if(sizeChanged) resized() }
    var visibleStamp=true
        set(value) { field=value;repaint() }
    var interactive=true
    var changed: (NormalizedStampPlacement)->Unit={}
    var resized: ()->Unit={}
    private var start: Point?=null
    private var origin=placement
    private var handle=Handle.MOVE
    private var snap: StampPlacementGeometry.Snap?=null
    init {
        background=AetherSwingTheme.container;preferredSize=Dimension(650,720)
        val mouse=object: MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {if(dropHighlight)dropHighlight=false}
            override fun mousePressed(e: MouseEvent) {
                if(!interactive || !visibleStamp) return
                val page=pageBounds()?:return;if(!page.contains(e.point)) return
                handle=hit(e.point)
                val x=(e.x-page.x).toFloat()/page.width;val y=(e.y-page.y).toFloat()/page.height
                if(handle==Handle.MOVE && !stampBounds(page).contains(e.point)) placement=placement.moved(x-placement.width/2,y-placement.height/2)
                start=e.point;origin=placement;changed(placement)
            }
            override fun mouseDragged(e: MouseEvent) {
                if(!interactive) return
                val point=start?:return;val page=pageBounds()?:return
                val next=StampPlacementGeometry.resize(origin,(e.x-point.x).toFloat()/page.width,(e.y-point.y).toFloat()/page.height,handle)
                snap=if(handle==Handle.MOVE) StampPlacementGeometry.snap(next) else null
                placement=snap?.placement?:next;changed(placement)
            }
            override fun mouseReleased(e: MouseEvent) { start=null;snap=null;repaint() }
            override fun mouseMoved(e: MouseEvent) {
                cursor=Cursor.getPredefinedCursor(if(!interactive || !visibleStamp) Cursor.DEFAULT_CURSOR else when(hit(e.point)) {
                    Handle.N,Handle.S->Cursor.N_RESIZE_CURSOR;Handle.E,Handle.W->Cursor.E_RESIZE_CURSOR
                    Handle.NE,Handle.SW->Cursor.NE_RESIZE_CURSOR;Handle.NW,Handle.SE->Cursor.SE_RESIZE_CURSOR;else->Cursor.MOVE_CURSOR })
            }
        }
        addMouseListener(mouse);addMouseMotionListener(mouse)
    }
    internal fun pageBounds(): Rectangle? {
        if(width<40 || height<40)return null
        val img=pageSize();val view=extent()
        val scale=minOf((view.width-40).toDouble()/img.width,(view.height-40).toDouble()/img.height).coerceAtLeast(.01)*zoom
        val w=(img.width*scale).toInt();val h=(img.height*scale).toInt()
        return Rectangle((width-w)/2,(height-h)/2,w,h)
    }
    private fun stampBounds(page: Rectangle)=Rectangle(page.x+(placement.left*page.width).toInt(),page.y+(placement.top*page.height).toInt(),
        (placement.width*page.width).toInt(),(placement.height*page.height).toInt())
    private fun hit(point: Point): Handle {
        val r=pageBounds()?.let(::stampBounds)?:return Handle.MOVE
        val left=kotlin.math.abs(point.x-r.x)<10;val right=kotlin.math.abs(point.x-r.x-r.width)<10
        val top=kotlin.math.abs(point.y-r.y)<10;val bottom=kotlin.math.abs(point.y-r.y-r.height)<10
        return when { left&&top->Handle.NW;right&&top->Handle.NE;left&&bottom->Handle.SW;right&&bottom->Handle.SE
            point.y in r.y..r.y+r.height && left->Handle.W;point.y in r.y..r.y+r.height && right->Handle.E
            point.x in r.x..r.x+r.width && top->Handle.N;point.x in r.x..r.x+r.width && bottom->Handle.S;else->Handle.MOVE }
    }
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g=graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            val page=pageBounds()
            if(dropHighlight) {g.color=AetherSwingTheme.primary;g.stroke=BasicStroke(3f);g.drawRoundRect(3,3,width-7,height-7,12,12)}
            if(page==null) { g.color=AetherSwingTheme.muted;g.drawString("Selecciona un PDF para colocar tu firma",28,48);return }
            g.color=Color(11,28,48,28);g.fillRoundRect(page.x-2,page.y+4,page.width+4,page.height+4,6,6)
            if(image!=null) g.drawImage(image,page.x,page.y,page.width,page.height,null) else {
                g.color=Color.WHITE;g.fillRect(page.x,page.y,page.width,page.height)
                val margin=(page.width*.10).toInt();val left=page.x+margin;val lineWidth=page.width-2*margin
                g.color=AetherSwingTheme.container
                for(i in 0..9) { val y=page.y+(page.height*(.10+i*.035)).toInt();g.fillRoundRect(left,y,if(i%3==2) (lineWidth*.65).toInt() else lineWidth,4,4,4) }
                g.color=AetherSwingTheme.muted;g.font=font.deriveFont(Font.BOLD,13f)
                val hint="Tu diseño empieza aquí";g.drawString(hint,page.x+(page.width-g.fontMetrics.stringWidth(hint))/2,page.y+page.height/2)
                g.font=font.deriveFont(11f)
                val detail="Añade un PDF para firmar";g.drawString(detail,page.x+(page.width-g.fontMetrics.stringWidth(detail))/2,page.y+page.height/2+20)
            }
            g.color=Color(0xCBDBF5);g.drawRect(page.x,page.y,page.width,page.height)
            if(visibleStamp) {
                val r=stampBounds(page)
                stampImage?.let { g.drawImage(it,r.x,r.y,r.width,r.height,null) }
                g.color=AetherSwingTheme.primary;g.stroke=BasicStroke(1.5f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10f,floatArrayOf(5f,4f),0f)
                g.drawRect(r.x,r.y,r.width,r.height)
                if(stampImage==null) { g.font=font.deriveFont(11f);g.drawString(if(stampError==null) "Preparando sello…" else "Amplía el sello o quita campos",r.x+6,r.y+16) }
                g.stroke=BasicStroke(1.5f)
                for(x in listOf(r.x,r.x+r.width)) for(y in listOf(r.y,r.y+r.height)) { g.color=Color.WHITE;g.fillOval(x-5,y-5,10,10);g.color=AetherSwingTheme.primary;g.drawOval(x-5,y-5,10,10) }
                snap?.vertical?.let { val x=page.x+(it*page.width).toInt();g.drawLine(x,page.y,x,page.y+page.height) }
                snap?.horizontal?.let { val y=page.y+(it*page.height).toInt();g.drawLine(page.x,y,page.x+page.width,y) }
            }
        } finally { g.dispose() }
    }
}
