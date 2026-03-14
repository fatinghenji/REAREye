package hk.uwu.reareye.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.screen.ConfigScreen
import hk.uwu.reareye.ui.screen.HomeScreen
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationDisplayMode
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Settings

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

        setContent {
            var currentScreen by remember { mutableStateOf("home") }
            var navBarVisible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                navBarVisible = true
            }

            hk.uwu.reareye.ui.theme.AppTheme {
                Scaffold(
                    bottomBar = {
                        AnimatedVisibility(
                            visible = navBarVisible,
                            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 },
                            exit = fadeOut(tween(500)) + slideOutVertically(tween(500)) { it / 2 }
                        ) {
                            FloatingNavigationBar(
                                mode = NavigationDisplayMode.IconOnly
                            ) {
                                FloatingNavigationBarItem(
                                    selected = currentScreen == "home",
                                    onClick = { currentScreen = "home" },
                                    icon = MiuixIcons.GridView,
                                    label = stringResource(R.string.home_navigation)
                                )
                                FloatingNavigationBarItem(
                                    selected = currentScreen == "config",
                                    onClick = { currentScreen = "config" },
                                    icon = MiuixIcons.Settings,
                                    label = stringResource(R.string.configuration_navigation)
                                )
                                FloatingNavigationBarItem(
                                    selected = false,
                                    onClick = {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            "https://afdian.com/a/rgbmc".toUri()
                                        ).apply {
                                            addCategory(Intent.CATEGORY_BROWSABLE)
                                        }
                                        startActivity(intent)
                                    },
                                    icon = MiuixIcons.Favorites,
                                    label = stringResource(R.string.sponsor_navigation)
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(
                                    animationSpec = tween(300)
                                )
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                "home" -> HomeScreen()
                                "config" -> ConfigScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
