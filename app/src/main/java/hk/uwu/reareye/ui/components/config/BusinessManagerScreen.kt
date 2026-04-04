package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import hk.uwu.reareye.R
import hk.uwu.reareye.repository.rearwidget.RearBusinessConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.repository.rearwidget.RearWidgetManagerRepository
import hk.uwu.reareye.ui.components.card.ModuleStyleDeleteAction
import hk.uwu.reareye.ui.components.card.ModuleStyleIconAction
import hk.uwu.reareye.ui.components.card.ModuleStyleManagerCard
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.components.motion.ArtRevealItem
import hk.uwu.reareye.ui.components.motion.ArtStaggeredReveal
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val DEFAULT_COMPONENT_ROUTE_PACKAGE = "com.xiaomi.subscreencenter"

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun BusinessManagerScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()
    val widgets = remember { mutableStateListOf<RearBusinessConfig>() }
    var widgetsLoaded by remember { mutableStateOf(false) }
    var dataCardsVisible by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftWidget by remember { mutableStateOf("") }
    var draftFilePath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        widgetsLoaded = false
        dataCardsVisible = false
        delay(220)
        val loadedWidgets = withContext(Dispatchers.IO) {
            RearWidgetManagerRepository.loadBusinesses(prefsManager)
        }
        widgets.clear()
        widgets.addAll(loadedWidgets)
        widgetsLoaded = true
        delay(90)
        dataCardsVisible = true
        withContext(Dispatchers.IO) {
            RearWidgetManagerRepository.refreshRuntimeFromPrefs(context, prefsManager)
        }
    }

    fun persist() {
        RearWidgetManagerRepository.saveBusinesses(context, prefsManager, widgets.toList())
    }

    fun openCreateDialog() {
        editingId = null
        draftWidget = ""
        draftFilePath = ""
        showDialog = true
    }

    fun openEditDialog(item: RearBusinessConfig) {
        editingId = item.id
        draftWidget = item.business
        draftFilePath = item.filePath
        showDialog = true
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun submitDialog() {
        val widget = draftWidget.trim()
        val path = draftFilePath.trim()
        if (widget.isBlank() || path.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.rear_widget_form_invalid),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val config = RearBusinessConfig(
            id = RearWidgetConfigCodec.newBusinessId(DEFAULT_COMPONENT_ROUTE_PACKAGE, widget),
            packageName = DEFAULT_COMPONENT_ROUTE_PACKAGE,
            business = widget,
            filePath = path,
            defaultIndex = 0,
            defaultPriority = 500,
        )

        editingId?.let { id ->
            val oldIndex = widgets.indexOfFirst { it.id == id }
            if (oldIndex >= 0) widgets.removeAt(oldIndex)
        }

        val existingIndex = widgets.indexOfFirst { it.business == widget }
        if (existingIndex >= 0) {
            widgets[existingIndex] = config
        } else {
            widgets.add(config)
        }

        persist()
        showDialog = false
        Toast.makeText(
            context,
            context.getString(R.string.rear_widget_business_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copied = RearWidgetManagerRepository.copyTemplateToManagedPath(
            context = context,
            uri = uri,
            businessNameHint = draftWidget,
        )
        if (copied.isNullOrBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.rear_widget_file_pick_failed),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            draftFilePath = copied
            Toast.makeText(
                context,
                context.getString(R.string.rear_widget_file_pick_success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = stringResource(R.string.rear_widget_business_manager),
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
                        onClick = { if (widgetsLoaded) openCreateDialog() }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical()
                .rearAcrylicSource(hazeState)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SuperCard(
                            title = stringResource(R.string.rear_widget_business_file_mode_title),
                            summary = buildString {
                                if (widgetsLoaded) {
                                    append(
                                        context.getString(
                                            R.string.rear_widget_component_count,
                                            widgets.size,
                                        )
                                    )
                                    append('\n')
                                }
                                append(context.getString(R.string.rear_widget_business_file_mode_hint))
                            },
                            onClick = {},
                            bottomAction = {
                                Button(
                                    onClick = { openCreateDialog() },
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    Text(text = stringResource(R.string.rear_widget_add_business))
                                }
                            }
                        )
                    }
                }
            }

            if (!dataCardsVisible) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(vertical = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator()
                                Text(text = stringResource(R.string.rear_widget_loading_data))
                            }
                        }
                    }
                }
            }

            if (dataCardsVisible) {
                itemsIndexed(widgets, key = { _, item -> item.id }) { index, item ->
                    ArtStaggeredReveal(
                        visible = true,
                        revealKey = item.id,
                        delayMillis = (36 + index * 18).coerceAtMost(150),
                    ) {
                        ModuleStyleManagerCard(
                            title = item.business,
                            summaryLines = listOf(item.filePath),
                            onCardClick = { openEditDialog(item) },
                            leftAction = {
                                ModuleStyleIconAction(
                                    icon = Icons.Rounded.EditNote,
                                    onClick = { openEditDialog(item) },
                                )
                            },
                            rightAction = {
                                ModuleStyleDeleteAction(
                                    icon = MiuixIcons.Delete,
                                    text = stringResource(R.string.rear_widget_action_delete),
                                    onClick = {
                                        widgets.remove(item)
                                        persist()
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                if (dataCardsVisible && widgets.isEmpty()) {
                    ArtRevealItem(visible = true, delayMillis = 40) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.rear_widget_empty_business),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = showDialog,
        title = stringResource(
            if (editingId == null) R.string.rear_widget_add_business else R.string.rear_widget_edit_business,
        ),
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = draftWidget,
                onValueChange = { draftWidget = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_business_name),
                singleLine = true,
            )
            TextField(
                value = draftFilePath,
                onValueChange = { draftFilePath = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_template_file),
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(stringResource(R.string.rear_widget_pick_file))
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { submitDialog() },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rear_widget_confirm))
                }
                Button(onClick = { showDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rear_widget_cancel))
                }
            }
        }
    }
}
