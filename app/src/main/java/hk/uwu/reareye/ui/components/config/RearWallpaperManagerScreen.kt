@file:OptIn(androidx.compose.foundation.style.ExperimentalFoundationStyleApi::class)

package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.StyleStateKey
import androidx.compose.foundation.style.styleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import hk.uwu.reareye.R
import hk.uwu.reareye.repository.rearstore.RearStoreInstalledWallpaper
import hk.uwu.reareye.repository.rearstore.RearStoreRepository
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperInfo
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperMetadataOptions
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperRepository
import hk.uwu.reareye.ui.components.DialogFormColumn
import hk.uwu.reareye.ui.components.OverlayDialog
import hk.uwu.reareye.ui.components.RearBadgeGroup
import hk.uwu.reareye.ui.components.card.ModuleStyleDeleteAction
import hk.uwu.reareye.ui.components.card.ModuleStyleIconAction
import hk.uwu.reareye.ui.components.card.ModuleStyleManagerCard
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.components.config.template.RearWallpaperTemplateConfigScreen
import hk.uwu.reareye.ui.components.config.template.TemplateConfigRouteTransition
import hk.uwu.reareye.ui.components.rememberRearWallpaperPreviewBitmap
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleCodec
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.Locale

private data class ItemBounds(
    val top: Float,
    val height: Float,
)

private data class SettlingScheduleOverlay(
    val entry: RearWallpaperScheduleEntry,
    val isCurrent: Boolean,
    val startTranslationY: Float,
    val targetTranslationY: Float,
)

private const val SETTLING_OVERLAY_DURATION_MS = 320L

private enum class WallpaperPickerMode { ADD_TO_SCHEDULE }

private enum class RearWallpaperPage { ROTATION, MANAGEMENT }

private const val WALLPAPER_PREVIEW_RATIO = 1.6f
private val SCHEDULE_ITEM_SHAPE = RoundedCornerShape(24.dp)
private val scheduleDraggedStateKey = StyleStateKey(false)

private var MutableStyleState.isScheduleDragged: Boolean
    get() = this[scheduleDraggedStateKey]
    set(value) {
        this[scheduleDraggedStateKey] = value
    }

private fun StyleScope.scheduleDragged(value: Style) {
    state(scheduleDraggedStateKey, value) { key, styleState -> styleState[key] }
}

