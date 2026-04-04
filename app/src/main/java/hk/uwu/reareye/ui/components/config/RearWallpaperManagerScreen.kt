package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import hk.uwu.reareye.R
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperCatalog
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperInfo
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperRepository
import hk.uwu.reareye.ui.components.card.ModuleStyleDeleteAction
import hk.uwu.reareye.ui.components.card.ModuleStyleIconAction
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.components.rememberRearWallpaperPreviewBitmap
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleCodec
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.Locale
import kotlin.math.roundToInt

private data class ItemBounds(
    val top: Float,
    val height: Float,
)

private enum class WallpaperPickerMode { ADD_TO_SCHEDULE }

private const val WALLPAPER_PREVIEW_RATIO = 1.6f
private val SCHEDULE_ITEM_SHAPE = RoundedCornerShape(24.dp)

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

    var currentWallpaperId by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf<WallpaperPickerMode?>(null) }

    var editTargetId by remember { mutableStateOf<Int?>(null) }
    var delayInput by remember { mutableStateOf("") }

    val scheduleItemBounds = remember { mutableStateMapOf<Int, ItemBounds>() }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var draggedInsertIndex by remember { mutableStateOf<Int?>(null) }
    var draggedStartTop by remember { mutableFloatStateOf(0f) }
    var draggedItemHeight by remember { mutableFloatStateOf(0f) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var contentTopInRoot by remember { mutableFloatStateOf(0f) }

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
                    RearWallpaperRepository.loadCatalog(context)
                }
            }
            result.onSuccess { catalog: RearWallpaperCatalog ->
                wallpapers.clear()
                wallpapers.addAll(catalog.wallpapers)
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
                pickerMode = null
            } else {
                toast(R.string.rear_wallpaper_switch_failed)
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
        pickerMode = null
        toast(R.string.rear_wallpaper_added)
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

    val wallpaperMap = wallpapers.associateBy { it.wallpaperId }
    val renderedSchedule = previewScheduleEntries(schedule, draggedId, draggedInsertIndex)
    val draggedEntry =
        draggedId?.let { wallpaperId -> schedule.firstOrNull { it.wallpaperId == wallpaperId } }
    val currentWallpaperName = wallpaperMap[currentWallpaperId]?.name
        ?: stringResource(R.string.rear_wallpaper_current_none)
    val statusLines = listOf(
        stringResource(R.string.rear_wallpaper_status_current_line, currentWallpaperName),
        stringResource(R.string.rear_wallpaper_status_count_line, wallpapers.size),
        stringResource(
            R.string.rear_wallpaper_status_schedule_line,
            stringResource(
                if (scheduleEnabled) {
                    R.string.rear_wallpaper_schedule_on
                } else {
                    R.string.rear_wallpaper_schedule_off
                }
            ),
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = stringResource(R.string.rear_wallpaper_manager),
                navigationIconPadding = 12.dp,
                actionIconPadding = 12.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                                summary = statusLines.joinToString(separator = "\n"),
                                onClick = {},
                                bottomAction = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                pickerMode = WallpaperPickerMode.ADD_TO_SCHEDULE
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
                                                if (!refreshing) refreshCatalog(
                                                    showSuccessToast = true
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 6.dp),
                                            )
                                            Text(stringResource(R.string.rear_wallpaper_refresh))
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
                    items(schedule, key = { it.wallpaperId }) { entry ->
                        val wallpaper = wallpaperMap[entry.wallpaperId]
                        val isDragged = draggedId == entry.wallpaperId
                        val previewOffsetY = if (draggedId != null && !isDragged) {
                            calculatePreviewOffsetY(
                                schedule = schedule,
                                previewSchedule = renderedSchedule,
                                bounds = scheduleItemBounds,
                                wallpaperId = entry.wallpaperId,
                            )
                        } else {
                            0f
                        }
                        val animatedPreviewOffsetY by animateFloatAsState(
                            targetValue = previewOffsetY,
                            label = "schedulePreviewOffset",
                        )
                        ScheduleItemCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 1f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    scheduleItemBounds[entry.wallpaperId] = ItemBounds(
                                        top = coordinates.positionInRoot().y,
                                        height = coordinates.size.height.toFloat(),
                                    )
                                }
                                .pointerInput(entry.wallpaperId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = entry.wallpaperId
                                            val bounds = scheduleItemBounds[entry.wallpaperId]
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
                                            val finalSchedule = previewScheduleEntries(
                                                schedule = schedule,
                                                draggingId = draggedId,
                                                insertIndex = draggedInsertIndex,
                                            )
                                            if (finalSchedule.map { it.wallpaperId } != schedule.map { it.wallpaperId }) {
                                                schedule.clear()
                                                schedule.addAll(finalSchedule)
                                                persistSchedule()
                                            }
                                            draggedId = null
                                            draggedInsertIndex = null
                                            draggedItemHeight = 0f
                                            draggedOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val draggingId =
                                                draggedId ?: return@detectDragGesturesAfterLongPress
                                            val currentHeight =
                                                scheduleItemBounds[draggingId]?.height
                                                    ?: draggedItemHeight
                                            draggedOffsetY += dragAmount.y
                                            val draggedCenter =
                                                draggedStartTop + draggedOffsetY + currentHeight / 2f
                                            draggedInsertIndex = findDraggedInsertIndex(
                                                schedule = schedule,
                                                bounds = scheduleItemBounds,
                                                draggedCenter = draggedCenter,
                                            )
                                        },
                                    )
                                },
                            wallpaper = wallpaper,
                            scheduleEntry = entry,
                            isCurrent = currentWallpaperId == entry.wallpaperId,
                            isDragged = isDragged,
                            isDragPlaceholder = isDragged,
                            dragOffsetY = animatedPreviewOffsetY,
                            onEdit = {
                                editTargetId = entry.wallpaperId
                                delayInput = entry.delayMs.toString()
                            },
                            onDelete = {
                                schedule.removeAll { it.wallpaperId == entry.wallpaperId }
                                persistSchedule()
                            },
                        )
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
                                CircularProgressIndicator()
                                Spacer(Modifier.width(10.dp))
                                Text(text = stringResource(R.string.rear_wallpaper_loading))
                            }
                        }
                    }
                }

                if (!loading && wallpapers.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.rear_wallpaper_catalog_empty))
                        }
                    }
                }
            }

            draggedEntry?.let { entry ->
                ScheduleItemCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (draggedStartTop - contentTopInRoot + draggedOffsetY).roundToInt(),
                            )
                        }
                        .zIndex(3f),
                    wallpaper = wallpaperMap[entry.wallpaperId],
                    scheduleEntry = entry,
                    isCurrent = currentWallpaperId == entry.wallpaperId,
                    isDragged = true,
                    isDragPlaceholder = false,
                    dragOffsetY = 0f,
                    onEdit = {
                        editTargetId = entry.wallpaperId
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
            }
        }
    }

    OverlayBottomSheet(
        show = pickerMode != null,
        title = stringResource(
            when (pickerMode) {
                WallpaperPickerMode.ADD_TO_SCHEDULE -> R.string.rear_wallpaper_picker_add
                null -> R.string.rear_wallpaper_manager
            }
        ),
        onDismissRequest = { pickerMode = null },
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
                        CircularProgressIndicator()
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
                    onUseNow = { switchWallpaper(wallpaper.wallpaperId) },
                    onAddToSchedule = { addToSchedule(wallpaper.wallpaperId) },
                )
            }
        }
    }

    OverlayDialog(
        show = editTargetId != null,
        title = stringResource(R.string.rear_wallpaper_edit_interval),
        onDismissRequest = { editTargetId = null },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = delayInput,
                onValueChange = { delayInput = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_wallpaper_interval_millis),
                singleLine = true,
            )
            Button(
                onClick = {
                    val targetId = editTargetId ?: return@Button
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
                    editTargetId = null
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rear_widget_confirm))
            }
            Button(
                onClick = { editTargetId = null },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rear_widget_cancel))
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

