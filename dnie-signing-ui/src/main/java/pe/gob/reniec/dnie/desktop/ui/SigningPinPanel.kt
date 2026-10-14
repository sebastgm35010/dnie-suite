package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Presentation only; the controller owns PIN lifetime once explicitly submitted. */
internal class SigningPinPanel(count: Int) : JPanel(BorderLayout(8,10)) {
    private val password=JPasswordField(12).apply {
        font=font.deriveFont(20f);preferredSize=Dimension(280,42)
        border=BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AetherSwingTheme.border),EmptyBorder(6,10,6,10))
    }
    init {
        background=AetherSwingTheme.surface;
        border=EmptyBorder(16,16,16,16)
        add(JLabel("<html><b>PIN de firma del DNI</b><br>Se autorizarán $count archivo(s).</html>"),BorderLayout.NORTH)
        add(password,BorderLayout.CENTER)
        add(JLabel("<html>Introduce entre 4 y 8 dígitos.<br>Mantén el DNI cerca del teléfono durante la firma.</html>"),BorderLayout.SOUTH)
    }
    fun takePin(): CharArray=password.password.also { clear() }
    fun clear() { password.text="" }
    fun focusPin() { SwingUtilities.invokeLater { password.requestFocusInWindow() } }
}