@Composable
fun RearWallpaperManagerScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()
    val listState = rememberLazyListState()

    val wallpapers = remember { mutableStateListOf<RearWallpaperInfo>() }
    val schedule = remember { mutableStateListOf<RearWallpaperScheduleEntry>() }
    val storeWallpaperSources = remember { mutableStateMapOf<Int, RearStoreInstalledWallpaper>() }

    var currentWallpaperId by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var activePage by remember { mutableStateOf(RearWallpaperPage.ROTATION) }
    var activeTemplateWallpaperId by remember { mutableStateOf<Int?>(null) }
    val pickerMode = remember { mutableStateOf<WallpaperPickerMode?>(null) }

    val editTargetId = remember { mutableStateOf<Int?>(null) }
    var delayInput by remember { mutableStateOf("") }

    val scheduleItemBounds = remember { mutableStateMapOf<Int, ItemBounds>() }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var draggedInsertIndex by remember { mutableStateOf<Int?>(null) }
    var draggedStartTop by remember { mutableFloatStateOf(0f) }
    var draggedItemHeight by remember { mutableFloatStateOf(0f) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var contentTopInRoot by remember { mutableFloatStateOf(0f) }
    var settlingOverlay by remember { mutableStateOf<SettlingScheduleOverlay?>(null) }
    var settlingWallpaperId by remember { mutableStateOf<Int?>(null) }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun toast(resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    fun persistSchedule() {
        val snapshot = schedule.toList()
        val enabled = scheduleEnabled && snapshot.isNotEmpty()
        if (scheduleEnabled && snapshot.isEmpty()) {
            scheduleEnabled = false
            toast(R.string.rear_wallpaper_schedule_empty)
        }
        scope.launch {
            val synced = withContext(Dispatchers.IO) {
                RearWallpaperRepository.saveSchedule(prefsManager, snapshot)
                RearWallpaperRepository.setScheduleEnabled(prefsManager, enabled)
                RearWallpaperRepository.syncSchedule(context, enabled, snapshot)
            }
            if (!synced) toast(R.string.rear_wallpaper_schedule_sync_failed)
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun refreshCatalog(showSuccessToast: Boolean = false) {
        scope.launch {
            refreshing = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val catalog = RearWallpaperRepository.loadCatalog(context)
                    RearStoreRepository.pruneInstalledWallpaperRecords(
                        prefsManager = prefsManager,
                        installedWallpaperIds = catalog.wallpapers.mapTo(HashSet()) { it.wallpaperId },
                    )
                    catalog to RearStoreRepository.loadInstalledWallpaperSources(prefsManager)
                }
            }
            result.onSuccess { loaded ->
                val (catalog, sources) = loaded
                wallpapers.clear()
                wallpapers.addAll(catalog.wallpapers)
                storeWallpaperSources.clear()
                storeWallpaperSources.putAll(sources)
                currentWallpaperId = catalog.currentWallpaperId
                if (showSuccessToast) toast(R.string.rear_wallpaper_refresh_success)
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.rear_wallpaper_refresh_failed,
                        it.message ?: "unknown"
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            loading = false
            refreshing = false
        }
    }

    fun switchWallpaper(wallpaperId: Int) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                RearWallpaperRepository.switchWallpaper(context, wallpaperId)
            }
            if (success) {
                currentWallpaperId = wallpaperId
                pickerMode.value = null
            } else {
                toast(R.string.rear_wallpaper_switch_failed)
            }
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun importWallpaperPackage(
        packageUri: Uri,
        metadataUri: Uri?,
        previewUri: Uri?,
        options: RearWallpaperMetadataOptions,
    ) {
        scope.launch {
            refreshing = true
            val result = withContext(Dispatchers.IO) {
                RearWallpaperRepository.importWallpaperPackage(
                    context = context,
                    packageUri = packageUri,
                    metadataUri = metadataUri,
                    previewUri = previewUri,
                    options = options,
                )
            }
            refreshing = false
            if (result.success) {
                toast(R.string.rear_wallpaper_import_success)
                refreshCatalog(showSuccessToast = false)
            } else {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.rear_wallpaper_import_failed,
                        result.error ?: "unknown",
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun updateWallpaperMetadata(
        wallpaper: RearWallpaperInfo,
        options: RearWallpaperMetadataOptions,
        previewUri: Uri?,
    ) {
        scope.launch {
            refreshing = true
            val result = withContext(Dispatchers.IO) {
                RearWallpaperRepository.updateWallpaperMetadata(
                    context = context,
                    wallpaperId = wallpaper.wallpaperId,
                    previewUri = previewUri,
                    options = options,
                )
            }
            refreshing = false
            if (result.success) {
                toast(R.string.rear_wallpaper_metadata_saved)
                refreshCatalog(showSuccessToast = false)
            } else {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.rear_wallpaper_metadata_save_failed,
                        result.error ?: "unknown",
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun generateWallpaperPreview(wallpaper: RearWallpaperInfo) {
        scope.launch {
            refreshing = true
            val result = withContext(Dispatchers.IO) {
                RearWallpaperRepository.generateWallpaperPreview(context, wallpaper.wallpaperId)
            }
            refreshing = false
            if (result.success) {
                toast(R.string.rear_wallpaper_preview_generated)
                refreshCatalog(showSuccessToast = false)
            } else {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.rear_wallpaper_preview_generate_failed,
                        result.error ?: "unknown",
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun deleteWallpaper(wallpaper: RearWallpaperInfo) {
        scope.launch {
            refreshing = true
            val result = withContext(Dispatchers.IO) {
                RearWallpaperRepository.deleteWallpaper(context, wallpaper.wallpaperId)
                    .also { result ->
                        if (result.success) {
                            RearStoreRepository.removeInstalledWallpaperRecord(
                                prefsManager = prefsManager,
                                wallpaperId = wallpaper.wallpaperId,
                            )
                        }
                    }
            }
            refreshing = false
            if (result.success) {
                schedule.removeAll { it.wallpaperId == wallpaper.wallpaperId }
                storeWallpaperSources.remove(wallpaper.wallpaperId)
                persistSchedule()
                toast(R.string.rear_wallpaper_delete_success)
                refreshCatalog(showSuccessToast = false)
            } else {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.rear_wallpaper_delete_failed,
                        result.error ?: "unknown",
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun addToSchedule(wallpaperId: Int) {
        if (schedule.any { it.wallpaperId == wallpaperId }) {
            toast(R.string.rear_wallpaper_already_added)
            return
        }
        schedule.add(
            RearWallpaperScheduleEntry(
                wallpaperId = wallpaperId,
                delayMs = RearWallpaperScheduleCodec.DEFAULT_DELAY_MS,
            )
        )
        persistSchedule()
        pickerMode.value = null
        toast(R.string.rear_wallpaper_added)
    }

    fun clearAllRotatingWallpapers() {
        if (schedule.isEmpty() && !scheduleEnabled) {
            toast(R.string.rear_wallpaper_schedule_empty)
            return
        }
        schedule.clear()
        scheduleEnabled = false
        draggedId = null
        draggedInsertIndex = null
        draggedItemHeight = 0f
        draggedOffsetY = 0f
        settlingOverlay = null
        settlingWallpaperId = null
        persistSchedule()
        toast(R.string.rear_wallpaper_schedule_cleared)
    }

    LaunchedEffect(Unit) {
        schedule.clear()
        schedule.addAll(RearWallpaperRepository.loadSchedule(prefsManager))
        scheduleEnabled = RearWallpaperRepository.isScheduleEnabled(prefsManager)
        refreshCatalog(showSuccessToast = false)
    }

    LaunchedEffect(schedule.map { it.wallpaperId }) {
        val activeIds = schedule.mapTo(HashSet()) { it.wallpaperId }
        scheduleItemBounds.keys.toList()
            .filter { it !in activeIds }
            .forEach(scheduleItemBounds::remove)
    }

    LaunchedEffect(settlingOverlay) {
        if (settlingOverlay != null) {
            delay(SETTLING_OVERLAY_DURATION_MS)
            settlingOverlay = null
            settlingWallpaperId = null
        }
    }

    val wallpaperMap = wallpapers.associateBy { it.wallpaperId }
    val renderedSchedule = previewScheduleEntries(schedule, draggedId, draggedInsertIndex)
    val draggedEntry =
        draggedId?.let { wallpaperId -> schedule.firstOrNull { it.wallpaperId == wallpaperId } }
    val currentWallpaperName = wallpaperMap[currentWallpaperId]?.name
        ?: stringResource(R.string.rear_wallpaper_current_none)
    val statusBadges = rearWallpaperStatusBadges(
        currentWallpaperName = currentWallpaperName,
        wallpaperCount = wallpapers.size,
        scheduleEnabled = scheduleEnabled,
    )

    val activeTemplateWallpaper = activeTemplateWallpaperId
        ?.let { id -> wallpapers.firstOrNull { it.wallpaperId == id } }

    BackHandler(enabled = activeTemplateWallpaper == null && activePage == RearWallpaperPage.MANAGEMENT) {
        activePage = RearWallpaperPage.ROTATION
    }

    TemplateConfigRouteTransition(
        target = activeTemplateWallpaper,
        contentKey = { it?.wallpaperId ?: -1 },
        templateContent = { wallpaper ->
            RearWallpaperTemplateConfigScreen(
                wallpaper = wallpaper,
                onBack = { activeTemplateWallpaperId = null },
                onSaved = {
                    activeTemplateWallpaperId = null
                    refreshCatalog(showSuccessToast = false)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                    color = Color.Transparent,
                    title = stringResource(
                        if (activePage == RearWallpaperPage.MANAGEMENT) {
                            R.string.rear_wallpaper_manage_title
                        } else {
                            R.string.rear_wallpaper_manager
                        }
                    ),
                    navigationIconPadding = 12.dp,
                    actionIconPadding = 12.dp,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activePage == RearWallpaperPage.MANAGEMENT) {
                                    activePage = RearWallpaperPage.ROTATION
                                } else {
                                    onBack()
                                }
                            },
                        ) {
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (activePage == RearWallpaperPage.ROTATION) {
                            IconButton(
                                onClick = { clearAllRotatingWallpapers() },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.rear_wallpaper_clear_schedule),
                                )
                            }
                        }
                        IconButton(
                            onClick = { if (!refreshing) refreshCatalog(showSuccessToast = true) },
                        ) {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            AnimatedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = true },
                targetState = activePage,
                contentKey = { it },
                transitionSpec = {
                    val forward = targetState == RearWallpaperPage.MANAGEMENT

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
                label = "RearWallpaperPageTransition",
            ) { currentPage ->
                when (currentPage) {
                    RearWallpaperPage.MANAGEMENT -> {
                        RearWallpaperManagementContent(
                            paddingValues = paddingValues,
                            scrollBehavior = scrollBehavior,
                            hazeState = hazeState,
                            wallpapers = wallpapers,
                            storeWallpaperSources = storeWallpaperSources,
                            currentWallpaperId = currentWallpaperId,
                            loading = loading,
                            refreshing = refreshing,
                            onRefresh = { if (!refreshing) refreshCatalog(showSuccessToast = true) },
                            onSetCurrent = ::switchWallpaper,
                            onImport = ::importWallpaperPackage,
                            onUpdateMetadata = ::updateWallpaperMetadata,
                            onEditTemplate = { activeTemplateWallpaperId = it.wallpaperId },
                            onGeneratePreview = ::generateWallpaperPreview,
                            onDelete = ::deleteWallpaper,
                        )
                    }

                    RearWallpaperPage.ROTATION -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    contentTopInRoot = coordinates.positionInRoot().y
                                }
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .scrollEndHaptic()
                                    .overScrollVertical()
                                    .rearAcrylicSource(hazeState)
                                    .padding(horizontal = 12.dp),
                                state = listState,
                                contentPadding = PaddingValues(
                                    top = paddingValues.calculateTopPadding() + 12.dp,
                                    bottom = paddingValues.calculateBottomPadding() + 12.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                overscrollEffect = null,
                                userScrollEnabled = draggedId == null,
                            ) {
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            SuperCard(
                                                title = stringResource(R.string.rear_wallpaper_status_title),
                                                onClick = {},
                                                bottomAction = {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            10.dp
                                                        )
                                                    ) {
                                                        RearBadgeGroup(badges = statusBadges)
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                8.dp
                                                            ),
                                                        ) {
                                                            Button(
                                                                onClick = {
                                                                    pickerMode.value =
                                                                        WallpaperPickerMode.ADD_TO_SCHEDULE
                                                                },
                                                                colors = ButtonDefaults.buttonColorsPrimary(),
                                                                modifier = Modifier.weight(1f),
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Add,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.padding(end = 6.dp),
                                                                )
                                                                Text(stringResource(R.string.rear_wallpaper_add_sheet_trigger))
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    activePage =
                                                                        RearWallpaperPage.MANAGEMENT
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                            ) {
                                                                Text(stringResource(R.string.rear_wallpaper_manage_title))
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        BasicComponent(
                                            title = stringResource(R.string.rear_wallpaper_schedule_feature_title),
                                            summary = stringResource(R.string.rear_wallpaper_schedule_hint),
                                            summaryColor = BasicComponentDefaults.summaryColor(),
                                            onClick = {},
                                            endActions = {
                                                Switch(
                                                    checked = scheduleEnabled,
                                                    onCheckedChange = { checked ->
                                                        if (checked && schedule.isEmpty()) {
                                                            toast(R.string.rear_wallpaper_schedule_empty)
                                                        } else {
                                                            scheduleEnabled = checked
                                                            persistSchedule()
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                    }
                                }

                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            BasicComponent(
                                                title = stringResource(R.string.rear_wallpaper_schedule_title),
                                                summary = if (schedule.isEmpty()) {
                                                    stringResource(R.string.rear_wallpaper_schedule_empty)
                                                } else {
                                                    stringResource(R.string.rear_wallpaper_schedule_order_hint)
                                                },
                                                summaryColor = BasicComponentDefaults.summaryColor(),
                                                onClick = {},
                                            )
                                        }
                                    }
                                }

                                if (schedule.isNotEmpty()) {
                                    items(renderedSchedule, key = { it.wallpaperId }) { entry ->
                                        val wallpaper = wallpaperMap[entry.wallpaperId]
                                        val isDragged = draggedId == entry.wallpaperId
                                        val isSettling = settlingWallpaperId == entry.wallpaperId
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (isDragged || isSettling) {
                                                        Modifier
                                                    } else {
                                                        Modifier.animateItem(
                                                            placementSpec = tween(
                                                                durationMillis = SETTLING_OVERLAY_DURATION_MS.toInt(),
                                                                easing = FastOutSlowInEasing,
                                                            )
                                                        )
                                                    }
                                                )
                                                .zIndex(if (isDragged) 1f else 0f)
                                                .onGloballyPositioned { coordinates ->
                                                    scheduleItemBounds[entry.wallpaperId] =
                                                        ItemBounds(
                                                            top = coordinates.positionInRoot().y,
                                                            height = coordinates.size.height.toFloat(),
                                                        )
                                                }
                                                .pointerInput(entry.wallpaperId) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            settlingOverlay = null
                                                            settlingWallpaperId = null
                                                            draggedId = entry.wallpaperId
                                                            val bounds =
                                                                scheduleItemBounds[entry.wallpaperId]
                                                            draggedInsertIndex =
                                                                schedule.indexOfFirst { it.wallpaperId == entry.wallpaperId }
                                                            draggedStartTop = bounds?.top ?: 0f
                                                            draggedItemHeight = bounds?.height ?: 0f
                                                            draggedOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggedId = null
                                                            draggedInsertIndex = null
                                                            draggedItemHeight = 0f
                                                            draggedOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            val draggingId = draggedId
                                                            val finalSchedule =
                                                                previewScheduleEntries(
                                                                    schedule = schedule,
                                                                    draggingId = draggingId,
                                                                    insertIndex = draggedInsertIndex,
                                                                )
                                                            val draggedEntrySnapshot =
                                                                draggingId?.let { wallpaperId ->
                                                                    schedule.firstOrNull { it.wallpaperId == wallpaperId }
                                                                }
                                                            val startTranslationY =
                                                                draggedStartTop - contentTopInRoot + draggedOffsetY
                                                            if (draggedEntrySnapshot != null) {
                                                                settlingOverlay =
                                                                    SettlingScheduleOverlay(
                                                                        entry = draggedEntrySnapshot,
                                                                        isCurrent = currentWallpaperId == draggedEntrySnapshot.wallpaperId,
                                                                        startTranslationY = startTranslationY,
                                                                        targetTranslationY = startTranslationY,
                                                                    )
                                                                settlingWallpaperId =
                                                                    draggedEntrySnapshot.wallpaperId
                                                            }
                                                            if (finalSchedule.map { it.wallpaperId } != schedule.map { it.wallpaperId }) {
                                                                schedule.clear()
                                                                schedule.addAll(finalSchedule)
                                                                persistSchedule()
                                                            }
                                                            draggedId = null
                                                            draggedInsertIndex = null
                                                            draggedItemHeight = 0f
                                                            draggedOffsetY = 0f

                                                            if (draggingId != null) {
                                                                scope.launch {
                                                                    withFrameNanos { }
                                                                    withFrameNanos { }
                                                                    val resolvedTarget =
                                                                        scheduleItemBounds[draggingId]?.top?.minus(
                                                                            contentTopInRoot
                                                                        )
                                                                    if (resolvedTarget != null && settlingOverlay?.entry?.wallpaperId == draggingId) {
                                                                        settlingOverlay =
                                                                            settlingOverlay?.copy(
                                                                                targetTranslationY = resolvedTarget
                                                                            )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val draggingId =
                                                                draggedId
                                                                    ?: return@detectDragGesturesAfterLongPress
                                                            val currentHeight =
                                                                scheduleItemBounds[draggingId]?.height
                                                                    ?: draggedItemHeight
                                                            draggedOffsetY += dragAmount.y
                                                            val draggedCenter =
                                                                draggedStartTop + draggedOffsetY + currentHeight / 2f
                                                            draggedInsertIndex =
                                                                findDraggedInsertIndex(
                                                                    schedule = schedule,
                                                                    bounds = scheduleItemBounds,
                                                                    draggedCenter = draggedCenter,
                                                                )
                                                        },
                                                    )
                                                }
                                        ) {
                                            ScheduleItemCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                wallpaper = wallpaper,
                                                scheduleEntry = entry,
                                                isCurrent = currentWallpaperId == entry.wallpaperId,
                                                isDragged = isDragged,
                                                isDragPlaceholder = isDragged || isSettling,
                                                dragOffsetY = 0f,
                                                onEdit = {
                                                    editTargetId.value = entry.wallpaperId
                                                    delayInput = entry.delayMs.toString()
                                                },
                                                onDelete = {
                                                    schedule.removeAll { it.wallpaperId == entry.wallpaperId }
                                                    persistSchedule()
                                                },
                                            )
                                        }
                                    }
                                }

                                if (loading) {
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                InfiniteProgressIndicator()
                                                Spacer(Modifier.width(10.dp))
                                                Text(text = stringResource(R.string.rear_wallpaper_loading))
                                            }
                                        }
                                    }
                                }

                                if (!loading && wallpapers.isEmpty()) {
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            SuperCard(
                                                title = stringResource(R.string.rear_wallpaper_catalog_empty)
                                            )
                                        }
                                    }
                                }
                            }

                            draggedEntry?.let { entry ->
                                val draggedOverlayStyleState =
                                    remember(entry.wallpaperId) { MutableStyleState(null) }
                                val draggedOverlayStyle =
                                    remember(draggedStartTop, contentTopInRoot, draggedOffsetY) {
                                        Style {
                                            translationY(draggedStartTop - contentTopInRoot + draggedOffsetY)
                                        }
                                    }
                                ScheduleItemCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .styleable(draggedOverlayStyleState, draggedOverlayStyle)
                                        .zIndex(3f),
                                    wallpaper = wallpaperMap[entry.wallpaperId],
                                    scheduleEntry = entry,
                                    isCurrent = currentWallpaperId == entry.wallpaperId,
                                    isDragged = true,
                                    isDragPlaceholder = false,
                                    dragOffsetY = 0f,
                                    externalShadow = 18.dp,
                                    onEdit = {
                                        editTargetId.value = entry.wallpaperId
                                        delayInput = entry.delayMs.toString()
                                    },
                                    onDelete = {
                                        schedule.removeAll { it.wallpaperId == entry.wallpaperId }
                                        draggedId = null
                                        draggedInsertIndex = null
                                        draggedItemHeight = 0f
                                        draggedOffsetY = 0f
                                        persistSchedule()
                                    },
                                )
                            } ?: settlingOverlay?.let { overlay ->
                                SettlingScheduleItemOverlay(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .zIndex(3f),
                                    wallpaper = wallpaperMap[overlay.entry.wallpaperId],
                                    scheduleEntry = overlay.entry,
                                    isCurrent = overlay.isCurrent,
                                    startTranslationY = overlay.startTranslationY,
                                    targetTranslationY = overlay.targetTranslationY,
                                    onEdit = {
                                        editTargetId.value = overlay.entry.wallpaperId
                                        delayInput = overlay.entry.delayMs.toString()
                                    },
                                    onDelete = {
                                        schedule.removeAll { it.wallpaperId == overlay.entry.wallpaperId }
                                        settlingOverlay = null
                                        settlingWallpaperId = null
                                        persistSchedule()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        OverlayBottomSheet(
            show = pickerMode.value != null,
            title = stringResource(
                when (pickerMode.value) {
                    WallpaperPickerMode.ADD_TO_SCHEDULE -> R.string.rear_wallpaper_picker_add
                    null -> R.string.rear_wallpaper_manager
                }
            ),
            onDismissRequest = { pickerMode.value = null },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 34.dp),
            ) {
                if (loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InfiniteProgressIndicator()
                            Spacer(Modifier.width(10.dp))
                            Text(text = stringResource(R.string.rear_wallpaper_loading))
                        }
                    }
                }

                items(wallpapers, key = { it.wallpaperId }) { wallpaper ->
                    WallpaperPickerCard(
                        wallpaper = wallpaper,
                        isCurrent = wallpaper.wallpaperId == currentWallpaperId,
                        inSchedule = schedule.any { it.wallpaperId == wallpaper.wallpaperId },
                        onAddToSchedule = { addToSchedule(wallpaper.wallpaperId) },
                    )
                }
            }
        }

        OverlayDialog(
            show = editTargetId.value != null,
            title = stringResource(R.string.rear_wallpaper_edit_interval),
            onDismissRequest = { editTargetId.value = null },
        ) {
            DialogFormColumn {
                TextField(
                    value = delayInput,
                    onValueChange = { delayInput = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.rear_wallpaper_interval_millis),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val targetId = editTargetId.value ?: return@Button
                        val delayMs = delayInput.toLongOrNull()
                        if (delayMs == null || delayMs < RearWallpaperScheduleCodec.MIN_DELAY_MS) {
                            toast(R.string.rear_wallpaper_interval_invalid)
                            return@Button
                        }
                        val index = schedule.indexOfFirst { it.wallpaperId == targetId }
                        if (index >= 0) {
                            schedule[index] = schedule[index].copy(delayMs = delayMs)
                            persistSchedule()
                        }
                        editTargetId.value = null
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rear_widget_confirm))
                }
                Button(
                    onClick = { editTargetId.value = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rear_widget_cancel))
                }
            }
        }

    }
}

