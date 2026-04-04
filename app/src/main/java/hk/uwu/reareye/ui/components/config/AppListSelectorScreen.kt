package hk.uwu.reareye.ui.components.config

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.components.rememberApplicationIconBitmap
import hk.uwu.reareye.ui.config.ConfigItem
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import kotlinx.coroutines.Dispatchers
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private enum class AppSortMode {
    LABEL,
    PACKAGE,
}

data class AppItem(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val labelKey: String,
    val packageKey: String,
)

private data class AppCatalog(
    val byLabel: List<AppItem>,
    val byPackage: List<AppItem>,
)

private val appRowPlacementSpec = spring<Float>(
    dampingRatio = 0.92f,
    stiffness = 520f,
)

private fun buildAppCatalog(packageManager: PackageManager): AppCatalog {
    val allApps = packageManager
        .getInstalledApplications(PackageManager.GET_META_DATA)
        .map { app ->
            val label = app.loadLabel(packageManager).toString()
            AppItem(
                applicationInfo = app,
                label = label,
                packageName = app.packageName,
                isSystem =
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                labelKey = label.lowercase(),
                packageKey = app.packageName.lowercase(),
            )
        }

    return AppCatalog(
        byLabel = allApps.sortedBy(AppItem::labelKey),
        byPackage = allApps.sortedBy(AppItem::packageKey),
    )
}

private fun AppCatalog.buildVisibleApps(
    sortMode: AppSortMode,
    reverseOrder: Boolean,
    showSystemApps: Boolean,
    query: String,
    selectedOrder: List<String>,
): List<AppItem> {
    val normalizedQuery = query.trim().lowercase()
    val source = when (sortMode) {
        AppSortMode.LABEL -> byLabel
        AppSortMode.PACKAGE -> byPackage
    }
    val visibleApps = source.filter { app ->
        val matchesVisibility = showSystemApps || !app.isSystem
        val matchesQuery = normalizedQuery.isEmpty() ||
                app.labelKey.contains(normalizedQuery) ||
                app.packageKey.contains(normalizedQuery)
        matchesVisibility && matchesQuery
    }
    val orderedApps = if (reverseOrder) visibleApps.asReversed() else visibleApps

    if (selectedOrder.isEmpty()) {
        return orderedApps
    }

    val selectedLookup = selectedOrder.toHashSet()
    val selectedItems = HashMap<String, AppItem>(selectedOrder.size)
    val unselectedItems = ArrayList<AppItem>(orderedApps.size)

    orderedApps.forEach { app ->
        if (app.packageName in selectedLookup) {
            selectedItems[app.packageName] = app
        } else {
            unselectedItems += app
        }
    }

    return buildList(orderedApps.size) {
        selectedOrder.forEach { packageName ->
            selectedItems[packageName]?.let(::add)
        }
        addAll(unselectedItems)
    }
}

private fun MutableList<String>.toggleSelection(packageName: String) {
    if (!remove(packageName)) {
        add(packageName)
    }
}

@Composable
private fun AppIcon(
    appInfo: ApplicationInfo,
    pm: PackageManager,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = rememberApplicationIconBitmap(
        packageManager = pm,
        applicationInfo = appInfo,
    )

    if (imageBitmap != null) {
        Image(bitmap = imageBitmap, contentDescription = null, modifier = modifier.size(44.dp))
    } else {
        Spacer(modifier = modifier.size(44.dp))
    }
}

