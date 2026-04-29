package hk.uwu.reareye.repository.rearwallpaper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager
import hk.uwu.reareye.widgetapi.RearWallpaperApiClient
import hk.uwu.reareye.widgetapi.RearWallpaperApiContract
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleCodec
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleEntry
import hk.uwu.reareye.widgetapi.RearWidgetApiContract
import hk.uwu.reareye.widgetapi.RearWidgetTemplateConfigState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object RearWallpaperRepository {

    private const val PREVIEW_CACHE_DIR = "rear_wallpaper_preview_cache"
    private const val IMPORT_CACHE_DIR = "rear_wallpaper_import_cache"

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

    suspend fun importWallpaperPackage(
        context: Context,
        packageUri: Uri,
        metadataUri: Uri?,
        previewUri: Uri?,
        options: RearWallpaperMetadataOptions,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            previewUri?.let { grantReadAccess(context, it) }
            val displayNameHint = queryDisplayName(context, packageUri)
                ?.trim()
                ?.ifBlank { null }
                ?: "wallpaper.mrc"
            val packageFile = createImportCacheFile(context, displayNameHint)
            val metadataBytes = metadataUri?.let { readBytes(context, it) }
            writeImportPackageToFile(
                targetFile = packageFile,
                metadataBytes = metadataBytes,
                source = {
                    context.contentResolver.openInputStream(packageUri)
                        ?: error("failed to open package uri")
                },
            )
            try {
                val result = withRemote(context) { client ->
                    ParcelFileDescriptor.open(packageFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        .use { packageFd ->
                            client.importWallpaperPackage(
                                packageFd = packageFd,
                                displayNameHint = displayNameHint,
                                previewUri = previewUri?.toString(),
                                options = options.toBundle(),
                            )
                        }
                }
                parseOperationResult(result, "wallpaper import failed without an error message")
            } finally {
                runCatching { packageFile.delete() }
            }
        }
    }

    suspend fun importWallpaperBytes(
        context: Context,
        bytes: ByteArray,
        displayNameHint: String,
        metadataBytes: ByteArray? = null,
        previewUri: Uri? = null,
        options: RearWallpaperMetadataOptions? = null,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            val cacheFile = createImportCacheFile(context, displayNameHint)
            writeImportPackageToFile(
                targetFile = cacheFile,
                metadataBytes = metadataBytes ?: options?.toMetadataJsonBytes(),
                source = { bytes.inputStream() },
            )
            try {
                previewUri?.let { grantReadAccess(context, it) }
                val result = withRemote(context) { client ->
                    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        .use { packageFd ->
                            client.importWallpaperPackage(
                                packageFd = packageFd,
                                displayNameHint = displayNameHint.trim().ifBlank { cacheFile.name },
                                previewUri = previewUri?.toString(),
                                options = options?.toBundle() ?: Bundle(),
                            )
                        }
                }
                parseOperationResult(result, "wallpaper import failed without an error message")
            } finally {
                runCatching { cacheFile.delete() }
            }
        }
    }

    suspend fun updateWallpaperMetadata(
        context: Context,
        wallpaperId: Int,
        previewUri: Uri?,
        options: RearWallpaperMetadataOptions,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            previewUri?.let { grantReadAccess(context, it) }
            val result = withRemote(context) { client ->
                client.updateWallpaperMetadata(
                    wallpaperId = wallpaperId,
                    previewUri = previewUri?.toString(),
                    options = options.toBundle(),
                )
            }
            parseOperationResult(
                result,
                "wallpaper metadata update failed without an error message"
            )
        }
    }

    suspend fun updateWallpaperBytes(
        context: Context,
        wallpaperId: Int,
        bytes: ByteArray,
        displayNameHint: String,
        metadataBytes: ByteArray? = null,
        previewUri: Uri? = null,
        options: RearWallpaperMetadataOptions? = null,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            val cacheFile = createImportCacheFile(context, displayNameHint)
            writeImportPackageToFile(
                targetFile = cacheFile,
                metadataBytes = metadataBytes ?: options?.toMetadataJsonBytes(),
                source = { bytes.inputStream() },
            )
            try {
                previewUri?.let { grantReadAccess(context, it) }
                val result = withRemote(context) { client ->
                    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        .use { packageFd ->
                            client.updateWallpaperPackage(
                                wallpaperId = wallpaperId,
                                packageFd = packageFd,
                                displayNameHint = displayNameHint.trim().ifBlank { cacheFile.name },
                                previewUri = previewUri?.toString(),
                                options = options?.toBundle() ?: Bundle(),
                            )
                        }
                }
                parseOperationResult(
                    result,
                    "wallpaper package update failed without an error message"
                )
            } finally {
                runCatching { cacheFile.delete() }
            }
        }
    }

    suspend fun generateWallpaperPreview(
        context: Context,
        wallpaperId: Int,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            val result = withRemote(context) { client ->
                client.generateWallpaperPreview(wallpaperId)
            }
            parseOperationResult(
                result,
                "wallpaper preview generation failed without an error message"
            )
        }
    }

    suspend fun deleteWallpaper(
        context: Context,
        wallpaperId: Int,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            val result = withRemote(context) { client ->
                client.deleteWallpaper(wallpaperId)
            }
            parseOperationResult(result, "wallpaper delete failed without an error message")
        }
    }

    suspend fun resolveTemplateConfigState(
        context: Context,
        wallpaperId: Int,
        currentOneConfigJson: String?,
    ): RearWidgetTemplateConfigState? {
        return withContext(Dispatchers.IO) {
            withRemote(context) { client ->
                client.resolveTemplateConfigState(wallpaperId, currentOneConfigJson)
            }
        }
    }

    suspend fun saveTemplateConfig(
        context: Context,
        wallpaperId: Int,
        oneConfigJson: String?,
    ): RearWallpaperOperationResult {
        return withContext(Dispatchers.IO) {
            val result = withRemote(context) { client ->
                client.saveTemplateConfig(wallpaperId, oneConfigJson)
            }
            parseOperationResult(
                result,
                "wallpaper template config save failed without an error message"
            )
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
                description = item.getString(RearWallpaperApiContract.BundleKeys.DESCRIPTION)
                    .orEmpty(),
                author = item.getString(RearWallpaperApiContract.BundleKeys.AUTHOR).orEmpty(),
                designer = item.getString(RearWallpaperApiContract.BundleKeys.DESIGNER).orEmpty(),
                resSubType = item.getString(RearWallpaperApiContract.BundleKeys.RES_SUB_TYPE)
                    .orEmpty(),
                imported = item.getBoolean(RearWallpaperApiContract.BundleKeys.IMPORTED, false),
                canEditMetadata = item.getBoolean(
                    RearWallpaperApiContract.BundleKeys.CAN_EDIT_METADATA,
                    false,
                ),
                canDelete = item.getBoolean(RearWallpaperApiContract.BundleKeys.CAN_DELETE, false),
                editable = item.getBoolean(RearWallpaperApiContract.BundleKeys.EDITABLE, false),
                thirdParties = item.getBoolean(
                    RearWallpaperApiContract.BundleKeys.THIRD_PARTIES,
                    false,
                ),
                supportAon = item.getBoolean(
                    RearWallpaperApiContract.BundleKeys.SUPPORT_AON,
                    false,
                ),
                templateConfigAvailable = item.getBoolean(
                    RearWallpaperApiContract.BundleKeys.TEMPLATE_CONFIG_AVAILABLE,
                    false,
                ),
                templateConfigCustomized = item.getBoolean(
                    RearWallpaperApiContract.BundleKeys.TEMPLATE_CONFIG_CUSTOMIZED,
                    false,
                ),
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

    private fun createImportCacheFile(context: Context, displayNameHint: String): File {
        val root = File(context.cacheDir, IMPORT_CACHE_DIR)
        if (!root.exists()) root.mkdirs()
        val fileName = sanitizeFileName(displayNameHint.ifBlank { "wallpaper.mrc" })
        return File(root, "${System.currentTimeMillis()}_$fileName")
    }

    private fun grantReadAccess(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        context.grantUriPermission(
            RearWidgetApiContract.HOOK_HOST_PACKAGE,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx < 0 || !cursor.moveToFirst()) return@use null
                cursor.getString(idx)
            }
        }.getOrNull()
    }

    private fun sanitizeFileName(source: String): String {
        return source.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun writeImportPackageToFile(
        targetFile: File,
        metadataBytes: ByteArray?,
        source: () -> java.io.InputStream,
    ) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (metadataBytes == null || metadataBytes.isEmpty()) {
            source().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            source().use { rawInput ->
                ZipInputStream(rawInput).use { input ->
                    tempFile.outputStream().use { rawOutput ->
                        ZipOutputStream(rawOutput).use { zipOutput ->
                            var metadataWritten = false
                            while (true) {
                                val entry = input.nextEntry ?: break
                                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                                val targetEntry = ZipEntry(entry.name).apply {
                                    time = entry.time
                                    comment = entry.comment
                                }
                                if (entry.isDirectory) {
                                    zipOutput.putNextEntry(targetEntry)
                                    zipOutput.closeEntry()
                                    continue
                                }
                                if (normalizedName == "metadata.mrm") {
                                    zipOutput.putNextEntry(ZipEntry("metadata.mrm"))
                                    zipOutput.write(metadataBytes)
                                    zipOutput.closeEntry()
                                    metadataWritten = true
                                    continue
                                }
                                zipOutput.putNextEntry(targetEntry)
                                input.copyTo(zipOutput)
                                zipOutput.closeEntry()
                            }
                            if (!metadataWritten) {
                                zipOutput.putNextEntry(ZipEntry("metadata.mrm"))
                                zipOutput.write(metadataBytes)
                                zipOutput.closeEntry()
                            }
                        }
                    }
                }
            }
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun RearWallpaperMetadataOptions.toMetadataJsonBytes(): ByteArray {
        return JSONObject().apply {
            put("authors", localeObject(author, author))
            put("designers", localeObject(designer, designer))
            put("titles", localeObject(titleFallback, titleZhCn))
            put("descriptions", localeObject(descriptionFallback, descriptionZhCn))
            put("subResourceType", category)
            put("resSubType", resSubType)
            put("isRearScreenEditable", editable)
            put("isThirdParties", thirdParties)
            put("supportAon", supportAon)
        }.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun localeObject(fallback: String, zhCn: String): JSONObject {
        return JSONObject().apply {
            put("fallback", fallback)
            put("zh_CN", zhCn.ifBlank { fallback })
        }
    }

    private fun RearWallpaperMetadataOptions.toBundle(): Bundle {
        return Bundle().apply {
            putString(RearWallpaperApiContract.BundleKeys.META_TITLE_FALLBACK, titleFallback)
            putString(RearWallpaperApiContract.BundleKeys.META_TITLE_ZH_CN, titleZhCn)
            putString(
                RearWallpaperApiContract.BundleKeys.META_DESCRIPTION_FALLBACK,
                descriptionFallback,
            )
            putString(RearWallpaperApiContract.BundleKeys.META_DESCRIPTION_ZH_CN, descriptionZhCn)
            putString(RearWallpaperApiContract.BundleKeys.META_AUTHOR, author)
            putString(RearWallpaperApiContract.BundleKeys.META_DESIGNER, designer)
            putString(RearWallpaperApiContract.BundleKeys.META_CATEGORY, category)
            putString(RearWallpaperApiContract.BundleKeys.META_RES_SUB_TYPE, resSubType)
            putBoolean(RearWallpaperApiContract.BundleKeys.META_EDITABLE, editable)
            putBoolean(RearWallpaperApiContract.BundleKeys.META_THIRD_PARTIES, thirdParties)
            putBoolean(RearWallpaperApiContract.BundleKeys.META_SUPPORT_AON, supportAon)
        }
    }

    private fun parseOperationResult(
        bundle: Bundle,
        fallbackError: String,
    ): RearWallpaperOperationResult {
        val success = bundle.getBoolean(RearWallpaperApiContract.BundleKeys.SUCCESS, false)
        val error = bundle.getString(RearWallpaperApiContract.BundleKeys.ERROR)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackError.takeIf { !success }
        return RearWallpaperOperationResult(
            success = success,
            wallpaperId = bundle.readNullableInt(RearWallpaperApiContract.BundleKeys.WALLPAPER_ID),
            error = error,
        )
    }

    private fun Bundle.readNullableInt(key: String): Int? {
        return if (containsKey(key)) getInt(key) else null
    }
}
