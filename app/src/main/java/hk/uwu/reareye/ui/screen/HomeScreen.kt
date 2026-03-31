package hk.uwu.reareye.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.highcapable.yukihookapi.YukiHookAPI
import hk.uwu.reareye.R
import hk.uwu.reareye.generated.AppProperties
import hk.uwu.reareye.utils.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SpinnerItemImpl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private object UpdateInfoCache {
    val lock = Mutex()
    var loaded = false
    var latestCommitHash: String? = null
}

private suspend fun fetchLatestCommitHashFromNetwork(): String? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val branchParts = AppProperties.GIT_BRANCH.split("/")
            val owner = branchParts.getOrNull(0) ?: "killerprojecte"
            val repo = branchParts.getOrNull(1) ?: "REAREye"
            val branch = branchParts.getOrNull(2)?.takeIf { it.isNotBlank() && it != "unknown" }
                ?: "master"
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/commits/$branch")
                .build()
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body.string()).optString("sha", "").take(7).ifBlank { null }
            } else {
                null
            }
        }.getOrNull()
    }
}

@Composable
fun HomeScreen() {
    val isActivated = YukiHookAPI.Status.isModuleActive
    val showTopMenu = remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var latestCommitHash by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var hasRootAccess by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        if (UpdateInfoCache.loaded) {
            latestCommitHash = UpdateInfoCache.latestCommitHash
            isCheckingUpdate = false
            return@LaunchedEffect
        }

        isCheckingUpdate = true
        latestCommitHash = UpdateInfoCache.lock.withLock {
            if (!UpdateInfoCache.loaded) {
                UpdateInfoCache.latestCommitHash = fetchLatestCommitHashFromNetwork()
                UpdateInfoCache.loaded = true
            }
            UpdateInfoCache.latestCommitHash
        }
        isCheckingUpdate = false
    }

    LaunchedEffect(Unit) {
        hasRootAccess = withContext(Dispatchers.IO) {
            RootHelper.hasRootAccess()
        }
    }

    val statusTitle = if (isActivated) {
        androidx.compose.ui.res.stringResource(R.string.home_status_working)
    } else {
        androidx.compose.ui.res.stringResource(R.string.home_status_inactive)
    }
    val normalizedCurrentHash = AppProperties.GIT_HASH.take(7).lowercase()
    val normalizedLatestHash = latestCommitHash?.take(7)?.lowercase()
    val showUpdateWarning =
        !isCheckingUpdate && !normalizedLatestHash.isNullOrBlank() && normalizedLatestHash != normalizedCurrentHash
    val showRootWarning = hasRootAccess == false
    val updateInfoDelay = if (showRootWarning) 150 else 100

    Scaffold(
        topBar = {
            TopAppBar(
                title = "REAREye",
                actions = {
                    Box {
                        IconButton(
                            modifier = Modifier.padding(end = 16.dp),
                            onClick = { showTopMenu.value = true },
                            holdDownState = showTopMenu.value,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.MoreCircle,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }

                        SuperListPopup(
                            show = showTopMenu.value,
                            popupModifier = Modifier,
                            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                            alignment = PopupPositionProvider.Align.End,
                            enableWindowDim = true,
                            onDismissRequest = { showTopMenu.value = false },
                            maxHeight = null,
                            minWidth = 220.dp,
                            renderInRootScaffold = true,
                        ) {
                            @SuppressLint("LocalContextGetResourceValueCall")
                            ListPopupColumn {
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(
                                        title = androidx.compose.ui.res.stringResource(
                                            R.string.quick_stop_subscreencenter,
                                        ),
                                    ),
                                    entryCount = 2,
                                    isSelected = false,
                                    index = 0,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        showTopMenu.value = false

                                        coroutineScope.launch {
                                            forceStopPackageByRoot(
                                                context = context,
                                                packageName = "com.xiaomi.subscreencenter",
                                                appName = context.getString(R.string.category_subscreencenter),
                                            )
                                        }
                                    },
                                )

                                SpinnerItemImpl(
                                    entry = SpinnerEntry(
                                        title = androidx.compose.ui.res.stringResource(
                                            R.string.quick_stop_thememanager,
                                        ),
                                    ),
                                    entryCount = 2,
                                    isSelected = false,
                                    index = 1,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        showTopMenu.value = false
                                        coroutineScope.launch {
                                            forceStopPackageByRoot(
                                                context = context,
                                                packageName = "com.android.thememanager",
                                                appName = context.getString(R.string.category_thememanager),
                                            )
                                        }
                                    },
                                )

                                SpinnerItemImpl(
                                    entry = SpinnerEntry(
                                        title = androidx.compose.ui.res.stringResource(
                                            R.string.quick_stop_systemui,
                                        ),
                                    ),
                                    entryCount = 2,
                                    isSelected = false,
                                    index = 1,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        showTopMenu.value = false
                                        coroutineScope.launch {
                                            forceKillSystemUI(context)
                                        }
                                    },
                                )

                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
    ) { paddingValues ->
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            overscrollEffect = null,
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RevealItem(visible = visible, delayMillis = 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WorkingStatusCard(
                                statusTitle = statusTitle,
                                statusVersion = AppProperties.BUILD_NUMBER.toString(),
                                activated = isActivated,
                            )

                            if (showRootWarning) {
                                RevealItem(visible = visible, delayMillis = 100) {
                                    RootWarningCard()
                                }
                            }

                            if (showUpdateWarning) {
                                UpdateWarningCard(
                                    currentHash = AppProperties.GIT_HASH.take(7),
                                    latestHash = latestCommitHash?.take(7).orEmpty(),
                                )
                            }
                        }
                    }

                    RevealItem(visible = visible, delayMillis = 50) {
                        ModuleInfoCard(
                            activated = isActivated,
                            moduleVersion =
                                "${AppProperties.PROJECT_APP_VERSION_NAME}-${AppProperties.GIT_HASH}-r${AppProperties.BUILD_NUMBER}-${AppProperties.BUILD_CHANNEL}",
                            releaseChannel = AppProperties.BUILD_CHANNEL,
                        )
                    }

                    RevealItem(visible = visible, delayMillis = updateInfoDelay) {
                        UpdateInfoCard(
                            currentHash = AppProperties.GIT_HASH,
                            latestHash = latestCommitHash,
                            checking = isCheckingUpdate,
                        )
                    }
                }
            }
        }

    }
}

