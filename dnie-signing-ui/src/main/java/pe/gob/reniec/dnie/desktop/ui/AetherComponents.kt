package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.nio.file.Path
import javax.swing.*
import javax.swing.border.EmptyBorder
import pe.gob.reniec.dnie.pdf.StampColorTheme

class AetherCardPanel(layout: LayoutManager=BorderLayout(8,8)) : JPanel(layout) {
    init { isOpaque=false;border=EmptyBorder(16,16,16,16) }
    override fun paintComponent(graphics: Graphics) {
        val g=graphics.create() as Graphics2D
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=AetherSwingTheme.surface;g.fill(RoundRectangle2D.Float(.5f,.5f,width-1f,height-1f,16f,16f))
            g.color=AetherSwingTheme.border;g.draw(RoundRectangle2D.Float(.5f,.5f,width-1f,height-1f,16f,16f))
        } finally { g.dispose() }
        super.paintComponent(graphics)
    }
}

enum class ButtonVariant { PRIMARY, SECONDARY, OUTLINE, GHOST }
class AetherButton(text: String,private val variant: ButtonVariant=ButtonVariant.SECONDARY) : JButton(text) {
    init { isContentAreaFilled=false;isBorderPainted=false;isFocusPainted=false;isOpaque=false
        border=EmptyBorder(10,14,10,14);font=Font("Segoe UI",Font.BOLD,13);cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) }
    override fun paintComponent(graphics: Graphics) {
        val g=graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=when { !isEnabled -> AetherSwingTheme.container
                variant==ButtonVariant.PRIMARY -> if(model.isRollover) (if(AetherSwingTheme.dark) AetherSwingTheme.primary.brighter() else Color(0x003A9B)) else AetherSwingTheme.primary
                variant==ButtonVariant.SECONDARY -> if(model.isRollover) (if(AetherSwingTheme.dark) AetherSwingTheme.primaryContainer.brighter() else Color(0xD0E1FF)) else AetherSwingTheme.primaryContainer
                else -> if(model.isRollover) AetherSwingTheme.container else AetherSwingTheme.surface }
            g.fillRoundRect(1,1,width-2,height-2,12,12)
            if(variant==ButtonVariant.OUTLINE || hasFocus()) { g.color=if(hasFocus()) AetherSwingTheme.primary else AetherSwingTheme.border;g.drawRoundRect(1,1,width-3,height-3,12,12) }
        } finally { g.dispose() }
        foreground=if(!isEnabled) AetherSwingTheme.outline else if(variant==ButtonVariant.PRIMARY) AetherSwingTheme.onPrimary else AetherSwingTheme.onPrimaryContainer
        super.paintComponent(graphics)
    }
}

enum class PillState { IDLE, WAITING, CONNECTED, ERROR }
class AetherToggleChip(text: String,selected: Boolean=false) : JCheckBox(text,selected) {
    init { isOpaque=false;isBorderPainted=false;isFocusPainted=false;cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border=EmptyBorder(7,12,7,12);font=Font("Segoe UI",Font.PLAIN,12) }
    override fun getPreferredSize()=Dimension(getFontMetrics(font).stringWidth(text)+34,36)
    override fun paintComponent(graphics: Graphics) {
        val g=graphics.create() as Graphics2D
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=if(isSelected) AetherSwingTheme.primaryContainer else AetherSwingTheme.surface
            g.fillRoundRect(1,2,width-2,height-4,18,18)
            g.color=if(isSelected || hasFocus()) AetherSwingTheme.primary else AetherSwingTheme.border
            g.drawRoundRect(1,2,width-3,height-5,18,18)
            if(isSelected) g.fillOval(10,height/2-2,4,4)
            g.color=if(!isEnabled) AetherSwingTheme.outline else if(isSelected) AetherSwingTheme.onPrimaryContainer else AetherSwingTheme.muted
            g.font=font;val metrics=g.fontMetrics;g.drawString(text,if(isSelected) 20 else 12,(height-metrics.height)/2+metrics.ascent)
        } finally { g.dispose() }
    }
}

class AetherStatusPill : JLabel("Desconectado") {
    var state=PillState.IDLE
        set(value) { field=value;repaint() }
    init { border=EmptyBorder(9,23,9,12);font=Font("Segoe UI",Font.BOLD,12);isOpaque=false }
    override fun paintComponent(graphics: Graphics) {
        val g=graphics.create() as Graphics2D
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.color=when(state) { PillState.CONNECTED->AetherSwingTheme.secondaryContainer;PillState.WAITING->if(AetherSwingTheme.dark) Color(0x443512) else Color(0xFFF8E1);PillState.ERROR->if(AetherSwingTheme.dark) Color(0x5C2524) else Color(0xFFEDEA);else->AetherSwingTheme.container }
            g.fillRoundRect(0,0,width,height,height,height)
            g.color=when(state) { PillState.CONNECTED->AetherSwingTheme.secondary;PillState.WAITING->if(AetherSwingTheme.dark) Color(0xFBBF24) else Color(0xD97706);PillState.ERROR->AetherSwingTheme.error;else->AetherSwingTheme.outline }
            g.fillOval(10,height/2-3,6,6)
        } finally { g.dispose() }
        foreground=AetherSwingTheme.muted;super.paintComponent(graphics)
    }
}

