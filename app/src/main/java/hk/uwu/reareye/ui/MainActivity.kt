package hk.uwu.reareye.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import hk.uwu.reareye.ui.components.navigation.RearNavigationBar
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.ModuleNavigationBarMode
import hk.uwu.reareye.ui.config.ModuleSettingsController
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import hk.uwu.reareye.ui.screen.AboutScreen
import hk.uwu.reareye.ui.screen.ConfigScreen
import hk.uwu.reareye.ui.screen.HomeScreen
import hk.uwu.reareye.ui.theme.AppTheme
import hk.uwu.reareye.ui.theme.AppThemeMode
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val permissionInfo = applicationContext.packageManager
                .getPermissionInfo("com.android.permission.GET_INSTALLED_APPS", 0)
            if (permissionInfo != null && permissionInfo.packageName == "com.lbe.security.miui") {
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        "com.android.permission.GET_INSTALLED_APPS"
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf("com.android.permission.GET_INSTALLED_APPS"),
                        999,
                    )
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val prefsManager = applicationContext.getPrefsManager()
        ModuleSettingsController.syncLauncherEntryVisibility(
            context = applicationContext,
            hidden = prefsManager.getBoolean(ConfigKeys.MODULE_HIDE_LAUNCHER_ENTRY, false),
        )

        setContent {
            var themeModeValue by remember {
                mutableIntStateOf(
                    prefsManager.getInt(
                        ConfigKeys.MODULE_THEME_MODE,
                        AppThemeMode.default.value,
                    )
                )
            }
            var navigationBarModeValue by remember {
                mutableIntStateOf(
                    prefsManager.getInt(
                        ConfigKeys.MODULE_NAVIGATION_BAR_MODE,
                        ModuleNavigationBarMode.default.value,
                    )
                )
            }
            var currentScreen by remember { mutableStateOf("home") }
            var navBarVisible by remember { mutableStateOf(false) }
            var configInAppListMode by remember { mutableStateOf(false) }
            val screenOrder = remember { listOf("home", "config", "about") }

            LaunchedEffect(Unit) {
                navBarVisible = true
            }

            AppTheme(themeMode = AppThemeMode.fromValue(themeModeValue)) {
                val navigationBarMode = ModuleNavigationBarMode.fromValue(navigationBarModeValue)
                val enableFloatingGlass =
                    navigationBarMode == ModuleNavigationBarMode.FLOATING_GLASS
                val showNavigation =
                    navBarVisible && !(currentScreen == "config" && configInAppListMode)
                val density = LocalDensity.current
                val surfaceColor = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(surfaceColor)
                    drawContent()
                }
                var stableBottomInset by remember { mutableStateOf(0.dp) }

                Scaffold { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { clip = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (enableFloatingGlass) {
                                        Modifier.layerBackdrop(backdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                contentKey = { it },
                                transitionSpec = {
                                    val initialIndex =
                                        screenOrder.indexOf(initialState).coerceAtLeast(0)
                                    val targetIndex =
                                        screenOrder.indexOf(targetState).coerceAtLeast(0)
                                    val forward = targetIndex >= initialIndex

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
                                label = "ScreenTransition"
                            ) { screen ->
                                when (screen) {
                                    "home" -> HomeScreen(bottomInnerPadding = stableBottomInset)

                                    "config" -> ConfigScreen(
                                        bottomInnerPadding = stableBottomInset,
                                        onAppListModeChange = {
                                            configInAppListMode = it
                                        },
                                        onThemeModeChange = { themeModeValue = it },
                                        onNavigationBarModeChange = {
                                            navigationBarModeValue = it
                                        },
                                    )

                                    "about" -> AboutScreen(bottomInnerPadding = stableBottomInset)
                                }
                            }
                        }

                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            visible = showNavigation,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 260,
                                    easing = LinearOutSlowInEasing,
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 380,
                                    easing = FastOutSlowInEasing,
                                )
                            ) { it / 3 },
                            exit = fadeOut(
                                animationSpec = tween(
                                    durationMillis = 180,
                                    easing = FastOutLinearInEasing,
                                )
                            ) + slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = 240,
                                    easing = FastOutLinearInEasing,
                                )
                            ) { it / 3 }
                        ) {
                            RearNavigationBar(
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        val totalHeight = with(density) {
                                            coordinates.size.height.toDp()
                                        }
                                        if (totalHeight != stableBottomInset) {
                                            stableBottomInset = totalHeight
                                        }
                                    },
                                currentScreen = currentScreen,
                                navigationBarMode = navigationBarMode,
                                backdrop = backdrop,
                                onScreenSelected = { currentScreen = it },
                            )
                        }
                    }
                }
            }
        }
    }
}
