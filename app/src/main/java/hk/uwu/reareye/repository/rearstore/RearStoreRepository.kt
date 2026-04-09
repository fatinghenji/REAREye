package hk.uwu.reareye.repository.rearstore

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import hk.uwu.reareye.BuildConfig
import hk.uwu.reareye.repository.rearwidget.RearBusinessConfig
import hk.uwu.reareye.repository.rearwidget.RearCardConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.repository.rearwidget.RearWidgetManagerRepository
import hk.uwu.reareye.ui.config.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

private const val REAR_STORE_BASE_URL = "https://rearstore-api.uwu.hk"
private const val REAR_STORE_DOWNLOAD_FOLDER = "REAREye"
private const val DEFAULT_COMPONENT_ROUTE_PACKAGE = "com.xiaomi.subscreencenter"

private fun String?.normalizedOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}

data class RearStoreAuthor(
    @SerializedName("login")
    val login: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("url")
    val url: String = "",
    @SerializedName("avatarUrl")
    val avatarUrl: String = "",
    @SerializedName("type")
    val type: String = "",
) {
    val displayName: String
        get() = name.normalizedOrNull() ?: login.normalizedOrNull().orEmpty()
}

data class RearStoreRepositoryLink(
    @SerializedName("fullName")
    val fullName: String = "",
    @SerializedName("url")
    val url: String = "",
)

data class RearStoreRepositoryInfo(
    @SerializedName("widgetName")
    val widgetName: String = "",
    @SerializedName("owner")
    val owner: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("fullName")
    val fullName: String = "",
    @SerializedName("url")
    val url: String = "",
    @SerializedName("description")
    val description: String = "",
    @SerializedName("homepage")
    val homepage: String = "",
    @SerializedName("updatedAt")
    val updatedAt: String = "",
    @SerializedName("pushedAt")
    val pushedAt: String = "",
    @SerializedName("stargazersCount")
    val stargazersCount: Int = 0,
)

data class RearStoreListItem(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("description")
    val description: String = "",
    @SerializedName("author")
    val author: RearStoreAuthor = RearStoreAuthor(),
    @SerializedName("updatedAt")
    val updatedAt: String = "",
    @SerializedName("repository")
    val repository: RearStoreRepositoryLink? = null,
    val stargazersCount: Int = 0,
) {
    val displayName: String
        get() = name.normalizedOrNull() ?: id.normalizedOrNull().orEmpty()
}

private data class RearStoreSearchResponse(
    @SerializedName("items")
    val items: List<RearStoreListItem> = emptyList(),
)

private data class RearStoreRecentUpdatesResponse(
    @SerializedName("items")
    val items: List<RearStoreListItem> = emptyList(),
)

private data class RearStoreDescriptionResponse(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("repository")
    val repository: RearStoreRepositoryInfo? = null,
)

private data class RearStoreAuthorResponse(
    @SerializedName("author")
    val author: RearStoreAuthor = RearStoreAuthor(),
)

private data class RearStoreWidgetInfoResponse(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("widgetInfo")
    val widgetInfo: RearStoreWidgetInfo? = null,
)

data class RearStoreWidgetInfo(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("business_setup")
    val businessSetup: RearStoreBusinessSetup? = null,
    @SerializedName("card_setup")
    val cardSetup: RearStoreCardSetup? = null,
)

data class RearStoreBusinessSetup(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("renameable")
    val renameable: Boolean = true,
)

data class RearStoreCardSetup(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("package")
    val packageName: String = "",
    @SerializedName("priority")
    val priority: Int = 500,
    @SerializedName("sticky")
    val sticky: Boolean = true,
    @SerializedName("renameable")
    val renameable: Boolean = true,
)

private data class RearStoreReadmeResponse(
    @SerializedName("readme")
    val readme: RearStoreReadme? = null,
)

data class RearStoreReadme(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("htmlUrl")
    val htmlUrl: String = "",
    @SerializedName("content")
    val content: String = "",
)

private data class RearStoreReleasesResponse(
    @SerializedName("releases")
    val releases: List<RearStoreRelease> = emptyList(),
)

data class RearStoreRelease(
    @SerializedName("tagName")
    val tagName: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("body")
    val body: String = "",
    @SerializedName("createdAt")
    val createdAt: String = "",
    @SerializedName("publishedAt")
    val publishedAt: String = "",
    @SerializedName("url")
    val url: String = "",
    @SerializedName("assets")
    val assets: List<RearStoreReleaseAsset> = emptyList(),
)

