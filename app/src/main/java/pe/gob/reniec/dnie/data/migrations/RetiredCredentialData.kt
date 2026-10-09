package pe.gob.reniec.dnie.data.migrations

import java.io.File

/** Deletion is limited to the retired feature's private paths and exact Keystore aliases. */
object RetiredCredentialData {
    val aliases = listOf("dnie-passkeys-biometric-v1", "dnie-passkeys-independent-v1")
    private fun targets(files: File, noBackup: File) = listOf(File(files.canonicalFile, "passkeys")) +
        listOf("", ".bak", ".new").map { File(noBackup.canonicalFile, "passkeys-biometric.bin$it") }
    fun hasFiles(files: File, noBackup: File) = targets(files, noBackup).any {
        it.exists() || it.canonicalFile != it.absoluteFile
    }
    fun remove(files: File, noBackup: File, deleteAlias: (String) -> Unit): Boolean {
        var success = true
        for (target in targets(files, noBackup)) {
            try { if (!removeTree(target)) success = false } catch (_: Exception) { success = false }
        }
        for (alias in aliases) {
            try { deleteAlias(alias) } catch (_: Exception) { success = false }
        }
        return success
    }
    private fun removeTree(file: File): Boolean {
        // Delete a link itself; never traverse into another feature or external directory.
        if (file.canonicalFile != file.absoluteFile) return file.delete()
        if (!file.exists()) return true
        var success = true
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            for (child in children) if (!removeTree(child)) success = false
        }
        return (file.delete() || !file.exists()) && success
    }
}
