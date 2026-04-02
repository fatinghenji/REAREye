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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.ModuleSettingsController
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import hk.uwu.reareye.ui.screen.AboutScreen
import hk.uwu.reareye.ui.screen.ConfigScreen
import hk.uwu.reareye.ui.screen.HomeScreen
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val permissionInfo = applicationContext.packageManager
                .getPermissionInfo("com.android.permission.GET_INSTALLED_APPS", 0)
            if (permissionInfo != null && permissionInfo.packageName == "com.lbe.security.miui") {
                //MIUI 系统支持动态申请该权限
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        "com.android.permission.GET_INSTALLED_APPS"
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    //没有权限，需要申请
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf("com.android.permission.GET_INSTALLED_APPS"),
                        999
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
            var currentScreen by remember { mutableStateOf("home") }
            var navBarVisible by remember { mutableStateOf(false) }
            var configInAppListMode by remember { mutableStateOf(false) }
            val screenOrder = remember { listOf("home", "config", "about") }

            LaunchedEffect(Unit) {
                navBarVisible = true
            }

            hk.uwu.reareye.ui.theme.AppTheme {
                Scaffold(
                    bottomBar = {
                        val showNavigation =
                            navBarVisible && !(currentScreen == "config" && configInAppListMode)

                        AnimatedVisibility(
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
                            NavigationBar(
                                mode = NavigationBarDisplayMode.IconAndText
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == "home",
                                    onClick = { currentScreen = "home" },
                                    icon = Icons.Filled.Cottage,
                                    label = stringResource(R.string.home_navigation)
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "config",
                                    onClick = { currentScreen = "config" },
                                    icon = Icons.Filled.Settings,
                                    label = stringResource(R.string.configuration_navigation)
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "about",
                                    onClick = { currentScreen = "about" },
                                    icon = Icons.Filled.Info,
                                    label = stringResource(R.string.about_navigation)
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val initialIndex =
                                    screenOrder.indexOf(initialState).coerceAtLeast(0)
                                val targetIndex = screenOrder.indexOf(targetState).coerceAtLeast(0)
                                val forward = targetIndex >= initialIndex

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
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                "home" -> HomeScreen()

                                "config" -> ConfigScreen(
                                    onAppListModeChange = {
                                        configInAppListMode = it
                                    }
                                )

                                "about" -> AboutScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
