package pe.gob.reniec.dnie.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

/** The indicator reads animation frames in drawing, leaving labels stable. */
@Composable
fun AetherSegmentedTabs(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, tags: List<String> = emptyList()) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp).selectableGroup()) {
        val cell = maxWidth / items.size
        val position = animateDpAsState(cell * selectedIndex, aetherSpring(reduced), label = "segmented-position")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.width(cell).fillMaxHeight().graphicsLayer { translationX = with(density) { position.value.toPx() } * if (rtl) -1f else 1f }.testTag("segmented_indicator")
                .clip(MaterialTheme.shapes.small).background(if (MaterialTheme.colorScheme.surface.luminance() < .2f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, text ->
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                    .then(tags.getOrNull(i)?.let { Modifier.testTag(it) } ?: Modifier)
                    .selectable(selectedIndex == i, role = Role.Tab, onClick = { if (i != selectedIndex) onSelect(i) })
                    .padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    val textColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selectedIndex == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = aetherSpring(reduced),
                        label = "segmented-text-color-$i"
                    )
                    Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                        color = textColor)
                }
            }
        }
    }
}