@Composable
private fun LoadingAppsCard() {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        insideMargin = PaddingValues(vertical = 26.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun EmptyAppsCard() {
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

@Composable
private fun AppSelectorRow(
    appItem: AppItem,
    packageManager: PackageManager,
    selected: Boolean,
    currentIndex: Int,
    averageItemHeightPx: Float,
    onToggle: () -> Unit,
) {
    val density = LocalDensity.current
    var previousIndex by remember(appItem.packageName) { mutableStateOf(currentIndex) }
    var placementOffset by remember(appItem.packageName) { mutableFloatStateOf(0f) }
    val animatedPlacementOffset by animateFloatAsState(
        targetValue = placementOffset,
        animationSpec = appRowPlacementSpec,
        label = "AppRowPlacementOffset",
    )
    val selectionOverlayAlpha by animateFloatAsState(
        targetValue = if (selected) 0.08f else 0f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 520f,
        ),
        label = "AppRowSelectionOverlayAlpha",
    )
    val selectionOverlayScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.965f,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = 540f,
        ),
        label = "AppRowSelectionOverlayScale",
    )
    val contentOffsetPx by animateFloatAsState(
        targetValue = with(density) { if (selected) 1.5.dp.toPx() else 0.dp.toPx() },
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = 560f,
        ),
        label = "AppRowContentOffset",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 0.998f else 1f,
        animationSpec = spring(
            dampingRatio = 0.95f,
            stiffness = 600f,
        ),
        label = "AppRowContentScale",
    )

    LaunchedEffect(currentIndex, averageItemHeightPx) {
        if (previousIndex == currentIndex) {
            return@LaunchedEffect
        }

        placementOffset = (previousIndex - currentIndex) * averageItemHeightPx
        previousIndex = currentIndex
        withFrameNanos { }
        placementOffset = 0f
    }

    Card(
        modifier = Modifier
            .padding(top = 10.dp)
            .graphicsLayer {
                translationY = animatedPlacementOffset
            }
            .fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onToggle,
        showIndication = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = selectionOverlayAlpha
                        scaleX = selectionOverlayScale
                        scaleY = selectionOverlayScale
                    }
                    .background(
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp),
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = contentOffsetPx
                        scaleX = contentScale
                        scaleY = contentScale
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    appInfo = appItem.applicationInfo,
                    pm = packageManager,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appItem.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = appItem.packageName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SelectionIndicator(selected = selected)
            }
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    val haloAlpha by animateFloatAsState(
        targetValue = if (selected) 0.18f else 0f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 460f,
        ),
        label = "SelectionHaloAlpha",
    )
    val haloScale by animateFloatAsState(
        targetValue = if (selected) 1.7f else 0.7f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 420f,
        ),
        label = "SelectionHaloScale",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
        animationSpec = spring(stiffness = 500f),
        label = "SelectionRingColor",
    )
    val ringScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 520f,
        ),
        label = "SelectionRingScale",
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
    val iconRotation by animateFloatAsState(
        targetValue = if (selected) 0f else -16f,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 560f,
        ),
        label = "SelectionIconRotation",
    )

    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    alpha = haloAlpha
                    scaleX = haloScale
                    scaleY = haloScale
                }
                .background(MiuixTheme.colorScheme.primary, CircleShape)
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                }
                .border(width = 2.dp, color = ringColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
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
                        rotationZ = iconRotation
                        scaleX = 0.9f + (0.1f * fillScale)
                        scaleY = 0.9f + (0.1f * fillScale)
                    }
            )
        }
    }
}

