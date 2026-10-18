package pe.gob.reniec.dnie.ui.design

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Override is for isolated previews/tests; the default always observes the real system preference. */
val LocalReducedMotionOverride = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun rememberReducedMotion(): Boolean {
    LocalReducedMotionOverride.current?.let { return it }
    val app = LocalContext.current.applicationContext as pe.gob.reniec.dnie.DnieApplication
    return app.motion.reduced.collectAsStateWithLifecycle().value
}

fun <T> aetherSpring(reduced: Boolean, stiffness: Float = 380f, damping: Float = .82f): FiniteAnimationSpec<T> =
    if (reduced) snap() else spring(stiffness = stiffness, dampingRatio = damping)

fun AnimatedContentTransitionScope<Int>.aetherTabTransition(reduced: Boolean): ContentTransform =
    if (reduced) EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }))
    else (slideInHorizontally(aetherSpring(false)) { if (targetState > initialState) it / 6 else -it / 6 } + fadeIn(tween(140)))
        .togetherWith(slideOutHorizontally(aetherSpring(false)) { if (targetState > initialState) -it / 6 else it / 6 } + fadeOut(tween(100)))
        .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }))

fun <S> AnimatedContentTransitionScope<S>.aetherContentTransition(reduced: Boolean, completed: Boolean = false): ContentTransform =
    if (reduced) EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }))
    else (fadeIn(tween(180)) + scaleIn(initialScale = .96f, animationSpec = spring(stiffness = 380f,
        dampingRatio = if (completed) Spring.DampingRatioMediumBouncy else .82f)))
        .togetherWith(fadeOut(tween(120))).using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> spring(stiffness = 380f, dampingRatio = .82f) }))

@Composable
fun Modifier.aetherPressScale(source: MutableInteractionSource): Modifier {
    val reduced = rememberReducedMotion()
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && !reduced) .98f else 1f,
        aetherSpring(reduced, 500f), label = "aether-press")
    // Read frame values only in drawing, keeping text/list composition stable.
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

fun Modifier.aetherPressScale(enabled: Boolean = true): Modifier = this

fun Modifier.aetherAnimateSize(): Modifier = composed {
    val reduced = rememberReducedMotion()
    if (reduced) this else animateContentSize(aetherSpring(false))
}

