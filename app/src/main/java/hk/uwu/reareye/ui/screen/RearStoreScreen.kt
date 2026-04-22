package hk.uwu.reareye.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import hk.uwu.reareye.BuildConfig
import hk.uwu.reareye.R
import hk.uwu.reareye.repository.rearstore.RearStoreAuthor
import hk.uwu.reareye.repository.rearstore.RearStoreInstallProgressStage
import hk.uwu.reareye.repository.rearstore.RearStoreInstalledWidget
import hk.uwu.reareye.repository.rearstore.RearStoreListItem
import hk.uwu.reareye.repository.rearstore.RearStoreRelease
import hk.uwu.reareye.repository.rearstore.RearStoreReleaseAsset
import hk.uwu.reareye.repository.rearstore.RearStoreRepository
import hk.uwu.reareye.repository.rearstore.RearStoreWidgetDetail
import hk.uwu.reareye.repository.rearstore.RearStoreWidgetInfoType
import hk.uwu.reareye.repository.rearstore.RearStoreWidgetMetadataType
import hk.uwu.reareye.repository.rearstore.resolvedType
import hk.uwu.reareye.ui.components.RearBadgeGroup
import hk.uwu.reareye.ui.components.RearBadgeItem
import hk.uwu.reareye.ui.components.RearBadgePalette
import hk.uwu.reareye.ui.components.RearSearchBar
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.components.motion.ArtRevealItem
import hk.uwu.reareye.ui.components.motion.ArtStaggeredReveal
import hk.uwu.reareye.ui.components.rememberRearAccentBadgePalette
import hk.uwu.reareye.ui.components.webview.ScrollWebView
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
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
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private val rearStoreAvatarHttpClient = OkHttpClient.Builder()
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
    }
    .build()

private val rearStoreReadmeWebViewMaxHeight = 420.dp
private val rearStoreReleaseWebViewMaxHeight = 260.dp

private enum class RearStoreDetailTab {
    README,
    DOWNLOADS,
    INFO,
}

private enum class RearStoreSortMode {
    UPDATED_AT,
    INSTALLED_AT,
    NAME,
    STARS,
}

private data class RearStoreActiveInstall(
    val assetKey: String,
    val phase: RearStoreInstallProgressStage = RearStoreInstallProgressStage.CONNECTING,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
)

private object RearStoreAvatarCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun peek(url: String?): ImageBitmap? {
        val key = url?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return cache[key]
    }

    suspend fun load(url: String?): ImageBitmap? {
        val key = url?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        cache[key]?.let { return it }

        val image = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(key)
                    .build()
                rearStoreAvatarHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
                }
            }.getOrNull()
        }

        if (image != null) {
            cache.putIfAbsent(key, image)
        }
        return cache[key] ?: image
    }
}

private object RearStoreHtmlDocumentCache {
    private const val TEMPLATE_LIGHT = "webview/template.html"
    private const val TEMPLATE_DARK = "webview/template_dark.html"

    private val htmlCache = ConcurrentHashMap<String, String>()
    private val templateCache = ConcurrentHashMap<String, String>()

    fun wrapDocument(
        context: Context,
        htmlBody: String,
        darkMode: Boolean,
        themeCssVariables: String,
    ): String? {
        val normalizedHtml = htmlBody.trim()
        if (normalizedHtml.isEmpty()) return null
        val cacheKey = listOf(darkMode.toString(), themeCssVariables, normalizedHtml)
            .joinToString(separator = "\u0000")
        htmlCache[cacheKey]?.let { return it }

        val direction =
            if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                "rtl"
            } else {
                "ltr"
            }
        val template = loadTemplate(context, darkMode)
        val document = template
            .replace("@dir@", direction)
            .replace("@theme_css@", themeCssVariables)
            .replace("@body@", normalizedHtml)
        htmlCache[cacheKey] = document
        return document
    }

    private fun loadTemplate(context: Context, darkMode: Boolean): String {
        val assetName = if (darkMode) TEMPLATE_DARK else TEMPLATE_LIGHT
        return templateCache.getOrPut(assetName) {
            runCatching {
                context.assets.open(assetName).bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
            }.getOrDefault("<html dir=\"@dir@\"><body>@body@</body></html>")
        }
    }
}

private fun Color.toCssColor(): String {
    val red = (this.red * 255).toInt().coerceIn(0, 255)
    val green = (this.green * 255).toInt().coerceIn(0, 255)
    val blue = (this.blue * 255).toInt().coerceIn(0, 255)
    return "rgba($red, $green, $blue, ${this.alpha})"
}

private fun String?.normalizedOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}

private fun isoDateLabel(value: String?): String {
    return value?.substringBefore('T')?.trim().takeUnless { it.isNullOrEmpty() } ?: "-"
}

private fun formatSize(size: Long): String {
    if (size <= 0L) return "0 B"
    val kb = size / 1024.0
    if (kb < 1024.0) {
        return DecimalFormat("0.#").format(kb) + " KB"
    }
    val mb = kb / 1024.0
    return DecimalFormat("0.##").format(mb) + " MB"
}

@Composable
private fun rememberSkeletonPulseAlpha(label: String): Float {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "$label-alpha",
    )
    return alpha.value
}

private data class RearStoreInstallInfo(
    val widgetInfoType: RearStoreWidgetInfoType,
    val businessId: String,
    val hasBusinessSetup: Boolean,
    val hasCard: Boolean,
    val cardId: String?,
)

private fun defaultInstallInfo(detail: RearStoreWidgetDetail): RearStoreInstallInfo {
    val widgetInfoType = detail.widgetInfo.resolvedType()
    val businessSetupId = detail.widgetInfo?.businessSetup?.id.normalizedOrNull()
    val businessId = businessSetupId ?: detail.widgetId
    val hasCard = widgetInfoType == RearStoreWidgetInfoType.WIDGET &&
            detail.widgetInfo?.cardSetup != null
    val cardId = if (hasCard) businessId else null
    return RearStoreInstallInfo(
        widgetInfoType = widgetInfoType,
        businessId = businessId,
        hasBusinessSetup = businessSetupId != null,
        hasCard = hasCard,
        cardId = cardId,
    )
}