private fun calculatePreviewOffsetY(
    schedule: List<RearWallpaperScheduleEntry>,
    previewSchedule: List<RearWallpaperScheduleEntry>,
    bounds: Map<Int, ItemBounds>,
    wallpaperId: Int,
): Float {
    val currentTop = bounds[wallpaperId]?.top ?: return 0f
    val previewIndex = previewSchedule.indexOfFirst { it.wallpaperId == wallpaperId }
    if (previewIndex < 0) return 0f

    val slotId = schedule.getOrNull(previewIndex)?.wallpaperId ?: return 0f
    val targetTop = bounds[slotId]?.top ?: return 0f
    return targetTop - currentTop
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val title = wallpaper?.name ?: stringResource(
        R.string.rear_wallpaper_unavailable,
        scheduleEntry.wallpaperId,
    )
    val unavailableSummary = stringResource(R.string.rear_wallpaper_unavailable_desc)
    val intervalSummary = stringResource(
        R.string.rear_wallpaper_interval_summary,
        formatDelay(scheduleEntry.delayMs, locale),
    )
    val currentLabel = stringResource(R.string.rear_wallpaper_current)
    val animatedScale by animateFloatAsState(if (isDragged) 1.018f else 1f, label = "scheduleScale")
    val animatedShadow by animateDpAsState(if (isDragged) 18.dp else 0.dp, label = "scheduleShadow")

    Card(
        modifier = modifier
            .alpha(if (isDragPlaceholder) 0f else 1f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .shadow(animatedShadow, SCHEDULE_ITEM_SHAPE, clip = false),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicComponent(
                title = title,
                summary = listOfNotNull(
                    wallpaper?.title ?: unavailableSummary,
                    intervalSummary,
                    currentLabel.takeIf { isCurrent },
                ).joinToString(separator = "\n"),
                startAction = {
                    WallpaperPreview(
                        cachePath = wallpaper?.cachePath,
                        modifier = Modifier
                            .width(88.dp)
                            .aspectRatio(WALLPAPER_PREVIEW_RATIO),
                    )
                },
                onClick = onEdit,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                thickness = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModuleStyleIconAction(
                    icon = Icons.Rounded.EditNote,
                    onClick = onEdit,
                )
                Spacer(Modifier.weight(1f))
                ModuleStyleDeleteAction(
                    icon = MiuixIcons.Delete,
                    text = stringResource(R.string.rear_widget_action_delete),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun WallpaperPickerCard(
    wallpaper: RearWallpaperInfo,
    isCurrent: Boolean,
    inSchedule: Boolean,
    onUseNow: () -> Unit,
    onAddToSchedule: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp),
        insideMargin = PaddingValues(8.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        BasicComponent(
            title = wallpaper.name,
            summary = buildString {
                append(wallpaper.title)
                if (isCurrent) {
                    append('\n')
                    append(stringResource(R.string.rear_wallpaper_current))
                }
            },
            startAction = {
                WallpaperPreview(
                    cachePath = wallpaper.cachePath,
                    modifier = Modifier
                        .width(104.dp)
                        .aspectRatio(WALLPAPER_PREVIEW_RATIO),
                )
            },
            bottomAction = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAddToSchedule,
                        enabled = !inSchedule,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = stringResource(
                                if (inSchedule) {
                                    R.string.rear_wallpaper_already_in_schedule
                                } else {
                                    R.string.rear_wallpaper_add_to_schedule
                                }
                            )
                        )
                    }
                    Button(
                        onClick = onUseNow,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surface,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.rear_wallpaper_set_now))
                    }
                }
            },
            onClick = {},
        )
    }
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
            "${totalSeconds} s"
        }
    }

    if (seconds == 0L) {
        return if (isChinese) {
            "${minutes}分钟"
        } else {
            "${minutes} min"
        }
    }

    return if (isChinese) {
        "${minutes}分钟 ${seconds}秒"
    } else {
        "${minutes} min ${seconds} s"
    }
}
