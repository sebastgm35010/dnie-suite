package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import pe.gob.reniec.dnie.pdf.NormalizedStampPlacement

/** UI geometry only. Page image is supplied by a backend preview adapter. */
class PreviewCanvas : JPanel() {
    var image: BufferedImage?=null
        set(value) { field=value; repaint() }
    var placement=NormalizedStampPlacement(0.36f,0.82f,0.60f,0.14f)
        set(value) { field=value; repaint() }
    var visibleStamp=true
        set(value) { field=value; repaint() }
    var interactive=true
    var changed: (NormalizedStampPlacement)->Unit={}
    private var start: Point?=null
    private var origin=placement
    private var resizing=false
    init {
        background=Color(229,233,239); preferredSize=Dimension(650,720)
        val mouse=object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if(!interactive || !visibleStamp) return
                val rect=pageBounds() ?: return
                if(!rect.contains(e.point)) return
                val x=(e.x-rect.x).toFloat()/rect.width; val y=(e.y-rect.y).toFloat()/rect.height
                resizing=kotlin.math.abs(x-placement.left-placement.width)*rect.width<16 &&
                    kotlin.math.abs(y-placement.top-placement.height)*rect.height<16
                if(!resizing && (x !in placement.left..placement.left+placement.width || y !in placement.top..placement.top+placement.height)) {
                    placement=placement.moved(x-placement.width/2,y-placement.height/2); changed(placement)
                }
                start=e.point; origin=placement
            }
            override fun mouseDragged(e: MouseEvent) {
                val point=start ?: return; val rect=pageBounds() ?: return
                val dx=(e.x-point.x).toFloat()/rect.width; val dy=(e.y-point.y).toFloat()/rect.height
                placement=if(resizing) origin.resizedAnchored(origin.width+dx,origin.height+dy)
                    else origin.moved(origin.left+dx,origin.top+dy)
                changed(placement)
            }
            override fun mouseReleased(e: MouseEvent) { start=null }
        }
        addMouseListener(mouse); addMouseMotionListener(mouse)
    }
    private fun pageBounds(): Rectangle? {
        val img=image ?: return null
        val scale=minOf((width-40).toDouble()/img.width,(height-40).toDouble()/img.height).coerceAtLeast(0.01)
        val w=(img.width*scale).toInt(); val h=(img.height*scale).toInt()
        return Rectangle((width-w)/2,(height-h)/2,w,h)
    }
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g=graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val rect=pageBounds()
            if(rect==null) { g.color=Color.DARK_GRAY; g.drawString("Selecciona un PDF para ver su página",35,55); return }
            g.drawImage(image,rect.x,rect.y,rect.width,rect.height,null)
            if(visibleStamp) {
                val x=rect.x+(placement.left*rect.width).toInt(); val y=rect.y+(placement.top*rect.height).toInt()
                val w=(placement.width*rect.width).toInt(); val h=(placement.height*rect.height).toInt()
                g.color=Color(24,94,190,45); g.fillRect(x,y,w,h); g.color=Color(24,94,190); g.stroke=BasicStroke(2f);g.drawRect(x,y,w,h)
                g.drawString("Área del sello",x+7,y+17);g.fillRect(x+w-6,y+h-6,12,12)
            }
        } finally { g.dispose() }
    }
}
