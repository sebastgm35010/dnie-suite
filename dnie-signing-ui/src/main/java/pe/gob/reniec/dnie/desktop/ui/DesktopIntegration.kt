package pe.gob.reniec.dnie.desktop.ui

import java.nio.file.Files
import java.nio.file.Path
import pe.gob.reniec.dnie.desktop.orchestration.*

internal object DesktopIntegration {
    fun helper(): Path? = runCatching {
        val jar=Path.of(DesktopIntegration::class.java.protectionDomain.codeSource.location.toURI())
        val executable=jar.parent.parent.resolve("tools/bridge-integration/DNIeSuiteBridgeAdapter.exe")
        executable.takeIf { Files.isRegularFile(it) }
    }.getOrNull()
    fun coordinator()=DesktopProductCoordinator(WindowsBridgeGateway(helper()))
}
