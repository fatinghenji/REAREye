package hk.uwu.reareye.ui.components.config

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.config.ConfigCategory
import hk.uwu.reareye.ui.config.ConfigCategoryIcon
import hk.uwu.reareye.ui.config.ConfigGroup
import hk.uwu.reareye.ui.config.ConfigItem
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.ConfigNode
import hk.uwu.reareye.ui.config.ModuleSettingsController
import hk.uwu.reareye.ui.config.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConfigNodeRow(
    node: ConfigNode,
    prefsManager: PrefsManager,
    onOpenCategory: (ConfigCategory) -> Unit,
    onOpenAppList: (ConfigItem) -> Unit
) {
    when (node) {
        is ConfigCategory -> ConfigCategoryNodeRow(
            category = node,
            onClick = { onOpenCategory(node) }
        )

        is ConfigGroup -> Unit

        is ConfigItem -> ConfigItemNodeRow(
            item = node,
            prefsManager = prefsManager,
            onOpenAppList = onOpenAppList
        )
    }
}

@Composable
fun ConfigCategoryNodeRow(category: ConfigCategory, onClick: () -> Unit) {
    SuperArrow(
        title = stringResource(category.titleRes),
        summary = category.descriptionRes?.let { stringResource(it) },
        startAction = category.icon?.let { icon ->
            {
                ConfigCategoryStartIcon(icon = icon)
            }
        },
        onClick = onClick
    )
}

@Composable
private fun ConfigCategoryStartIcon(icon: ConfigCategoryIcon) {
    when (icon) {
        is ConfigCategoryIcon.Compose -> {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        is ConfigCategoryIcon.Package -> {
            val context = LocalContext.current
            var imageBitmap by remember(icon.packageName) { mutableStateOf<ImageBitmap?>(null) }
            var loadFinished by remember(icon.packageName) { mutableStateOf(false) }

            LaunchedEffect(icon.packageName) {
                val loadedBitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val drawable = context.packageManager.getApplicationIcon(icon.packageName)
                        if (drawable is BitmapDrawable) {
                            drawable.bitmap
                        } else {
                            val bmp = createBitmap(
                                drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                                drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
                            )
                            val canvas = Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                    }.getOrNull()?.asImageBitmap()
                }

                withContext(Dispatchers.Main) {
                    imageBitmap = loadedBitmap
                    loadFinished = true
                }
            }

            if (imageBitmap != null) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (!loadFinished) {
                Spacer(modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun ConfigItemNodeRow(
    item: ConfigItem,
    prefsManager: PrefsManager,
    onOpenAppList: (ConfigItem) -> Unit
) {
    item.type.RenderInput(
        item = item,
        prefsManager = prefsManager,
        onOpenAppList = onOpenAppList
    )
}

@Composable
fun BooleanConfigInput(item: ConfigItem, defaultValue: Boolean, prefsManager: PrefsManager) {
    val context = LocalContext.current
    var checked by remember(item.key) {
        mutableStateOf(prefsManager.getBoolean(item.key, defaultValue))
    }

    SuperSwitch(
        title = stringResource(item.titleRes),
        summary = item.descriptionRes?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = {
            checked = it
            prefsManager.putBoolean(item.key, it)
            if (item.key == ConfigKeys.MODULE_HIDE_LAUNCHER_ENTRY) {
                ModuleSettingsController.syncLauncherEntryVisibility(context, hidden = it)
            }
        }
    )
}

@Composable
fun AppListConfigInput(
    item: ConfigItem,
    defaultValues: Set<String>,
    prefsManager: PrefsManager,
    onClick: () -> Unit
) {
    val selectedCount = prefsManager.getStringSet(item.key, defaultValues).size
    val selectedSummary = stringResource(R.string.config_selected_apps, selectedCount)
    val description = item.descriptionRes?.let { stringResource(it) }
    val summary = if (description.isNullOrBlank()) {
        selectedSummary
    } else {
        "$description\n$selectedSummary"
    }

    SuperArrow(
        title = stringResource(item.titleRes),
        summary = summary,
        onClick = onClick
    )
}
