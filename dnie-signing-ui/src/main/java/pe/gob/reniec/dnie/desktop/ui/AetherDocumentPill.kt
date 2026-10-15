package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Reusable renderer label. Paints a pill without installing a square Swing border. */
internal class AetherDocumentPill(private val success: Boolean=false): JLabel() {
    init {font=Font("Segoe UI",Font.BOLD,11);border=EmptyBorder(6,9,6,9);isOpaque=false}
    override fun paintComponent(graphics: Graphics) {
        val g=graphics.create() as Graphics2D
        try {g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.color=if(success)AetherSwingTheme.secondaryContainer else AetherSwingTheme.primaryContainer;g.fillRoundRect(0,(height-26)/2,width,26,16,16)}finally{g.dispose()}
        foreground=if(success)AetherSwingTheme.secondary else AetherSwingTheme.primary;super.paintComponent(graphics)
    }
}

internal class AetherChevronIcon(private val expanded: Boolean): Icon {
    override fun getIconWidth()=20
    override fun getIconHeight()=20
    override fun paintIcon(c: Component,graphics: Graphics,x: Int,y: Int) {
        val g=graphics.create() as Graphics2D
        try{g.translate(x,y);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.color=AetherSwingTheme.primary;g.stroke=BasicStroke(1.7f)
            if(expanded)g.drawPolyline(intArrayOf(5,10,15),intArrayOf(7,12,7),3) else g.drawPolyline(intArrayOf(7,12,7),intArrayOf(5,10,15),3)
        }finally{g.dispose()}
    }
}

internal class AetherDisclosureButton(title: String): AetherButton(title,ButtonVariant.GHOST) {
    private val opened=AetherChevronIcon(true)
    private val closed=AetherChevronIcon(false)
    var expanded=true
        set(value){field=value;toolTipText=if(value)"Plegar sección" else "Desplegar sección";repaint()}
    init {font=Font("Segoe UI",Font.BOLD,14);horizontalAlignment=SwingConstants.LEFT;maximumSize=Dimension(Int.MAX_VALUE,48);border=EmptyBorder(10,12,10,36)}
    override fun paintComponent(g: Graphics){super.paintComponent(g);(if(expanded)opened else closed).paintIcon(this,g,width-28,(height-20)/2)}
}

internal object AetherConfirmDialog {
    fun ask(owner: JFrame,title: String,message: String,action: String): Boolean {
        var accepted=false
        val dialog=JDialog(owner,title,true)
        val content=AetherCardPanel(BorderLayout(12,18))
        content.add(JLabel("<html><body style='width:320px'>$message</body></html>"),BorderLayout.CENTER)
        content.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {isOpaque=false
            add(AetherButton("Cancelar",ButtonVariant.GHOST).apply {addActionListener {dialog.dispose()}})
            add(AetherButton(action,ButtonVariant.PRIMARY).apply {addActionListener {accepted=true;dialog.dispose()}})
        },BorderLayout.SOUTH)
        dialog.contentPane=content;dialog.defaultCloseOperation=WindowConstants.DISPOSE_ON_CLOSE
        dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),"close")
        dialog.rootPane.actionMap.put("close",object: AbstractAction(){override fun actionPerformed(e: java.awt.event.ActionEvent){dialog.dispose()}})
        try{dialog.pack();dialog.setLocationRelativeTo(owner);dialog.isVisible=true;return accepted}finally{dialog.dispose()}
    }
}