private fun previewScheduleEntries(
    schedule: List<RearWallpaperScheduleEntry>,
    draggingId: Int?,
    insertIndex: Int?,
): List<RearWallpaperScheduleEntry> {
    if (draggingId == null || insertIndex == null) return schedule

    val draggedIndex = schedule.indexOfFirst { it.wallpaperId == draggingId }
    if (draggedIndex < 0) return schedule

    val draggedEntry = schedule[draggedIndex]
    val remaining = schedule.filterNot { it.wallpaperId == draggingId }
    val safeInsertIndex = insertIndex.coerceIn(0, remaining.size)
    return buildList(schedule.size) {
        addAll(remaining.take(safeInsertIndex))
        add(draggedEntry)
        addAll(remaining.drop(safeInsertIndex))
    }
}

private fun findDraggedInsertIndex(
    schedule: List<RearWallpaperScheduleEntry>,
    bounds: Map<Int, ItemBounds>,
    draggedCenter: Float,
): Int {
    val orderedBounds = schedule.mapNotNull { entry ->
        bounds[entry.wallpaperId]?.let { entry.wallpaperId to it }
    }

    if (orderedBounds.size <= 1) return 0

    val boundaries = orderedBounds
        .zipWithNext { (_, current), (_, next) ->
            val currentCenter = current.top + current.height / 2f
            val nextCenter = next.top + next.height / 2f
            (currentCenter + nextCenter) / 2f
        }

    return boundaries.count { draggedCenter > it }
}

