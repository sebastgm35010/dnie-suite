package pe.gob.reniec.dnie.desktop.ui

import javax.swing.*
import org.junit.Assert.*
import org.junit.Test

class SigningPinPanelTest {
    @Test fun explicitPinFieldReturnsAndClearsSecret() {
        SwingUtilities.invokeAndWait {
            val panel=SigningPinPanel(2)
            val field=panel.components.filterIsInstance<JPasswordField>().single()
            assertTrue(field.preferredSize.height>=40)
            assertTrue(panel.components.filterIsInstance<JLabel>().any { it.text.contains("PIN de firma") })
            field.text="0000"
            val pin=panel.takePin()
            try { assertArrayEquals("0000".toCharArray(),pin);assertEquals(0,field.password.size) }
            finally { pin.fill('\u0000');panel.clear() }
        }
    }
}
