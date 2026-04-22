package hk.uwu.reareye.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape

@Composable
fun OverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = MiuixTheme.colorScheme.onBackground,
    summary: String? = null,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = DpSize(12.dp, 12.dp),
    insideMargin: DpSize = DpSize(24.dp, 24.dp),
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val currentOnDismissFinished = rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }
    val animationProgress = remember { Animatable(0f) }
    val dimProgress = remember { Animatable(0f) }

    if (!show && !internalVisible.value) return

    Dialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onDismissRequest != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val windowProvider =
            androidx.compose.ui.platform.LocalView.current.parent as? DialogWindowProvider
        DisposableEffect(windowProvider) {
            val window = windowProvider?.window
            window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            onDispose {}
        }

        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        val windowWidth = windowInfo.containerDpSize.width
        val windowHeight = windowInfo.containerDpSize.height
        val isLargeScreen = windowHeight >= 480.dp && windowWidth >= 840.dp
        val windowHeightPx = with(density) { windowHeight.toPx() }
        val topInset = maxOf(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            WindowInsets.captionBar.asPaddingValues().calculateTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
        )
        val imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val shape = miuixShape(if (isLargeScreen) 28.dp else 32.dp)

        LaunchedEffect(show, isLargeScreen) {
            if (show) {
                internalVisible.value = true
                launch {
                    dimProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    )
                }
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = if (isLargeScreen) {
                        spring(dampingRatio = 0.9f, stiffness = 420f)
                    } else {
                        spring(dampingRatio = 0.88f, stiffness = 420f)
                    },
                )
            } else {
                if (!internalVisible.value) return@LaunchedEffect
                launch {
                    dimProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    )
                }
                animationProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
                internalVisible.value = false
                currentOnDismissFinished.value?.invoke()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enableWindowDim) {
                        MiuixTheme.colorScheme.windowDimming.copy(
                            alpha = MiuixTheme.colorScheme.windowDimming.alpha * dimProgress.value,
                        )
                    } else {
                        Color.Transparent
                    }
                )
                .pointerInput(onDismissRequest) {
                    detectTapGestures { onDismissRequest?.invoke() }
                }
                .then(
                    if (defaultWindowInsetsPadding) {
                        Modifier.padding(top = topInset, bottom = imeBottomInset)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = outsideMargin.width, vertical = outsideMargin.height),
        ) {
            Column(
                modifier = modifier
                    .align(if (isLargeScreen) Alignment.Center else Alignment.BottomCenter)
                    .graphicsLayer {
                        if (isLargeScreen) {
                            val scale = 0.8f + 0.2f * animationProgress.value
                            scaleX = scale
                            scaleY = scale
                            alpha = animationProgress.value
                        } else {
                            translationY = (1f - animationProgress.value) * windowHeightPx
                        }
                    }
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .heightIn(max = if (isLargeScreen) windowHeight * (2f / 3f) else windowHeight * 0.86f)
                    .clip(shape)
                    .background(backgroundColor)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .padding(horizontal = insideMargin.width, vertical = insideMargin.height),
            ) {
                title?.let {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        text = it,
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = titleColor,
                    )
                }
                summary?.let {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        text = it,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        textAlign = TextAlign.Center,
                        color = summaryColor,
                    )
                }
                content()
            }
        }
    }
}
