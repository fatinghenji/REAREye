package hk.uwu.reareye.ui.screen

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import dev.chrisbanes.haze.HazeState
import hk.uwu.reareye.R
import hk.uwu.reareye.generated.AppProperties
import hk.uwu.reareye.repository.contributor.ContributorLoadState
import hk.uwu.reareye.repository.contributor.ContributorProfile
import hk.uwu.reareye.repository.contributor.ContributorRepository
import hk.uwu.reareye.ui.components.card.SuperCard
import hk.uwu.reareye.ui.components.motion.ArtRevealItem
import hk.uwu.reareye.ui.components.motion.ArtStaggeredReveal
import hk.uwu.reareye.ui.theme.rearAcrylicEffect
import hk.uwu.reareye.ui.theme.rearAcrylicSource
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeState
import hk.uwu.reareye.ui.theme.rememberAcrylicHazeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.concurrent.ConcurrentHashMap

private val contributorAvatarHttpClient = OkHttpClient()

private object AppLogoCache {
    @Volatile
    private var cachedImage: ImageBitmap? = null

    fun peek(): ImageBitmap? = cachedImage

    fun store(image: ImageBitmap) {
        cachedImage = image
    }
}

private object ContributorAvatarCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun peek(url: String?): ImageBitmap? {
        val key = url?.takeIf { it.isNotBlank() } ?: return null
        return cache[key]
    }

    suspend fun preload(urls: List<String>) {
        urls.distinct().forEach { url ->
            load(url)
        }
    }

    suspend fun load(url: String?): ImageBitmap? {
        val key = url?.takeIf { it.isNotBlank() } ?: return null
        cache[key]?.let { return it }

        val image = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(key)
                    .build()
                contributorAvatarHttpClient.newCall(request).execute().use { response ->
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

private sealed interface AboutRoute {
    data object Root : AboutRoute
    data object Contributors : AboutRoute
}

private data class CreditEntry(
    val titleRes: Int,
    val summaryRes: Int,
    val url: String,
)

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

@Composable
fun AboutScreen(bottomInnerPadding: Dp = 0.dp) {
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberAcrylicHazeState()
    val hazeStyle = rememberAcrylicHazeStyle()
    val versionText = rememberVersionText()
    val contributorState by ContributorRepository.state.collectAsState()

    var route by remember { mutableStateOf<AboutRoute>(AboutRoute.Root) }

    val entries = remember {
        listOf(
            CreditEntry(
                titleRes = R.string.credits_github_title,
                summaryRes = R.string.credits_github_desc,
                url = "https://github.com/killerprojecte/REAREye",
            ),
            CreditEntry(
                titleRes = R.string.credits_docs,
                summaryRes = R.string.credits_docs_desc,
                url = "https://reareye.uwu.hk"
            ),
            CreditEntry(
                titleRes = R.string.credits_afdian_title,
                summaryRes = R.string.credits_afdian_desc,
                url = "https://ifdian.net/a/rgbmc",
            ),
            CreditEntry(
                titleRes = R.string.credits_qq_title,
                summaryRes = R.string.credits_qq_desc,
                url = "https://qm.qq.com/q/cg2MU3kw6W"
            ),
            CreditEntry(
                titleRes = R.string.credits_coolapk_title,
                summaryRes = R.string.credits_coolapk_desc,
                url = "https://www.coolapk.com/u/7190992"
            )
        )
    }

    LaunchedEffect(Unit) {
        ContributorRepository.preload()
    }

    LaunchedEffect(route) {
        if (route is AboutRoute.Contributors) {
            ContributorRepository.ensureLoaded(force = false)
        }
    }

    LaunchedEffect(contributorState) {
        val loadedState = contributorState as? ContributorLoadState.Loaded ?: return@LaunchedEffect
        ContributorAvatarCache.preload(
            loadedState.contributors.mapNotNull { it.avatar?.takeIf(String::isNotBlank) }
        )
    }

    BackHandler(enabled = route is AboutRoute.Contributors) {
        route = AboutRoute.Root
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.rearAcrylicEffect(hazeState, hazeStyle),
                color = Color.Transparent,
                title = stringResource(
                    if (route is AboutRoute.Root) {
                        R.string.about_navigation
                    } else {
                        R.string.credits_contributors_title
                    }
                ),
                navigationIcon = {
                    if (route is AboutRoute.Contributors) {
                        IconButton(onClick = { route = AboutRoute.Root }) {
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                navigationIconPadding = 12.dp,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = true },
            targetState = route,
            contentKey = { it },
            transitionSpec = {
                val forward = targetState is AboutRoute.Contributors

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
            label = "AboutRouteTransition",
        ) { currentRoute ->
            when (currentRoute) {
                AboutRoute.Root -> AboutRootContent(
                    bottomInnerPadding = bottomInnerPadding,
                    paddingValues = paddingValues,
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                    versionText = versionText,
                    entries = entries,
                    onOpenContributors = { route = AboutRoute.Contributors },
                )

                AboutRoute.Contributors -> ContributorListContent(
                    bottomInnerPadding = bottomInnerPadding,
                    paddingValues = paddingValues,
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                    state = contributorState,
                )
            }
        }
    }
}

@Composable
private fun AboutRootContent(
    bottomInnerPadding: Dp,
    paddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    hazeState: HazeState,
    versionText: String,
    entries: List<CreditEntry>,
    onOpenContributors: () -> Unit,
) {
    val context = LocalContext.current

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
        overscrollEffect = null,
    ) {
        item {
            ArtRevealItem(visible = true, delayMillis = 18) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MiuixTheme.textStyles.title3,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = versionText,
                                style = MiuixTheme.textStyles.body2,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        item {
            ArtStaggeredReveal(
                visible = true,
                revealKey = "contributors",
                delayMillis = 36,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    SuperCard(
                        title = stringResource(R.string.credits_contributors_title),
                        summary = stringResource(R.string.credits_contributors_desc),
                        onClick = onOpenContributors,
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Create,
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        itemsIndexed(entries, key = { _, entry -> entry.url }) { index, entry ->
            ArtStaggeredReveal(
                visible = true,
                revealKey = entry.url,
                delayMillis = (54 + index * 18).coerceAtMost(150),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    SuperCard(
                        title = stringResource(entry.titleRes),
                        summary = stringResource(entry.summaryRes),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, entry.url.toUri()))
                        },
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                tint = colorScheme.onSurface,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorListContent(
    bottomInnerPadding: Dp,
    paddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    hazeState: HazeState,
    state: ContributorLoadState,
) {
    val avatarPlaceholderAlpha = rememberSkeletonPulseAlpha("contributor-avatar-skeleton")

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
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (state) {
            ContributorLoadState.Idle,
            ContributorLoadState.Loading,
                -> item {
                ArtRevealItem(visible = true, delayMillis = 40) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(text = stringResource(R.string.credits_contributors_loading))
                        }
                    }
                }
            }

            ContributorLoadState.Failed -> item {
                ArtRevealItem(visible = true, delayMillis = 40) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SuperCard(
                            title = stringResource(R.string.credits_contributors_title),
                            summary = stringResource(R.string.credits_contributors_load_failed),
                            bottomAction = {
                                Button(
                                    onClick = { ContributorRepository.ensureLoaded(force = true) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = stringResource(R.string.credits_contributors_retry))
                                }
                            },
                        )
                    }
                }
            }

            is ContributorLoadState.Loaded -> {
                if (state.contributors.isEmpty()) {
                    item {
                        ArtRevealItem(visible = true, delayMillis = 40) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.credits_contributors_empty),
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        state.contributors,
                        key = { _, item -> item.link?.takeIf { it.isNotBlank() } ?: item.name },
                    ) { index, item ->
                        val revealKey = item.link?.takeIf { it.isNotBlank() } ?: item.name
                        ArtStaggeredReveal(
                            visible = true,
                            revealKey = revealKey,
                            delayMillis = (36 + index * 18).coerceAtMost(150),
                        ) {
                            ContributorCard(
                                item = item,
                                avatarPlaceholderAlpha = avatarPlaceholderAlpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorCard(
    item: ContributorProfile,
    avatarPlaceholderAlpha: Float,
) {
    val context = LocalContext.current
    val link = item.link?.takeIf { it.isNotBlank() }
    val hasLink = link != null

    Card(modifier = Modifier.fillMaxWidth()) {
        SuperCard(
            title = item.name,
            summary = item.description.takeIf { it.isNotBlank() },
            startAction = {
                ContributorAvatar(
                    avatarUrl = item.avatar,
                    placeholderAlpha = avatarPlaceholderAlpha,
                )
            },
            onClick = link?.let { targetLink ->
                {
                    context.startActivity(Intent(Intent.ACTION_VIEW, targetLink.toUri()))
                }
            },
            endActions = {
                if (hasLink) {
                    Icon(
                        imageVector = MiuixIcons.Link,
                        tint = colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            },
        )
    }
}

@Composable
private fun rememberVersionText(): String {
    return "${AppProperties.PROJECT_APP_VERSION_NAME}-${AppProperties.GIT_HASH}-r${AppProperties.BUILD_NUMBER}-${AppProperties.BUILD_CHANNEL}"
}

@Composable
private fun ContributorAvatar(
    avatarUrl: String?,
    placeholderAlpha: Float,
    modifier: Modifier = Modifier,
) {
    var imageBitmap by remember(avatarUrl) {
        mutableStateOf(ContributorAvatarCache.peek(avatarUrl))
    }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) {
            imageBitmap = null
            return@LaunchedEffect
        }

        imageBitmap = ContributorAvatarCache.load(avatarUrl)
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier
                .size(42.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.secondaryContainer.copy(alpha = placeholderAlpha)),
        )
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var imageBitmap by remember { mutableStateOf(AppLogoCache.peek()) }

    LaunchedEffect(Unit) {
        if (imageBitmap != null) {
            return@LaunchedEffect
        }

        val loadedBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
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
                bitmap.asImageBitmap()
            }
        }.getOrNull()

        if (loadedBitmap != null) {
            AppLogoCache.store(loadedBitmap)
            imageBitmap = loadedBitmap
        }
    }

    if (imageBitmap != null) {
        Image(bitmap = imageBitmap!!, contentDescription = null, modifier = modifier)
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariantSummary,
            modifier = modifier,
        )
    }
}
