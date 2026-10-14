package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import java.nio.file.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.pdmodel.*
import pe.gob.reniec.dnie.pdf.*

class AetherSuitePresentationTest {
    @get:Rule val folder=TemporaryFolder()
    private fun children(container: Container): List<Component> = container.components.flatMap { listOf(it)+(if(it is Container) children(it) else emptyList()) }
    @Test fun actualWindowRendersStampUpdatesAppearanceAndKeepsAdvancedRoutesOutOfMainView() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val file=folder.root.toPath().resolve("Documento de prueba.pdf")
        PDDocument().use { doc -> repeat(3) {doc.addPage(PDPage())};doc.save(file.toFile()) }
        lateinit var window: SuiteWindow;lateinit var canvas: PreviewCanvas
        SwingUtilities.invokeAndWait { AetherSwingTheme.install();window=SuiteWindow(listOf(file),settings=SuiteTestSettings.create());window.isVisible=true
            children(window).filterIsInstance<JComboBox<*>>().first { it.name=="suite-phone" }.editor.item="192.168.1.10"
            canvas=children(window).filterIsInstance<PreviewCanvas>().single() }
        fun await(condition: ()->Boolean) {
            val limit=System.nanoTime()+15_000_000_000L;var ready=false
            while(!ready && System.nanoTime()<limit) { SwingUtilities.invokeAndWait { ready=condition() };if(!ready)Thread.sleep(25) }
            assertTrue("Rendered appearance must arrive off EDT",ready)
        }
        try {
            await { canvas.image!=null && canvas.stampImage!=null }
            lateinit var before: BufferedImage
            SwingUtilities.invokeAndWait {
                assertFalse(children(window).filterIsInstance<JTextField>().any { it.name=="suite-workspace" })
                val advanced=window.ownedWindows.filterIsInstance<JDialog>().single { it.title=="Red y rutas" }
                assertTrue(children(advanced).filterIsInstance<JTextField>().any { it.name=="suite-workspace" })
                before=canvas.stampImage!!
                val styles=children(window).filterIsInstance<JComboBox<*>>().single { it.name=="stamp-style" }
                styles.selectedItem=StampStyle.MINIMAL
                val targets=children(window).filterIsInstance<JComboBox<*>>().single { it.name=="stamp-target" }
                targets.selectedItem=StampPageTarget.LAST_PAGE
            }
            await { canvas.stampImage!==before && children(window).filterIsInstance<JLabel>().any { it.text=="3 / 3" } }
            SwingUtilities.invokeAndWait { children(window).filterIsInstance<JButton>().single {it.toolTipText=="Página anterior"}.doClick() }
            await {children(window).filterIsInstance<JLabel>().any {it.text=="2 / 3"}}
            SwingUtilities.invokeAndWait { assertEquals(StampPageTarget.LAST_PAGE,children(window).filterIsInstance<JComboBox<*>>().single {it.name=="stamp-target"}.selectedItem) }

            SwingUtilities.invokeAndWait {
                val page=canvas.pageBounds()!!;val p=canvas.placement
                val corner=Point(page.x+(p.left*page.width).toInt(),page.y+(p.top*page.height).toInt())
                val opposite=p.left+p.width
                canvas.dispatchEvent(MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,0,corner.x,corner.y,1,false))
                canvas.dispatchEvent(MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,0,corner.x-20,corner.y-10,1,false))
                canvas.dispatchEvent(MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,0,corner.x-20,corner.y-10,1,false))
                assertEquals(opposite,canvas.placement.left+canvas.placement.width,.002f)
                assertTrue(canvas.placement.width>p.width)
            }
            await { canvas.stampImage!=null && canvas.stampError==null }
            System.getProperty("dnie.ui.evidence")?.let { output -> SwingUtilities.invokeAndWait {
                val path=Path.of(output);Files.createDirectories(path)
                val image=BufferedImage(window.width,window.height,BufferedImage.TYPE_INT_RGB)
                image.createGraphics().let { g -> try { window.paint(g) } finally { g.dispose() } }
                ImageIO.write(image,"png",path.resolve("suite-aether.png").toFile());image.flush()

            } }
        } finally { SwingUtilities.invokeAndWait { window.dispatchEvent(WindowEvent(window,WindowEvent.WINDOW_CLOSING)) } }
    }
}
