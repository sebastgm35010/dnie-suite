package pe.gob.reniec.dnie.desktop.ui

import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import javax.swing.JComponent
import java.io.File

/** Native DnD only imports paths; document preparation stays in the application controller. */
object PdfDropSupport {
    fun install(component: JComponent,enabled: ()->Boolean,highlight: (Boolean)->Unit,import: (List<File>)->Unit) {
        DropTarget(component,object: DropTargetAdapter() {
            private fun allowed(e: DropTargetDragEvent)=enabled() && e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            override fun dragEnter(e: DropTargetDragEvent) {val ok=allowed(e);highlight(ok);if(ok)e.acceptDrag(DnDConstants.ACTION_COPY) else e.rejectDrag()}
            override fun dragOver(e: DropTargetDragEvent) {if(!allowed(e)){highlight(false);e.rejectDrag()}}
            override fun dragExit(e: DropTargetEvent) {highlight(false)}
            override fun drop(e: DropTargetDropEvent) {
                highlight(false)
                if(!enabled() || !e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)){e.rejectDrop();return}
                e.acceptDrop(DnDConstants.ACTION_COPY)
                val files=runCatching {(e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>().filter {it.extension.equals("pdf",true)}}.getOrDefault(emptyList())
                if(files.isNotEmpty())import(files)
                e.dropComplete(files.isNotEmpty())
            }
        })
    }
}
