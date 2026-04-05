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

private data class NavigationDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun RearNavigationBar(
    currentScreen: String,
    navigationBarMode: ModuleNavigationBarMode,
    backdrop: Backdrop,
    onScreenSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavigationDestination(
            route = "home",
            label = stringResource(R.string.home_navigation),
            icon = Icons.Rounded.Cottage,
        ),
        NavigationDestination(
            route = "config",
            label = stringResource(R.string.configuration_navigation),
            icon = Icons.Rounded.Settings,
        ),
        NavigationDestination(
            route = "about",
            label = stringResource(R.string.about_navigation),
            icon = Icons.Rounded.Info,
        ),
    )

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
