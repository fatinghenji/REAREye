package hk.uwu.reareye.ui.components.config.template

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun <T : Any> TemplateConfigRouteTransition(
    modifier: Modifier = Modifier,
    target: T?,
    contentKey: (T?) -> Any? = { it },
    templateContent: @Composable (T) -> Unit,
    content: @Composable () -> Unit,
) {
    AnimatedContent(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = true },
        targetState = target,
        contentKey = contentKey,
        transitionSpec = {
            val forward = targetState != null

            fadeIn(
                animationSpec = tween(
                    durationMillis = 210,
                    delayMillis = 50,
                    easing = LinearOutSlowInEasing,
                )
            ) + slideInHorizontally(
                animationSpec = tween(
                    durationMillis = 280,
                    easing = FastOutSlowInEasing,
                )
            ) { fullWidth ->
                if (forward) fullWidth / 9 else -fullWidth / 9
            } togetherWith (
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = 110,
                            easing = FastOutLinearInEasing,
                        )
                    ) + slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = 190,
                            easing = FastOutLinearInEasing,
                        )
                    ) { fullWidth ->
                        if (forward) -fullWidth / 12 else fullWidth / 12
                    }
                    )
        },
        label = "TemplateConfigRouteTransition",
    ) { currentTarget ->
        if (currentTarget != null) {
            templateContent(currentTarget)
        } else {
            content()
        }
    }
}
