package hk.uwu.reareye.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.config.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

data class AppItem(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val packageName: String,
    val isSystem: Boolean
)

@Composable
fun AppIcon(
    appInfo: ApplicationInfo,
    pm: PackageManager,
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember(appInfo.packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(appInfo.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = appInfo.loadIcon(pm)
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bmp = createBitmap(drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                        drawable.intrinsicHeight.takeIf { it > 0 } ?: 1)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
                imageBitmap = bitmap.asImageBitmap()
            } catch (_: Exception) {
                // Ignore icon loading errors
            }
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier.size(40.dp)
        )
    } else {
        Spacer(modifier = modifier.size(40.dp))
    }
}

@Composable
fun AppSelectorDialog(
    configKey: String,
    prefsManager: PrefsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var installedApps by remember { mutableStateOf<List<AppItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val selectedPackages = remember {
        mutableStateListOf<String>().apply {
            addAll(prefsManager.getStringSet(configKey, emptySet()))
        }
    }

    var showSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val items = apps.map { app ->
                AppItem(
                    applicationInfo = app,
                    label = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                installedApps = items
                loading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.application_select_title))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.show_system_apps),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = showSystemApps,
                            onCheckedChange = { showSystemApps = it }
                        )
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = stringResource(R.string.search_apps),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true
                )

                if (loading || installedApps == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filteredApps = remember(
                        installedApps,
                        showSystemApps,
                        searchQuery,
                        selectedPackages.toList()
                    ) {
                        val base = installedApps?.filter { app ->
                            val matchesSystemAppPolicy = if (showSystemApps) true else !app.isSystem
                            val query = searchQuery.lowercase()
                            (app.label.lowercase().contains(query) || app.packageName.lowercase()
                                .contains(query)) && matchesSystemAppPolicy
                        } ?: emptyList()

                        base.sortedWith(
                            compareByDescending<AppItem> { it.packageName in selectedPackages }
                                .thenBy { it.label.lowercase() }
                        )
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps, key = { it.packageName }) { appItem ->
                            val pkg = appItem.packageName
                            val isSelected = selectedPackages.contains(pkg)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedPackages.remove(pkg)
                                        } else {
                                            selectedPackages.add(pkg)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    state = ToggleableState(value = isSelected),
                                    onClick = null,
                                    modifier = Modifier,
                                    colors = CheckboxDefaults.checkboxColors(),
                                    enabled = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AppIcon(
                                    appInfo = appItem.applicationInfo,
                                    pm = pm,
                                    modifier = Modifier.padding(end = 12.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = appItem.label)
                                    Text(text = pkg)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.selection_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        prefsManager.putStringSet(configKey, selectedPackages.toSet())
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.selection_save))
                    }
                }
            }
        }
    }
}
