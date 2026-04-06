package hk.uwu.reareye.ui.components.card

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ModuleStyleManagerCard(
    title: String,
    summaryLines: List<String>,
    trailing: @Composable (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
    leftAction: @Composable () -> Unit,
    rightAction: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = 620f,
                )
            )
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
        onClick = onCardClick ?: {},
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight(550),
                )
                summaryLines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            leftAction()
            Spacer(Modifier.weight(1f))
            rightAction()
        }
    }
}

@Composable
fun ModuleStyleIconAction(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconAlpha = if (secondaryContainer.luminance() < 0.5f) 0.7f else 0.9f
    val actionIconTint =
        MiuixTheme.colorScheme.onSurface.copy(alpha = actionIconAlpha)
    IconButton(
        minHeight = 35.dp,
        minWidth = 35.dp,
        onClick = onClick,
        backgroundColor = secondaryContainer,
    ) {
        Icon(
            imageVector = icon,
            tint = actionIconTint,
            contentDescription = null,
        )
    }
}

@Composable
fun ModuleStyleDeleteAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconAlpha = if (secondaryContainer.luminance() < 0.5f) 0.7f else 0.9f
    val actionIconTint =
        MiuixTheme.colorScheme.onSurface.copy(alpha = actionIconAlpha)
    IconButton(
        minHeight = 35.dp,
        minWidth = 35.dp,
        onClick = onClick,
        backgroundColor = secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                tint = actionIconTint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                modifier = Modifier.padding(start = 4.dp, end = 3.dp),
                text = text,
                color = actionIconTint,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }
    }
}
