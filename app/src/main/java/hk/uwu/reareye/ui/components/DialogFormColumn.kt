package hk.uwu.reareye.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialogFormColumn(
    modifier: Modifier = Modifier,
    maxHeight: Dp? = null,
    verticalSpacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val noRelocationBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = 0f
        }
    }
    val boundedMaxHeight = remember(windowHeight, maxHeight) {
        val viewportLimit = if (windowHeight > 0.dp) {
            (windowHeight * 0.68f).coerceAtLeast(240.dp)
        } else {
            null
        }
        when {
            maxHeight != null && viewportLimit != null -> minOf(maxHeight, viewportLimit)
            maxHeight != null -> maxHeight
            viewportLimit != null -> viewportLimit
            else -> null
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides noRelocationBringIntoViewSpec) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (boundedMaxHeight != null) {
                        Modifier.heightIn(max = boundedMaxHeight)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}
