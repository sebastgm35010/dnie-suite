package pe.gob.reniec.dnie.desktop.ui

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import javax.swing.filechooser.FileSystemView

/** Resolves user-selected shell shortcuts before passing real paths to engines. */
internal object DesktopFileSelection {
    fun resolve(selected: File, view: FileSystemView=FileSystemView.getFileSystemView(),
                recent: Path?=recentDirectory()): Path {
        var current=selected
        val seen=mutableSetOf<Path>()
        repeat(8) {
            val path=current.toPath().toAbsolutePath().normalize()
            require(seen.add(path) && !Files.isSymbolicLink(path)) { "Acceso directo circular o enlace no admitido" }
            // Windows Recent can return a virtual name with the .lnk hidden,
            // even though that displayed path does not exist on the filesystem.
            if(!Files.exists(path,NOFOLLOW_LINKS) && path.parent==recent?.toAbsolutePath()?.normalize()) {
                val link=path.resolveSibling(path.fileName.toString()+".lnk")
                if(Files.isRegularFile(link,NOFOLLOW_LINKS)) { current=link.toFile();return@repeat }
            }
            if(view.isLink(current)) {
                current=requireNotNull(view.getLinkLocation(current)) { "No se encuentra el destino del acceso directo" }
                return@repeat
            }
            require(Files.isRegularFile(path,NOFOLLOW_LINKS)) { "Selecciona un archivo existente" }
            return path
        }
        error("Demasiados accesos directos encadenados")
    }
    private fun recentDirectory(): Path? = if(System.getProperty("os.name").startsWith("Windows"))
        System.getenv("APPDATA")?.let { Path.of(it,"Microsoft/Windows/Recent") } else null
}
