package hk.uwu.reareye.ui.components.navigation

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.config.ModuleNavigationBarMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
private data class NavigationDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private const val HOME_ROUTE = "home"
private const val CONFIG_ROUTE = "config"
private const val ABOUT_ROUTE = "about"

@Composable
fun RearNavigationBar(
    modifier: Modifier = Modifier,
    currentScreen: String,
    navigationBarMode: ModuleNavigationBarMode,
    backdrop: Backdrop,
    shadowVisibilityProgress: Float = 1f,
    onScreenSelected: (String) -> Unit,
) {
    val homeLabel = stringResource(R.string.home_navigation)
    val configLabel = stringResource(R.string.configuration_navigation)
    val aboutLabel = stringResource(R.string.about_navigation)
    val items = remember(homeLabel, configLabel, aboutLabel) {
        listOf(
            NavigationDestination(
                route = HOME_ROUTE,
                label = homeLabel,
                icon = Icons.Rounded.Cottage,
            ),
            NavigationDestination(
                route = CONFIG_ROUTE,
                label = configLabel,
                icon = Icons.Rounded.Settings,
            ),
            NavigationDestination(
                route = ABOUT_ROUTE,
                label = aboutLabel,
                icon = Icons.Rounded.Info,
            ),
        )
    }

    if (navigationBarMode == ModuleNavigationBarMode.NORMAL) {
        NavigationBar(
            modifier = modifier,
            color = MiuixTheme.colorScheme.surface,
            mode = NavigationBarDisplayMode.IconAndText,
        ) {
            items.forEach { item ->
                NavigationBarItem(
                    selected = currentScreen == item.route,
                    onClick = { onScreenSelected(item.route) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
        return
    }

    val enableGlass = navigationBarMode == ModuleNavigationBarMode.FLOATING_GLASS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val selectedIndex = items.indexOfFirst { it.route == currentScreen }.coerceAtLeast(0)

    FloatingBottomBar(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(
                bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
            ),
        selectedIndex = selectedIndex,
        onSelected = { onScreenSelected(items[it].route) },
        backdrop = backdrop,
        tabsCount = items.size,
        isBlurEnabled = enableGlass,
        shadowVisibilityProgress = shadowVisibilityProgress,
    ) {
        items.forEachIndexed { index, item ->
            FloatingBottomBarItem(
                onClick = { onScreenSelected(items[index].route) },
                modifier = Modifier.defaultMinSize(minWidth = 76.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}
