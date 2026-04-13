package hk.uwu.reareye.ui.components.config

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hk.uwu.reareye.R
import hk.uwu.reareye.repository.rearwidget.RearBusinessConfig
import hk.uwu.reareye.repository.rearwidget.RearCardConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.repository.rearwidget.RearWidgetManagerRepository
import hk.uwu.reareye.repository.widgettemplate.WidgetTemplateConfigRepository
import hk.uwu.reareye.ui.components.card.ModuleStyleDeleteAction
import hk.uwu.reareye.ui.components.card.ModuleStyleIconAction
import hk.uwu.reareye.ui.components.card.ModuleStyleManagerCard
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.components.config.template.WidgetTemplateConfigScreen
import hk.uwu.reareye.ui.components.motion.ArtRevealItem
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val REAR_WIDGET_DEBUG_TAG = "RearWidgetDebug"

private fun normalizeTemplateBusinessName(raw: String): String {
    return when (raw.trim()) {
        "taxi", "car_hailing", "carHailing" -> "carHailing"
        "food_Delivery", "food_delivery", "foodDelivery" -> "foodDelivery"
        "miHomeCamera", "mihomeCamera" -> "mihomeCamera"
        "xiaomi_ev", "xiaomiev" -> "xiaomiev"
        else -> raw.trim()
    }
}

