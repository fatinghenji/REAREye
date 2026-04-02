package hk.uwu.reareye.ui.components.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

private const val DEFAULT_VISIBILITY_DELAY = 0

@Composable
fun ArtRevealItem(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 240,
                delayMillis = delayMillis,
                easing = LinearOutSlowInEasing,
            )
        ) + scaleIn(
            initialScale = 0.985f,
            animationSpec = tween(
                durationMillis = 360,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = 360,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing,
            )
        ) { it / 14 },
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 120,
                easing = FastOutLinearInEasing,
            )
        ) + scaleOut(
            targetScale = 0.992f,
            animationSpec = tween(
                durationMillis = 160,
                easing = FastOutLinearInEasing,
            ),
        ),
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
    val visibleState = remember(revealKey) { MutableTransitionState(false) }

    LaunchedEffect(visible, revealKey, delayMillis) {
        if (visible) {
            if (delayMillis > 0) {
                delay(delayMillis.toLong())
            }
            visibleState.targetState = true
        } else {
            visibleState.targetState = false
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing,
            )
        ) + scaleIn(
            initialScale = 0.988f,
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing,
            )
        ) { it / 18 },
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 90,
                easing = FastOutLinearInEasing,
            )
        ) + scaleOut(
            targetScale = 0.992f,
            animationSpec = tween(
                durationMillis = 120,
                easing = FastOutLinearInEasing,
            ),
        ),
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