data class RearStoreReleaseAsset(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("contentType")
    val contentType: String = "",
    @SerializedName("size")
    val size: Long = 0L,
    @SerializedName("downloadCount")
    val downloadCount: Long = 0L,
    @SerializedName("downloadUrl")
    val downloadUrl: String = "",
)

data class RearStoreWidgetDetail(
    val widgetId: String,
    val name: String,
    val description: String,
    val author: RearStoreAuthor,
    val repository: RearStoreRepositoryInfo?,
    val widgetInfo: RearStoreWidgetInfo?,
    val readme: RearStoreReadme?,
    val releases: List<RearStoreRelease>,
)

data class RearStoreInstalledWidget(
    val widgetId: String,
    val widgetName: String,
    val businessId: String,
    val releaseTag: String?,
    val releasePublishedAt: String?,
    val renameable: Boolean,
)

data class RearStoreQuickInstallResult(
    val widgetId: String,
    val widgetName: String,
    val releaseTag: String?,
    val cardInstalled: Boolean,
    val fallbackUsed: Boolean,
    val updatedExistingInstall: Boolean,
)

private data class RearStoreSavedFile(
    val fileName: String,
    val locationLabel: String,
)

private data class RearStoreSelectedAsset(
    val release: RearStoreRelease,
    val asset: RearStoreReleaseAsset,
)

object RearStoreRepository {
    private val httpClient = OkHttpClient.Builder()
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
    private val gson = Gson()
    private val recentUpdatesCache =
        java.util.concurrent.atomic.AtomicReference<List<RearStoreListItem>?>(null)
    private val searchCache =
        java.util.concurrent.ConcurrentHashMap<String, List<RearStoreListItem>>()
    private val descriptionCache =
        java.util.concurrent.ConcurrentHashMap<String, RearStoreDescriptionResponse>()
    private val authorCache = java.util.concurrent.ConcurrentHashMap<String, RearStoreAuthor>()
    private val widgetInfoCache =
        java.util.concurrent.ConcurrentHashMap<String, RearStoreWidgetInfo>()
    private val releasesCache =
        java.util.concurrent.ConcurrentHashMap<String, List<RearStoreRelease>>()
    private val detailCache =
        java.util.concurrent.ConcurrentHashMap<String, RearStoreWidgetDetail>()
    private val readmeCache = java.util.concurrent.ConcurrentHashMap<String, RearStoreReadme>()

    suspend fun loadRecentUpdates(): List<RearStoreListItem> {
        return withContext(Dispatchers.IO) {
            recentUpdatesCache.get()?.let { return@withContext it }
            val loaded = fetchJson<RearStoreRecentUpdatesResponse>("/recent-updates")?.items
                ?.let { enrichListItems(it) }
            if (loaded != null) recentUpdatesCache.set(loaded)
            loaded ?: recentUpdatesCache.get().orEmpty()
        }
    }

