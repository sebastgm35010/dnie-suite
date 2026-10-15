package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.nio.file.*
import javax.imageio.ImageIO
import javax.swing.*
import org.junit.Test
import org.junit.Assert.*

class FinishR10Test {
    private fun all(c: Container): List<Component> = c.components.flatMap { listOf(it) + if(it is Container)all(it) else emptyList() }
    @Test fun pinSubmissionAndAllCancellationPathsClearTheField() {
        SwingUtilities.invokeAndWait {
            val owner=JFrame()
            try {
                for(action in listOf("submit","escape","close","cancel")) {
                    val dialog=AetherPinDialog(owner,3)
                    val field=all(dialog).filterIsInstance<JPasswordField>().single()
                    val confirm=all(dialog).filterIsInstance<JButton>().single { it.name=="confirm-pin" }
                    assertFalse(confirm.isEnabled)
                    field.text="abc";assertFalse(confirm.isEnabled)
                    field.text="123456789";assertFalse(confirm.isEnabled)
                    field.text="0000";assertTrue(confirm.isEnabled)
                    var expired=false
                    val watchdog=Timer(5000) {expired=true;dialog.dispatchEvent(WindowEvent(dialog,WindowEvent.WINDOW_CLOSING))}.apply {isRepeats=false}
                    val timer=Timer(60,null).apply {isRepeats=false;addActionListener {
                        if(action=="submit")System.getProperty("dnie.ui.evidence")?.let {dir ->val path=Path.of(dir);Files.createDirectories(path);val image=BufferedImage(dialog.width,dialog.height,BufferedImage.TYPE_INT_RGB);image.createGraphics().also{dialog.paint(it);it.dispose()};ImageIO.write(image,"png",path.resolve("pin-dialog-r10.png").toFile());image.flush()}
                        when(action) {
                            "submit" -> dialog.rootPane.defaultButton.doClick()
                            "escape" -> dialog.rootPane.actionMap.get("cancel-pin").actionPerformed(java.awt.event.ActionEvent(dialog,java.awt.event.ActionEvent.ACTION_PERFORMED,"cancel-pin"))
                            "close" -> dialog.dispatchEvent(WindowEvent(dialog,WindowEvent.WINDOW_CLOSING))
                            else -> all(dialog).filterIsInstance<JButton>().single{it.name=="cancel-pin"}.doClick()
                        }
                    } }
                    dialog.addWindowListener(object: java.awt.event.WindowAdapter(){override fun windowOpened(e: WindowEvent){timer.start()}})
                    watchdog.start()
                    val pin=try {dialog.requestPin()}finally{timer.stop();watchdog.stop()}
                    assertFalse("Modal action must complete before its watchdog",expired)
                    try {if(action=="submit")assertArrayEquals(charArrayOf('0','0','0','0'),pin) else assertNull(pin);assertEquals(0,field.password.size);assertFalse(dialog.isDisplayable)}finally{pin?.fill('\u0000')}
                }
            } finally {owner.dispose()}
        }
    }
    @Test fun animatorCompletesStopsWhenHiddenAndHonorsReducedMotion() {
        SwingUtilities.invokeAndWait {
            val owner=JButton("fixture");val window=JFrame().apply{add(owner);setSize(200,100);isVisible=true}
            try {
                val anim=AetherSwingAnimator(owner,reduced={false});anim.to(1f);assertTrue(anim.running)
                anim.tick(System.nanoTime()+200_000_000);assertEquals(1f,anim.value,0f);assertFalse(anim.running)
                anim.to(0f);assertTrue(anim.running);window.isVisible=false;assertFalse(anim.running)
                val reduced=AetherSwingAnimator(owner,reduced={true});reduced.to(1f);assertEquals(1f,reduced.value,0f);assertFalse(reduced.running)
                assertEquals(Color(50,100,127),AetherSwingAnimator.blend(Color(0,0,0),Color(100,200,255),.5f))
            }finally{window.dispose()}
        }
    }
    @Test fun documentRendererSelectionHasRoundedBordersAndSignedStatusWithoutFileAccess() {
        SwingUtilities.invokeAndWait {
            AetherSwingTheme.install()
            val renderer=DocumentCardCellRenderer({1024},{"Firmado"})
            val panel=renderer.getListCellRendererComponent(JList<Path>(),Path.of("fixture.pdf"),0,true,false) as AetherCardPanel
            assertTrue(panel.selected);assertTrue(panel.border is javax.swing.border.EmptyBorder)
            assertTrue(all(panel).filterIsInstance<JLabel>().any {it.text=="Firmado" && it.isVisible})
            fun layout(c: Container){c.doLayout();c.components.filterIsInstance<Container>().forEach(::layout)}
            panel.setSize(420,78);layout(panel)
            System.getProperty("dnie.ui.evidence")?.let{dir ->val path=Path.of(dir);Files.createDirectories(path);val image=BufferedImage(420,78,BufferedImage.TYPE_INT_RGB);image.createGraphics().also{panel.paint(it);it.dispose()};ImageIO.write(image,"png",path.resolve("document-selected-r10.png").toFile());image.flush()}
            renderer.getListCellRendererComponent(JList<Path>(),Path.of("fixture.txt"),1,false,false);assertFalse(panel.selected)
        }
    }
}