@Composable
private fun ScheduleItemCard(
    modifier: Modifier,
    wallpaper: RearWallpaperInfo?,
    scheduleEntry: RearWallpaperScheduleEntry,
    isCurrent: Boolean,
    isDragged: Boolean,
    isDragPlaceholder: Boolean,
    dragOffsetY: Float,
    externalShadow: androidx.compose.ui.unit.Dp? = null,
    externalScale: Float? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: LocalLocale.current.platformLocale
    val title = wallpaper?.name ?: stringResource(
        R.string.rear_wallpaper_unavailable,
        scheduleEntry.wallpaperId,
    )
    val unavailableSummary = stringResource(R.string.rear_wallpaper_unavailable_desc)
    val intervalLabel = formatDelay(scheduleEntry.delayMs, locale)
    val scheduleBadges = rearWallpaperScheduleItemBadges(
        wallpaper = wallpaper,
        intervalLabel = intervalLabel,
        isCurrent = isCurrent,
    )
    val animatedShadow by animateDpAsState(
        targetValue = if (isDragged) 18.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 460f,
        ),
        label = "scheduleShadow",
    )
    val shadowElevation = externalShadow ?: animatedShadow
    val baseScale = externalScale ?: 1f
    val motionStyleState = remember(scheduleEntry.wallpaperId) { MutableStyleState(null) }
    motionStyleState.isScheduleDragged = isDragged
    val motionStyle = remember(dragOffsetY, isDragPlaceholder, baseScale) {
        Style {
            alpha(if (isDragPlaceholder) 0f else 1f)
            scale(baseScale)

            scheduleDragged {
                animate(
                    spring(
                        dampingRatio = 0.9f,
                        stiffness = 520f,
                    )
                ) {
                    scale(1.018f)
                }
            }
        }
    }

    ModuleStyleManagerCard(
        modifier = modifier
            .styleable(motionStyleState, motionStyle)
            .graphicsLayer {
                translationY = dragOffsetY
            }
            .shadow(shadowElevation, SCHEDULE_ITEM_SHAPE, clip = false),
        bottomPadding = 0.dp,
        title = title,
        summaryLines = listOfNotNull(unavailableSummary.takeIf { wallpaper == null }),
        badges = scheduleBadges,
        headerVerticalAlignment = Alignment.Top,
        onCardClick = onEdit,
        trailing = {
            WallpaperPreview(
                cachePath = wallpaper?.cachePath,
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(WALLPAPER_PREVIEW_RATIO),
            )
        },
        leftAction = {
            ModuleStyleIconAction(
                icon = Icons.Rounded.EditNote,
                onClick = onEdit,
            )
        },
        rightAction = {
            ModuleStyleDeleteAction(
                icon = MiuixIcons.Delete,
                text = stringResource(R.string.rear_widget_action_delete),
                onClick = onDelete,
            )
        },
    )
}

