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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.components.config.AppListSelectorScreen
import hk.uwu.reareye.ui.components.config.BusinessExtraConfigManagerScreen
import hk.uwu.reareye.ui.components.config.BusinessManagerScreen
import hk.uwu.reareye.ui.components.config.CardManagerScreen
import hk.uwu.reareye.ui.components.config.ConfigNodeRow
import hk.uwu.reareye.ui.config.ConfigCategory
import hk.uwu.reareye.ui.config.ConfigGroup
import hk.uwu.reareye.ui.config.ConfigItem
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.ConfigNode
import hk.uwu.reareye.ui.config.ConfigType
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import hk.uwu.reareye.ui.config.REAREyeConfig
import hk.uwu.reareye.ui.theme.AppThemeMode
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
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
    data object BusinessManager : ConfigRoute
    data object CardManager : ConfigRoute
    data object BusinessExtraManager : ConfigRoute
}

@Composable
fun ConfigScreen(
    onAppListModeChange: (Boolean) -> Unit = {},
    onThemeModeChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val prefsManager = remember { context.getPrefsManager() }

    var routeStack by remember { mutableStateOf(listOf<ConfigRoute>(ConfigRoute.Root)) }
    val currentRoute = routeStack.last()
    val isOverlayMode = currentRoute is ConfigRoute.AppList ||
            currentRoute is ConfigRoute.BusinessManager ||
            currentRoute is ConfigRoute.CardManager ||
            currentRoute is ConfigRoute.BusinessExtraManager
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()

    val handlePreferenceChanged = remember(prefsManager, onThemeModeChange) {
        { item: ConfigItem ->
            if (item.key == ConfigKeys.MODULE_THEME_MODE) {
                onThemeModeChange(
                    prefsManager.getInt(
                        ConfigKeys.MODULE_THEME_MODE,
                        AppThemeMode.default.value,
                    )
                )
            }
        }
    }

    LaunchedEffect(isOverlayMode) {
        onAppListModeChange(isOverlayMode)
    }

    BackHandler(enabled = routeStack.size > 1) {
        routeStack = routeStack.dropLast(1)
    }

    Scaffold(
        topBar = {
            if (!isOverlayMode) {
                TopAppBar(
                    modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                    color = Color.Transparent,
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
                    modifier = Modifier.rearAcrylicSource(hazeState),
                    onOpenCategory = { category ->
                        routeStack = routeStack + ConfigRoute.Category(category)
                    },
                    onOpenAppList = { item ->
                        routeStack = routeStack + ConfigRoute.AppList(item)
                    },
                    onOpenManager = { item ->
                        when ((item.type as? ConfigType.Manager)?.managerType) {
                            ConfigType.ManagerType.BUSINESS -> {
                                routeStack = routeStack + ConfigRoute.BusinessManager
                            }

                            ConfigType.ManagerType.CARD -> {
                                routeStack = routeStack + ConfigRoute.CardManager
                            }

                            ConfigType.ManagerType.BUSINESS_EXTRA -> {
                                routeStack = routeStack + ConfigRoute.BusinessExtraManager
                            }

                            null -> Unit
                        }
                    },
                    onPreferenceChanged = handlePreferenceChanged,
                )

                is ConfigRoute.Category -> ConfigNodeList(
                    nodes = route.category.children,
                    prefsManager = prefsManager,
                    contentPadding = paddingValues,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.rearAcrylicSource(hazeState),
                    onOpenCategory = { category ->
                        routeStack = routeStack + ConfigRoute.Category(category)
                    },
                    onOpenAppList = { item ->
                        routeStack = routeStack + ConfigRoute.AppList(item)
                    },
                    onOpenManager = { item ->
                        when ((item.type as? ConfigType.Manager)?.managerType) {
                            ConfigType.ManagerType.BUSINESS -> {
                                routeStack = routeStack + ConfigRoute.BusinessManager
                            }

                            ConfigType.ManagerType.CARD -> {
                                routeStack = routeStack + ConfigRoute.CardManager
                            }

                            ConfigType.ManagerType.BUSINESS_EXTRA -> {
                                routeStack = routeStack + ConfigRoute.BusinessExtraManager
                            }

                            null -> Unit
                        }
                    },
                    onPreferenceChanged = handlePreferenceChanged,
                )

                is ConfigRoute.AppList -> AppListSelectorScreen(
                    configItem = route.item,
                    prefsManager = prefsManager,
                    onCancel = { routeStack = routeStack.dropLast(1) },
                    onSave = { routeStack = routeStack.dropLast(1) }
                )

                ConfigRoute.BusinessManager -> BusinessManagerScreen(
                    prefsManager = prefsManager,
                    onBack = { routeStack = routeStack.dropLast(1) },
                )

                ConfigRoute.CardManager -> CardManagerScreen(
                    prefsManager = prefsManager,
                    onBack = { routeStack = routeStack.dropLast(1) },
                )

                ConfigRoute.BusinessExtraManager -> BusinessExtraConfigManagerScreen(
                    prefsManager = prefsManager,
                    onBack = { routeStack = routeStack.dropLast(1) },
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
    modifier: Modifier = Modifier,
    onOpenCategory: (ConfigCategory) -> Unit,
    onOpenAppList: (ConfigItem) -> Unit,
    onOpenManager: (ConfigItem) -> Unit,
    onPreferenceChanged: (ConfigItem) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .then(modifier)
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
                            onOpenAppList = onOpenAppList,
                            onOpenManager = onOpenManager,
                            onPreferenceChanged = onPreferenceChanged,
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
                        onOpenAppList = onOpenAppList,
                        onOpenManager = onOpenManager,
                        onPreferenceChanged = onPreferenceChanged,
                    )
                }
            }
        }
    }
}
