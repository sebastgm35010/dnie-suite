package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.WindowEvent
import java.net.NetworkInterface
import java.net.ServerSocket
import javax.swing.*
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.gob.reniec.dnie.signing.link.SigningProvision

/** Real button, DPAPI test identity and local waiting session; no phone or DNI. */
class SuiteQrButtonTest {
    @get:Rule val folder=TemporaryFolder()
    private fun components(parent: Container): List<Component> = parent.components.flatMap { listOf(it)+(if(it is Container) components(it) else emptyList()) }
    @Test fun opensReopensAndRetiresQrFromActualButton() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val local=NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp&&!it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }.firstOrNull(SigningProvision::privateAddress)
        assumeNotNull(local)
        assumeTrue(runCatching { ServerSocket(SigningProvision.PHONE_PORT).use { };true }.getOrDefault(false))
        lateinit var window: SuiteWindow
        fun button(text: String)=components(window).filterIsInstance<JButton>().single { it.text==text }
        fun qrWindow()=Window.getWindows().filterIsInstance<JDialog>().firstOrNull { it.title=="Emparejar Android"&&it.isShowing }
        fun awaitQr(): JDialog {
            var result: JDialog?=null
            val deadline=System.nanoTime()+15_000_000_000L
            while(result==null&&System.nanoTime()<deadline) {
                SwingUtilities.invokeAndWait { result=qrWindow()?.takeIf { components(it).filterIsInstance<JLabel>().any { label -> label.icon is ImageIcon } } }
                if(result==null) Thread.sleep(25)
            }
            assertNotNull("Actual QR button must display a rendered QR",result)
            return result!!
        }
        SwingUtilities.invokeAndWait {
            window=SuiteWindow(settings=SuiteTestSettings.create());window.isVisible=true
            fun field(name: String,value: String) {
                val component=(components(window)+window.ownedWindows.flatMap { components(it) }).single { it.name==name }
                when(component) {is JTextField->component.text=value;is JComboBox<*>->component.editor.item=value;else->error("Missing input")}
            }
            field("suite-pc",local!!);field("suite-phone",local.substringBeforeLast('.')+".254")
            field("suite-workspace",folder.root.resolve("jobs").absolutePath);field("suite-identity",folder.root.resolve("state/pc.dpapi").absolutePath)
        }
        try {
            SwingUtilities.invokeAndWait { assertTrue(button("Abrir QR").isEnabled);button("Abrir QR").doClick() }
            val first=awaitQr()
            SwingUtilities.invokeAndWait { first.dispatchEvent(WindowEvent(first,WindowEvent.WINDOW_CLOSING));button("Abrir QR").doClick() }
            val second=awaitQr();assertNotSame(first,second)
            SwingUtilities.invokeAndWait { button("Desconectar").doClick();assertNull(qrWindow()) }
            assertFalse(folder.root.resolve("jobs/suite-emparejamiento.png").exists())
        } finally {
            SwingUtilities.invokeAndWait { button("Desconectar").doClick();window.dispatchEvent(WindowEvent(window,WindowEvent.WINDOW_CLOSING)) }
        }
    }
}