@Composable
private fun AppSelectorHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchAcrylicShape = RoundedCornerShape(14.dp)
    val searchAcrylicBase = Color(0xFF9EA6B2).copy(alpha = 0.34f)
    val searchAcrylicStroke = Color.White.copy(alpha = 0.34f)
    val searchAcrylicOverlay = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.04f),
        )
    )
    val searchHint = stringResource(R.string.search_apps)
    val searchTextStyle = TextStyle(
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = 14.sp,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .border(1.dp, searchAcrylicStroke, searchAcrylicShape)
                .fillMaxWidth(),
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

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onSearchFocusChange(it.isFocused) },
                    singleLine = true,
                    textStyle = searchTextStyle,
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = searchHint,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
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
    val popupScope = rememberCoroutineScope()

    var appCatalog by remember { mutableStateOf<AppCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    val selectedOrder = remember(configItem.key) {
        mutableStateListOf<String>().apply {
            addAll(prefsManager.getStringSet(configItem.key, configItem.type.defaultStringSet))
        }
    }

    var showSystemApps by remember(configItem.key) { mutableStateOf(false) }
    var searchInput by remember(configItem.key) { mutableStateOf("") }
    var searchQuery by remember(configItem.key) { mutableStateOf("") }
    var searchFocused by remember(configItem.key) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var sortMode by remember(configItem.key) { mutableStateOf(AppSortMode.LABEL) }
    var reverseOrder by remember(configItem.key) { mutableStateOf(false) }

    val showSortMenu = remember(configItem.key) { mutableStateOf(false) }
    val showFilterMenu = remember(configItem.key) { mutableStateOf(false) }
    val showMoreMenu = remember(configItem.key) { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()

    BackHandler(enabled = searchFocused) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(configItem.key) {
        loading = true
        appCatalog = null
        delay(220)
        appCatalog = withContext(Dispatchers.IO) { buildAppCatalog(pm) }
        delay(80)
        loading = false
    }

    LaunchedEffect(searchInput) {
        delay(90)
        if (searchQuery != searchInput) {
            searchQuery = searchInput
        }
    }

    val selectedOrderSnapshot by remember {
        derivedStateOf { selectedOrder.toList() }
    }
    val selectedLookup = remember(selectedOrderSnapshot) {
        selectedOrderSnapshot.toHashSet()
    }
    val filteredApps by remember(
        appCatalog,
        showSystemApps,
        searchQuery,
        selectedOrderSnapshot,
        sortMode,
        reverseOrder,
    ) {
        derivedStateOf {
            appCatalog?.buildVisibleApps(
                sortMode = sortMode,
                reverseOrder = reverseOrder,
                showSystemApps = showSystemApps,
                query = searchQuery,
                selectedOrder = selectedOrderSnapshot,
            ) ?: emptyList()
        }
    }
    val indexMap = remember(filteredApps) {
        filteredApps.mapIndexed { index, appItem -> appItem.packageName to index }.toMap()
    }
    val averageItemHeightPx by remember(listState, density) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .takeIf { it.isNotEmpty() }
                ?.map { it.size }
                ?.average()
                ?.toFloat()
                ?.takeIf { it > 0f }
                ?: with(density) { 82.dp.toPx() }
        }
    }
    val dismissThenApply = remember(popupScope) {
        { dismiss: () -> Unit, apply: () -> Unit ->
            dismiss()
            popupScope.launch {
                withFrameNanos { }
                apply()
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .rearAcrylicEffect(hazeState, hazeStyle),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TopAppBar(
                    color = Color.Transparent,
                    title = stringResource(configItem.titleRes),
                    navigationIconPadding = 12.dp,
                    actionIconPadding = 12.dp,
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
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
                        OverlayListPopup(
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
                                            dismissThenApply(
                                                { showSortMenu.value = false },
                                                { sortMode = AppSortMode.LABEL },
                                            )
                                        },
                                    )
                                    SpinnerItemImpl(
                                        entry = SpinnerEntry(title = stringResource(R.string.app_list_sort_by_package)),
                                        entryCount = 3,
                                        isSelected = sortMode == AppSortMode.PACKAGE,
                                        index = 1,
                                        spinnerColors = SpinnerDefaults.spinnerColors(),
                                        onSelectedIndexChange = {
                                            dismissThenApply(
                                                { showSortMenu.value = false },
                                                { sortMode = AppSortMode.PACKAGE },
                                            )
                                        },
                                    )
                                    SpinnerItemImpl(
                                        entry = SpinnerEntry(title = stringResource(R.string.app_list_sort_reverse)),
                                        entryCount = 3,
                                        isSelected = reverseOrder,
                                        index = 2,
                                        spinnerColors = SpinnerDefaults.spinnerColors(),
                                        onSelectedIndexChange = {
                                            dismissThenApply(
                                                { showSortMenu.value = false },
                                                { reverseOrder = !reverseOrder },
                                            )
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
                        OverlayListPopup(
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
                                            dismissThenApply(
                                                { showFilterMenu.value = false },
                                                { showSystemApps = !showSystemApps },
                                            )
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
                        OverlayListPopup(
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
                                            dismissThenApply(
                                                { showMoreMenu.value = false },
                                                { selectedOrder.clear() },
                                            )
                                        },
                                    )
                                }
                            })
                    },
                    scrollBehavior = scrollBehavior,
                )

                AppSelectorHeader(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    searchQuery = searchInput,
                    onSearchQueryChange = { searchInput = it },
                    onSearchFocusChange = { searchFocused = it },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    prefsManager.putStringSet(configItem.key, selectedLookup)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .rearAcrylicSource(hazeState)
                .padding(horizontal = 12.dp),
            state = listState,
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 84.dp,
            ),
            overscrollEffect = null,
        ) {
            if (loading || appCatalog == null) {
                item {
                    LoadingAppsCard()
                }
            } else if (filteredApps.isEmpty()) {
                item {
                    EmptyAppsCard()
                }
            } else {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    val currentIndex = indexMap.getValue(appItem.packageName)
                    AppSelectorRow(
                        appItem = appItem,
                        packageManager = pm,
                        selected = appItem.packageName in selectedLookup,
                        currentIndex = currentIndex,
                        averageItemHeightPx = averageItemHeightPx,
                        onToggle = { selectedOrder.toggleSelection(appItem.packageName) },
                    )
                }
            }
        }
    }
}