private suspend fun forceStopPackageByRoot(
    context: Context,
    packageName: String,
    appName: String,
) {
    withContext(Dispatchers.IO) {
        if (!RootHelper.hasRootAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_need_root_permission),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return@withContext
        }

        val success = RootHelper.executeRootCommandSuccess("am force-stop $packageName")
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.quick_stop_success else R.string.quick_stop_failed,
                    appName,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

private suspend fun forceKillSystemUI(context: Context) {
    withContext(Dispatchers.IO) {
        if (!RootHelper.hasRootAccess()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_need_root_permission),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return@withContext
        }

        RootHelper.executeRootCommandSuccess("kill -9 $(pgrep systemui)")
    }
}

@Composable
private fun RevealItem(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 320,
                delayMillis = delayMillis,
                easing = LinearOutSlowInEasing,
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = 420,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing,
            )
        ) { it / 8 },
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 120,
                easing = FastOutLinearInEasing,
            )
        ),
    ) {
        content()
    }
}

@Composable
private fun WorkingStatusCard(statusTitle: String, statusVersion: String, activated: Boolean) {
    val cardColor = if (activated) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)
    val iconColor = if (activated) Color(0xFF36D167) else Color(0xFFE06767)
    val titleColor = if (activated) Color(0xFF1E5A31) else Color(0xFF7A2A2A)
    val summaryColor = if (activated) Color(0xFF2C7D45) else Color(0xFF9A4D4D)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
        insideMargin = PaddingValues(14.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = false
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(198.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 72.dp, y = 56.dp),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = statusTitle,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.home_working_version,
                        statusVersion
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = summaryColor,
                )
            }
        }
    }
}

@Composable
private fun RootWarningCard() {
    val darkMode = isSystemInDarkTheme()
    val cardColor = if (darkMode) Color(0xFF4E2528) else Color(0xFFFDE9E9)
    val iconColor = if (darkMode) Color(0xFFFF8A80) else Color(0xFFD94B4B)
    val titleColor = if (darkMode) Color(0xFFFFD2CC) else Color(0xFF8C1F1F)
    val summaryColor = if (darkMode) Color(0xFFFFB4AB) else Color(0xFFA63737)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
        insideMargin = PaddingValues(14.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = false
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Outlined.DoNotDisturb,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(108.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 50.dp, y = 42.dp),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.home_root_warning_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.home_root_warning_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = summaryColor,
                )
            }
        }
    }
}

@Composable
private fun UpdateWarningCard(currentHash: String, latestHash: String) {
    val context = LocalContext.current
    val cardColor = Color(0xFFFFF3CD)
    val iconColor = Color(0xFFE0A100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
        insideMargin = PaddingValues(14.dp),
        onClick = {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/killerprojecte/REAREye/actions".toUri()
                )
            )
        },
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = false
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(108.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 50.dp, y = 44.dp),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.home_update_warning_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7A5A00),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.home_update_warning_desc,
                        currentHash,
                        latestHash,
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFF8A6B00),
                )
            }
        }
    }
}

@Composable
private fun ModuleInfoCard(activated: Boolean, moduleVersion: String, releaseChannel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        InfoLine(
            title = androidx.compose.ui.res.stringResource(R.string.status_card),
            value = if (activated) {
                androidx.compose.ui.res.stringResource(R.string.module_is_activated)
            } else {
                androidx.compose.ui.res.stringResource(R.string.module_not_activated)
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoLine(
            title = androidx.compose.ui.res.stringResource(R.string.module_version_label),
            value = moduleVersion,
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoLine(
            title = androidx.compose.ui.res.stringResource(R.string.home_status_channel),
            value = releaseChannel,
        )
    }
}

@Composable
private fun UpdateInfoCard(currentHash: String, latestHash: String?, checking: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.home_update_title),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(modifier = Modifier.height(8.dp))
        InfoLine(
            title = androidx.compose.ui.res.stringResource(R.string.home_update_current),
            value = currentHash,
        )
        Spacer(modifier = Modifier.height(8.dp))
        InfoLine(
            title = androidx.compose.ui.res.stringResource(R.string.home_update_latest),
            value = when {
                checking -> androidx.compose.ui.res.stringResource(R.string.home_update_checking)
                latestHash.isNullOrBlank() -> androidx.compose.ui.res.stringResource(R.string.home_update_unknown)
                else -> latestHash
            },
        )
    }
}

@Composable
private fun InfoLine(title: String, value: String) {
    Text(text = title, style = MiuixTheme.textStyles.headline1)
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = value,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
