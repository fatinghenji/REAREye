package hk.uwu.reareye.repository.rearwallpaper

import android.content.Context
import android.os.Bundle
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.widgetapi.RearWallpaperApiClient
import hk.uwu.reareye.widgetapi.RearWallpaperApiContract
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleCodec
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object RearWallpaperRepository {

    private const val PREVIEW_CACHE_DIR = "rear_wallpaper_preview_cache"

    private val clientMutex = Mutex()

    @Volatile
    private var remoteClient: RearWallpaperApiClient? = null

    suspend fun loadCatalog(context: Context): RearWallpaperCatalog {
        val catalogBundle = withContext(Dispatchers.IO) {
            withRemote(context) { client ->
                client.getCatalog()
            }
        }
        val currentIndex =
            catalogBundle.getInt(RearWallpaperApiContract.BundleKeys.CURRENT_INDEX, -1)
        val currentWallpaperId = catalogBundle.readNullableInt(
            RearWallpaperApiContract.BundleKeys.CURRENT_WALLPAPER_ID,
        )

        val remoteItems = parseCatalogItems(catalogBundle)
        val wallpapers = syncPreviewCache(context, remoteItems)

        return RearWallpaperCatalog(
            wallpapers = wallpapers,
            currentIndex = currentIndex,
            currentWallpaperId = currentWallpaperId,
        )
    }

    suspend fun switchWallpaper(context: Context, wallpaperId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            withRemote(context) { client ->
                client.switchWallpaper(wallpaperId)
            }
        }
    }

    suspend fun syncSchedule(
        context: Context,
        enabled: Boolean,
        schedule: List<RearWallpaperScheduleEntry>,
    ): Boolean {
        val encoded = encodeSchedule(schedule)
        return withContext(Dispatchers.IO) {
            withRemote(context) { client ->
                client.syncSchedule(enabled, encoded)
            }
        }
    }

    fun loadSchedule(prefsManager: PrefsManager): List<RearWallpaperScheduleEntry> {
        return RearWallpaperScheduleCodec.parse(
            prefsManager.getString(
                ConfigKeys.REAR_WALLPAPER_SCHEDULE_DATA,
                RearWallpaperScheduleCodec.EMPTY_ARRAY,
            )
        )
    }

    fun saveSchedule(
        prefsManager: PrefsManager,
        schedule: List<RearWallpaperScheduleEntry>,
    ) {
        val encoded = encodeSchedule(schedule)
        prefsManager.prefs
            .edit {
                putString(
                    ConfigKeys.REAR_WALLPAPER_SCHEDULE_DATA,
                    encoded
                )
            }
    }

    fun isScheduleEnabled(prefsManager: PrefsManager): Boolean {
        return prefsManager.getBoolean(ConfigKeys.REAR_WALLPAPER_SCHEDULE_ENABLED, false)
    }

    fun setScheduleEnabled(prefsManager: PrefsManager, enabled: Boolean) {
        prefsManager.prefs.edit { putBoolean(ConfigKeys.REAR_WALLPAPER_SCHEDULE_ENABLED, enabled) }
    }

    private suspend fun <T> withRemote(
        context: Context,
        block: (RearWallpaperApiClient) -> T,
    ): T {
        return clientMutex.withLock {
            val appContext = context.applicationContext
            val client = remoteClient ?: RearWallpaperApiClient().also {
                remoteClient = it
            }

            fun ensureConnected(): RearWallpaperApiClient {
                if (client.isConnected() || client.bind(appContext)) return client
                throw IllegalStateException("rear wallpaper hook service is not connected")
            }

            runCatching {
                val connectedClient = ensureConnected()
                runCatching {
                    block(connectedClient)
                }.recoverCatching {
                    client.unbind()
                    block(ensureConnected())
                }.getOrThrow()
            }.onFailure {
                client.unbind()
                if (remoteClient === client) remoteClient = null
            }.getOrThrow()
        }
    }

    @Suppress("DEPRECATION")
    private fun parseCatalogItems(bundle: Bundle): List<RearWallpaperInfo> {
        val rawItems =
            bundle.getParcelableArrayList<Bundle>(RearWallpaperApiContract.BundleKeys.ITEMS)
                .orEmpty()
        return rawItems.map { item ->
            RearWallpaperInfo(
                wallpaperId = item.getInt(RearWallpaperApiContract.BundleKeys.WALLPAPER_ID),
                title = item.getString(RearWallpaperApiContract.BundleKeys.TITLE).orEmpty()
                    .ifBlank { "Wallpaper" },
                name = item.getString(RearWallpaperApiContract.BundleKeys.NAME).orEmpty()
                    .ifBlank { "unknown" },
                previewAvailable = item.getBoolean(
                    RearWallpaperApiContract.BundleKeys.PREVIEW_AVAILABLE,
                    false,
                ),
                previewSignature = item.getString(RearWallpaperApiContract.BundleKeys.PREVIEW_SIGNATURE)
                    .orEmpty(),
            )
        }
    }

    private suspend fun syncPreviewCache(
        context: Context,
        wallpapers: List<RearWallpaperInfo>,
    ): List<RearWallpaperInfo> {
        return withContext(Dispatchers.IO) {
            val root = resolvePreviewCacheRoot(context)
            if (!root.exists()) root.mkdirs()

            val expectedFiles = HashSet<String>()
            val result = wallpapers.map { wallpaper ->
                if (!wallpaper.previewAvailable || wallpaper.previewSignature.isBlank()) {
                    return@map wallpaper.copy(cachePath = null)
                }

                val cacheFile = File(root, previewCacheFileName(wallpaper))
                expectedFiles += cacheFile.name

                if (!cacheFile.isFile || cacheFile.length() <= 0L) {
                    fetchAndStorePreview(context, wallpaper.wallpaperId, cacheFile)
                }

                wallpaper.copy(cachePath = cacheFile.takeIf { it.isFile && it.length() > 0L }?.absolutePath)
            }

            root.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (file.name !in expectedFiles) runCatching { file.delete() }
            }

            result
        }
    }

    private fun previewCacheFileName(wallpaper: RearWallpaperInfo): String {
        return "${wallpaper.wallpaperId}_${wallpaper.previewSignature}.jpg"
    }

    private fun encodeSchedule(schedule: List<RearWallpaperScheduleEntry>): String {
        return RearWallpaperScheduleCodec.encode(schedule)
    }

    private suspend fun fetchAndStorePreview(
        context: Context,
        wallpaperId: Int,
        targetFile: File,
    ) {
        val bytes = withRemote(context) { client ->
            client.getPreview(wallpaperId)
        }
        if (bytes == null || bytes.isEmpty()) return
        writeBytesAtomically(targetFile, bytes)
    }

    private fun writeBytesAtomically(targetFile: File, bytes: ByteArray) {
        runCatching {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tempFile.outputStream().use { it.write(bytes) }
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        }
    }

    private fun resolvePreviewCacheRoot(context: Context): File {
        return File(context.filesDir, PREVIEW_CACHE_DIR)
    }

    private fun Bundle.readNullableInt(key: String): Int? {
        return if (containsKey(key)) getInt(key) else null
    }
}