@Composable
private fun SettlingScheduleItemOverlay(
    modifier: Modifier,
    wallpaper: RearWallpaperInfo?,
    scheduleEntry: RearWallpaperScheduleEntry,
    isCurrent: Boolean,
    startTranslationY: Float,
    targetTranslationY: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var settlingStarted by remember(
        scheduleEntry.wallpaperId,
        startTranslationY,
        targetTranslationY
    ) {
        mutableStateOf(false)
    }
    LaunchedEffect(scheduleEntry.wallpaperId, startTranslationY, targetTranslationY) {
        settlingStarted = true
    }

    val translationY by animateFloatAsState(
        targetValue = if (settlingStarted) targetTranslationY else startTranslationY,
        animationSpec = tween(
            durationMillis = SETTLING_OVERLAY_DURATION_MS.toInt(),
            easing = FastOutSlowInEasing,
        ),
        label = "settlingOverlayTranslation",
    )
    val scale by animateFloatAsState(
        targetValue = if (settlingStarted) 1f else 1.018f,
        animationSpec = tween(
            durationMillis = SETTLING_OVERLAY_DURATION_MS.toInt(),
            easing = FastOutSlowInEasing,
        ),
        label = "settlingOverlayScale",
    )
    ScheduleItemCard(
        modifier = modifier,
        wallpaper = wallpaper,
        scheduleEntry = scheduleEntry,
        isCurrent = isCurrent,
        isDragged = false,
        isDragPlaceholder = false,
        dragOffsetY = translationY,
        externalScale = scale,
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun WallpaperPickerCard(
    wallpaper: RearWallpaperInfo,
    isCurrent: Boolean,
    inSchedule: Boolean,
    onAddToSchedule: () -> Unit,
) {
    val pickerBadges = rearWallpaperPickerBadges(
        wallpaper = wallpaper,
        isCurrent = isCurrent,
        inSchedule = inSchedule,
    )

    ModuleStyleManagerCard(
        title = wallpaper.name,
        summaryLines = emptyList(),
        badges = pickerBadges,
        headerVerticalAlignment = Alignment.Top,
        showActions = !inSchedule,
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
        trailing = {
            WallpaperPreview(
                cachePath = wallpaper.cachePath,
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(WALLPAPER_PREVIEW_RATIO),
            )
        },
        leftAction = {
            ModuleStyleIconAction(
                icon = Icons.Filled.Add,
                backgroundColor = MiuixTheme.colorScheme.surface,
                contentColor = MiuixTheme.colorScheme.onSurface,
                onClick = onAddToSchedule,
            )
        },
        rightAction = {},
    )
}

@Composable
private fun WallpaperPreview(
    cachePath: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberRearWallpaperPreviewBitmap(cachePath)
    val iconTint = Color.White.copy(alpha = 0.82f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .graphicsLayer { clip = true },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = iconTint,
                )
            }
        }
    }
}

private fun formatDelay(delayMs: Long, locale: Locale): String {
    val totalSeconds = ((delayMs + 999L) / 1000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val isChinese = locale.language.startsWith("zh")

    if (minutes <= 0L) {
        return if (isChinese) {
            "${totalSeconds}秒"
        } else {
            "$totalSeconds s"
        }
    }

    if (seconds == 0L) {
        return if (isChinese) {
            "${minutes}分钟"
        } else {
            "$minutes min"
        }
    }

    return if (isChinese) {
        "${minutes}分钟 ${seconds}秒"
    } else {
        "$minutes min $seconds s"
    }
}
