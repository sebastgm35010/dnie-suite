package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Presentation only: opening help never connects, authorizes or starts a signing job. */
class SuiteQuickGuideDialog(owner: JFrame) : AutoCloseable {
    private val dialog = JDialog(owner, "Guía rápida", false)
    init {
        dialog.name = "suite-quick-guide"
        dialog.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = AetherSwingTheme.background
            border = EmptyBorder(20, 20, 20, 20)
        }
        val steps = listOf(
            "1. Conecta el teléfono" to "Abre Suite en Android y vincula la PC con el QR. Después usa la PC guardada; Wi-Fi y Tailscale buscan el teléfono disponible. Si la red lo impide, revisa Red y rutas.",
            "2. Prepara los documentos" to "Arrastra PDFs o añádelos a la lista. Elige un sello y su posición en la vista previa; también puedes firmar PDF invisible u otros archivos con firma separada.",
            "3. Autoriza el lote" to "Introduce el PIN cuando se solicite y acerca el DNIe al teléfono. Mantenlo inmóvil hasta ver el resultado. Comprueba los archivos de salida antes de enviar otro lote."
        )
        steps.forEachIndexed { index, (title, body) ->
            content.add(AetherCardPanel(BorderLayout(0, 8)).apply {
                name = "guide-step-${index + 1}"
                alignmentX = Component.LEFT_ALIGNMENT
                add(JLabel(title).apply { font = font.deriveFont(Font.BOLD, 16f) }, BorderLayout.NORTH)
                add(JTextArea(body).apply {
                    lineWrap = true; wrapStyleWord = true; isEditable = false; isOpaque = false
                    foreground = AetherSwingTheme.muted; rows = 3
                }, BorderLayout.CENTER)
            })
            content.add(Box.createVerticalStrut(12))
        }
        content.add(AetherButton("Cerrar guía").apply { name = "guide-close"; addActionListener { dialog.isVisible = false } })
        dialog.contentPane = content
        dialog.setSize(600, 560)
    }
    fun open() { dialog.setLocationRelativeTo(dialog.owner); dialog.isVisible = true }
    override fun close() = dialog.dispose()
}

/** Vector icon avoids depending on a font containing sun/moon Unicode glyphs. */
internal fun updateThemeButton(button: JButton) {
    button.text = if (AetherSwingTheme.dark) "Modo claro" else "Modo oscuro"
    button.toolTipText = "Cambiar a ${button.text.lowercase()}"
    button.icon = object : Icon {
        override fun getIconWidth() = 18
        override fun getIconHeight() = 18
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = AetherSwingTheme.primary; g.stroke = BasicStroke(1.5f)
                if (AetherSwingTheme.dark) {
                    g.drawOval(5, 5, 8, 8)
                    repeat(8) { g.drawLine(9, 0, 9, 3); g.rotate(Math.PI / 4, 9.0, 9.0) }
                } else { g.drawArc(3, 2, 12, 13, 70, 260); g.drawArc(7, 0, 10, 11, 150, 160) }
            } finally { g.dispose() }
        }
    }
}
