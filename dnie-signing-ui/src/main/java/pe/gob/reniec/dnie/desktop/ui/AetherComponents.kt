package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.nio.file.Path
import javax.swing.*
import javax.swing.border.EmptyBorder
import pe.gob.reniec.dnie.pdf.StampColorTheme

class AetherCardPanel(layout: LayoutManager=BorderLayout(8,8)) : JPanel(layout) {
    private val selectAnim = AetherSwingAnimator(this)
    var selected = false
        set(value) {
            if (field != value) {
                field = value
                selectAnim.to(if (value) 1f else 0f)
            }
        }
    override fun removeNotify() { selectAnim.stop(); super.removeNotify() }
    init { isOpaque = false; border = EmptyBorder(16,16,16,16) }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val sel = selectAnim.value
            val targetBg = AetherSwingAnimator.blend(AetherSwingTheme.surface, AetherSwingTheme.primaryContainer, 0.55f)
            val bg = AetherSwingAnimator.blend(AetherSwingTheme.surface, targetBg, sel)
            g.color = bg
            g.fill(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 24f, 24f))
            val topAlpha = if (AetherSwingTheme.dark) (15 + 15 * sel).toInt() else (110 - 30 * sel).toInt()
            g.color = Color(255, 255, 255, topAlpha)
            g.stroke = BasicStroke(1f)
            g.draw(RoundRectangle2D.Float(1.5f, 1.5f, width - 3f, height - 3f, 22f, 22f))
            val borderColor = AetherSwingAnimator.blend(AetherSwingTheme.border, AetherSwingTheme.primary, sel)
            g.color = borderColor
            g.stroke = BasicStroke(if (sel > 0f) 1f + 0.5f * sel else 1f)
            g.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 24f, 24f))
            if (sel > 0f) {
                val alpha = (255 * sel).toInt().coerceIn(0, 255)
                g.color = Color(AetherSwingTheme.primary.red, AetherSwingTheme.primary.green, AetherSwingTheme.primary.blue, alpha)
                g.fillRoundRect(3, 14, 4, (height - 28).coerceAtLeast(4), 4, 4)
            }
        } finally { g.dispose() }
        super.paintComponent(graphics)
    }
}

enum class ButtonVariant { PRIMARY, SECONDARY, OUTLINE, GHOST }

/** Rounded editable field; it keeps JTextField semantics for automation and accessibility. */
class AetherTextField(text: String = "", columns: Int = 0) : JTextField(text, columns) {
    private val focus = AetherSwingAnimator(this)
    init {
        isOpaque = false; border = EmptyBorder(8,11,8,11); background = AetherSwingTheme.surface
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) { focus.to(1f) }
            override fun focusLost(e: java.awt.event.FocusEvent?) { focus.to(0f) }
        })
    }
    override fun removeNotify() { focus.stop(); super.removeNotify() }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (focus.value > 0f) {
                val haloAlpha = (50 * focus.value).toInt().coerceIn(0, 255)
                g.color = Color(AetherSwingTheme.primary.red, AetherSwingTheme.primary.green, AetherSwingTheme.primary.blue, haloAlpha)
                g.stroke = BasicStroke(2.5f)
                g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
            }
            g.color = AetherSwingTheme.surface; g.fillRoundRect(0, 0, width - 1, height - 1, 10, 10)
            val accent = AetherSwingAnimator.blend(AetherSwingTheme.border, AetherSwingTheme.primary, focus.value)
            g.color = accent; g.stroke = BasicStroke(1f + focus.value); g.drawRoundRect(1, 1, width - 3, height - 3, 10, 10)
        } finally { g.dispose() }
        super.paintComponent(graphics)
    }
}