private fun RearStoreWidgetInfoType.labelResId(): Int {
    return when (this) {
        RearStoreWidgetInfoType.WIDGET -> R.string.rear_store_widget_info_type_widget
        RearStoreWidgetInfoType.WALLPAPER -> R.string.rear_store_widget_info_type_wallpaper
    }
}

private fun RearStoreWidgetMetadataType.labelResId(): Int? {
    return when (this) {
        RearStoreWidgetMetadataType.CARD -> R.string.rear_store_metadata_type_card
        RearStoreWidgetMetadataType.NOTIFICATION -> R.string.rear_store_metadata_type_notification
        RearStoreWidgetMetadataType.ENHANCED -> R.string.rear_store_metadata_type_enhanced
        RearStoreWidgetMetadataType.WALLPAPER -> R.string.rear_store_metadata_type_wallpaper
        RearStoreWidgetMetadataType.UNKNOWN -> null
    }
}

private fun parseMetadataType(rawType: String?): RearStoreWidgetMetadataType {
    val raw = rawType?.trim().orEmpty()
    if (raw.contains("壁纸")) return RearStoreWidgetMetadataType.WALLPAPER
    if (raw.contains("通知")) return RearStoreWidgetMetadataType.NOTIFICATION
    if (raw.contains("增强")) return RearStoreWidgetMetadataType.ENHANCED
    if (raw.contains("卡片") || raw.contains("组件")) return RearStoreWidgetMetadataType.CARD

    val normalized = rawType
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace('-', '_')
        ?.replace(' ', '_')
        .orEmpty()
    if (normalized.isBlank()) return RearStoreWidgetMetadataType.UNKNOWN
    return when {
        normalized.contains("wallpaper") -> RearStoreWidgetMetadataType.WALLPAPER
        normalized.contains("notification") || normalized.contains("notify") -> {
            RearStoreWidgetMetadataType.NOTIFICATION
        }

        normalized.contains("enhanced") || normalized.contains("enhance") -> {
            RearStoreWidgetMetadataType.ENHANCED
        }

        normalized.contains("card") || normalized.contains("widget") -> {
            RearStoreWidgetMetadataType.CARD
        }

        else -> RearStoreWidgetMetadataType.UNKNOWN
    }
}

private fun RearStoreListItem.displayMetadataType(): RearStoreWidgetMetadataType {
    parseMetadataType(type).takeIf {
        it != RearStoreWidgetMetadataType.UNKNOWN
    }?.let { return it }

    parseMetadataType(metadata?.type).takeIf {
        it != RearStoreWidgetMetadataType.UNKNOWN
    }?.let { return it }

    val metadataType = metadata.resolvedType()
    if (metadataType != RearStoreWidgetMetadataType.UNKNOWN) return metadataType

    return RearStoreWidgetMetadataType.CARD
}

private fun RearStoreWidgetDetail.displayMetadataType(): RearStoreWidgetMetadataType {
    parseMetadataType(type).takeIf {
        it != RearStoreWidgetMetadataType.UNKNOWN
    }?.let { return it }

    parseMetadataType(metadata?.type).takeIf {
        it != RearStoreWidgetMetadataType.UNKNOWN
    }?.let { return it }

    val metadataType = metadata.resolvedType()
    if (metadataType != RearStoreWidgetMetadataType.UNKNOWN) {
        return metadataType
    }

    parseMetadataType(widgetInfo?.type).takeIf {
        it != RearStoreWidgetMetadataType.UNKNOWN
    }?.let { return it }

    return when (widgetInfo.resolvedType()) {
        RearStoreWidgetInfoType.WALLPAPER -> RearStoreWidgetMetadataType.WALLPAPER
        RearStoreWidgetInfoType.WIDGET -> RearStoreWidgetMetadataType.CARD
    }
}

private fun resolveInstallBlockMessage(
    context: Context,
    detail: RearStoreWidgetDetail,
): String? {
    val widgetInfoType = detail.widgetInfo.resolvedType()
    if (!widgetInfoType.supportedInCurrentVersion) {
        return context.getString(
            R.string.rear_store_install_type_not_supported,
            context.getString(widgetInfoType.labelResId()),
        )
    }
    if (widgetInfoType == RearStoreWidgetInfoType.WIDGET &&
        detail.widgetInfo?.businessSetup?.id.normalizedOrNull() == null
    ) {
        return context.getString(R.string.rear_store_install_missing_business_setup)
    }
    return null
}

private fun parseIsoEpochMillis(value: String?): Long {
    val normalized = value.normalizedOrNull() ?: return Long.MIN_VALUE
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    patterns.forEach { pattern ->
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalized)
        }.getOrNull()
        if (parsed != null) {
            return parsed.time
        }
    }
    return Long.MIN_VALUE
}

private fun RearStoreListItem.matchesLocalQuery(query: String): Boolean {
    val tokens = query.trim()
        .split(Regex("\\s+"))
        .mapNotNull(String::normalizedOrNull)
    if (tokens.isEmpty()) return true

    val searchableFields = listOf(
        id,
        displayName,
        description,
        author.displayName,
        author.login,
        repository?.fullName.orEmpty(),
        latestReleaseTag.orEmpty(),
    )

    return tokens.all { token ->
        searchableFields.any { field -> field.contains(token, ignoreCase = true) }
    }
}

private fun RearStoreListItem.hasAvailableUpdate(
    installedWidgets: Map<String, RearStoreInstalledWidget>,
): Boolean {
    val installedReleaseTag = installedWidgets[id]?.releaseTag.normalizedOrNull() ?: return false
    val latestReleaseTag = latestReleaseTag.normalizedOrNull() ?: return false
    return installedReleaseTag != latestReleaseTag
}

private fun RearStoreListItem.updatedAtSortValue(): Long {
    return maxOf(
        parseIsoEpochMillis(latestReleasePublishedAt),
        parseIsoEpochMillis(updatedAt),
    )
}

private fun RearStoreListItem.installedAtSortValue(
    installedWidgets: Map<String, RearStoreInstalledWidget>,
): Long {
    val installedWidget = installedWidgets[id] ?: return Long.MIN_VALUE
    return maxOf(
        parseIsoEpochMillis(installedWidget.installedAt),
        parseIsoEpochMillis(installedWidget.releasePublishedAt),
    )
}

