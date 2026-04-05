package hk.uwu.reareye.ui.components.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val DEFAULT_VISIBILITY_DELAY = 0

@Composable
fun ArtRevealItem(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    ArtRevealContainer(
        visible = visible,
        delayMillis = delayMillis,
        enterAlphaDurationMillis = 240,
        enterTransformDurationMillis = 360,
        exitAlphaDurationMillis = 120,
        exitTransformDurationMillis = 160,
        hiddenEnterScale = 0.985f,
        hiddenExitScale = 0.992f,
        slideDivisor = 14,
    ) {
        content()
    }
}

@Composable
fun ArtStaggeredReveal(
    visible: Boolean,
    revealKey: Any,
    delayMillis: Int = DEFAULT_VISIBILITY_DELAY,
    content: @Composable () -> Unit,
) {
    ArtRevealContainer(
        visible = visible,
        revealKey = revealKey,
        delayMillis = delayMillis,
        enterAlphaDurationMillis = 220,
        enterTransformDurationMillis = 320,
        exitAlphaDurationMillis = 90,
        exitTransformDurationMillis = 120,
        hiddenEnterScale = 0.988f,
        hiddenExitScale = 0.992f,
        slideDivisor = 18,
    ) {
        content()
    }
}

@Composable
private fun ArtRevealContainer(
    visible: Boolean,
    delayMillis: Int,
    enterAlphaDurationMillis: Int,
    enterTransformDurationMillis: Int,
    exitAlphaDurationMillis: Int,
    exitTransformDurationMillis: Int,
    hiddenEnterScale: Float,
    hiddenExitScale: Float,
    slideDivisor: Int,
    revealKey: Any = Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var contentHeightPx by remember(revealKey) { mutableIntStateOf(0) }
    var startedVisible by remember(revealKey) { mutableStateOf(false) }

    LaunchedEffect(visible, revealKey, delayMillis) {
        if (visible) {
            if (delayMillis > 0) {
                delay(delayMillis.toLong())
            }
            startedVisible = true
        } else {
            startedVisible = false
        }
    }

    val targetAlpha = if (startedVisible) 1f else 0f
    if (startedVisible) 1f else hiddenExitScale
    val hiddenOffsetPx = remember(contentHeightPx, density, slideDivisor) {
        if (contentHeightPx > 0) {
            contentHeightPx / slideDivisor.toFloat()
        } else {
            with(density) { 18.dp.toPx() }
        }
    }
    val targetTranslationY = if (startedVisible) 0f else hiddenOffsetPx

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (startedVisible) enterAlphaDurationMillis else exitAlphaDurationMillis,
            delayMillis = 0,
            easing = if (startedVisible) LinearOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "ArtRevealAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (startedVisible) 1f else if (visible) hiddenEnterScale else hiddenExitScale,
        animationSpec = tween(
            durationMillis = if (startedVisible) enterTransformDurationMillis else exitTransformDurationMillis,
            delayMillis = 0,
            easing = if (startedVisible) FastOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "ArtRevealScale",
    )
    val translationY by animateFloatAsState(
        targetValue = targetTranslationY,
        animationSpec = tween(
            durationMillis = if (startedVisible) enterTransformDurationMillis else exitTransformDurationMillis,
            delayMillis = 0,
            easing = if (startedVisible) FastOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "ArtRevealTranslationY",
    )

    Box(
        modifier = Modifier
            .onSizeChanged { contentHeightPx = it.height }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                this.translationY = translationY
            }
    ) {
        content()
    }
}

@Composable
fun <T> ArtSwapContent(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any = { it as Any },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        modifier = modifier.graphicsLayer { clip = true },
        targetState = targetState,
        contentKey = contentKey,
        transitionSpec = {
            (fadeIn(
                animationSpec = tween(
                    durationMillis = 200,
                    delayMillis = 40,
                    easing = LinearOutSlowInEasing,
                )
            ) + scaleIn(
                initialScale = 0.992f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing,
                ),
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing,
                )
            ) { it / 20 }) togetherWith (
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = 100,
                            easing = FastOutLinearInEasing,
                        )
                    ) + scaleOut(
                        targetScale = 0.996f,
                        animationSpec = tween(
                            durationMillis = 120,
                            easing = FastOutLinearInEasing,
                        ),
                    ) + slideOutVertically(
                        animationSpec = tween(
                            durationMillis = 120,
                            easing = FastOutLinearInEasing,
                        )
                    ) { it / 24 }
                    )
        },
        label = "ArtSwapContent",
        content = { state -> content(state) },
    )
}
