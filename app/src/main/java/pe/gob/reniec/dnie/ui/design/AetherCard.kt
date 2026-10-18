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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke

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
    val restBorderColor = MaterialTheme.colorScheme.outlineVariant
    val activeBorderColor = animateColorAsState(
        targetValue = if ((pressed || focused) && enabled) MaterialTheme.colorScheme.primary.copy(alpha = .65f)
                      else restBorderColor,
        animationSpec = aetherSpring(reduced),
        label = "aether-card-border"
    )
    val cardModifier = (if (animateSize) modifier.aetherAnimateSize() else modifier)
        .heightIn(min = 48.dp)
        .aetherPressScale(interactionSource)
        .let { base ->
            if (border != null) {
                base.drawWithContent {
                    drawContent()
                    val current = activeBorderColor.value
                    if (current != restBorderColor) {
                        drawOutline(
                            outline = shape.createOutline(size, layoutDirection, this),
                            color = current,
                            style = Stroke(border.width.toPx())
                        )
                    }
                }
            } else base
        }
    MaterialCard(onClick, cardModifier, enabled,
        shape, colors, elevation, border, interactionSource, content)
}
