package hk.uwu.reareye.ui.screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.components.config.AppListSelectorScreen
import hk.uwu.reareye.ui.components.config.ConfigNodeRow
import hk.uwu.reareye.ui.config.ConfigCategory
import hk.uwu.reareye.ui.config.ConfigGroup
import hk.uwu.reareye.ui.config.ConfigItem
import hk.uwu.reareye.ui.config.ConfigNode
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.config.REAREyeConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private sealed interface ConfigRoute {
    data object Root : ConfigRoute
    data class Category(val category: ConfigCategory) : ConfigRoute
    data class AppList(val item: ConfigItem) : ConfigRoute
}

@Composable
fun ConfigScreen(onAppListModeChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }

    var routeStack by remember { mutableStateOf(listOf<ConfigRoute>(ConfigRoute.Root)) }
    val currentRoute = routeStack.last()
    val isAppListMode = currentRoute is ConfigRoute.AppList
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(isAppListMode) {
        onAppListModeChange(isAppListMode)
    }

    BackHandler(enabled = routeStack.size > 1) {
        routeStack = routeStack.dropLast(1)
    }

    Scaffold(
        topBar = {
            if (!isAppListMode) {
                TopAppBar(
                    title = when (currentRoute) {
                        ConfigRoute.Root -> stringResource(R.string.configuration_title)
                        is ConfigRoute.Category -> stringResource(currentRoute.category.titleRes)
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = routeStack,
            transitionSpec = {
                val forward = targetState.size >= initialState.size

                (fadeIn(
                    animationSpec = tween(
                        durationMillis = 260,
                        delayMillis = 40,
                        easing = LinearOutSlowInEasing,
                    )
                ) + slideInHorizontally(
                    animationSpec = tween(
                        durationMillis = 320,
                        easing = FastOutSlowInEasing,
                    )
                ) { fullWidth ->
                    if (forward) fullWidth / 5 else -fullWidth / 5
                }) togetherWith (
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = FastOutLinearInEasing,
                            )
                        ) + slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = 240,
                                easing = FastOutLinearInEasing,
                            )
                        ) { fullWidth ->
                            if (forward) -fullWidth / 6 else fullWidth / 6
                        }
                        )
            },
            label = "ConfigRouteTransition"
        ) { stack ->
            when (val route = stack.last()) {
                ConfigRoute.Root -> ConfigNodeList(
                    nodes = REAREyeConfig,
                    prefsManager = prefsManager,
                    contentPadding = paddingValues,
                    scrollBehavior = scrollBehavior,
                    onOpenCategory = { category ->
                        routeStack = routeStack + ConfigRoute.Category(category)
                    },
                    onOpenAppList = { item ->
                        routeStack = routeStack + ConfigRoute.AppList(item)
                    }
                )

                is ConfigRoute.Category -> ConfigNodeList(
                    nodes = route.category.children,
                    prefsManager = prefsManager,
                    contentPadding = paddingValues,
                    scrollBehavior = scrollBehavior,
                    onOpenCategory = { category ->
                        routeStack = routeStack + ConfigRoute.Category(category)
                    },
                    onOpenAppList = { item ->
                        routeStack = routeStack + ConfigRoute.AppList(item)
                    }
                )

                is ConfigRoute.AppList -> AppListSelectorScreen(
                    configItem = route.item,
                    prefsManager = prefsManager,
                    onCancel = { routeStack = routeStack.dropLast(1) },
                    onSave = { routeStack = routeStack.dropLast(1) }
                )
            }
        }
    }
}

@Composable
private fun ConfigNodeList(
    nodes: List<ConfigNode>,
    prefsManager: PrefsManager,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenCategory: (ConfigCategory) -> Unit,
    onOpenAppList: (ConfigItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = 12.dp),
        contentPadding = contentPadding,
        overscrollEffect = null
    ) {
        itemsIndexed(nodes, key = { index, node -> node.key ?: "node_$index" }) { index, node ->
            if (node is ConfigGroup) {
                Card(
                    modifier = Modifier
                        .padding(top = if (index == 0) 12.dp else 8.dp)
                        .fillMaxWidth()
                ) {
                    node.children.forEach { child ->
                        ConfigNodeRow(
                            node = child,
                            prefsManager = prefsManager,
                            onOpenCategory = onOpenCategory,
                            onOpenAppList = onOpenAppList
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .padding(top = if (index == 0) 12.dp else 8.dp)
                        .fillMaxWidth()
                ) {
                    ConfigNodeRow(
                        node = node,
                        prefsManager = prefsManager,
                        onOpenCategory = onOpenCategory,
                        onOpenAppList = onOpenAppList
                    )
                }
            }
        }
    }
}