private fun compareStoreItemNames(
    left: RearStoreListItem,
    right: RearStoreListItem,
): Int {
    val displayNameComparison =
        left.displayName.lowercase().compareTo(right.displayName.lowercase())
    if (displayNameComparison != 0) return displayNameComparison
    return left.id.lowercase().compareTo(right.id.lowercase())
}

private fun buildVisibleStoreItems(
    allItems: List<RearStoreListItem>,
    query: String,
    sortMode: RearStoreSortMode,
    prioritizeUpdates: Boolean,
    installedWidgets: Map<String, RearStoreInstalledWidget>,
): List<RearStoreListItem> {
    return allItems
        .asSequence()
        .filter { it.matchesLocalQuery(query) }
        .sortedWith { left, right ->
            if (prioritizeUpdates) {
                val leftHasUpdate = left.hasAvailableUpdate(installedWidgets)
                val rightHasUpdate = right.hasAvailableUpdate(installedWidgets)
                if (leftHasUpdate != rightHasUpdate) {
                    return@sortedWith rightHasUpdate.compareTo(leftHasUpdate)
                }
            }

            when (sortMode) {
                RearStoreSortMode.UPDATED_AT -> {
                    val updatedComparison =
                        right.updatedAtSortValue().compareTo(left.updatedAtSortValue())
                    if (updatedComparison != 0) return@sortedWith updatedComparison
                }

                RearStoreSortMode.INSTALLED_AT -> {
                    val installedComparison = right.installedAtSortValue(installedWidgets)
                        .compareTo(left.installedAtSortValue(installedWidgets))
                    if (installedComparison != 0) return@sortedWith installedComparison

                    val updatedComparison =
                        right.updatedAtSortValue().compareTo(left.updatedAtSortValue())
                    if (updatedComparison != 0) return@sortedWith updatedComparison
                }

                RearStoreSortMode.NAME -> {
                    val nameComparison = compareStoreItemNames(left, right)
                    if (nameComparison != 0) return@sortedWith nameComparison
                }

                RearStoreSortMode.STARS -> {
                    val starsComparison = right.stargazersCount.compareTo(left.stargazersCount)
                    if (starsComparison != 0) return@sortedWith starsComparison

                    val updatedComparison =
                        right.updatedAtSortValue().compareTo(left.updatedAtSortValue())
                    if (updatedComparison != 0) return@sortedWith updatedComparison
                }
            }

            compareStoreItemNames(left, right)
        }
        .toList()
}

@Composable
fun RearStoreScreen(bottomInnerPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val prefsManager = remember { context.getPrefsManager() }
    val selectedWidgetIdState = remember { mutableStateOf<String?>(null) }
    val selectedWidgetId = selectedWidgetIdState.value
    var installedWidgets by remember {
        mutableStateOf<Map<String, RearStoreInstalledWidget>>(
            emptyMap()
        )
    }

    suspend fun reloadInstalledWidgets() {
        installedWidgets = withContext(Dispatchers.IO) {
            RearStoreRepository.loadInstalledWidgetSummaries(prefsManager)
        }
    }

    LaunchedEffect(Unit) {
        reloadInstalledWidgets()
    }

    BackHandler(enabled = selectedWidgetId != null) {
        selectedWidgetIdState.value = null
    }

    AnimatedContent(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = true },
        targetState = selectedWidgetId,
        contentKey = { it ?: "rear-store-root" },
        transitionSpec = {
            val forward = targetState != null
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
            ) { fullWidth -> if (forward) fullWidth / 9 else -fullWidth / 9 } togetherWith (
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
                    ) { fullWidth -> if (forward) -fullWidth / 12 else fullWidth / 12 }
                    )
        },
        label = "RearStoreRouteTransition",
    ) { currentWidgetId ->
        if (currentWidgetId == null) {
            RearStoreRootContent(
                bottomInnerPadding = bottomInnerPadding,
                prefsManager = prefsManager,
                installedWidgets = installedWidgets,
                onOpenDetail = { selectedWidgetIdState.value = it },
            )
        } else {
            RearStoreDetailContent(
                widgetId = currentWidgetId,
                bottomInnerPadding = bottomInnerPadding,
                installedWidget = installedWidgets[currentWidgetId],
                onBack = { selectedWidgetIdState.value = null },
                onInstalled = { reloadInstalledWidgets() },
            )
        }
    }
}

