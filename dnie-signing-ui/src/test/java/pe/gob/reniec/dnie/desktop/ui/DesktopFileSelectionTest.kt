package pe.gob.reniec.dnie.desktop.ui

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.swing.filechooser.FileSystemView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import pe.gob.reniec.dnie.pdf.desktop.DesktopPdfPreview

class DesktopFileSelectionTest {
    @get:Rule val folder=TemporaryFolder()
    private class Links(val links: Map<File,File>) : FileSystemView() {
        override fun createNewFolder(directory: File): File = error("unused")
        override fun isLink(file: File)=links.containsKey(file)
        override fun getLinkLocation(file: File): File?=links[file]
    }
    @Test fun resolvesSelectedShortcutAndPreservesRealFile() {
        val target=folder.newFile("document.pdf");val shortcut=folder.newFile("document.lnk")
        val view=Links(mapOf(shortcut to target))
        assertEquals(target.toPath(),DesktopFileSelection.resolve(shortcut,view))
        assertEquals(target.toPath(),DesktopFileSelection.resolve(target,view))
    }
    @Test fun virtualRecentNameResolvesHiddenLnkExtension() {
        val recent=folder.newFolder("Recent");val target=folder.newFile("real.pdf")
        val alias=File(recent,"document.pdf");val link=File(recent,"document.pdf.lnk").apply { createNewFile() }
        assertFalse(alias.exists())
        assertEquals(target.toPath(),DesktopFileSelection.resolve(alias,Links(mapOf(link to target)),recent.toPath()))
    }
    @Test fun rejectsBrokenCircularAndNonRecentMissingSelections() {
        val a=folder.newFile("a.lnk");val b=folder.newFile("b.lnk")
        assertThrows(IllegalArgumentException::class.java) { DesktopFileSelection.resolve(a,Links(mapOf(a to b,b to a))) }
        assertThrows(IllegalArgumentException::class.java) { DesktopFileSelection.resolve(a,Links(mapOf(a to File(folder.root,"absent.pdf")))) }
        assertThrows(IllegalArgumentException::class.java) { DesktopFileSelection.resolve(folder.root,Links(emptyMap())) }
        assertThrows(IllegalArgumentException::class.java) { DesktopFileSelection.resolve(File(folder.root,"missing.pdf"),Links(emptyMap())) }
    }
    @Test fun windowsRecentShortcutFeedsActualPdfPreview() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val target=folder.root.resolve("ficha sintética.pdf")
        PDDocument().use { it.addPage(PDPage());it.save(target) }
        val recent=folder.newFolder("Recent");val link=File(recent,"ficha.pdf.lnk")
        val script=folder.newFile("shortcut.ps1")
        Files.writeString(script.toPath(),"param([string]\u0024Link,[string]\u0024Target)\n\u0024shell=New-Object -ComObject WScript.Shell\n\u0024shortcut=\u0024shell.CreateShortcut(\u0024Link)\n\u0024shortcut.TargetPath=\u0024Target\n\u0024shortcut.Save()\n")
        val process=ProcessBuilder("powershell.exe","-ExecutionPolicy","Bypass","-NoProfile","-NonInteractive","-File",script.absolutePath,"-Link",link.absolutePath,"-Target",target.absolutePath).start()
        try { assertTrue(process.waitFor(15,TimeUnit.SECONDS));assertEquals(0,process.exitValue()) }
        finally { if(process.isAlive)process.destroyForcibly() }
        val resolved=DesktopFileSelection.resolve(File(recent,"ficha.pdf"),FileSystemView.getFileSystemView(),recent.toPath())
        assertEquals(target.toPath(),resolved)
        val preview=DesktopPdfPreview().render(resolved,1)
        assertEquals(1,preview.count);assertTrue(preview.image.width>0&&preview.image.height>0)
        preview.image.flush()
    }
}