@Composable
fun CardManagerScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()
    val cards = remember { mutableStateListOf<RearCardConfig>() }
    val businesses = remember { mutableStateListOf<RearBusinessConfig>() }
    val templateAvailability = remember { mutableStateMapOf<String, Boolean>() }
    var cardsLoaded by remember { mutableStateOf(false) }
    var dataCardsVisible by remember { mutableStateOf(false) }
    var runtimeRefreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(220)
        val loadedCards =
            withContext(Dispatchers.IO) { RearWidgetManagerRepository.loadCards(prefsManager) }
        val loadedBusinesses = withContext(Dispatchers.IO) {
            RearWidgetManagerRepository.loadBusinesses(prefsManager)
        }
        cards.clear()
        cards.addAll(loadedCards)
        businesses.clear()
        businesses.addAll(loadedBusinesses)
        cardsLoaded = true
        delay(90)
        dataCardsVisible = true
        withContext(Dispatchers.IO) {
            RearWidgetManagerRepository.refreshRuntimeFromPrefs(context, prefsManager)
        }
        runtimeRefreshTick++
    }

    val showDialog = remember { mutableStateOf(false) }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var draftCardId by remember { mutableStateOf(RearWidgetConfigCodec.newCardId()) }
    val activeTemplateCardId = remember { mutableStateOf<String?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftPackageName by remember { mutableStateOf("hk.uwu.reareye") }
    var draftBusiness by remember { mutableStateOf("") }
    var draftPriorityText by remember { mutableStateOf("500") }
    var draftSticky by remember { mutableStateOf(true) }
    var draftOneConfigJson by remember { mutableStateOf<String?>(null) }

    fun debugLog(message: String) {
        if (prefsManager.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
            Log.d(REAR_WIDGET_DEBUG_TAG, message)
        }
    }

    fun persist() {
        RearWidgetManagerRepository.saveCards(context, prefsManager, cards.toList())
    }

    fun openCreateDialog() {
        editingCardId = null
        draftCardId = RearWidgetConfigCodec.newCardId()
        draftTitle = ""
        draftPackageName = "hk.uwu.reareye"
        draftBusiness = ""
        draftPriorityText = "500"
        draftSticky = true
        draftOneConfigJson = null
        showDialog.value = true
    }

    fun openEditDialog(item: RearCardConfig) {
        if (item.downloadedFromStore) {
            debugLog(
                "open card editor: title=${item.title}, renameable=${item.renameable}, storeWidgetId=${item.storeWidgetId}, priority=${item.priority}"
            )
        }
        editingCardId = item.id
        draftCardId = item.id
        draftTitle = item.title
        draftPackageName = item.packageName
        draftBusiness = item.business
        draftPriorityText = item.priority.toString()
        draftSticky = item.sticky
        draftOneConfigJson = item.oneConfigJson
        showDialog.value = true
    }

    fun openTemplateConfig(item: RearCardConfig) {
        activeTemplateCardId.value = item.id
    }

    val activeTemplateCard =
        activeTemplateCardId.value?.let { id -> cards.firstOrNull { it.id == id } }
    if (activeTemplateCard != null) {
        val normalizedBusiness = normalizeTemplateBusinessName(activeTemplateCard.business)
        val sourceFilePath = businesses.firstOrNull {
            it.business == activeTemplateCard.business || normalizeTemplateBusinessName(it.business) == normalizedBusiness
        }?.filePath.orEmpty()
        WidgetTemplateConfigScreen(
            business = activeTemplateCard.business,
            sourceFilePath = sourceFilePath,
            cardStorageKey = activeTemplateCard.id,
            currentConfigJson = activeTemplateCard.oneConfigJson,
            onBack = { activeTemplateCardId.value = null },
            onSave = { normalizedJson ->
                val index = cards.indexOfFirst { it.id == activeTemplateCard.id }
                if (index >= 0) {
                    cards[index] =
                        cards[index].copy(oneConfigJson = normalizedJson?.takeIf { it.isNotBlank() })
                    persist()
                    activeTemplateCardId.value = null
                } else {
                    activeTemplateCardId.value = null
                }
            },
        )
        return
    }

    LaunchedEffect(cardsLoaded, cards.toList(), businesses.toList(), runtimeRefreshTick) {
        if (!cardsLoaded) return@LaunchedEffect
        val businessSourceByName = businesses.associate { it.business to it.filePath }
        val uniqueBusinesses = cards.map { it.business.trim() }
            .filter { it.isNotBlank() }
            .map(::normalizeTemplateBusinessName)
            .distinct()
        val availability = linkedMapOf<String, Boolean>()
        uniqueBusinesses.forEach { business ->
            val sourcePath = businessSourceByName.entries.firstOrNull {
                it.key == business || normalizeTemplateBusinessName(it.key) == business
            }?.value.orEmpty()

            var editableCount: Int
            var available = false
            repeat(2) { attempt ->
                val state = withContext(Dispatchers.IO) {
                    RearWidgetManagerRepository.resolveTemplateConfigState(
                        context = context,
                        business = business,
                        sourceFilePath = sourcePath,
                        currentOneConfigJson = null,
                    )
                }
                val schema = state?.templateSchemaJson
                    ?.let(WidgetTemplateConfigRepository::decodeSchema)
                if (state?.templateSchemaJson?.isNotBlank() == true && schema == null) {
                    debugLog(
                        "schema decode failed business=$business source=${sourcePath.ifBlank { "<builtin>" }} schemaLength=${state.templateSchemaJson.length}"
                    )
                }
                editableCount = schema?.editableItemCount ?: -1
                available = editableCount > 0
                debugLog(
                    "availability probe business=$business source=${sourcePath.ifBlank { "<builtin>" }} attempt=${attempt + 1} editable=$editableCount available=$available"
                )
                if (available || attempt == 1) return@repeat
                delay(300)
            }
            availability[business] = available
        }
        templateAvailability.clear()
        templateAvailability.putAll(availability)
        debugLog("template availability: ${availability.entries.joinToString { "${it.key}=${it.value}" }}")
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun submitDialog() {
        val pkg = draftPackageName.trim()
        val widget = draftBusiness.trim()
        val editingCard = editingCardId?.let { id -> cards.firstOrNull { it.id == id } }
        val lockedCard = editingCard?.takeIf { !it.renameable }
        if (lockedCard != null && lockedCard.downloadedFromStore) {
            debugLog(
                "submit locked card editor: title=${lockedCard.title}, storeWidgetId=${lockedCard.storeWidgetId}, oldPriority=${lockedCard.priority}, newPriority=${draftPriorityText}"
            )
        }
        if (pkg.isBlank() || widget.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.rear_widget_form_invalid),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val card = lockedCard?.copy(
            priority = draftPriorityText.toIntOrNull() ?: lockedCard.priority,
            oneConfigJson = draftOneConfigJson?.takeIf { it.isNotBlank() },
        ) ?: RearCardConfig(
            id = draftCardId,
            title = draftTitle.trim().ifBlank { widget },
            packageName = pkg,
            business = widget,
            oneConfigJson = draftOneConfigJson?.takeIf { it.isNotBlank() },
            enabled = editingCard?.enabled ?: true,
            sticky = draftSticky,
            priority = draftPriorityText.toIntOrNull() ?: 500,
            renameable = editingCard?.renameable ?: true,
            downloadedFromStore = editingCard?.downloadedFromStore ?: false,
            storeWidgetId = editingCard?.storeWidgetId,
            storeWidgetName = editingCard?.storeWidgetName,
            storeReleaseTag = editingCard?.storeReleaseTag,
            storeReleaseAssetName = editingCard?.storeReleaseAssetName,
            storeReleasePublishedAt = editingCard?.storeReleasePublishedAt,
        )

        val editingIndex = cards.indexOfFirst { it.id == card.id }
        if (editingIndex >= 0) {
            cards[editingIndex] = card
        } else {
            cards.add(card)
        }

        persist()
        showDialog.value = false
        Toast.makeText(
            context,
            context.getString(R.string.rear_widget_card_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = stringResource(R.string.rear_widget_card_manager),
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
                        onClick = { if (cardsLoaded) openCreateDialog() }) {
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
                            title = stringResource(R.string.rear_widget_card_dialog_hint_title),
                            summary = buildString {
                                if (cardsLoaded) {
                                    append(
                                        stringResource(
                                            R.string.rear_widget_card_count,
                                            cards.size,
                                        )
                                    )
                                    append('\n')
                                }
                                append(stringResource(R.string.rear_widget_card_dialog_hint))
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
                                    Text(text = stringResource(R.string.rear_widget_add_card))
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
                itemsIndexed(
                    items = cards,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "card_item" },
                ) { _, item ->
                    val normalizedBusiness = normalizeTemplateBusinessName(item.business)
                    val hasTemplateConfig =
                        templateAvailability[item.business] == true ||
                                templateAvailability[normalizedBusiness] == true ||
                                item.oneConfigJson.isNullOrBlank().not()
                    if (prefsManager.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                        debugLog(
                            "card template action id=${item.id} business=${item.business} normalized=$normalizedBusiness available=$hasTemplateConfig rawAvailable=${templateAvailability[item.business]} normalizedAvailable=${templateAvailability[normalizedBusiness]} hasConfig=${
                                item.oneConfigJson.isNullOrBlank().not()
                            }"
                        )
                    }
                    ModuleStyleManagerCard(
                        title = item.title,
                        summaryLines = buildList {
                            add(
                                stringResource(
                                    R.string.rear_widget_card_summary,
                                    item.packageName,
                                    item.business,
                                    item.priority,
                                )
                            )
                            add(
                                stringResource(
                                    R.string.rear_widget_card_sticky_summary,
                                    stringResource(
                                        if (item.sticky) {
                                            R.string.rear_wallpaper_schedule_on
                                        } else {
                                            R.string.rear_wallpaper_schedule_off
                                        }
                                    ),
                                )
                            )
                            item.storeWidgetId?.takeIf { it.isNotBlank() }?.let {
                                add(
                                    stringResource(
                                        R.string.rear_widget_store_source_summary,
                                        it,
                                    )
                                )
                            }
                            if (!item.renameable) {
                                add(stringResource(R.string.rear_widget_card_locked_summary))
                            }
                            add(
                                stringResource(
                                    if (item.oneConfigJson.isNullOrBlank()) {
                                        R.string.rear_widget_card_template_status_default
                                    } else {
                                        R.string.rear_widget_card_template_status_custom
                                    }
                                )
                            )
                        },
                        trailing = {
                            if (item.renameable) {
                                Switch(
                                    checked = item.enabled,
                                    onCheckedChange = { checked ->
                                        val i = cards.indexOfFirst { it.id == item.id }
                                        if (i >= 0) {
                                            cards[i] = cards[i].copy(enabled = checked)
                                            scope.launch(Dispatchers.IO) {
                                                RearWidgetManagerRepository.setCardEnabled(
                                                    context = context,
                                                    prefsManager = prefsManager,
                                                    cardId = item.id,
                                                    enabled = checked,
                                                )
                                            }
                                        }
                                    },
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    Text(
                                        text = stringResource(R.string.rear_store_locked),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        },
                        onCardClick = { openEditDialog(item) },
                        leftAction = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ModuleStyleIconAction(
                                    icon = Icons.Rounded.EditNote,
                                    onClick = { openEditDialog(item) },
                                )
                                if (hasTemplateConfig) {
                                    ModuleStyleDeleteAction(
                                        icon = Icons.Filled.Tune,
                                        text = stringResource(R.string.rear_widget_action_config),
                                        onClick = { openTemplateConfig(item) },
                                    )
                                }
                            }
                        },
                        rightAction = {
                            if (item.renameable) {
                                ModuleStyleDeleteAction(
                                    icon = MiuixIcons.Delete,
                                    text = stringResource(R.string.rear_widget_action_delete),
                                    onClick = {
                                        if (!item.renameable) return@ModuleStyleDeleteAction
                                        cards.remove(item)
                                        persist()
                                    },
                                )
                            }
                        },
                    )
                }
            }

            item {
                if (dataCardsVisible && cards.isEmpty()) {
                    ArtRevealItem(visible = true, delayMillis = 40) {
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
    }

    OverlayDialog(
        show = showDialog.value,
        title = stringResource(
            if (editingCardId == null) R.string.rear_widget_add_card else R.string.rear_widget_edit_card,
        ),
        onDismissRequest = { showDialog.value = false },
    ) {
        val editingCard = editingCardId?.let { id -> cards.firstOrNull { it.id == id } }
        val lockedCard = editingCard?.takeIf { !it.renameable }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lockedCard == null) {
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
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.rear_widget_card_locked_summary),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.rear_widget_card_summary,
                                lockedCard.packageName,
                                lockedCard.business,
                                lockedCard.priority,
                            ),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            TextField(
                value = draftPriorityText,
                onValueChange = { draftPriorityText = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.rear_widget_default_priority),
                singleLine = true,
            )
            if (lockedCard == null) {
                SwitchPreference(
                    title = stringResource(R.string.rear_widget_card_sticky),
                    summary = stringResource(R.string.rear_widget_card_sticky_desc),
                    checked = draftSticky,
                    onCheckedChange = { draftSticky = it },
                )
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
                Button(onClick = { showDialog.value = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rear_widget_cancel))
                }
            }
        }
    }

}
