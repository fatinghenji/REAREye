package hk.uwu.reareye.ui.components.config

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.config.ConfigItem
import hk.uwu.reareye.ui.config.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
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
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.abs

private enum class AppSortMode {
    LABEL,
    PACKAGE,
}

data class AppItem(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
)

@Composable
private fun AppIcon(
    appInfo: ApplicationInfo,
    pm: PackageManager,
    modifier: Modifier = Modifier,
) {
    var imageBitmap by remember(appInfo.packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(appInfo.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = appInfo.loadIcon(pm)
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bmp = createBitmap(
                        drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                        drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                    )
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
                imageBitmap = bitmap.asImageBitmap()
            } catch (_: Exception) {
                // Ignore icon loading errors.
            }
        }
    }

    if (imageBitmap != null) {
        Image(bitmap = imageBitmap!!, contentDescription = null, modifier = modifier.size(44.dp))
    } else {
        Spacer(modifier = modifier.size(44.dp))
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
        animationSpec = spring(stiffness = 500f),
        label = "SelectionRingColor",
    )
    val fillScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 680f,
        ),
        label = "SelectionFillScale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 750f,
        ),
        label = "SelectionIconAlpha",
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .border(width = 2.dp, color = ringColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fillScale
                    scaleY = fillScale
                }
                .background(MiuixTheme.colorScheme.primary, CircleShape)
        )

        Icon(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleX = 0.92f + (0.08f * fillScale)
                    scaleY = 0.92f + (0.08f * fillScale)
                }
        )
    }
}