class ColorSwatchSelector : JPanel(FlowLayout(FlowLayout.LEFT,8,3)) {
    var selected=StampColorTheme.AETHER_BLUE
        set(value) { field=value;repaint() }
    var changed: (StampColorTheme)->Unit={}
    private val choices=StampColorTheme.entries.map { theme -> object: JButton() {
        init { preferredSize=Dimension(36,36);isContentAreaFilled=false;isBorderPainted=false;toolTipText=theme.label;getAccessibleContext().accessibleName=theme.label
            addActionListener { selected=theme;changed(theme) } }
        override fun paintComponent(graphics: Graphics) {
            val g=graphics.create() as Graphics2D
            try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                g.color=Color(theme.accentRgb);g.fillOval(6,6,width-12,height-12)
                if(selected==theme || hasFocus()) { g.stroke=BasicStroke(2f);g.drawOval(2,2,width-5,height-5) }
            } finally { g.dispose() }
        }
    } }
    init { isOpaque=false;choices.forEach(::add) }
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled);components.forEach { it.isEnabled=enabled } }
}

/** Reuses one Swing renderer; no filesystem calls or component allocation while scrolling. */
class DocumentCardCellRenderer(private val sizeOf: (Path)->Long?,private val statusOf: (Path)->String) : ListCellRenderer<Path> {
    private val panel=AetherCardPanel(BorderLayout(8,4))
    private val badge=JLabel().apply { font=font.deriveFont(Font.BOLD,11f) }
    private val nameLabel=JLabel().apply { font=font.deriveFont(Font.BOLD);alignmentX=Component.LEFT_ALIGNMENT;maximumSize=Dimension(Int.MAX_VALUE,24) }
    private val metadata=JLabel().apply { foreground=AetherSwingTheme.muted;font=font.deriveFont(11f);alignmentX=Component.LEFT_ALIGNMENT;maximumSize=Dimension(Int.MAX_VALUE,24) }
    private val normal=EmptyBorder(12,10,12,10)
    private val active=javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(AetherSwingTheme.primary,1),EmptyBorder(11,9,11,9))
    init {
        val content=JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS);add(nameLabel);add(Box.createVerticalStrut(5));add(metadata) }
        panel.add(badge,BorderLayout.WEST);panel.add(content,BorderLayout.CENTER)
    }
    override fun getListCellRendererComponent(list: JList<out Path>,value: Path,index: Int,selected: Boolean,focus: Boolean): Component {
        val pdf=value.fileName.toString().endsWith(".pdf",true)
        badge.text=if(pdf) "PDF" else "DOC";badge.foreground=if(pdf) AetherSwingTheme.primary else AetherSwingTheme.outline
        nameLabel.text=value.fileName.toString();nameLabel.toolTipText=value.toString();nameLabel.foreground=if(selected) AetherSwingTheme.primary else AetherSwingTheme.text
        val size=sizeOf(value)?.let { if(it>=1024*1024) String.format(java.util.Locale.ROOT,"%.1f MiB",it/1048576.0) else String.format(java.util.Locale.ROOT,"%.1f KiB",it/1024.0) } ?: ""
        metadata.text=listOf(size,statusOf(value)).filter(String::isNotEmpty).joinToString(" · ")
        panel.border=if(selected) active else normal
        return panel
    }
}

class AetherVectorIcon(private val direction: Int?=null) : Icon {
    override fun getIconWidth()=20
    override fun getIconHeight()=20
    override fun paintIcon(c: Component,g: Graphics,x: Int,y: Int) {
        val copy=g.create() as Graphics2D
        try { copy.translate(x,y);copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color=if(c.isEnabled) AetherSwingTheme.primary else AetherSwingTheme.outline;copy.stroke=BasicStroke(1.7f)
            if(direction==null) { copy.drawPolygon(intArrayOf(10,18,10,2),intArrayOf(2,10,18,10),4);copy.drawPolygon(intArrayOf(10,14,10,6),intArrayOf(6,10,14,10),4) }
            else {
                val dx=when(direction) {0,2,4->-1;else->1};val dy=when(direction) {0,1->-1;2,3->1;else->0}
                val endX=10+dx*6;val endY=10+dy*6
                copy.drawLine(10-dx*5,10-dy*5,endX,endY)
                if(dy==0) { copy.drawLine(endX,endY,endX-dx*5,endY-5);copy.drawLine(endX,endY,endX-dx*5,endY+5) }
                else { copy.drawLine(endX,endY,endX-dx*6,endY);copy.drawLine(endX,endY,endX,endY-dy*6) }
            }
        } finally { copy.dispose() }
    }
}
