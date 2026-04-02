package hk.uwu.reareye.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberAcrylicHazeState(): HazeState {
    return remember { HazeState() }
}

@Composable
fun rememberAcrylicHazeStyle(): HazeStyle {
    val surface = MiuixTheme.colorScheme.surface
    val tintAlpha = if (surface.luminance() < 0.5f) 0.72f else 0.82f

    return remember(surface, tintAlpha) {
        HazeStyle(
            backgroundColor = surface,
            tint = HazeTint(surface.copy(alpha = tintAlpha)),
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
fun Modifier.rearAcrylicEffect(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    blurRadius: Dp = 24.dp,
): Modifier = this.hazeEffect(
    state = hazeState,
    style = hazeStyle,
) {
    this.blurRadius = blurRadius
    inputScale = HazeInputScale.Fixed(0.35f)
    noiseFactor = 0f
    forceInvalidateOnPreDraw = false
}

@OptIn(ExperimentalHazeApi::class)
fun Modifier.rearAcrylicSource(hazeState: HazeState): Modifier {
    return this.hazeSource(state = hazeState)
}