open class AetherButton(text: String, private val variant: ButtonVariant = ButtonVariant.SECONDARY) : JButton(text) {
    private val hover = AetherSwingAnimator(this, durationNanos = 140_000_000L)
    private val press = AetherSwingAnimator(this, durationNanos = 90_000_000L)
    private val focus = AetherSwingAnimator(this, durationNanos = 140_000_000L)
    override fun removeNotify() { hover.stop(); press.stop(); focus.stop(); super.removeNotify() }
    init {
        model.addChangeListener {
            hover.to(if (isEnabled && model.isRollover && !model.isPressed) 1f else 0f)
            press.to(if (isEnabled && model.isPressed) 1f else 0f)
        }
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) { focus.to(1f) }
            override fun focusLost(e: java.awt.event.FocusEvent?) { focus.to(0f) }
        })
        isRolloverEnabled = true; isContentAreaFilled = false; isBorderPainted = false; isFocusPainted = false; isOpaque = false
        border = EmptyBorder(10,14,10,14); font = Font("Segoe UI", Font.BOLD, 13); cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (focus.value > 0f) {
                val haloAlpha = (55 * focus.value).toInt().coerceIn(0, 255)
                g.color = Color(AetherSwingTheme.primary.red, AetherSwingTheme.primary.green, AetherSwingTheme.primary.blue, haloAlpha)
                g.stroke = BasicStroke(2.5f)
                g.drawRoundRect(0, 0, width - 1, height - 1, 14, 14)
            }
            val scale = press.pressScale(0.985f)
            if (scale < 1f) {
                val cx = width / 2.0
                val cy = height / 2.0
                g.translate(cx, cy)
                g.scale(scale.toDouble(), scale.toDouble())
                g.translate(-cx, -cy)
            }
            val base = when {
                !isEnabled -> AetherSwingTheme.container
                variant == ButtonVariant.PRIMARY -> AetherSwingTheme.primary
                variant == ButtonVariant.SECONDARY -> AetherSwingTheme.primaryContainer
                else -> AetherSwingTheme.surface
            }
            val highlight = when (variant) {
                ButtonVariant.PRIMARY -> if (AetherSwingTheme.dark) AetherSwingTheme.primary.brighter() else Color(0x003A9B)
                ButtonVariant.SECONDARY -> if (AetherSwingTheme.dark) AetherSwingTheme.primaryContainer.brighter() else Color(0xD0E1FF)
                else -> AetherSwingTheme.container
            }
            var tone = AetherSwingAnimator.blend(base, highlight, if (isEnabled) hover.value else 0f)
            if (press.value > 0f && isEnabled) {
                val pressedTarget = when (variant) {
                    ButtonVariant.PRIMARY -> if (AetherSwingTheme.dark) Color(0x4F83E6) else Color(0x002B7A)
                    ButtonVariant.SECONDARY -> if (AetherSwingTheme.dark) Color(0x16223B) else Color(0xB8D4FF)
                    else -> if (AetherSwingTheme.dark) Color(0x1A253C) else Color(0xD8E5FF)
                }
                tone = AetherSwingAnimator.blend(tone, pressedTarget, press.value * 0.65f)
            }
            g.color = tone
            g.fillRoundRect(1, 1, width - 2, height - 2, 12, 12)
            val topAlpha = if (AetherSwingTheme.dark) 18 else 90
            g.color = Color(255, 255, 255, topAlpha)
            g.drawLine(6, 2, width - 6, 2)
            if (variant == ButtonVariant.OUTLINE || focus.value > 0f) {
                val borderAccent = AetherSwingAnimator.blend(AetherSwingTheme.border, AetherSwingTheme.primary, focus.value)
                g.color = borderAccent
                g.stroke = BasicStroke(1f + 0.5f * focus.value)
                g.drawRoundRect(1, 1, width - 3, height - 3, 12, 12)
            }
        } finally { g.dispose() }
        foreground = if (!isEnabled) AetherSwingTheme.outline else if (variant == ButtonVariant.PRIMARY) AetherSwingTheme.onPrimary else AetherSwingTheme.onPrimaryContainer
        super.paintComponent(graphics)
    }
}

