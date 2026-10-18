package pe.gob.reniec.dnie.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState

/** Shared surface geometry, preserving semantic success/error colors passed by each feature. */
@Composable
fun AetherCard(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    animateSize: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val cardModifier = if (animateSize) modifier.aetherAnimateSize() else modifier
    MaterialCard(cardModifier, shape, colors, elevation, border, content)
}

@Composable
fun AetherCard(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    animateSize: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit) {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val reduced = rememberReducedMotion()
    val activeBorderColor by animateColorAsState(
        targetValue = if ((pressed || focused) && enabled) MaterialTheme.colorScheme.primary.copy(alpha = .65f)
                      else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = aetherSpring(reduced),
        label = "aether-card-border"
    )
    val effectiveBorder = if (border != null) BorderStroke(border.width, activeBorderColor) else null
    val cardModifier = (if (animateSize) modifier.aetherAnimateSize() else modifier)
        .heightIn(min = 48.dp)
        .aetherPressScale(interactionSource)
    MaterialCard(onClick, cardModifier, enabled,
        shape, colors, elevation, effectiveBorder, interactionSource, content)
}
