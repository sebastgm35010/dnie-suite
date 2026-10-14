package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.pdf.StampPlacementGeometry.Handle

/** Pointer events only; PDF adapter supplies page and stamp images. */
class PreviewCanvas : JPanel() {
    var image: BufferedImage?=null
        set(value) { field=value;repaint() }
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
        val img=image?:return null
        val scale=minOf((width-40).toDouble()/img.width,(height-40).toDouble()/img.height).coerceAtLeast(.01)
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
            if(page==null) { g.color=AetherSwingTheme.muted;g.drawString("Selecciona un PDF para colocar tu firma",28,48);return }
            g.color=Color(11,28,48,28);g.fillRoundRect(page.x-2,page.y+4,page.width+4,page.height+4,6,6)
            g.drawImage(image,page.x,page.y,page.width,page.height,null);g.color=Color(0xCBDBF5);g.drawRect(page.x,page.y,page.width,page.height)
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