enum class PillState { IDLE, WAITING, CONNECTED, ERROR }
class AetherToggleChip(text: String, selected: Boolean = false) : JCheckBox(text, selected) {
    private val hover = AetherSwingAnimator(this, durationNanos = 140_000_000L)
    private val selectAnim = AetherSwingAnimator(this, durationNanos = 140_000_000L)
    override fun removeNotify() { hover.stop(); selectAnim.stop(); super.removeNotify() }
    init {
        isRolloverEnabled = true
        if (selected) selectAnim.to(1f)
        model.addChangeListener {
            hover.to(if (isEnabled && model.isRollover && !model.isPressed) 1f else 0f)
            selectAnim.to(if (isSelected) 1f else 0f)
        }
        isOpaque = false; isBorderPainted = false; isFocusPainted = false; cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = EmptyBorder(7,12,7,12); font = Font("Segoe UI", Font.PLAIN, 12)
    }
    override fun getPreferredSize() = Dimension(getFontMetrics(font).stringWidth(text) + 34, 36)
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val sel = selectAnim.value
            val unselectedBg = AetherSwingAnimator.blend(AetherSwingTheme.surface, AetherSwingTheme.container, hover.value)
            val bg = AetherSwingAnimator.blend(unselectedBg, AetherSwingTheme.primaryContainer, sel)
            g.color = bg
            g.fillRoundRect(1, 2, width - 2, height - 4, 18, 18)
            val topAlpha = if (AetherSwingTheme.dark) 18 else 100
            g.color = Color(255, 255, 255, topAlpha)
            g.drawLine(10, 3, width - 10, 3)
            val borderColor = AetherSwingAnimator.blend(AetherSwingTheme.border, AetherSwingTheme.primary, if (hasFocus()) 1f else sel)
            g.color = borderColor
            g.stroke = BasicStroke(if (sel > 0f || hasFocus()) 1.4f else 1f)
            g.drawRoundRect(1, 2, width - 3, height - 5, 18, 18)
            if (sel > 0f) {
                val dotAlpha = (255 * sel).toInt().coerceIn(0, 255)
                g.color = Color(AetherSwingTheme.primary.red, AetherSwingTheme.primary.green, AetherSwingTheme.primary.blue, dotAlpha)
                g.fillOval(10, height / 2 - 2, 4, 4)
            }
            val fg = when {
                !isEnabled -> AetherSwingTheme.outline
                sel > 0f -> AetherSwingAnimator.blend(AetherSwingTheme.muted, AetherSwingTheme.onPrimaryContainer, sel)
                else -> AetherSwingTheme.muted
            }
            g.color = fg
            g.font = font
            val metrics = g.fontMetrics
            val xText = (12 + (8 * sel)).toInt()
            g.drawString(text, xText, (height - metrics.height) / 2 + metrics.ascent)
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
        private val hover=AetherSwingAnimator(this)
        override fun removeNotify(){hover.stop();super.removeNotify()}
        init { isRolloverEnabled=true;model.addChangeListener {hover.to(if(isEnabled && model.isRollover)1f else 0f)};preferredSize=Dimension(36,36);isContentAreaFilled=false;isBorderPainted=false;toolTipText=theme.label;getAccessibleContext().accessibleName=theme.label
            addActionListener { selected=theme;changed(theme) } }
        override fun paintComponent(graphics: Graphics) {
            val g=graphics.create() as Graphics2D
            try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                g.color=AetherSwingAnimator.blend(AetherSwingTheme.surface,AetherSwingTheme.primaryContainer,hover.value);g.fillOval(1,1,width-2,height-2)
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
    private val badge=AetherDocumentPill()
    private val statePill=AetherDocumentPill(success=true)
    private val nameLabel=JLabel().apply { font=font.deriveFont(Font.BOLD);alignmentX=Component.LEFT_ALIGNMENT;maximumSize=Dimension(Int.MAX_VALUE,24) }
    private val metadata=JLabel().apply { foreground=AetherSwingTheme.muted;font=font.deriveFont(11f);alignmentX=Component.LEFT_ALIGNMENT;maximumSize=Dimension(Int.MAX_VALUE,24) }
    private val normal=EmptyBorder(12,10,12,10)
    init {
        val content=JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS);add(nameLabel);add(Box.createVerticalStrut(5));add(metadata) }
        panel.add(badge,BorderLayout.WEST);panel.add(content,BorderLayout.CENTER);panel.add(statePill,BorderLayout.EAST)
    }
    override fun getListCellRendererComponent(list: JList<out Path>,value: Path,index: Int,selected: Boolean,focus: Boolean): Component {
        val pdf=value.fileName.toString().endsWith(".pdf",true)
        badge.text=if(pdf) "PDF" else "DOC";badge.foreground=if(pdf) AetherSwingTheme.primary else AetherSwingTheme.outline
        nameLabel.text=value.fileName.toString();nameLabel.toolTipText=value.toString();nameLabel.foreground=if(selected) AetherSwingTheme.primary else AetherSwingTheme.text
        val size=sizeOf(value)?.let { if(it>=1024*1024) String.format(java.util.Locale.ROOT,"%.1f MiB",it/1048576.0) else String.format(java.util.Locale.ROOT,"%.1f KiB",it/1024.0) } ?: ""
        val status=statusOf(value);val signed=status.equals("Firmado",true) || status.startsWith("Firmado",true)
        metadata.text=listOf(size,if(signed) "" else status).filter(String::isNotEmpty).joinToString(" · ")
        statePill.text="Firmado";statePill.isVisible=signed
        panel.selected=selected;panel.border=normal
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
