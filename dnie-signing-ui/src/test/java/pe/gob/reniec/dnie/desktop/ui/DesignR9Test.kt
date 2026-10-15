package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.*
import javax.imageio.ImageIO
import javax.swing.*
import org.junit.Test
import org.junit.Assert.*

class DesignR9Test {
    @Test fun quickGuideAndBothPalettesWorkWithoutOpeningConnection() {
        SwingUtilities.invokeAndWait {
            val window = SuiteWindow(settings = SuiteTestSettings.create())
            try {
                fun all(c: Container): List<Component> = c.components.flatMap { listOf(it) + if(it is Container) all(it) else emptyList() }
                val buttons = all(window).filterIsInstance<JButton>()
                val theme = buttons.single { it.name == "suite-theme" }
                buttons.single { it.name == "suite-guide" }.doClick()
                val guide = window.ownedWindows.single { it.name == "suite-quick-guide" } as JDialog
                assertTrue(guide.isVisible)
                assertEquals(3, all(guide).count { it.name?.startsWith("guide-step-") == true })
                assertFalse(buttons.single { it.text == "Firmar lote" }.isEnabled)
                theme.doClick()
                assertEquals(Color(0x6B9EFF), AetherSwingTheme.primary)
                assertEquals(Color(0x00174B), AetherSwingTheme.onPrimary)
                assertEquals(Color(0x1D2840), AetherSwingTheme.container)
                assertEquals("Modo claro", theme.text)
                assertNotNull(theme.icon)
                System.getProperty("dnie.ui.evidence")?.let { path ->
                    val dir = Path.of(path); Files.createDirectories(dir)
                    val image = BufferedImage(guide.width, guide.height, BufferedImage.TYPE_INT_RGB)
                    image.createGraphics().also { guide.paint(it); it.dispose() }
                    ImageIO.write(image, "png", dir.resolve("guide-dark-r9.png").toFile()); image.flush()
                }
                all(guide).filterIsInstance<JButton>().single { it.name == "guide-close" }.doClick()
                assertFalse(guide.isVisible)
                theme.doClick(); assertEquals(Color.WHITE, AetherSwingTheme.onPrimary)
            } finally { window.dispose() }
        }
    }
}
