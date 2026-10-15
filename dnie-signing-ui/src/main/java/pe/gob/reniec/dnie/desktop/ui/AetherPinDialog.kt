package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** PIN is never stored as a String in application state. Returned array belongs to the controller caller. */
internal class AetherPinDialog(owner: JFrame, count: Int) : JDialog(owner, "Autorizar firma con DNIe", true) {
    private val password = JPasswordField(12).apply { name="signing-pin";font=Font("Segoe UI",Font.PLAIN,18);preferredSize=Dimension(300,46) }
    private val masked = password.echoChar
    private val confirm = AetherButton("Firmar ahora",ButtonVariant.PRIMARY).apply { name="confirm-pin";isEnabled=false }
    private var result: CharArray? = null
    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        contentPane = JPanel(BorderLayout()).apply { background=AetherSwingTheme.background;border=EmptyBorder(20,20,20,20) }
        val card=AetherCardPanel().apply { layout=BoxLayout(this,BoxLayout.Y_AXIS) }
        card.add(JLabel("Autorizar firma digital",AetherShieldIcon(),SwingConstants.LEFT).apply { font=Font("Segoe UI",Font.BOLD,18);alignmentX=Component.LEFT_ALIGNMENT })
        card.add(Box.createVerticalStrut(10))
        card.add(JLabel("Se firmarán $count documento(s) con tu DNIe en Android.").apply { alignmentX=Component.LEFT_ALIGNMENT })
        card.add(Box.createVerticalStrut(18))
        card.add(JLabel("PIN de firma (4 a 8 dígitos)").apply { labelFor=password;alignmentX=Component.LEFT_ALIGNMENT })
        password.border=object : EmptyBorder(10,12,10,12) {
            override fun paintBorder(c: Component,g: Graphics,x: Int,y: Int,w: Int,h: Int) {
                val copy=g.create() as Graphics2D
                try {copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);copy.stroke=BasicStroke(if(c.hasFocus())2f else 1f);copy.color=if(c.hasFocus())AetherSwingTheme.primary else AetherSwingTheme.border;copy.drawRoundRect(x+1,y+1,w-3,h-3,16,16)}finally{copy.dispose()}
            }
        }
        password.background=AetherSwingTheme.surface;password.foreground=AetherSwingTheme.text;password.caretColor=AetherSwingTheme.primary
        password.alignmentX=Component.LEFT_ALIGNMENT;password.maximumSize=Dimension(Int.MAX_VALUE,46);card.add(password)
        val reveal=AetherToggleChip("Mostrar PIN").apply { name="reveal-pin";alignmentX=Component.LEFT_ALIGNMENT;addActionListener { password.echoChar=if(isSelected) 0.toChar() else masked } }
        card.add(reveal)
        card.add(JLabel("<html>Los intentos incorrectos pueden bloquear el PIN.<br>Mantén el DNI junto al teléfono hasta ver el resultado.</html>").apply { foreground=AetherSwingTheme.muted;alignmentX=Component.LEFT_ALIGNMENT })
        val footer=JPanel(FlowLayout(FlowLayout.RIGHT)).apply { isOpaque=false;alignmentX=Component.LEFT_ALIGNMENT }
        footer.add(AetherButton("Cancelar",ButtonVariant.GHOST).apply { name="cancel-pin";addActionListener { cancel() } });footer.add(confirm);card.add(Box.createVerticalStrut(12));card.add(footer)
        add(card)
        password.document.addDocumentListener(object: DocumentListener {
            fun update(){val p=password.password;try{confirm.isEnabled=p.size in 4..8 && p.all {it in '0'..'9'}}finally{p.fill('\u0000')}}
            override fun insertUpdate(e: DocumentEvent)=update();override fun removeUpdate(e: DocumentEvent)=update();override fun changedUpdate(e: DocumentEvent)=update()
        })
        confirm.addActionListener {
            val pin=password.password
            if(pin.size in 4..8 && pin.all {it in '0'..'9'}) { result=pin;password.text="";password.echoChar=masked;isVisible=false }
            else pin.fill('\u0000')
        }
        rootPane.defaultButton=confirm
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel-pin")
        rootPane.actionMap.put("cancel-pin",object: AbstractAction(){override fun actionPerformed(e: ActionEvent)=cancel()})
        addWindowListener(object: WindowAdapter(){override fun windowClosing(e: WindowEvent)=cancel();override fun windowOpened(e: WindowEvent){password.requestFocusInWindow()}})
        pack();isResizable=false;setLocationRelativeTo(owner)
    }
    fun requestPin(): CharArray? { try {isVisible=true;return result.also {result=null}} finally {dispose()} }
    private fun cancel(){result?.fill('\u0000');result=null;password.text="";password.echoChar=masked;isVisible=false}
    override fun dispose(){result?.fill('\u0000');result=null;password.text="";password.echoChar=masked;super.dispose()}
}

internal class AetherShieldIcon : Icon {
    override fun getIconWidth()=44
    override fun getIconHeight()=44
    override fun paintIcon(c: Component,graphics: Graphics,x: Int,y: Int) {
        val g=graphics.create() as Graphics2D
        try{g.translate(x,y);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.color=AetherSwingTheme.primaryContainer;g.fillRoundRect(0,0,42,42,16,16);g.color=AetherSwingTheme.primary;g.stroke=BasicStroke(2f);g.drawPolygon(intArrayOf(22,33,31,22,13,11),intArrayOf(8,13,27,35,27,13),6);g.drawLine(17,21,21,25);g.drawLine(21,25,28,17)}finally{g.dispose()}
    }
}
