package pe.gob.reniec.dnie.ui.vault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.data.vault.*

/** Not exported. Losing visibility terminates the preview; returning requires a fresh DNI operation. */
class VaultPreviewActivity : ComponentActivity() {
    private var token: String? = null
    private var previewVisible by mutableStateOf(true)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        token = intent.getStringExtra("token")
        val file = runCatching { VaultPreviewFiles(this).file(requireNotNull(token)) }.getOrNull()
        if (savedInstanceState != null || file?.isFile != true) { finish(); return }
        val reader = VaultPreviewReader(file, intent.getStringExtra("mime").orEmpty())
        val name = intent.getStringExtra("name").orEmpty()
        setContent {
            pe.gob.reniec.dnie.ui.theme.DnieTheme {
                if (previewVisible) PreviewContent(name, reader, onClose = { finish() })
                else Surface(modifier = Modifier.fillMaxSize()) {}
            }
        }
    }
    override fun onPause() { previewVisible = false; discard(); super.onPause(); finish() }
    override fun onStop() { discard(); super.onStop(); finish() }
    override fun onDestroy() { discard(); super.onDestroy() }
    private fun discard() { token?.let { runCatching { VaultPreviewFiles(this).delete(it) } }; token = null }
    companion object {
        fun intent(context: Context, ticket: VaultPreviewTicket) = Intent(context, VaultPreviewActivity::class.java)
            .putExtra("token", ticket.token).putExtra("name", ticket.name).putExtra("mime", ticket.mime)
    }
}

@Composable
private fun PreviewContent(name: String, reader: VaultPreviewReader, onClose: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<VaultPreviewFrame?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(page) {
        loading = true; error = null; frame = null
        try { frame = reader.read(page) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = "No se pudo mostrar este contenido. Puedes descifrarlo a un archivo para abrirlo con otra aplicación." }
        finally { loading = false }
    }
    pe.gob.reniec.dnie.ui.design.FeatureScaffold(name, "Vista privada", onBack = onClose) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Vista privada · se cierra al salir", style = MaterialTheme.typography.labelMedium)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it) }
            frame?.let { content ->
                if (content.pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { page-- }, enabled = page > 0 && !loading) { Text("Anterior") }
                    Text("${page + 1} / ${content.pages}")
                    TextButton(onClick = { page++ }, enabled = page + 1 < content.pages && !loading) { Text("Siguiente") }
                }
                content.bitmap?.let {
                    Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) { VaultPreviewImage(it, Modifier.fillMaxSize()) }
                }
                content.text?.let { Text(it, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) }
            }
        }
    }
}
