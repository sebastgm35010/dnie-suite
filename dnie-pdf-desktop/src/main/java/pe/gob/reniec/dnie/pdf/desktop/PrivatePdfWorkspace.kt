package pe.gob.reniec.dnie.pdf.desktop

import java.nio.file.*
import java.nio.file.attribute.*
import java.util.EnumSet

/** A distinct private working directory per PDF. Originals/results stay in the job workspace. */
internal class PrivatePdfWorkspace(parent: Path) : AutoCloseable {
    val directory: Path
    init {
        Files.createDirectories(parent)
        directory = Files.createTempDirectory(parent, "pdf-")
        val acl = Files.getFileAttributeView(directory, AclFileAttributeView::class.java)
        if (acl != null) acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
            .setPrincipal(Files.getOwner(directory)).setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
            .setPermissions(EnumSet.allOf(AclEntryPermission::class.java)).build())
        else Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
    }
    override fun close() {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}
