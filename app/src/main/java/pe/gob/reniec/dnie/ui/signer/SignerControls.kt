package pe.gob.reniec.dnie.ui.signer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import pe.gob.reniec.dnie.ui.design.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.domain.signing.*
import pe.gob.reniec.dnie.pdf.*

/** Options only; no credentials, NFC, PDF processing or platform result launchers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignerControls(viewModel: SignerViewModel, workspace: SigningWorkspace, doc: SigningDocument,
    enabled: Boolean, editing: Boolean, batch: Boolean, onReadIdentity: () -> Unit,
    onReadCertificates: () -> Unit, onExportCertificate: (Boolean) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    var tab by rememberSaveable { mutableStateOf(0) }
    val config = doc.options
    val look = config.appearance
    fun appearance(change: (SignatureAppearance) -> SignatureAppearance) {
        viewModel.updateOptions { it.copy(appearance = change(it.appearance)) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StampTemplate.entries.toList()) { template ->
            FilterChip(selected = look == template.apply(config).appearance, enabled = enabled,
                onClick = { viewModel.updateOptions(template::apply) }, label = { Text(template.label) })
        }
    }
    AetherSegmentedTabs(listOf("Apariencia", "Datos y TSA", "Páginas y Titular"), tab, { tab = it })
    androidx.compose.animation.AnimatedContent(tab, transitionSpec = { aetherTabTransition(reducedMotion) }, label = "signer-controls") { selectedTab ->
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (selectedTab) {
            0 -> {
                SwitchRow("Mostrar sello en el PDF", config.visible, enabled) { value -> viewModel.updateOptions { it.copy(visible = value) } }
                if (config.visible) {
                    ChoiceChips(StampStyle.entries.toList(), look.style, enabled, { it.label }) { style ->
                        appearance { it.copy(style = style, showHandwritten = style == StampStyle.INK || it.showHandwritten) }
                    }
                    if (look.style != StampStyle.HANDWRITTEN_ONLY) {
                        Text("Color del sello", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StampColorTheme.entries.forEach { color ->
                                FilterChip(selected = look.colorTheme == color, enabled = enabled,
                                    onClick = { appearance { it.copy(colorTheme = color) } },
                                    leadingIcon = { Canvas(Modifier.size(12.dp)) { drawCircle(Color(0xFF000000 or color.accentRgb.toLong())) } },
                                    label = { Text(color.label) })
                            }
                        }
                        ChoiceChips(StampBackgroundMode.entries.toList(), look.backgroundMode, enabled, { it.label }) { value -> appearance { it.copy(backgroundMode = value) } }
                        ChoiceChips(StampDensity.entries.toList(), look.density, enabled, { it.label }) { value -> appearance { it.copy(density = value) } }
                        SwitchRow("Mostrar borde", look.showBorder, enabled) { value -> appearance { it.copy(showBorder = value) } }
                    }
                }
                TextButton(enabled = enabled, onClick = viewModel::restoreDefaultDesign) { Text("Restaurar diseño predeterminado") }
            }
            1 -> {
                SwitchRow("Sello de tiempo RENIEC · PAdES-T", config.reniecTimestamp, enabled) { value -> viewModel.updateOptions { it.copy(reniecTimestamp = value) } }
                if (config.reniecTimestamp) Text("Requiere internet. Se envía solo el resumen de la firma.", style = MaterialTheme.typography.bodySmall)
                if (config.visible && look.style != StampStyle.HANDWRITTEN_ONLY) {
                    Text("Insignia", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf(StampBadgeType.SEAL_VECTOR, StampBadgeType.CHECK_SEAL, StampBadgeType.LOCK_SEAL, StampBadgeType.NONE), look.badge, enabled, { it.label }) { value -> appearance { it.copy(badge = value) } }
                    ChoiceChips(StampDateFormat.entries.toList(), look.dateFormat, enabled, { it.label }) { value -> appearance { it.copy(dateFormat = value) } }
                    OutlinedTextField(look.customHeader, { value -> appearance { it.copy(customHeader = value.take(160)) } }, enabled = enabled,
                        label = { Text("Título del sello") }, modifier = Modifier.fillMaxWidth())
                    if (config.reniecTimestamp) SwitchRow("Mostrar distintivo TSA", look.showTsaBadge, enabled) { value -> appearance { it.copy(showTsaBadge = value) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(look.showDni, { appearance { it.copy(showDni = !it.showDni) } }, enabled = enabled, label = { Text("DNI") })
                        FilterChip(look.showDate, { appearance { it.copy(showDate = !it.showDate) } }, enabled = enabled, label = { Text("Fecha") })
                        FilterChip(look.showTime, { appearance { it.copy(showTime = !it.showTime) } }, enabled = enabled, label = { Text("Hora") })
                        FilterChip(look.showReason, { appearance { it.copy(showReason = !it.showReason) } }, enabled = enabled, label = { Text("Motivo") })
                        FilterChip(look.showLocation, { appearance { it.copy(showLocation = !it.showLocation) } }, enabled = enabled, label = { Text("Lugar") })
                    }
                }
                if (look.showReason) OutlinedTextField(config.reason, { text -> viewModel.updateOptions { it.copy(reason = text.take(160)) } }, enabled = enabled, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
                if (look.showLocation) OutlinedTextField(config.location, { text -> viewModel.updateOptions { it.copy(location = text.take(120)) } }, enabled = enabled, label = { Text("Lugar") }, modifier = Modifier.fillMaxWidth())
            }
            2 -> {
                StampPageTargetSelector(config.pageTarget, enabled) { value -> viewModel.updateOptions {
                    it.copy(pageTarget = value, pageNumber = when (value) { StampPageTarget.FIRST_PAGE -> 1; StampPageTarget.LAST_PAGE -> doc.pages.size; else -> it.pageNumber })
                } }
                SwitchRow("Incluir firma manuscrita del DNI", look.requiresHandwritten,
                    enabled && look.style !in listOf(StampStyle.INK, StampStyle.HANDWRITTEN_ONLY) && (workspace.mark != null || look.showHandwritten)) { value -> appearance { it.copy(showHandwritten = value) } }
                if (look.style == StampStyle.HANDWRITTEN_ONLY) Text("Solo se verá la rúbrica. La firma digital sigue incorporada en el PDF.", style = MaterialTheme.typography.bodySmall)
                if (workspace.mark == null) {
                    Text(workspace.markError ?: "Lee Identidad para obtener tu firma manuscrita, si está disponible.")
                    OutlinedButton(enabled = editing, onClick = onReadIdentity) { Text("Leer firma manuscrita") }
                } else if (look.requiresHandwritten) {
                    SwitchRow("Confirmo que la imagen pertenece al titular que firmará", workspace.markConfirmed, enabled, viewModel::confirmMark)
                }
                pe.gob.reniec.dnie.ui.design.FeatureSection("Certificado del firmante") {
                    Text(if (workspace.certificate == null) "Lee los certificados para previsualizar el nombre real y exportarlos." else workspace.previewName)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = editing, onClick = onReadCertificates) { Text("Leer certificados") }
                        if (workspace.certificate != null) {
                            TextButton(enabled = editing, onClick = { onExportCertificate(false) }) { Text("CER") }
                            TextButton(enabled = editing, onClick = { onExportCertificate(true) }) { Text("PEM") }
                        }
                    }
                    Text("La clave privada permanece en el DNI.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    }
    if (batch) OutlinedButton(enabled = enabled, onClick = viewModel::applyOptionsToAll) { Text("Copiar configuración a los PDF pendientes") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(values: List<T>, selected: T, enabled: Boolean, label: (T) -> String, change: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value -> FilterChip(selected = selected == value, enabled = enabled, onClick = { change(value) }, label = { Text(label(value)) }) }
    }
}

@Composable
internal fun SwitchRow(text: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, modifier = Modifier.weight(1f).padding(top = 12.dp, end = 8.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}