@Composable
fun AppListSelectorScreen(
    configItem: ConfigItem,
    prefsManager: PrefsManager,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var installedApps by remember { mutableStateOf<List<AppItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val selectedPackages = remember(configItem.key) {
        mutableStateListOf<String>().apply {
            addAll(prefsManager.getStringSet(configItem.key, configItem.type.defaultStringSet))
        }
    }
    val selectedOrder = remember(configItem.key) {
        mutableStateListOf<String>().apply {
            addAll(selectedPackages.sorted())
        }
    }

    var showSystemApps by remember(configItem.key) { mutableStateOf(false) }
    var searchQuery by remember(configItem.key) { mutableStateOf("") }
    var searchFocused by remember(configItem.key) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var sortMode by remember(configItem.key) { mutableStateOf(AppSortMode.LABEL) }
    var reverseOrder by remember(configItem.key) { mutableStateOf(false) }

    val showSortMenu = remember(configItem.key) { mutableStateOf(false) }
    val showFilterMenu = remember(configItem.key) { mutableStateOf(false) }
    val showMoreMenu = remember(configItem.key) { mutableStateOf(false) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    val scrollBehavior = MiuixScrollBehavior()

    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    BackHandler(enabled = searchFocused) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(configItem.key) {
        loading = true
        installedApps = null
        withContext(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val items = apps.map { app ->
                AppItem(
                    applicationInfo = app,
                    label = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    isSystem =
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }

            withContext(Dispatchers.Main) {
                installedApps = items
                loading = false
            }
        }
    }

    val selectedSnapshot = selectedPackages.toSet()
    LaunchedEffect(selectedSnapshot) {
        selectedOrder.removeAll { it !in selectedSnapshot }
        selectedSnapshot.forEach { packageName ->
            if (packageName !in selectedOrder) {
                selectedOrder.add(packageName)
            }
        }
    }

    val selectedOrderSnapshot = selectedOrder.toList()
    val filteredApps = remember(
        installedApps,
        showSystemApps,
        searchQuery,
        selectedSnapshot,
        selectedOrderSnapshot,
        sortMode,
        reverseOrder,
    ) {
        val query = searchQuery.trim().lowercase()
        val base = installedApps?.filter { app ->
            val matchesVisibility = showSystemApps || !app.isSystem
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            }
            matchesVisibility && matchesQuery
        } ?: emptyList()

        val sorted = when (sortMode) {
            AppSortMode.LABEL -> base.sortedBy { it.label.lowercase() }
            AppSortMode.PACKAGE -> base.sortedBy { it.packageName.lowercase() }
        }

        val baseOrder = if (reverseOrder) sorted.reversed() else sorted
        val byPackage = baseOrder.associateBy { it.packageName }
        val selectedPart = selectedOrderSnapshot.mapNotNull(byPackage::get)
        val unselectedPart = baseOrder.filterNot { it.packageName in selectedSnapshot }

        selectedPart + unselectedPart
    }
    val indexMap = remember(filteredApps) {
        filteredApps.mapIndexed { index, appItem -> appItem.packageName to index }.toMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(configItem.titleRes),
                navigationIcon = {
                    IconButton(modifier = Modifier.padding(start = 16.dp), onClick = onCancel) {
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSortMenu.value = true },
                        holdDownState = showSortMenu.value
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = stringResource(R.string.app_list_sort),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    SuperListPopup(
                        show = showSortMenu.value,
                        popupModifier = Modifier,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        enableWindowDim = true,
                        onDismissRequest = { showSortMenu.value = false },
                        maxHeight = null,
                        minWidth = 200.dp,
                        renderInRootScaffold = true,
                        content = {
                            ListPopupColumn {
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(title = stringResource(R.string.app_list_sort_by_name)),
                                    entryCount = 3,
                                    isSelected = sortMode == AppSortMode.LABEL,
                                    index = 0,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        sortMode = AppSortMode.LABEL
                                        showSortMenu.value = false
                                    },
                                )
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(title = stringResource(R.string.app_list_sort_by_package)),
                                    entryCount = 3,
                                    isSelected = sortMode == AppSortMode.PACKAGE,
                                    index = 1,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        sortMode = AppSortMode.PACKAGE
                                        showSortMenu.value = false
                                    },
                                )
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(title = stringResource(R.string.app_list_sort_reverse)),
                                    entryCount = 3,
                                    isSelected = reverseOrder,
                                    index = 2,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        reverseOrder = !reverseOrder
                                        showSortMenu.value = false
                                    },
                                )
                            }
                        })

                    IconButton(
                        onClick = { showFilterMenu.value = true },
                        holdDownState = showFilterMenu.value
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Tune,
                            contentDescription = stringResource(R.string.app_list_filter),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    SuperListPopup(
                        show = showFilterMenu.value,
                        popupModifier = Modifier,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        enableWindowDim = true,
                        onDismissRequest = { showFilterMenu.value = false },
                        maxHeight = null,
                        minWidth = 200.dp,
                        renderInRootScaffold = true,
                        content = {
                            ListPopupColumn {
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(
                                        icon = { modifier ->
                                            Icon(
                                                imageVector = Icons.Filled.Apps,
                                                contentDescription = null,
                                                modifier = modifier,
                                                tint = MiuixTheme.colorScheme.onBackground,
                                            )
                                        },
                                        title = stringResource(R.string.show_system_apps),
                                    ),
                                    entryCount = 1,
                                    isSelected = showSystemApps,
                                    index = 0,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        showSystemApps = !showSystemApps
                                        showFilterMenu.value = false
                                    },
                                )
                            }
                        })

                    IconButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { showMoreMenu.value = true },
                        holdDownState = showMoreMenu.value,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.MoreCircle,
                            contentDescription = stringResource(R.string.app_list_more_actions),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    SuperListPopup(
                        show = showMoreMenu.value,
                        popupModifier = Modifier,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        enableWindowDim = true,
                        onDismissRequest = { showMoreMenu.value = false },
                        maxHeight = null,
                        minWidth = 200.dp,
                        renderInRootScaffold = true,
                        content = {
                            ListPopupColumn {
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(
                                        icon = { modifier ->
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null,
                                                modifier = modifier,
                                                tint = MiuixTheme.colorScheme.onBackground,
                                            )
                                        },
                                        title = stringResource(R.string.selection_clear),
                                    ),
                                    entryCount = 1,
                                    isSelected = false,
                                    index = 0,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    onSelectedIndexChange = {
                                        selectedPackages.clear()
                                        selectedOrder.clear()
                                        showMoreMenu.value = false
                                    },
                                )
                            }
                        })
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    prefsManager.putStringSet(configItem.key, selectedPackages.toSet())
                    onSave()
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.selection_save),
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars,
    ) { paddingValues ->
        val searchTopPadding = paddingValues.calculateTopPadding() + dynamicTopPadding
        val searchAcrylicShape = RoundedCornerShape(14.dp)
        val searchAcrylicBase = Color(0xFF9EA6B2).copy(alpha = 0.34f)
        val searchAcrylicStroke = Color.White.copy(alpha = 0.34f)
        val searchAcrylicOverlay = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.04f)
            )
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                state = listState,
                contentPadding = PaddingValues(
                    top = searchTopPadding + 46.dp,
                    bottom = paddingValues.calculateBottomPadding() + 84.dp,
                ),
                overscrollEffect = null,
            ) {
                if (loading || installedApps == null) {
                    item {
                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            insideMargin = PaddingValues(vertical = 26.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            insideMargin = PaddingValues(vertical = 22.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.app_list_empty_result),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { appItem ->
                        val packageName = appItem.packageName
                        val isSelected = packageName in selectedPackages
                        val reorderAnimation = remember(packageName) { Animatable(0f) }
                        val cardAlphaAnimation = remember(packageName) { Animatable(1f) }
                        val whiteOverlayAnimation = remember(packageName) { Animatable(0f) }
                        val previousIndexState = remember(packageName) {
                            mutableStateOf(indexMap[packageName])
                        }
                        val currentIndex = indexMap[packageName]
                        val frameAvgItemHeight =
                            listState.layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }
                                ?.map { it.size }
                                ?.average()
                                ?.toFloat()
                                ?.takeIf { it > 0f }
                                ?: with(density) { 82.dp.toPx() }
                        val frameDeltaIndex =
                            if (previousIndexState.value != null && currentIndex != null) {
                                previousIndexState.value!! - currentIndex
                            } else {
                                0
                            }
                        val shouldUsePreTranslate =
                            frameDeltaIndex != 0 &&
                                    !reorderAnimation.isRunning &&
                                    abs(reorderAnimation.value) < with(density) { 1.dp.toPx() }
                        val preTranslatePx =
                            if (shouldUsePreTranslate) frameDeltaIndex * frameAvgItemHeight else 0f

                        LaunchedEffect(currentIndex, isSelected) {
                            val previousIndex = previousIndexState.value
                            if (previousIndex != null && currentIndex != null) {
                                val deltaIndex = previousIndex - currentIndex
                                if (deltaIndex != 0) {
                                    // Update immediately so rapid toggles don't reuse stale indices.
                                    previousIndexState.value = currentIndex

                                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                                    val avgItemHeight =
                                        visibleItems.takeIf { it.isNotEmpty() }
                                            ?.map { it.size }
                                            ?.average()
                                            ?.toFloat()
                                            ?.takeIf { it > 0f }
                                            ?: with(density) { 82.dp.toPx() }

                                    val moveDuration =
                                        (460 + abs(deltaIndex) * 120).coerceAtMost(1350)
                                    val longUpwardMove = isSelected && deltaIndex > 1
                                    val baseShift = deltaIndex * avgItemHeight
                                    val currentOffset = reorderAnimation.value
                                    // Preserve visual continuity when list order changes mid-animation.
                                    val startOffset = currentOffset + baseShift

                                    val startAdjustDistance = abs(currentOffset - startOffset)
                                    val isInterrupted =
                                        reorderAnimation.isRunning ||
                                                cardAlphaAnimation.isRunning ||
                                                whiteOverlayAnimation.isRunning

                                    if (isInterrupted && startAdjustDistance > with(density) { 8.dp.toPx() }) {
                                        reorderAnimation.animateTo(
                                            targetValue = startOffset,
                                            animationSpec = tween(
                                                durationMillis =
                                                    (90 + (startAdjustDistance / avgItemHeight * 50).toInt())
                                                        .coerceAtMost(210),
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    } else {
                                        reorderAnimation.snapTo(startOffset)
                                    }

                                    if (isInterrupted) {
                                        coroutineScope {
                                            launch {
                                                cardAlphaAnimation.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(
                                                        durationMillis = 100,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                            launch {
                                                whiteOverlayAnimation.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 100,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        cardAlphaAnimation.snapTo(1f)
                                        whiteOverlayAnimation.snapTo(0f)
                                    }

                                    if (longUpwardMove) {
                                        val oneBeforeOffset = avgItemHeight
                                        val initialRiseDuration =
                                            (moveDuration * 0.22f).toInt().coerceAtLeast(130)
                                        val turnWhiteDuration =
                                            (moveDuration * 0.13f).toInt().coerceAtLeast(90)
                                        val fadeOutDuration =
                                            (moveDuration * 0.08f).toInt().coerceAtLeast(70)
                                        val hiddenHoldDuration = 40
                                        val revealDuration = 110
                                        val consumed =
                                            initialRiseDuration + turnWhiteDuration + fadeOutDuration +
                                                    hiddenHoldDuration + revealDuration
                                        val finalSlideDuration =
                                            (moveDuration - consumed).coerceAtLeast(240)

                                        // 1) Start moving upward while still normal.
                                        reorderAnimation.animateTo(
                                            targetValue = startOffset * 0.82f,
                                            animationSpec = tween(
                                                durationMillis = initialRiseDuration,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )

                                        // 2) Turn white while still moving upward.
                                        coroutineScope {
                                            launch {
                                                whiteOverlayAnimation.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(
                                                        durationMillis = turnWhiteDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                            launch {
                                                reorderAnimation.animateTo(
                                                    targetValue = startOffset * 0.58f,
                                                    animationSpec = tween(
                                                        durationMillis = turnWhiteDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                        }

                                        // 3) Fade out, then jump under target and wait.
                                        coroutineScope {
                                            launch {
                                                cardAlphaAnimation.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = fadeOutDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                            launch {
                                                reorderAnimation.animateTo(
                                                    targetValue = startOffset * 0.5f,
                                                    animationSpec = tween(
                                                        durationMillis = fadeOutDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                        }

                                        reorderAnimation.snapTo(oneBeforeOffset)
                                        delay(hiddenHoldDuration.toLong())

                                        // 4) Show again while others are still descending, then settle.
                                        coroutineScope {
                                            launch {
                                                cardAlphaAnimation.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(
                                                        durationMillis = revealDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                            launch {
                                                whiteOverlayAnimation.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = revealDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                            launch {
                                                reorderAnimation.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = finalSlideDuration,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        reorderAnimation.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = moveDuration,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                        cardAlphaAnimation.snapTo(1f)
                                        whiteOverlayAnimation.snapTo(0f)
                                    }
                                }
                            }
                            if (previousIndexState.value != currentIndex) {
                                previousIndexState.value = currentIndex
                            }
                        }

                        Card(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .graphicsLayer {
                                    translationY = reorderAnimation.value + preTranslatePx
                                    alpha = cardAlphaAnimation.value
                                }
                                .fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            onClick = {
                                if (isSelected) {
                                    selectedPackages.remove(packageName)
                                    selectedOrder.remove(packageName)
                                } else {
                                    selectedPackages.add(packageName)
                                    if (packageName !in selectedOrder) {
                                        selectedOrder.add(packageName)
                                    }
                                }
                            },
                            showIndication = true,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(
                                        appInfo = appItem.applicationInfo,
                                        pm = pm,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appItem.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = packageName,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SelectionIndicator(selected = isSelected)
                                }

                                if (whiteOverlayAnimation.value > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Color.White.copy(alpha = whiteOverlayAnimation.value)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (searchFocused) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(1f)
                        .pointerInput(searchBarBounds) {
                            detectTapGestures { tapOffset ->
                                val bounds = searchBarBounds
                                if (bounds == null || !bounds.contains(tapOffset)) {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            }
                        }
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
                    .padding(top = searchTopPadding)
                    .onGloballyPositioned { coordinates ->
                        searchBarBounds = coordinates.boundsInParent()
                    }
                    .border(1.dp, searchAcrylicStroke, searchAcrylicShape)
                    .fillMaxWidth(0.88f),
                cornerRadius = 14.dp,
                colors = CardDefaults.defaultColors(
                    color = searchAcrylicBase,
                    contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(searchAcrylicOverlay)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 6.dp),
                    )

                    val searchHint = stringResource(R.string.search_apps)
                    val searchTextColor = MiuixTheme.colorScheme.onBackground.toArgb()
                    val searchHintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.toArgb()

                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        factory = { editContext ->
                            EditText(editContext).apply {
                                background = null
                                setSingleLine(true)
                                maxLines = 1
                                imeOptions = EditorInfo.IME_ACTION_SEARCH
                                inputType =
                                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                                setPadding(0, 0, 0, 0)
                                minHeight = 0
                                minimumHeight = 0
                                includeFontPadding = false
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

                                setText(searchQuery)
                                setSelection(text?.length ?: 0)

                                setOnFocusChangeListener { _, hasFocus ->
                                    searchFocused = hasFocus
                                }

                                setOnEditorActionListener { _, actionId, _ ->
                                    if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        true
                                    } else {
                                        false
                                    }
                                }

                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int,
                                    ) = Unit

                                    override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int,
                                    ) {
                                        val next = s?.toString().orEmpty()
                                        if (next != searchQuery) {
                                            searchQuery = next
                                        }
                                    }

                                    override fun afterTextChanged(s: Editable?) = Unit
                                })
                            }
                        },
                        update = { editText ->
                            val expectedText = searchQuery
                            if (editText.text?.toString() != expectedText) {
                                editText.setText(expectedText)
                                editText.setSelection(expectedText.length)
                            }

                            editText.hint = searchHint
                            editText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                            editText.setTextColor(searchTextColor)
                            editText.setHintTextColor(searchHintColor)
                        },
                    )
                }
            }
        }
    }
}
