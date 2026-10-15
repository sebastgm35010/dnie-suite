package pe.gob.reniec.dnie.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.ui.design.aetherSpring

/** Infinite frame values are read in drawing only; reduced motion creates no infinite transition. */
@Composable
internal fun NfcOperationVisual(phase: DnieOperationPhase, reduced: Boolean) {
    val active = phase == DnieOperationPhase.AWAITING_TAG || phase == DnieOperationPhase.TAG_ENGAGED
    val target = when (phase) {
        DnieOperationPhase.ERROR -> MaterialTheme.colorScheme.error
        DnieOperationPhase.TAG_ENGAGED, DnieOperationPhase.COMPLETED -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val tint = animateColorAsState(target, aetherSpring(reduced), label = "nfc-state-color")
    val wave = if (active && !reduced) rememberInfiniteTransition(label = "nfc-radar")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "nfc-wave") else null
    Box(Modifier.size(128.dp).testTag(if (wave == null) "nfc_visual_static" else "nfc_visual_animated"), contentAlignment = Alignment.Center) {
        if (active) repeat(2) { index ->
            Canvas(Modifier.size(if (wave == null) (86 + index * 18).dp else 86.dp).graphicsLayer {
                val fraction = wave?.let { (it.value + index * .5f) % 1f } ?: 0f
                scaleX = if (wave == null) 1f else 1f + .45f * fraction; scaleY = scaleX
                alpha = if (wave == null) .22f else .35f * (1f - fraction)
            }) { drawCircle(tint.value, style = Stroke(2.dp.toPx())) }
        }
        Box(Modifier.size(80.dp).background(target.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(when (phase) {
                DnieOperationPhase.COMPLETED -> Icons.Default.CheckCircle
                DnieOperationPhase.ERROR -> Icons.Default.ErrorOutline
                else -> Icons.Default.Nfc
            }, null, tint = tint.value, modifier = Modifier.size(48.dp))
        }
    }
}
