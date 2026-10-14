package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.nio.file.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import javax.swing.*
import org.junit.*
import org.junit.Assert.*
import pe.gob.reniec.dnie.pdf.*
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.domain.signing.DocumentSignStatus

object SuiteTestSettings {
    private val nodes=mutableListOf<Preferences>()
    init { Runtime.getRuntime().addShutdownHook(Thread { synchronized(nodes) {nodes.forEach { runCatching {it.removeNode()} }} }) }
    fun create()=Preferences.userRoot().node("pe/gob/reniec/dnie/suite-tests/"+UUID.randomUUID()).also { synchronized(nodes) { nodes+=it } }
}
class AetherR4Test {
    private fun children(c: Container): List<Component> = c.components.flatMap { listOf(it)+(if(it is Container) children(it) else emptyList()) }
    private fun await(condition: ()->Boolean) {
        val limit=System.nanoTime()+15_000_000_000L;var ready=false
        while(!ready && System.nanoTime()<limit) {SwingUtilities.invokeAndWait {ready=condition()};if(!ready)Thread.sleep(30)}
        assertTrue("Expected asynchronous presentation",ready)
    }
    @Test fun preferencesSurviveReopenAndRejectCorruptValuesWithoutSecrets() {
        val prefs=SuiteTestSettings.create();val store=SuiteDesignPreferences(prefs)
        val design=SuiteDesign(PdfSigningOptions(reason="Conformidad áé",location="Lima, Perú",reniecTimestamp=true,pageTarget=StampPageTarget.LAST_PAGE,
            placement=NormalizedStampPlacement(.15f,.7f,.5f,.15f),appearance=SignatureAppearance(style=StampStyle.MINIMAL,colorTheme=StampColorTheme.CRIMSON_SEAL,badge=StampBadgeType.NONE,showDni=false,showTsaBadge=false)),DesktopPdfMode.VISIBLE,"Nombre de muestra")
        store.save(design);store.close();assertTrue(store.awaitClosed());assertEquals(design,SuiteDesignPreferences(prefs).use {it.load()})
        val encoded=SuiteDesignPreferences.encode(design);assertFalse(encoded.contains("pin",true));assertFalse(encoded.contains("can="));assertFalse(encoded.contains("handwritten"))
        assertEquals(SuiteDesign().options.placement,SuiteDesignPreferences.decode("left=NaN\nwidth=-5\ncolor=missing\n").options.placement)
        assertEquals(StampStyle.INSTITUTIONAL,SuiteDesignPreferences.decode("style=HANDWRITTEN_ONLY").options.appearance.style)
    }
    @Test fun sampleSheetUpdatesBeforePdfZoomsAndRestoresDesign() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());val prefs=SuiteTestSettings.create()
        lateinit var window: SuiteWindow;lateinit var canvas: PreviewCanvas
        SwingUtilities.invokeAndWait {AetherSwingTheme.install();window=SuiteWindow(settings=prefs);window.isVisible=true;canvas=children(window).filterIsInstance<PreviewCanvas>().single()}
        try {
            await {canvas.image==null && canvas.stampImage!=null}
            val before=canvas.stampImage
            SwingUtilities.invokeAndWait {children(window).filterIsInstance<JComboBox<*>>().single {it.name=="stamp-style"}.selectedItem=StampStyle.MINIMAL}
            await {canvas.stampImage!=null && canvas.stampImage!==before}
            await {prefs.get("stamp.design.v1","").contains("style=MINIMAL")}
            SwingUtilities.invokeAndWait {window.dispose();window=SuiteWindow(settings=prefs);window.isVisible=true;canvas=children(window).filterIsInstance<PreviewCanvas>().single()}
            await {canvas.stampImage!=null}
            SwingUtilities.invokeAndWait {
                assertEquals(StampStyle.MINIMAL,children(window).filterIsInstance<JComboBox<*>>().single {it.name=="stamp-style"}.selectedItem)
                val placement=canvas.placement;val bounds=canvas.pageBounds()!!
                children(window).filterIsInstance<JButton>().single {it.name=="preview-zoom-in"}.doClick()
                assertEquals(1.25,canvas.zoom,0.0);assertTrue(canvas.pageBounds()!!.width>bounds.width);assertEquals(placement,canvas.placement)
                children(window).filterIsInstance<JButton>().single {it.name=="preview-fit"}.doClick();assertEquals(1.0,canvas.zoom,0.0)
                children(window).filterIsInstance<JButton>().single {it.name=="restore-stamp-design"}.doClick()
                assertEquals(SuiteDesign().options.placement,canvas.placement)
                assertEquals(StampStyle.INSTITUTIONAL,children(window).filterIsInstance<JComboBox<*>>().single {it.name=="stamp-style"}.selectedItem)
            }
            await {canvas.stampImage!=null && canvas.stampError==null}
            System.getProperty("dnie.ui.evidence")?.let {output->SwingUtilities.invokeAndWait {
                val image=BufferedImage(window.width,window.height,BufferedImage.TYPE_INT_RGB);val graphics=image.createGraphics()
                try {window.paint(graphics);val dir=Path.of(output);Files.createDirectories(dir);ImageIO.write(image,"png",dir.resolve("suite-r4-sample.png").toFile())} finally {graphics.dispose();image.flush()}
            }}
        } finally {SwingUtilities.invokeAndWait {window.dispose()}}
    }
    @Test fun completedOutputsUseReturnedPathsAndOpenOffEdt() {
        val input=Path.of("original.pdf");val original=Path.of("job/original.pdf");val signed=Path.of("job/signed/uuid/actual-result.pdf")
        val result=DesktopSigningResult("job",SigningBackend.PRIVATE_ANDROID,Path.of("job"),listOf(DesktopDocumentResult("id","original.pdf",DocumentSignStatus.SIGNED,original,signed,null)),null)
        assertEquals(signed,CompletedBatchPresentation.outputs(listOf(input),result)[input]);assertNull(CompletedBatchPresentation.citizen(result))
        val latch=CountDownLatch(1);var opened: Path?=null;var onEdt=true
        val actions=object: SuiteDesktopActions {override fun open(path: Path){opened=path;onEdt=SwingUtilities.isEventDispatchThread()};override fun reveal(path: Path){open(path)}}
        SuiteController().use {controller->controller.openOutput(signed,false,actions) {assertTrue(it);latch.countDown()};assertTrue(latch.await(5,TimeUnit.SECONDS))}
        assertEquals(signed,opened);assertFalse(onEdt)
    }
    @Test fun completedBatchFillsNameAndDoubleClickOpensActualResultOffEdt() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());val dir=Files.createTempDirectory("suite-completed-")
        val input=dir.resolve("original.txt");Files.writeString(input,"Synthetic presentation only")
        val output=dir.resolve("actual-returned-result.p7s");Files.writeString(output,"Not a signature: isolated UI fixture")
        val card=pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard()
        Files.write(dir.resolve("certificate.cer"),card.certificate.encoded);card.close()
        val result=DesktopSigningResult("fixture",SigningBackend.SIMULATED,dir,listOf(DesktopDocumentResult("id","original.txt",DocumentSignStatus.SIGNED,input,output,null)),null)
        assertEquals("12345678",CompletedBatchPresentation.citizen(result)?.dniNumber)
        val opened=CountDownLatch(1);var openedPath: Path?=null;var onEdt=true
        val actions=object: SuiteDesktopActions {override fun open(path: Path){openedPath=path;onEdt=SwingUtilities.isEventDispatchThread();opened.countDown()};override fun reveal(path: Path){open(path)}}
        lateinit var window: SuiteWindow
        SwingUtilities.invokeAndWait {window=SuiteWindow(listOf(input),SuiteTestSettings.create(),actions);window.isVisible=true}
        try {
            await {children(window).filterIsInstance<JList<*>>().single().model.size==1}
            SwingUtilities.invokeAndWait {window.showCompleted(listOf(input),result)}
            await {children(window).filterIsInstance<JTextField>().single {it.name=="stamp-preview-name"}.text.contains("SIMULACION")}
            SwingUtilities.invokeAndWait {
                val list=children(window).filterIsInstance<JList<*>>().single();list.selectedIndex=0
                list.dispatchEvent(MouseEvent(list,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,10,20,2,false,MouseEvent.BUTTON1))
            }
            assertTrue(opened.await(5,TimeUnit.SECONDS));assertEquals(output,openedPath);assertFalse(onEdt)
        } finally {SwingUtilities.invokeAndWait {window.dispose()};listOf(input,output,dir.resolve("certificate.cer")).forEach(Files::deleteIfExists);Files.deleteIfExists(dir)}
    }

}
