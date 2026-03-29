package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import hk.uwu.reareye.rearwidget.RearCardConfig
import hk.uwu.reareye.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.rearwidget.RearWidgetManagerRepository
import hk.uwu.reareye.ui.config.PrefsManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun CardManagerScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val cards = remember {
        mutableStateListOf<RearCardConfig>().apply {
            addAll(RearWidgetManagerRepository.loadCards(prefsManager))
        }
    }

    LaunchedEffect(Unit) {
        RearWidgetManagerRepository.refreshRuntimeFromPrefs(context, prefsManager)
    }

    var showDialog by remember { mutableStateOf(false) }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftPackageName by remember { mutableStateOf("com.xiaomi.subscreencenter") }
    var draftBusiness by remember { mutableStateOf("") }
    var draftPriorityText by remember { mutableStateOf("500") }
    var draftEnabled by remember { mutableStateOf(true) }

    fun persist() {
        RearWidgetManagerRepository.saveCards(context, prefsManager, cards.toList())
    }

    fun moveCard(from: Int, to: Int) {
        if (from !in cards.indices || to !in cards.indices || from == to) return
        val item = cards.removeAt(from)
        cards.add(to, item)
        persist()
    }

    fun openCreateDialog() {
        editingCardId = null
        draftTitle = ""
        draftPackageName = "com.xiaomi.subscreencenter"
        draftBusiness = ""
        draftPriorityText = "500"
        draftEnabled = true
        showDialog = true
    }

    fun openEditDialog(item: RearCardConfig) {
        editingCardId = item.id
        draftTitle = item.title
        draftPackageName = item.packageName
        draftBusiness = item.business
        draftPriorityText = item.priority.toString()
        draftEnabled = item.enabled
        showDialog = true
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun submitDialog() {
        val pkg = draftPackageName.trim()
        val widget = draftBusiness.trim()
        if (pkg.isBlank() || widget.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.rear_widget_form_invalid),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val card = RearCardConfig(
            id = editingCardId ?: RearWidgetConfigCodec.newCardId(),
            title = draftTitle.trim().ifBlank { widget },
            packageName = pkg,
            business = widget,
            enabled = draftEnabled,
            priority = draftPriorityText.toIntOrNull() ?: 500,
        )

        val editingIndex = cards.indexOfFirst { it.id == card.id }
        if (editingIndex >= 0) {
            cards[editingIndex] = card
        } else {
            cards.add(card)
        }

        persist()
        showDialog = false
        Toast.makeText(
            context,
            context.getString(R.string.rear_widget_card_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.rear_widget_card_manager),
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
                        title = stringResource(R.string.rear_widget_card_dialog_hint_title),
                        summary = stringResource(
                            R.string.rear_widget_card_count,
                            cards.size,
                        ) + "\n" + stringResource(R.string.rear_widget_card_dialog_hint),
                        onClick = { openCreateDialog() },
                    )
                }
            }

            itemsIndexed(cards, key = { _, item -> item.id }) { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    SuperArrow(
                        title = item.title,
                        summary = stringResource(
                            R.string.rear_widget_card_summary,
                            item.packageName,
                            item.business,
                            item.priority,
                        ),
                        onClick = { openEditDialog(item) },
                        endActions = {
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { checked ->
                                    val i = cards.indexOfFirst { it.id == item.id }
                                    if (i >= 0) {
                                        cards[i] = cards[i].copy(enabled = checked)
                                        persist()
                                    }
                                },
                            )
                            IconButton(onClick = {
                                moveCard(
                                    index,
                                    (index - 1).coerceAtLeast(0)
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = null
                                )
                            }
                            IconButton(onClick = {
                                moveCard(
                                    index,
                                    (index + 1).coerceAtMost(cards.lastIndex)
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                            IconButton(onClick = {
                                cards.remove(item)
                                persist()
                            }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                            }
                        },
                    )
                }
            }

            item {
                AnimatedVisibility(visible = cards.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.rear_widget_empty_card),
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
            if (editingCardId == null) R.string.rear_widget_add_card else R.string.rear_widget_edit_card,
        ),
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_card_title),
                singleLine = true,
            )
            TextField(
                value = draftPackageName,
                onValueChange = { draftPackageName = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_target_package),
                singleLine = true,
            )
            TextField(
                value = draftBusiness,
                onValueChange = { draftBusiness = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_business_name),
                singleLine = true,
            )
            TextField(
                value = draftPriorityText,
                onValueChange = { draftPriorityText = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_default_priority),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.rear_widget_enabled))
                Switch(checked = draftEnabled, onCheckedChange = { draftEnabled = it })
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
