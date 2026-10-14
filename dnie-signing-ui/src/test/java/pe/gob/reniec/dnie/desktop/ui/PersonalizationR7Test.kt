package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.*
import javax.swing.*
import javax.imageio.ImageIO
import org.junit.*
import org.junit.Assert.*
import pe.gob.reniec.dnie.pdf.*

class PersonalizationR7Test {
    @Test fun pngWhiteRemovalPreservesInkAndBoundsAreEnforced() {
        val dir=Files.createTempDirectory("stamp-image-test")
        try {
            val png=dir.resolve("mark.png");val image=BufferedImage(12,12,BufferedImage.TYPE_INT_RGB)
            image.createGraphics().also {it.color=Color.WHITE;it.fillRect(0,0,12,12);it.color=Color.BLACK;it.fillRect(5,5,2,2);it.dispose()}
            ImageIO.write(image,"png",png.toFile());image.flush()
            val clear=ImageIO.read(StampImageLoader.load(png,true).inputStream())
            assertEquals(0,clear.getRGB(0,0) ushr 24);assertEquals(255,clear.getRGB(5,5) ushr 24);clear.flush()
            val opaque=ImageIO.read(StampImageLoader.load(png,false).inputStream());assertEquals(255,opaque.getRGB(0,0) ushr 24);opaque.flush()
            Files.write(png,ByteArray(512*1024+1));assertThrows(IllegalArgumentException::class.java){StampImageLoader.load(png,false)}
            Files.write(png,"not png".toByteArray());assertThrows(IllegalArgumentException::class.java){StampImageLoader.load(png,false)}
        } finally {Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::delete)}}
    }
    @Test fun desktopOptionsAndDarkThemeAreAvailableWithoutOpeningAnyCardSession() {
        SwingUtilities.invokeAndWait {
            val prefs=SuiteTestSettings.create();val window=SuiteWindow(settings=prefs)
            try {
                fun all(c: Container): List<Component> = c.components.flatMap {listOf(it)+if(it is Container)all(it)else emptyList()}
                val components=all(window)
                val styles=components.filterIsInstance<JComboBox<*>>().single {it.name=="stamp-style"}
                assertEquals(5,styles.itemCount);assertTrue((0 until styles.itemCount).any {styles.getItemAt(it)==StampStyle.HANDWRITTEN_ONLY})
                val toggle=components.filterIsInstance<JButton>().single {it.name=="suite-theme"}
                toggle.doClick();assertTrue(AetherSwingTheme.dark);assertEquals(Color(0x0B1326),window.contentPane.background)
                assertTrue(prefs.getBoolean("theme.dark",false))
                System.getProperty("dnie.ui.evidence")?.let {output->
                    val dir=Path.of(output);Files.createDirectories(dir);window.isVisible=true
                    val canvas=components.filterIsInstance<PreviewCanvas>().single()
                    val loop=Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop();val started=System.nanoTime()
                    val timer=Timer(40,null)
                    timer.addActionListener {if(canvas.stampImage!=null || canvas.stampError!=null || System.nanoTime()-started>5_000_000_000L) {timer.stop();loop.exit()}}
                    timer.start();loop.enter();assertNotNull(canvas.stampImage)
                    val image=BufferedImage(window.width,window.height,BufferedImage.TYPE_INT_RGB)
                    image.createGraphics().also {window.paint(it);it.dispose()};ImageIO.write(image,"png",dir.resolve("suite-dark-r7.png").toFile());image.flush()
                }
                toggle.doClick();assertFalse(AetherSwingTheme.dark)
            } finally {window.dispose()}
        }
    }
}
