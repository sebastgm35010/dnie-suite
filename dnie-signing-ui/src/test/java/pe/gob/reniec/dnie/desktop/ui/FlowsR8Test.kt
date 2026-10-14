package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.pdmodel.*
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus
import pe.gob.reniec.dnie.pdf.*

class FlowsR8Test {
    @get:Rule val folder = TemporaryFolder()
    private fun children(c: Container): List<Component> = c.components.flatMap { listOf(it) + if (it is Container) children(it) else emptyList() }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 15_000_000_000L
        var ready = false
        while (!ready && System.nanoTime() < end) { SwingUtilities.invokeAndWait { ready = condition() }; if (!ready) Thread.sleep(25) }
        assertTrue("UI fixture did not complete", ready)
    }
    @Test fun matchingTemplateIgnoresSigningIntentButDetectsCustomAppearance() {
        val options = StampTemplate.EXECUTIVE.apply(PdfSigningOptions()).copy(pageNumber = 9, reason = "Independent intent", location = "Test", reniecTimestamp = true)
        assertTrue(SuiteTemplateSelection.matches(StampTemplate.EXECUTIVE, options))
        assertFalse(SuiteTemplateSelection.matches(StampTemplate.EXECUTIVE, options.copy(appearance = options.appearance.copy(showBorder = false))))
        assertFalse(SuiteTemplateSelection.matches(StampTemplate.EXECUTIVE, options.copy(visible = false)))
        assertFalse(SuiteTemplateSelection.matches(StampTemplate.PRINT, options))
    }
    @Test fun gridSelectionProgressAndDirectPdfActionUseActualReturnedResult() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless())
        val input = folder.root.toPath().resolve("entrada.pdf")
        PDDocument().use { it.addPage(PDPage()); it.save(input.toFile()) }
        val output = folder.root.toPath().resolve("ruta-devuelta-por-motor.pdf")
        Files.copy(input, output)
        val result = DesktopSigningResult("fixture", SigningBackend.SIMULATED, folder.root.toPath(),
            listOf(DesktopDocumentResult("id", "entrada.pdf", DocumentSignStatus.SIGNED, input, output, null)), null)
        val opened = CountDownLatch(1); var actual: Path? = null; var onEdt = true
        val actions = object : SuiteDesktopActions {
            override fun open(path: Path) { actual = path; onEdt = SwingUtilities.isEventDispatchThread(); opened.countDown() }
            override fun reveal(path: Path) { open(path) }
        }
        lateinit var window: SuiteWindow
        SwingUtilities.invokeAndWait { AetherSwingTheme.install(); window = SuiteWindow(listOf(input), SuiteTestSettings.create(), actions); window.isVisible = true }
        try {
            await { children(window).filterIsInstance<JList<*>>().single().model.size == 1 }
            SwingUtilities.invokeAndWait {
                val grid = children(window).filterIsInstance<JPanel>().single { it.name == "stamp-templates" }
                assertEquals(2, (grid.layout as GridLayout).rows); assertEquals(2, (grid.layout as GridLayout).columns)
                val button = children(window).filterIsInstance<AetherToggleChip>().single { it.name == "stamp-template-executive" }
                button.doClick(); assertTrue(button.isSelected)
                val background = children(window).filterIsInstance<JComboBox<*>>().single { it.name == "stamp-background" }
                background.selectedItem = StampBackgroundMode.SOLID_WHITE; assertFalse(button.isSelected)
                val busy = SuiteWindow::class.java.getDeclaredMethod("setBusy", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                busy.invoke(window, true)
                val progress = children(window).filterIsInstance<JProgressBar>().single { it.name == "suite-progress" }
                assertTrue(progress.isVisible); assertTrue(progress.isIndeterminate)
                busy.invoke(window, false); assertFalse(progress.isVisible)
                window.showCompleted(listOf(input), result)
                val open = children(window).filterIsInstance<JButton>().single { it.name == "open-signed-pdf" }
                assertTrue(open.isEnabled); open.doClick()
                val dark = children(window).filterIsInstance<JButton>().single { it.name == "suite-theme" }
                if (!AetherSwingTheme.dark) dark.doClick()
            }
            assertTrue(opened.await(5, TimeUnit.SECONDS)); assertEquals(output, actual); assertFalse(onEdt)
            await { children(window).filterIsInstance<PreviewCanvas>().single().stampImage != null }
            System.getProperty("dnie.ui.evidence")?.let { dir -> SwingUtilities.invokeAndWait {
                val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics(); try { window.paint(graphics) } finally { graphics.dispose() }
                val path = Path.of(dir); Files.createDirectories(path); ImageIO.write(image, "png", path.resolve("suite-flujos-r8.png").toFile()); image.flush()
            } }
        } finally { SwingUtilities.invokeAndWait { window.dispose() } }
    }
}
