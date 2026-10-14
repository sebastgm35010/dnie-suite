package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import javax.swing.*
import javax.swing.plaf.basic.*

private fun frame(g: Graphics,c: JComponent) {
    val copy=g.create() as Graphics2D
    try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
        copy.color=AetherSwingTheme.surface;copy.fillRoundRect(1,1,c.width-2,c.height-2,10,10)
        copy.color=if(c.hasFocus() || c is JComboBox<*> && c.isPopupVisible) AetherSwingTheme.primary else AetherSwingTheme.border
        copy.drawRoundRect(1,1,c.width-3,c.height-3,10,10)
    } finally { copy.dispose() }
}
private fun arrow(direction: Int)=object: JButton() {
    init { isOpaque=false;isContentAreaFilled=false;isBorderPainted=false;isFocusable=false;preferredSize=Dimension(26,16) }
    override fun paintComponent(graphics: Graphics) {
        val g=graphics.create() as Graphics2D
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=if(isEnabled) AetherSwingTheme.muted else AetherSwingTheme.outline;g.stroke=BasicStroke(1.6f)
            val x=width/2;val y=height/2;g.drawPolyline(intArrayOf(x-4,x,x+4),intArrayOf(y-direction*2,y+direction*2,y-direction*2),3)
        } finally { g.dispose() }
    }
}
class AetherComboBox<T>(values: Array<T>) : JComboBox<T>(values) {
    init { isOpaque=false;border=BorderFactory.createEmptyBorder(4,7,4,3);minimumSize=Dimension(80,34) }
    override fun updateUI() { setUI(object: BasicComboBoxUI() {
        override fun createArrowButton(): JButton=arrow(1)
        override fun paint(g: Graphics,c: JComponent) { frame(g,c);super.paint(g,c) }
        override fun paintCurrentValueBackground(g: Graphics,bounds: Rectangle,hasFocus: Boolean) {}
    }) }
}
class AetherSpinner(model: SpinnerModel) : JSpinner(model) {
    init { isOpaque=false;border=BorderFactory.createEmptyBorder(3,6,3,3)
        (editor as? DefaultEditor)?.textField?.apply { border=BorderFactory.createEmptyBorder();isOpaque=false;foreground=AetherSwingTheme.text } }
    override fun updateUI() { setUI(object: BasicSpinnerUI() {
        override fun createNextButton(): Component=arrow(-1).also { installNextButtonListeners(it) }
        override fun createPreviousButton(): Component=arrow(1).also { installPreviousButtonListeners(it) }
        override fun paint(g: Graphics,c: JComponent) { frame(g,c) }
    }) }
}
class AetherScrollBarUI : BasicScrollBarUI() {
    override fun createDecreaseButton(orientation: Int)=JButton().apply { preferredSize=Dimension(0,0);minimumSize=Dimension(0,0);maximumSize=Dimension(0,0) }
    override fun createIncreaseButton(orientation: Int)=createDecreaseButton(orientation)
    override fun getPreferredSize(c: JComponent)=if(scrollbar.orientation==JScrollBar.VERTICAL) Dimension(8,48) else Dimension(48,8)
    override fun paintTrack(g: Graphics,c: JComponent,r: Rectangle) { g.color=c.parent?.background?:AetherSwingTheme.background;g.fillRect(r.x,r.y,r.width,r.height) }
    override fun paintThumb(graphics: Graphics,c: JComponent,r: Rectangle) {
        if(r.isEmpty || !c.isEnabled)return
        val g=graphics.create() as Graphics2D
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=if(isThumbRollover) AetherSwingTheme.outline else Color(0xCBDBF5);g.fillRoundRect(r.x+1,r.y+1,(r.width-2).coerceAtLeast(2),(r.height-2).coerceAtLeast(2),6,6)
        } finally { g.dispose() }
    }
}
fun <T: JScrollPane> T.aetherScroll(): T=apply {
    border=BorderFactory.createEmptyBorder();verticalScrollBar.setUI(AetherScrollBarUI());horizontalScrollBar.setUI(AetherScrollBarUI())
    verticalScrollBar.unitIncrement=24;horizontalScrollBar.unitIncrement=24
}
