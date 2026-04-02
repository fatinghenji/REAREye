package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import hk.uwu.reareye.rearwidget.RearBusinessExtraConfig
import hk.uwu.reareye.rearwidget.RearBusinessExtraConfigEntry
import hk.uwu.reareye.rearwidget.RearBusinessExtraConfigFields
import hk.uwu.reareye.rearwidget.RearBusinessExtraConfigRepository
import hk.uwu.reareye.ui.components.card.ModuleStyleDeleteAction
import hk.uwu.reareye.ui.components.card.ModuleStyleIconAction
import hk.uwu.reareye.ui.components.card.ModuleStyleManagerCard
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.config.ConfigGroup
import hk.uwu.reareye.ui.config.ConfigItem
import hk.uwu.reareye.ui.config.ConfigNode
import hk.uwu.reareye.ui.config.ConfigType
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import kotlinx.coroutines.Dispatchers
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
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val BusinessExtraConfigNodes = listOf<ConfigNode>(
    ConfigGroup(
        children = listOf(
            ConfigItem(
                key = RearBusinessExtraConfigFields.HIDE_TIME_TIP,
                titleRes = R.string.rear_widget_business_hide_time_tip,
                descriptionRes = R.string.rear_widget_business_hide_time_tip_desc,
                type = ConfigType.BooleanVal(defaultValue = false),
            )
        )
    )
)

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun BusinessExtraConfigManagerScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()
    val entries = remember { mutableStateListOf<RearBusinessExtraConfigEntry>() }
    var loaded by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var draftBusiness by remember { mutableStateOf("") }
    var editingBusiness by remember { mutableStateOf<String?>(null) }

    suspend fun reloadEntries() {
        val loadedEntries = withContext(Dispatchers.IO) {
            RearBusinessExtraConfigRepository.getAllConfigs(prefsManager)
        }
        entries.clear()
        entries.addAll(loadedEntries)
    }

    LaunchedEffect(Unit) {
        contentVisible = true
        reloadEntries()
        loaded = true
    }

    LaunchedEffect(editingBusiness) {
        if (editingBusiness == null && loaded) {
            reloadEntries()
        }
    }

    fun openCreateDialog() {
        draftBusiness = ""
        showDialog = true
    }

    fun submitDialog() {
        val business = draftBusiness.trim()
        if (business.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.rear_widget_form_invalid),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val existing = entries.firstOrNull { it.business == business }
        if (existing == null) {
            RearBusinessExtraConfigRepository.saveConfigForBusiness(
                prefsManager = prefsManager,
                business = business,
                config = RearBusinessExtraConfig(),
            )

            entries.add(
                RearBusinessExtraConfigEntry(
                    business = business,
                    config = RearBusinessExtraConfigRepository.getConfigForBusiness(
                        prefsManager = prefsManager,
                        business = business,
                    ),
                )
            )
            entries.sortBy { it.business.lowercase() }
        }

        showDialog = false
        editingBusiness = business
    }

    BackHandler(enabled = editingBusiness != null) {
        editingBusiness = null
    }

    if (editingBusiness != null) {
        BusinessExtraConfigScreen(
            prefsManager = prefsManager,
            business = editingBusiness!!,
            onBack = {
                editingBusiness = null
                showDialog = false
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = stringResource(R.string.rear_widget_business_extra_manager),
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
                        onClick = { if (loaded) openCreateDialog() },
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
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
                ManagerRevealItem(visible = contentVisible, delayMillis = 0) {
                    if (loaded) {
                        Card(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SuperCard(
                                    title = stringResource(R.string.rear_widget_business_extra_hint_title),
                                    summary = stringResource(
                                        R.string.rear_widget_business_extra_count,
                                        entries.size,
                                    ) + "\n" + stringResource(R.string.rear_widget_business_extra_hint),
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
                                            Text(text = stringResource(R.string.rear_widget_business_extra_add))
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .fillMaxWidth(),
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
            }

            if (loaded) {
                itemsIndexed(entries, key = { _, item -> item.business }) { index, item ->
                    ManagerRevealItem(
                        visible = contentVisible,
                        delayMillis = (80 + index * 24).coerceAtMost(280),
                    ) {
                        ModuleStyleManagerCard(
                            title = item.business,
                            summaryLines = listOf(),
                            onCardClick = { editingBusiness = item.business },
                            leftAction = {
                                ModuleStyleIconAction(
                                    icon = Icons.Rounded.EditNote,
                                    onClick = { editingBusiness = item.business },
                                )
                            },
                            rightAction = {
                                ModuleStyleDeleteAction(
                                    icon = MiuixIcons.Delete,
                                    text = stringResource(R.string.rear_widget_action_delete),
                                    onClick = {
                                        RearBusinessExtraConfigRepository.removeConfigForBusiness(
                                            prefsManager = prefsManager,
                                            business = item.business,
                                        )
                                        entries.remove(item)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = loaded && entries.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.rear_widget_business_extra_empty),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    SuperDialog(
        show = showDialog,
        title = stringResource(R.string.rear_widget_business_extra_add),
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
                value = draftBusiness,
                onValueChange = { draftBusiness = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_business_name),
                singleLine = true,
            )
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

@Composable
fun BusinessExtraConfigScreen(
    prefsManager: PrefsManager,
    business: String,
    onBack: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()
    var config by remember(business) {
        mutableStateOf(
            RearBusinessExtraConfigRepository.getConfigForBusiness(
                prefsManager = prefsManager,
                business = business,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = stringResource(R.string.rear_widget_business_extra_settings),
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
                scrollBehavior = scrollBehavior,
            )
        }
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
            overscrollEffect = null,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.rear_widget_business_extra_settings_target,
                            business
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }

            itemsIndexed(
                BusinessExtraConfigNodes,
                key = { index, node -> node.key ?: "extra_$index" }) { index, node ->
                if (node is ConfigGroup) {
                    Card(
                        modifier = Modifier
                            .padding(top = if (index == 0) 0.dp else 8.dp)
                            .fillMaxWidth()
                    ) {
                        node.children.forEach { child ->
                            BusinessExtraConfigNodeRow(
                                node = child,
                                config = config,
                                onConfigChange = { updated ->
                                    config = updated
                                    RearBusinessExtraConfigRepository.saveConfigForBusiness(
                                        prefsManager = prefsManager,
                                        business = business,
                                        config = updated,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessExtraConfigNodeRow(
    node: ConfigNode,
    config: RearBusinessExtraConfig,
    onConfigChange: (RearBusinessExtraConfig) -> Unit,
) {
    if (node !is ConfigItem) return
    when (val type = node.type) {
        is ConfigType.BooleanVal -> {
            val checked = config.getBoolean(node.key, type.defaultValue)
            SuperSwitch(
                title = stringResource(node.titleRes),
                summary = node.descriptionRes?.let { stringResource(it) },
                checked = checked,
                onCheckedChange = { value ->
                    onConfigChange(config.withBoolean(node.key, value))
                },
            )
        }

        else -> Unit
    }
}

@Composable
private fun ManagerRevealItem(
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