@Composable
private fun RearStoreRootContent(
    bottomInnerPadding: Dp,
    prefsManager: hk.uwu.reareye.ui.config.PrefsManager,
    installedWidgets: Map<String, RearStoreInstalledWidget>,
    onOpenDetail: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()

    var searchInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusedState = remember { mutableStateOf(false) }
    val searchFocused = searchFocusedState.value
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var allItems by remember { mutableStateOf(emptyList<RearStoreListItem>()) }
    var sortMode by remember { mutableStateOf(RearStoreSortMode.UPDATED_AT) }
    var prioritizeUpdates by remember { mutableStateOf(true) }
    val showSortMenu = remember { mutableStateOf(false) }

    suspend fun loadWidgets() {
        loading = true
        loadFailed = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                RearStoreRepository.loadWidgets(prefsManager)
            }.getOrNull()
        }
        if (result == null) {
            loadFailed = true
            allItems = emptyList()
        } else {
            allItems = result
        }
        loading = false
    }

    BackHandler(enabled = searchFocused) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        loadWidgets()
    }

    LaunchedEffect(searchInput) {
        delay(90)
        val nextQuery = searchInput.trim()
        if (searchQuery != nextQuery) {
            searchQuery = nextQuery
        }
    }

    val items = remember(allItems, searchQuery, sortMode, prioritizeUpdates, installedWidgets) {
        buildVisibleStoreItems(
            allItems = allItems,
            query = searchQuery,
            sortMode = sortMode,
            prioritizeUpdates = prioritizeUpdates,
            installedWidgets = installedWidgets,
        )
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
                    title = stringResource(R.string.store_navigation),
                    navigationIconPadding = 12.dp,
                    actionIconPadding = 12.dp,
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showSortMenu.value = true },
                                holdDownState = showSortMenu.value,
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
                                popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                alignment = PopupPositionProvider.Align.End,
                                enableWindowDim = true,
                                onDismissRequest = { showSortMenu.value = false },
                                maxHeight = null,
                                minWidth = 220.dp,
                                renderInRootScaffold = true,
                                content = {
                                    ListPopupColumn {
                                        SpinnerItemImpl(
                                            entry = SpinnerEntry(title = stringResource(R.string.rear_store_prioritize_updates)),
                                            entryCount = 5,
                                            isSelected = prioritizeUpdates,
                                            index = 0,
                                            spinnerColors = SpinnerDefaults.spinnerColors(),
                                            onSelectedIndexChange = {
                                                prioritizeUpdates = !prioritizeUpdates
                                            },
                                        )
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                                        )
                                        SpinnerItemImpl(
                                            entry = SpinnerEntry(title = stringResource(R.string.rear_store_sort_by_updated_at)),
                                            entryCount = 5,
                                            isSelected = sortMode == RearStoreSortMode.UPDATED_AT,
                                            index = 1,
                                            spinnerColors = SpinnerDefaults.spinnerColors(),
                                            onSelectedIndexChange = {
                                                showSortMenu.value = false
                                                sortMode = RearStoreSortMode.UPDATED_AT
                                            },
                                        )
                                        SpinnerItemImpl(
                                            entry = SpinnerEntry(title = stringResource(R.string.rear_store_sort_by_installed_at)),
                                            entryCount = 5,
                                            isSelected = sortMode == RearStoreSortMode.INSTALLED_AT,
                                            index = 2,
                                            spinnerColors = SpinnerDefaults.spinnerColors(),
                                            onSelectedIndexChange = {
                                                showSortMenu.value = false
                                                sortMode = RearStoreSortMode.INSTALLED_AT
                                            },
                                        )
                                        SpinnerItemImpl(
                                            entry = SpinnerEntry(title = stringResource(R.string.rear_store_sort_by_name)),
                                            entryCount = 5,
                                            isSelected = sortMode == RearStoreSortMode.NAME,
                                            index = 3,
                                            spinnerColors = SpinnerDefaults.spinnerColors(),
                                            onSelectedIndexChange = {
                                                showSortMenu.value = false
                                                sortMode = RearStoreSortMode.NAME
                                            },
                                        )
                                        SpinnerItemImpl(
                                            entry = SpinnerEntry(title = stringResource(R.string.rear_store_sort_by_stars)),
                                            entryCount = 5,
                                            isSelected = sortMode == RearStoreSortMode.STARS,
                                            index = 4,
                                            spinnerColors = SpinnerDefaults.spinnerColors(),
                                            onSelectedIndexChange = {
                                                showSortMenu.value = false
                                                sortMode = RearStoreSortMode.STARS
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                RearSearchBar(
                    query = searchInput,
                    hint = stringResource(R.string.rear_store_search_hint),
                    prefsManager = prefsManager,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    onQueryChange = { searchInput = it },
                    onSearchSubmit = { searchQuery = searchInput.trim() },
                    onSearchFocusChange = { focused -> searchFocusedState.value = focused },
                )
            }
        },
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
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + bottomInnerPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            if (loading) {
                item {
                    RearStoreLoadingCard()
                }
            } else if (loadFailed) {
                item {
                    RearStoreMessageCard(text = stringResource(R.string.rear_store_load_failed))
                }
            } else if (items.isEmpty()) {
                item {
                    RearStoreMessageCard(text = stringResource(R.string.rear_store_search_empty))
                }
            } else {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    ArtStaggeredReveal(
                        visible = true,
                        revealKey = item.id + searchQuery,
                        delayMillis = (24 + index * 18).coerceAtMost(160),
                    ) {
                        RearStoreListCard(
                            item = item,
                            installedWidget = installedWidgets[item.id],
                            updateAvailable = item.hasAvailableUpdate(installedWidgets),
                            onClick = { onOpenDetail(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun RearStoreDetailContent(
    widgetId: String,
    bottomInnerPadding: Dp,
    installedWidget: RearStoreInstalledWidget?,
    onBack: () -> Unit,
    onInstalled: suspend () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { context.getPrefsManager() }
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()

    var detail by remember(widgetId) { mutableStateOf<RearStoreWidgetDetail?>(null) }
    var loading by remember(widgetId) { mutableStateOf(true) }
    var loadFailed by remember(widgetId) { mutableStateOf(false) }
    var selectedTab by remember(widgetId) { mutableStateOf(RearStoreDetailTab.README) }
    var showAllReleases by remember(widgetId) { mutableStateOf(false) }
    var activeInstall by remember(widgetId) { mutableStateOf<RearStoreActiveInstall?>(null) }
    var readmeLoading by remember(widgetId) { mutableStateOf(false) }
    var readmeLoaded by remember(widgetId) { mutableStateOf(false) }

    suspend fun reloadDetail() {
        loading = true
        loadFailed = false
        detail = withContext(Dispatchers.IO) {
            runCatching { RearStoreRepository.loadWidgetDetail(prefsManager, widgetId) }.getOrNull()
        }
        readmeLoading = false
        readmeLoaded = detail?.readme != null
        loadFailed = detail == null
        loading = false
    }

    LaunchedEffect(widgetId) {
        reloadDetail()
    }

    LaunchedEffect(selectedTab, widgetId, detail?.repository?.fullName) {
        if (selectedTab != RearStoreDetailTab.README || readmeLoaded || readmeLoading) return@LaunchedEffect
        if (detail == null) return@LaunchedEffect
        if (detail?.readme != null) {
            readmeLoaded = true
            return@LaunchedEffect
        }
        readmeLoading = true
        val loadedReadme = withContext(Dispatchers.IO) {
            runCatching { RearStoreRepository.loadWidgetReadme(prefsManager, widgetId) }.getOrNull()
        }
        detail = detail?.copy(readme = loadedReadme)
        readmeLoaded = true
        readmeLoading = false
    }

    Scaffold(
        topBar = {
            val repositoryUrl = detail?.repository?.url?.normalizedOrNull()
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = detail?.let { stringResource(R.string.rear_store_detail_header) }
                    ?: stringResource(R.string.store_navigation),
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
                    if (repositoryUrl != null) {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        repositoryUrl.toUri()
                                    )
                                )
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Link,
                                contentDescription = stringResource(R.string.rear_store_open_repository),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val widgetDetail = detail
        if (loadFailed || widgetDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                RearStoreMessageCard(text = stringResource(R.string.rear_store_detail_failed))
            }
            return@Scaffold
        }

        val authorPlaceholderAlpha = rememberSkeletonPulseAlpha("rear-store-author-avatar")
        val visibleReleases = if (showAllReleases) {
            widgetDetail.releases
        } else {
            widgetDetail.releases.take(5)
        }
        val markdownWebViewCache = remember(widgetId) { mutableMapOf<String, WebView>() }
        val latestReleaseTag = widgetDetail.releases.firstOrNull { it.assets.isNotEmpty() }
            ?.tagName
            ?.normalizedOrNull()
        val installedReleaseTag = installedWidget?.releaseTag?.normalizedOrNull()
        val updateAvailable = installedReleaseTag != null &&
                latestReleaseTag != null &&
                installedReleaseTag != latestReleaseTag
        val tabs = listOf(
            stringResource(R.string.rear_store_tab_readme),
            stringResource(R.string.rear_store_tab_downloads),
            stringResource(R.string.rear_store_tab_info),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .rearAcrylicSource(hazeState)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + bottomInnerPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            item {
                ArtRevealItem(visible = true, delayMillis = 18) {
                    RearStoreDetailHeroCard(
                        detail = widgetDetail,
                        installedWidget = installedWidget,
                        latestReleaseTag = latestReleaseTag,
                        updateAvailable = updateAvailable,
                        metadataType = widgetDetail.displayMetadataType(),
                    )
                }
            }

            item {
                ArtRevealItem(visible = true, delayMillis = 30) {
                    TabRowWithContour(
                        tabs = tabs,
                        selectedTabIndex = selectedTab.ordinal,
                        onTabSelected = { selectedTab = RearStoreDetailTab.entries[it] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                RearStoreDetailTabContent(
                    selectedTab = selectedTab,
                    widgetDetail = widgetDetail,
                    visibleReleases = visibleReleases,
                    showAllReleases = showAllReleases,
                    readmeLoading = readmeLoading,
                    readmeLoaded = readmeLoaded,
                    webViewCache = markdownWebViewCache,
                    activeInstall = activeInstall,
                    authorPlaceholderAlpha = authorPlaceholderAlpha,
                    onShowAllReleases = { showAllReleases = true },
                    onInstallAsset = { release, asset ->
                        scope.launch {
                            val blockedMessage = resolveInstallBlockMessage(context, widgetDetail)
                            if (blockedMessage != null) {
                                Toast.makeText(context, blockedMessage, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val assetKey =
                                widgetDetail.widgetId + ":" + release.tagName + ":" + asset.name
                            activeInstall = RearStoreActiveInstall(assetKey = assetKey)
                            val result = runCatching {
                                RearStoreRepository.quickInstallWidget(
                                    context = context,
                                    prefsManager = prefsManager,
                                    widgetId = widgetDetail.widgetId,
                                    releaseTag = release.tagName,
                                    assetName = asset.name,
                                    onProgress = { progress ->
                                        if (activeInstall?.assetKey == assetKey) {
                                            activeInstall = activeInstall?.copy(
                                                phase = progress.stage,
                                                downloadedBytes = progress.downloadedBytes,
                                                totalBytes = progress.totalBytes,
                                            )
                                        }
                                    },
                                )
                            }
                            val installResult = result.getOrNull()
                            if (installResult != null) {
                                activeInstall =
                                    activeInstall?.takeIf { it.assetKey == assetKey }?.let {
                                        if (it.totalBytes > 0L) {
                                            it.copy(
                                                phase = RearStoreInstallProgressStage.DOWNLOADING,
                                                downloadedBytes = it.totalBytes,
                                            )
                                        } else {
                                            it
                                        }
                                    }
                                delay(360)
                                activeInstall = null
                                onInstalled()
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (installResult.cardInstalled) {
                                            R.string.rear_store_install_success_with_card
                                        } else if (installResult.fallbackUsed) {
                                            R.string.rear_store_install_success_fallback
                                        } else {
                                            R.string.rear_store_install_success
                                        },
                                        installResult.widgetName,
                                        installResult.releaseTag ?: asset.name,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                activeInstall = null
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.rear_store_install_failed,
                                        result.exceptionOrNull()?.message ?: "unknown",
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RearStoreDetailTabContent(
    selectedTab: RearStoreDetailTab,
    widgetDetail: RearStoreWidgetDetail,
    visibleReleases: List<RearStoreRelease>,
    showAllReleases: Boolean,
    readmeLoading: Boolean,
    readmeLoaded: Boolean,
    webViewCache: MutableMap<String, WebView>,
    activeInstall: RearStoreActiveInstall?,
    authorPlaceholderAlpha: Float,
    onShowAllReleases: () -> Unit,
    onInstallAsset: (RearStoreRelease, RearStoreReleaseAsset) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (selectedTab) {
            RearStoreDetailTab.README -> {
                when {
                    readmeLoading && widgetDetail.readme == null -> RearStoreLoadingCard()
                    readmeLoaded && widgetDetail.readme?.content.isNullOrBlank() -> {
                        RearStoreMessageCard(text = stringResource(R.string.rear_store_readme_empty))
                    }

                    else -> RearStoreReadmeCard(
                        markdown = widgetDetail.readme?.content.orEmpty(),
                        repoBaseUrl = widgetDetail.repository?.url,
                        webViewCache = webViewCache,
                    )
                }
            }

            RearStoreDetailTab.DOWNLOADS -> {
                if (visibleReleases.isEmpty()) {
                    RearStoreMessageCard(text = stringResource(R.string.rear_store_release_empty))
                } else {
                    visibleReleases.forEach { release ->
                        RearStoreReleaseCard(
                            release = release,
                            widgetId = widgetDetail.widgetId,
                            repoBaseUrl = widgetDetail.repository?.url,
                            webViewCache = webViewCache,
                            activeInstall = activeInstall,
                            onInstallAsset = { asset -> onInstallAsset(release, asset) },
                        )
                    }

                    if (!showAllReleases && widgetDetail.releases.size > 5) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onShowAllReleases,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(text = stringResource(R.string.rear_store_show_older_releases))
                            }
                        }
                    }
                }
            }

            RearStoreDetailTab.INFO -> {
                RearStoreInstallInfoCard(detail = widgetDetail)
                RearStoreRepositoryCard(detail = widgetDetail)
                RearStoreAuthorCard(
                    author = widgetDetail.author,
                    placeholderAlpha = authorPlaceholderAlpha,
                )
            }
        }
    }
}

@Composable
private fun RearStoreListCard(
    item: RearStoreListItem,
    installedWidget: RearStoreInstalledWidget?,
    updateAvailable: Boolean,
    onClick: () -> Unit,
) {
    val descriptionText = item.description.normalizedOrNull()
    val metadataType = item.displayMetadataType()
    val metadataLabelRes = metadataType.labelResId()
    val metadataPalette = rememberMetadataBadgePalette(metadataType)
    val statusBadgeText = when {
        updateAvailable -> stringResource(R.string.rear_store_update_available_badge)
        installedWidget != null && item.latestReleaseTag.normalizedOrNull() != null -> {
            stringResource(R.string.rear_store_up_to_date)
        }

        else -> null
    }
    val badges = buildList {
        statusBadgeText?.let {
            add(
                RearBadgeItem(
                    text = it,
                    emphasized = updateAvailable,
                )
            )
        }
        metadataLabelRes?.let {
            add(
                RearBadgeItem(
                    text = stringResource(it),
                    emphasized = false,
                    palette = metadataPalette,
                )
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        SuperCard(
            title = item.displayName,
            summary = item.author.displayName.ifBlank {
                stringResource(R.string.rear_store_unknown_author)
            },
            endActions = {
                RearBadgeGroup(badges = badges)
            },
            onClick = onClick,
            bottomAction = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.4f),
                    )
                    descriptionText?.let {
                        Text(
                            text = it,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = item.stargazersCount.toString(),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = isoDateLabel(item.updatedAt),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun RearStoreDetailHeroCard(
    detail: RearStoreWidgetDetail,
    installedWidget: RearStoreInstalledWidget?,
    latestReleaseTag: String?,
    updateAvailable: Boolean,
    metadataType: RearStoreWidgetMetadataType,
) {
    val metadataLabelRes = metadataType.labelResId()
    val metadataPalette = rememberMetadataBadgePalette(metadataType)
    val installedVersionBadgeText = buildString {
        append(stringResource(R.string.rear_store_installed_version_label))
        append("  ")
        append(
            installedWidget?.releaseTag?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.rear_store_status_not_installed)
        )
    }
    val updateStateBadgeText = when {
        updateAvailable -> stringResource(R.string.rear_store_update_available_badge)
        latestReleaseTag != null && installedWidget != null -> stringResource(R.string.rear_store_up_to_date)
        else -> null
    }
    val heroBadges = buildList {
        add(
            RearBadgeItem(
                text = installedVersionBadgeText,
                emphasized = false,
            )
        )
        updateStateBadgeText?.let {
            add(
                RearBadgeItem(
                    text = it,
                    emphasized = updateAvailable,
                )
            )
        }
        metadataLabelRes?.let {
            add(
                RearBadgeItem(
                    text = stringResource(it),
                    emphasized = false,
                    palette = metadataPalette,
                )
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        SuperCard(
            title = detail.name,
            summary = detail.author.displayName.ifBlank {
                stringResource(R.string.rear_store_unknown_author)
            },
            onClick = {},
            bottomAction = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    RearBadgeGroup(badges = heroBadges)
                }
            },
        )
    }
}

@Composable
private fun rememberMetadataBadgePalette(metadataType: RearStoreWidgetMetadataType): RearBadgePalette {
    val accent = when (metadataType) {
        RearStoreWidgetMetadataType.CARD -> Color(0xFF3B82F6)
        RearStoreWidgetMetadataType.NOTIFICATION -> Color(0xFF34A853)
        RearStoreWidgetMetadataType.ENHANCED -> Color(0xFFF2B827)
        RearStoreWidgetMetadataType.WALLPAPER -> Color(0xFFEF4444)
        RearStoreWidgetMetadataType.UNKNOWN -> Color(0xFF64748B)
    }
    return rememberRearAccentBadgePalette(accent)
}

@Composable
private fun RearStoreDownloadBadge(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = MiuixIcons.Download,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun RearStoreReleaseCard(
    release: RearStoreRelease,
    widgetId: String,
    repoBaseUrl: String?,
    webViewCache: MutableMap<String, WebView>,
    activeInstall: RearStoreActiveInstall?,
    onInstallAsset: (RearStoreReleaseAsset) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SuperCard(
            title = release.name.ifBlank {
                release.tagName.ifBlank { stringResource(R.string.rear_store_unknown_release) }
            },
            summary = release.tagName.ifBlank { stringResource(R.string.rear_store_unknown_release) },
            endActions = {
                Text(
                    text = isoDateLabel(release.publishedAt.ifBlank { release.createdAt }),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            },
            onClick = null,
            bottomAction = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MarkdownCardBody(
                        markdown = release.body.ifBlank {
                            stringResource(R.string.rear_store_release_body_empty)
                        },
                        repoBaseUrl = repoBaseUrl,
                        webViewCache = webViewCache,
                        maxWindowHeight = rearStoreReleaseWebViewMaxHeight,
                    )
                    if (release.assets.isNotEmpty()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        release.assets.forEachIndexed { index, asset ->
                            RearStoreAssetRow(
                                widgetId = widgetId,
                                release = release,
                                asset = asset,
                                activeInstall = activeInstall,
                                onInstall = { onInstallAsset(asset) },
                            )
                            if (index != release.assets.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun RearStoreAssetRow(
    widgetId: String,
    release: RearStoreRelease,
    asset: RearStoreReleaseAsset,
    activeInstall: RearStoreActiveInstall?,
    onInstall: () -> Unit,
) {
    val assetKey = widgetId + ":" + release.tagName + ":" + asset.name
    val installState = activeInstall?.takeIf { it.assetKey == assetKey }

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
                text = asset.name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.rear_store_asset_meta_summary,
                    formatSize(asset.size),
                    asset.downloadCount,
                ),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        AnimatedContent(
            targetState = installState,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 180,
                        delayMillis = 50,
                        easing = LinearOutSlowInEasing,
                    )
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = 130,
                        easing = FastOutLinearInEasing,
                    )
                )
            },
            label = "RearStoreAssetInstallState",
        ) { currentInstallState ->
            if (currentInstallState != null) {
                RearStoreInstallProgressView(installState = currentInstallState)
            } else {
                RearStoreDownloadBadge(
                    text = stringResource(R.string.rear_store_asset_install),
                    onClick = onInstall,
                )
            }
        }
    }
}

@Composable
private fun RearStoreInstallProgressView(installState: RearStoreActiveInstall) {
    val totalBytes = installState.totalBytes
    val downloadedBytes = installState.downloadedBytes.coerceAtLeast(0L)
    val determinateProgress = if (totalBytes > 0L) {
        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (determinateProgress != null && installState.phase == RearStoreInstallProgressStage.DOWNLOADING) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { determinateProgress },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MiuixTheme.colorScheme.primary,
                trackColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            )
        } else {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MiuixTheme.colorScheme.primary,
                trackColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun RearStoreInstallInfoCard(detail: RearStoreWidgetDetail) {
    val installInfo = defaultInstallInfo(detail)
    val typeLabel = stringResource(installInfo.widgetInfoType.labelResId())
    val summaryText = buildString {
        append(stringResource(R.string.rear_store_widget_id_summary, installInfo.businessId))
        append('\n')
        append(stringResource(R.string.rear_store_install_mode_summary, typeLabel))
        if (installInfo.widgetInfoType == RearStoreWidgetInfoType.WIDGET && !installInfo.hasBusinessSetup) {
            append('\n')
            append(stringResource(R.string.rear_store_install_missing_business_setup))
        }
        append('\n')
        append(
            stringResource(
                R.string.rear_store_will_install_card_summary,
                stringResource(
                    if (installInfo.hasCard) R.string.rear_store_yes else R.string.rear_store_no
                ),
            )
        )
        installInfo.cardId?.let {
            append('\n')
            append(stringResource(R.string.rear_store_card_id_summary, it))
        }
        if (installInfo.hasCard) {
            detail.widgetInfo?.cardSetup
                ?.packageName
                ?.normalizedOrNull()
                ?.let {
                    append('\n')
                    append(stringResource(R.string.rear_store_card_package_summary, it))
                }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.rear_store_install_info_title),
            summary = summaryText,
            summaryColor = BasicComponentDefaults.summaryColor(),
            onClick = null,
        )
    }
}

@Composable
private fun RearStoreRepositoryCard(detail: RearStoreWidgetDetail) {
    val context = LocalContext.current
    val repositoryUrl = detail.repository?.url?.normalizedOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.rear_store_repository_card_title),
            summary = detail.repository?.fullName?.normalizedOrNull()
                ?: stringResource(R.string.rear_store_repository_missing),
            summaryColor = BasicComponentDefaults.summaryColor(),
            onClick = repositoryUrl?.let {
                { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
            },
        )
    }
}

@Composable
private fun RearStoreAuthorCard(
    author: RearStoreAuthor,
    placeholderAlpha: Float,
) {
    val context = LocalContext.current
    val authorUrl = author.url.normalizedOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            startAction = {
                RearStoreAuthorAvatar(
                    avatarUrl = author.avatarUrl,
                    placeholderAlpha = placeholderAlpha,
                )
            },
            onClick = authorUrl?.let {
                { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
            },
            content = {
                Text(
                    text = stringResource(R.string.rear_store_author_card_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = author.displayName.ifBlank {
                        stringResource(R.string.rear_store_unknown_author)
                    },
                    fontWeight = FontWeight.Medium,
                )
                author.login.normalizedOrNull()?.let {
                    Text(
                        text = it,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            },
        )
    }
}

@Composable
private fun RearStoreAuthorAvatar(
    avatarUrl: String?,
    placeholderAlpha: Float,
    modifier: Modifier = Modifier,
) {
    var imageBitmap by remember(avatarUrl) {
        mutableStateOf(RearStoreAvatarCache.peek(avatarUrl))
    }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) return@LaunchedEffect
        imageBitmap = RearStoreAvatarCache.load(avatarUrl)
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = placeholderAlpha)),
        )
    }
}

@Composable
private fun RearStoreReadmeCard(
    markdown: String,
    repoBaseUrl: String? = null,
    webViewCache: MutableMap<String, WebView>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            onClick = null,
            content = {
                MarkdownCardBody(
                    markdown = markdown,
                    repoBaseUrl = repoBaseUrl,
                    webViewCache = webViewCache,
                    maxWindowHeight = rearStoreReadmeWebViewMaxHeight,
                )
            },
        )
    }
}

@Composable
private fun MarkdownCardBody(
    markdown: String,
    repoBaseUrl: String? = null,
    webViewCache: MutableMap<String, WebView>,
    maxWindowHeight: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefsManager = remember(context) { context.getPrefsManager() }
    val webViewHardwareAccelerationEnabled = prefsManager.getBoolean(
        ConfigKeys.MODULE_STORE_WEBVIEW_HARDWARE_ACCELERATION,
        true,
    )
    val colorScheme = MiuixTheme.colorScheme
    val backgroundColor = colorScheme.background
    val surfaceColor = colorScheme.surface
    val onSurfaceColor = colorScheme.onSurface
    val onSurfaceVariantColor = colorScheme.onSurfaceVariantSummary
    val primaryColor = colorScheme.primary
    val outlineColor = colorScheme.outline
    val secondaryContainerColor = colorScheme.secondaryContainer
    val webViewBackgroundColor = if (webViewHardwareAccelerationEnabled) {
        Color.Transparent
    } else {
        surfaceColor
    }
    val darkMode = backgroundColor.luminance() < 0.5f
    val themeCssVariables = remember(
        backgroundColor,
        surfaceColor,
        onSurfaceColor,
        onSurfaceVariantColor,
        primaryColor,
        outlineColor,
        secondaryContainerColor,
        webViewBackgroundColor,
    ) {
        buildString {
            append("--background:${webViewBackgroundColor.toCssColor()};")
            append("--textPrimary:${onSurfaceColor.toCssColor()};")
            append("--textSecondary:${onSurfaceVariantColor.toCssColor()};")
            append("--link:${primaryColor.toCssColor()};")
            append("--border:${outlineColor.copy(alpha = 0.42f).toCssColor()};")
            append("--surface:${secondaryContainerColor.copy(alpha = 0.92f).toCssColor()};")
            append("--quote:${onSurfaceVariantColor.toCssColor()};")
        }
    }
    val normalizedBaseUrl = remember(repoBaseUrl) {
        repoBaseUrl.normalizedOrNull()?.let {
            if (it.endsWith('/')) it else "$it/"
        }
    }
    val htmlDocument = remember(markdown, darkMode, themeCssVariables) {
        RearStoreHtmlDocumentCache.wrapDocument(
            context = context.applicationContext,
            htmlBody = markdown,
            darkMode = darkMode,
            themeCssVariables = themeCssVariables,
        )
    } ?: return
    val webViewKey = remember(
        htmlDocument,
        normalizedBaseUrl,
        webViewHardwareAccelerationEnabled,
    ) {
        listOf(
            normalizedBaseUrl.orEmpty(),
            htmlDocument.hashCode().toString(),
            webViewHardwareAccelerationEnabled.toString(),
        )
            .joinToString(separator = "\u0000")
    }
    val maxWindowHeightPx = remember(maxWindowHeight, density) {
        (maxWindowHeight.value * density.density).roundToInt().coerceAtLeast(1)
    }
    val webViewHeightPx = remember(webViewKey) { mutableIntStateOf(1) }
    val webViewHeightDp = (webViewHeightPx.intValue / density.density).dp
    val nestedScrollInterop = rememberNestedScrollInteropConnection()

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(webViewHeightDp)
            .nestedScroll(nestedScrollInterop),
        factory = { viewContext ->
            webViewCache.getOrPut(webViewKey) {
                createGithubMarkdownWebView(
                    context = viewContext,
                    hardwareAccelerationEnabled = webViewHardwareAccelerationEnabled,
                    onContentHeightChanged = { height ->
                        webViewHeightPx.intValue = height.coerceIn(1, maxWindowHeightPx)
                    },
                    backgroundColor = webViewBackgroundColor.toArgb(),
                ).apply {
                    tag = htmlDocument
                    loadDataWithBaseURL(
                        normalizedBaseUrl ?: "about:blank",
                        htmlDocument,
                        "text/html",
                        StandardCharsets.UTF_8.name(),
                        null,
                    )
                }
            }
        },
        update = { webView ->
            webView.setLayerType(
                if (webViewHardwareAccelerationEnabled) {
                    View.LAYER_TYPE_NONE
                } else {
                    View.LAYER_TYPE_SOFTWARE
                },
                null,
            )
            val currentLayoutParams = webView.layoutParams
            if (currentLayoutParams == null ||
                currentLayoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                currentLayoutParams.height != webViewHeightPx.intValue
            ) {
                webView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    webViewHeightPx.intValue,
                )
            }
            if (webView.tag != htmlDocument) {
                webView.tag = htmlDocument
                webView.loadDataWithBaseURL(
                    normalizedBaseUrl ?: "about:blank",
                    htmlDocument,
                    "text/html",
                    StandardCharsets.UTF_8.name(),
                    null,
                )
            } else {
                // Re-entering the page reuses cached WebView. Re-publish height to avoid 1dp blank body.
                webView.publishMarkdownContentHeight { height ->
                    webViewHeightPx.intValue = height.coerceIn(1, maxWindowHeightPx)
                }
            }
        },
    )
}

private fun createGithubMarkdownWebView(
    context: Context,
    hardwareAccelerationEnabled: Boolean,
    onContentHeightChanged: (Int) -> Unit,
    backgroundColor: Int,
): WebView {
    return ScrollWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setLayerType(
            if (hardwareAccelerationEnabled) {
                View.LAYER_TYPE_NONE
            } else {
                View.LAYER_TYPE_SOFTWARE
            },
            null,
        )
        setBackgroundColor(backgroundColor)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.apply {
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            allowFileAccess = false
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            textZoom = 80
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.publishMarkdownContentHeight(onContentHeightChanged)
                view.postDelayed(
                    { view.publishMarkdownContentHeight(onContentHeightChanged) },
                    120,
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, request.url).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                if (!request.url.scheme.orEmpty().startsWith("http")) return null
                return runCatching {
                    val headers = Headers.Builder().apply {
                        request.requestHeaders.forEach { (key, value) -> add(key, value) }
                    }.build()
                    val networkRequest = Request.Builder()
                        .url(request.url.toString())
                        .headers(headers)
                        .method(request.method, null)
                        .build()
                    rearStoreAvatarHttpClient.newCall(networkRequest).execute().use { response ->
                        val header = response.header("content-type") ?: "text/plain; charset=utf-8"
                        val contentTypes = header.split(";\\s*")
                        val mimeType =
                            contentTypes.getOrNull(0)?.trim().orEmpty().ifEmpty { "text/plain" }
                        val charset = contentTypes.getOrNull(1)
                            ?.substringAfter('=')
                            ?.trim()
                            .orEmpty()
                            .ifEmpty { "utf-8" }
                        val body = response.body
                        WebResourceResponse(mimeType, charset, ByteArrayInputStream(body.bytes()))
                    }
                }.getOrElse { error ->
                    WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(
                            error.stackTraceToString().toByteArray(StandardCharsets.UTF_8)
                        ),
                    )
                }
            }
        }
    }
}

private fun WebView.publishMarkdownContentHeight(onContentHeightChanged: (Int) -> Unit) {
    post {
        @Suppress("DEPRECATION")
        val contentHeightPx = (contentHeight * scale).roundToInt()
        val measuredOrContentHeight = maxOf(measuredHeight, contentHeightPx)
        val resolvedHeight =
            (measuredOrContentHeight + paddingTop + paddingBottom).coerceAtLeast(1)
        onContentHeightChanged(resolvedHeight)
    }
}

@Composable
private fun RearStoreLoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.rear_widget_loading_data),
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun RearStoreMessageCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
        )
    }
}