    suspend fun searchWidgets(query: String): List<RearStoreListItem> {
        val normalizedQuery = query.normalizedOrNull() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            searchCache[normalizedQuery]?.let { return@withContext it }
            val loaded =
                fetchJson<RearStoreSearchResponse>("/search?q=${Uri.encode(normalizedQuery)}")?.items
                    ?.let { enrichListItems(it) }
            if (loaded != null) searchCache[normalizedQuery] = loaded
            loaded ?: searchCache[normalizedQuery].orEmpty()
        }
    }

    suspend fun loadWidgetDetail(widgetId: String): RearStoreWidgetDetail? {
        val normalizedWidgetId = widgetId.normalizedOrNull() ?: return null
        detailCache[normalizedWidgetId]?.let { return it }
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val descriptionDeferred = async { loadDescriptionCached(normalizedWidgetId) }
                val authorDeferred = async { loadAuthorCached(normalizedWidgetId) }
                val infoDeferred = async { loadWidgetInfoCached(normalizedWidgetId) }
                val releasesDeferred = async { loadReleasesCached(normalizedWidgetId) }

                val description = descriptionDeferred.await()
                    ?: detailCache[normalizedWidgetId]?.let { cached ->
                        return@coroutineScope cached.copy(
                            readme = readmeCache[normalizedWidgetId] ?: cached.readme
                        )
                    }
                    ?: return@coroutineScope null
                val repository = description.repository
                val widgetInfo = infoDeferred.await()
                val resolvedAuthor = resolveAuthor(authorDeferred.await(), repository)
                val detail = RearStoreWidgetDetail(
                    widgetId = normalizedWidgetId,
                    name = description.name.normalizedOrNull()
                        ?: widgetInfo?.name.normalizedOrNull()
                        ?: normalizedWidgetId,
                    description = repository?.description.normalizedOrNull().orEmpty(),
                    author = resolvedAuthor,
                    repository = repository,
                    widgetInfo = widgetInfo,
                    readme = readmeCache[normalizedWidgetId],
                    releases = releasesDeferred.await().orEmpty(),
                )
                detailCache[normalizedWidgetId] = detail
                detail
            }
        }
    }

    suspend fun loadWidgetReadme(widgetId: String): RearStoreReadme? {
        val normalizedWidgetId = widgetId.normalizedOrNull() ?: return null
        readmeCache[normalizedWidgetId]?.let { return it }
        return withContext(Dispatchers.IO) {
            val readme = fetchJson<RearStoreReadmeResponse>(
                "/widget/${Uri.encode(normalizedWidgetId)}/readme"
            )?.readme ?: readmeCache[normalizedWidgetId]
            if (readme != null) {
                readmeCache[normalizedWidgetId] = readme
                detailCache[normalizedWidgetId] = detailCache[normalizedWidgetId]
                    ?.copy(readme = readme)
                    ?: loadWidgetDetail(normalizedWidgetId)?.copy(readme = readme)
                            ?: return@withContext readme
            }
            readme
        }
    }

    fun loadInstalledWidgetSummaries(prefsManager: PrefsManager): Map<String, RearStoreInstalledWidget> {
        return RearWidgetManagerRepository.loadBusinesses(prefsManager)
            .asSequence()
            .filter { it.downloadedFromStore }
            .mapNotNull { business ->
                val widgetId = business.storeWidgetId.normalizedOrNull() ?: return@mapNotNull null
                widgetId to RearStoreInstalledWidget(
                    widgetId = widgetId,
                    widgetName = business.storeWidgetName.normalizedOrNull()
                        ?: business.business.normalizedOrNull()
                        ?: widgetId,
                    businessId = business.business,
                    releaseTag = business.storeReleaseTag.normalizedOrNull(),
                    releasePublishedAt = business.storeReleasePublishedAt.normalizedOrNull(),
                    renameable = business.renameable,
                )
            }
            .toMap(LinkedHashMap())
    }

    suspend fun quickInstallWidget(
        context: Context,
        prefsManager: PrefsManager,
        widgetId: String,
        releaseTag: String? = null,
        assetName: String? = null,
    ): RearStoreQuickInstallResult = withContext(Dispatchers.IO) {
        val detail = loadWidgetDetail(widgetId)
            ?: error("Unable to load widget detail")
        val selectedAsset = selectAsset(
            releases = detail.releases,
            preferredReleaseTag = releaseTag,
            preferredAssetName = assetName,
        )
            ?: error("No downloadable release asset found")
        val assetBytes = downloadAssetBytes(detail.widgetId, selectedAsset)
            ?: error("Failed to download widget asset")

        val businessSetup = detail.widgetInfo?.businessSetup
        val businessId = businessSetup?.id.normalizedOrNull() ?: detail.widgetId
        val businessName = detail.widgetInfo?.name.normalizedOrNull() ?: detail.name
        val targetPath = RearWidgetManagerRepository.saveTemplateBytesToManagedPath(
            context = context,
            bytes = assetBytes,
            businessNameHint = businessId,
            fileNameHint = selectedAsset.asset.name,
        ) ?: error("Failed to save widget asset")

        val businesses = RearWidgetManagerRepository.loadBusinesses(prefsManager)
        val previousBusiness = businesses.firstOrNull {
            it.matchesStoreBusiness(detail.widgetId, businessId)
        }
        val nextBusinesses = businesses
            .filterNot { it.matchesStoreBusiness(detail.widgetId, businessId) }
            .plus(
                RearBusinessConfig(
                    id = previousBusiness?.id
                        ?: RearWidgetConfigCodec.newBusinessId(
                            DEFAULT_COMPONENT_ROUTE_PACKAGE,
                            businessId
                        ),
                    packageName = DEFAULT_COMPONENT_ROUTE_PACKAGE,
                    business = businessId,
                    filePath = targetPath,
                    defaultIndex = previousBusiness?.defaultIndex ?: 0,
                    defaultPriority = previousBusiness?.defaultPriority ?: 500,
                    renameable = businessSetup?.renameable ?: true,
                    downloadedFromStore = true,
                    storeWidgetId = detail.widgetId,
                    storeWidgetName = businessName,
                    storeReleaseTag = selectedAsset.release.tagName.normalizedOrNull(),
                    storeReleaseAssetName = selectedAsset.asset.name.normalizedOrNull(),
                    storeReleasePublishedAt = selectedAsset.release.publishedAt.normalizedOrNull()
                        ?: selectedAsset.release.createdAt.normalizedOrNull(),
                )
            )
        RearWidgetManagerRepository.saveBusinesses(
            context = context,
            prefsManager = prefsManager,
            businesses = nextBusinesses,
            allowLockedEdits = true,
        )

        var cardInstalled = false
        detail.widgetInfo?.cardSetup?.let { cardSetup ->
            val cardPackage = cardSetup.packageName.normalizedOrNull()
            if (cardPackage != null) {
                val cards = RearWidgetManagerRepository.loadCards(prefsManager)
                val previousCard = cards.firstOrNull {
                    it.matchesStoreCard(detail.widgetId, businessId, cardPackage)
                }
                val nextCards = cards
                    .filterNot { it.matchesStoreCard(detail.widgetId, businessId, cardPackage) }
                    .plus(
                        RearCardConfig(
                            id = previousCard?.id ?: RearWidgetConfigCodec.newCardId(),
                            title = cardSetup.name.normalizedOrNull() ?: businessName,
                            packageName = cardPackage,
                            business = businessId,
                            enabled = previousCard?.enabled ?: true,
                            sticky = cardSetup.sticky,
                            priority = previousCard?.priority ?: cardSetup.priority,
                            renameable = cardSetup.renameable,
                            downloadedFromStore = true,
                            storeWidgetId = detail.widgetId,
                            storeWidgetName = businessName,
                            storeReleaseTag = selectedAsset.release.tagName.normalizedOrNull(),
                            storeReleaseAssetName = selectedAsset.asset.name.normalizedOrNull(),
                            storeReleasePublishedAt = selectedAsset.release.publishedAt.normalizedOrNull()
                                ?: selectedAsset.release.createdAt.normalizedOrNull(),
                        )
                    )
                RearWidgetManagerRepository.saveCards(
                    context = context,
                    prefsManager = prefsManager,
                    cards = nextCards,
                    allowLockedEdits = true,
                )
                cardInstalled = true
            }
        }

        RearStoreQuickInstallResult(
            widgetId = detail.widgetId,
            widgetName = businessName,
            releaseTag = selectedAsset.release.tagName.normalizedOrNull(),
            cardInstalled = cardInstalled,
            fallbackUsed = businessSetup == null,
            updatedExistingInstall = previousBusiness != null,
        )
    }

    private fun fetchBytes(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.bytes()
            }
        }.getOrNull()
    }

    private suspend fun enrichListItems(items: List<RearStoreListItem>): List<RearStoreListItem> {
        if (items.isEmpty()) return emptyList()
        return coroutineScope {
            items.map { item ->
                async {
                    val description = loadDescriptionCached(item.id)
                    val repository = description?.repository
                    val resolvedAuthor = resolveAuthor(
                        item.author.takeIf { it.displayName.isNotBlank() }
                            ?: loadAuthorCached(item.id),
                        repository,
                    )
                    item.copy(
                        name = description?.name.normalizedOrNull() ?: item.name,
                        description = repository?.description.normalizedOrNull()
                            ?: item.description,
                        author = resolvedAuthor,
                        updatedAt = item.updatedAt.normalizedOrNull()
                            ?: repository?.updatedAt.normalizedOrNull()
                            ?: repository?.pushedAt.normalizedOrNull()
                            ?: "",
                        repository = item.repository ?: repository?.let {
                            RearStoreRepositoryLink(
                                fullName = it.fullName,
                                url = it.url,
                            )
                        },
                        stargazersCount = repository?.stargazersCount ?: item.stargazersCount,
                    )
                }
            }.awaitAll()
        }
    }

    private fun loadDescriptionCached(widgetId: String): RearStoreDescriptionResponse? {
        descriptionCache[widgetId]?.let { return it }
        val loaded = fetchJson<RearStoreDescriptionResponse>(
            "/widget/${Uri.encode(widgetId)}/description"
        )
        if (loaded != null) descriptionCache[widgetId] = loaded
        return loaded ?: descriptionCache[widgetId]
    }

    private fun loadAuthorCached(widgetId: String): RearStoreAuthor? {
        authorCache[widgetId]?.let { return it }
        val loaded = fetchJson<RearStoreAuthorResponse>(
            "/widget/${Uri.encode(widgetId)}/author"
        )?.author
        val normalizedLoaded = loaded?.takeIf { it.displayName.isNotBlank() }
        if (normalizedLoaded != null) authorCache[widgetId] = normalizedLoaded
        return normalizedLoaded ?: authorCache[widgetId]
    }

    private fun resolveAuthor(
        author: RearStoreAuthor?,
        repository: RearStoreRepositoryInfo?,
    ): RearStoreAuthor {
        author?.takeIf { it.displayName.isNotBlank() }?.let { return it }
        val repositoryOwner = repository?.owner.normalizedOrNull() ?: return RearStoreAuthor()
        return RearStoreAuthor(
            login = repositoryOwner,
            name = repositoryOwner,
            url = repository?.url.normalizedOrNull().orEmpty(),
        )
    }

    private fun loadWidgetInfoCached(widgetId: String): RearStoreWidgetInfo? {
        widgetInfoCache[widgetId]?.let { return it }
        val loaded = fetchJson<RearStoreWidgetInfoResponse>(
            "/widget/${Uri.encode(widgetId)}/widget-info"
        )?.widgetInfo
        if (loaded != null) widgetInfoCache[widgetId] = loaded
        return loaded ?: widgetInfoCache[widgetId]
    }

    private fun loadReleasesCached(widgetId: String): List<RearStoreRelease>? {
        releasesCache[widgetId]?.let { return it }
        val loaded = fetchJson<RearStoreReleasesResponse>(
            "/widget/${Uri.encode(widgetId)}/releases"
        )?.releases
        if (loaded != null) releasesCache[widgetId] = loaded
        return loaded ?: releasesCache[widgetId]
    }

    private inline fun <reified T> fetchJson(path: String): T? {
        return runCatching {
            val request = Request.Builder()
                .url(REAR_STORE_BASE_URL + path)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                gson.fromJson(response.body.string(), T::class.java)
            }
        }.getOrNull()
    }

    private fun downloadAssetBytes(
        widgetId: String,
        selectedAsset: RearStoreSelectedAsset,
    ): ByteArray? {
        val tagName = selectedAsset.release.tagName.normalizedOrNull() ?: return null
        val assetName = selectedAsset.asset.name.normalizedOrNull() ?: return null
        val apiBytes = fetchBytes(
            REAR_STORE_BASE_URL + "/widget/${Uri.encode(widgetId)}/releases/${Uri.encode(tagName)}/${
                Uri.encode(
                    assetName
                )
            }"
        )
        return apiBytes ?: fetchBytes(selectedAsset.asset.downloadUrl)
    }

    private fun selectAsset(
        releases: List<RearStoreRelease>,
        preferredReleaseTag: String? = null,
        preferredAssetName: String? = null,
    ): RearStoreSelectedAsset? {
        val normalizedReleaseTag = preferredReleaseTag.normalizedOrNull()
        val normalizedAssetName = preferredAssetName.normalizedOrNull()
        val preferredRelease = releases.firstOrNull {
            it.tagName.normalizedOrNull() == normalizedReleaseTag && it.assets.isNotEmpty()
        }
        val release =
            preferredRelease ?: releases.firstOrNull { it.assets.isNotEmpty() } ?: return null
        val preferredAsset = release.assets.firstOrNull {
            it.name.normalizedOrNull() == normalizedAssetName
        }
        val asset =
            preferredAsset ?: release.assets.minByOrNull { it.installPriority() } ?: return null
        return RearStoreSelectedAsset(release = release, asset = asset)
    }

    private fun RearStoreReleaseAsset.installPriority(): Int {
        val fileName = name.lowercase()
        return when {
            fileName.endsWith(".mrc") -> 0
            fileName.endsWith(".zip") -> 1
            else -> 2
        }
    }

    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.outputStream().use { it.write(bytes) }
        if (target.exists()) target.delete()
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
    }

    private fun RearBusinessConfig.matchesStoreBusiness(
        widgetId: String,
        businessId: String
    ): Boolean {
        return (downloadedFromStore && storeWidgetId.normalizedOrNull() == widgetId) ||
                (packageName == DEFAULT_COMPONENT_ROUTE_PACKAGE && business == businessId)
    }

    private fun RearCardConfig.matchesStoreCard(
        widgetId: String,
        businessId: String,
        packageName: String,
    ): Boolean {
        return (downloadedFromStore && storeWidgetId.normalizedOrNull() == widgetId) ||
                (this.packageName == packageName && business == businessId)
    }

}
