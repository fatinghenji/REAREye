package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import hk.uwu.reareye.R
import hk.uwu.reareye.rearwidget.RearBusinessConfig
import hk.uwu.reareye.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.rearwidget.RearWidgetManagerRepository
import hk.uwu.reareye.ui.config.PrefsManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val DEFAULT_COMPONENT_ROUTE_PACKAGE = "com.xiaomi.subscreencenter"

@Composable
fun BusinessManagerScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val components = remember {
        mutableStateListOf<RearBusinessConfig>().apply {
            addAll(RearWidgetManagerRepository.loadBusinesses(prefsManager))
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftComponent by remember { mutableStateOf("") }
    var draftFilePath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        RearWidgetManagerRepository.refreshRuntimeFromPrefs(context, prefsManager)
    }

    fun persist() {
        RearWidgetManagerRepository.saveBusinesses(context, prefsManager, components.toList())
    }

    fun openCreateDialog() {
        editingId = null
        draftComponent = ""
        draftFilePath = ""
        showDialog = true
    }

    fun openEditDialog(item: RearBusinessConfig) {
        editingId = item.id
        draftComponent = item.business
        draftFilePath = item.filePath
        showDialog = true
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun submitDialog() {
        val widget = draftComponent.trim()
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
            val oldIndex = components.indexOfFirst { it.id == id }
            if (oldIndex >= 0) components.removeAt(oldIndex)
        }

        val existingIndex = components.indexOfFirst { it.business == widget }
        if (existingIndex >= 0) {
            components[existingIndex] = config
        } else {
            components.add(config)
        }

        persist()
        showDialog = false
        Toast.makeText(
            context,
            context.getString(R.string.rear_widget_business_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val copied = RearWidgetManagerRepository.copyTemplateToManagedPath(
            context = context,
            uri = uri,
            businessNameHint = draftComponent,
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
                title = stringResource(R.string.rear_widget_business_manager),
                navigationIcon = {
                    IconButton(modifier = Modifier.padding(start = 16.dp), onClick = onBack) {
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
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { openCreateDialog() }) {
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
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = stringResource(R.string.rear_widget_business_file_mode_title),
                        summary = stringResource(
                            R.string.rear_widget_component_count,
                            components.size,
                        ) + "\n" + stringResource(R.string.rear_widget_business_file_mode_hint),
                        onClick = { openCreateDialog() },
                    )
                }
            }

            itemsIndexed(
                items = components,
                key = { _, item -> item.id },
            ) { _, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    SuperArrow(
                        title = item.business,
                        summary = item.filePath,
                        onClick = { openEditDialog(item) },
                        endActions = {
                            IconButton(onClick = { openEditDialog(item) }) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                            }
                            IconButton(onClick = {
                                components.remove(item)
                                persist()
                            }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                            }
                        },
                    )
                }
            }

            item {
                AnimatedVisibility(visible = components.isEmpty()) {
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

    SuperDialog(
        show = showDialog,
        title = stringResource(
            if (editingId == null) R.string.rear_widget_add_business else R.string.rear_widget_edit_business,
        ),
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = draftComponent,
                onValueChange = { draftComponent = it },
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
                onClick = { picker.launch(arrayOf("*/*")) },
            ) {
                Text(stringResource(R.string.rear_widget_pick_file))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.rear_widget_cancel),
                    onClick = { showDialog = false },
                )
                TextButton(
                    text = stringResource(R.string.rear_widget_confirm),
                    onClick = { submitDialog() },
                )
            }
        }
    }
}
