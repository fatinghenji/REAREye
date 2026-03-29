package hk.uwu.reareye.rearwidget

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import com.highcapable.yukihookapi.hook.factory.dataChannel
import hk.uwu.reareye.actions.RearWidgetApi
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager
import java.io.File
import java.security.MessageDigest

object RearWidgetManagerRepository {

    private const val TARGET_HOOK_PACKAGE = "com.xiaomi.subscreencenter"
    private const val BUSINESS_TEMPLATE_DIR = "rear_widget_business"

    fun loadBusinesses(prefsManager: PrefsManager): List<RearBusinessConfig> {
        val raw = prefsManager.getString(
            ConfigKeys.REAR_WIDGET_BUSINESS_DATA,
            RearWidgetConfigCodec.EMPTY_ARRAY,
        )
        return RearWidgetConfigCodec.parseBusinesses(raw)
    }

    fun saveBusinesses(
        context: Context,
        prefsManager: PrefsManager,
        businesses: List<RearBusinessConfig>
    ) {
        val oldBusinesses = loadBusinesses(prefsManager)
        val preparedBusinesses = prepareBusinessesInManagedDir(context, businesses)
        cacheBusinessTemplatesInPrefs(prefsManager, oldBusinesses, preparedBusinesses)
        prefsManager.putString(
            ConfigKeys.REAR_WIDGET_BUSINESS_DATA,
            RearWidgetConfigCodec.encodeBusinesses(preparedBusinesses),
        )
        cleanupStaleManagedFiles(context, oldBusinesses, preparedBusinesses)
        applyBusinessFilesViaApi(context, oldBusinesses, preparedBusinesses)

        // business 文件变化后，重放当前卡片，确保显示状态与模板一致。
        val cards = loadCards(prefsManager)
        applyCardsViaApi(context, cards, cards, preparedBusinesses)
    }

    fun loadCards(prefsManager: PrefsManager): List<RearCardConfig> {
        val raw = prefsManager.getString(
            ConfigKeys.REAR_WIDGET_CARD_DATA,
            RearWidgetConfigCodec.EMPTY_ARRAY,
        )
        return RearWidgetConfigCodec.parseCards(raw)
    }

    fun saveCards(context: Context, prefsManager: PrefsManager, cards: List<RearCardConfig>) {
        val oldCards = loadCards(prefsManager)
        prefsManager.putString(
            ConfigKeys.REAR_WIDGET_CARD_DATA,
            RearWidgetConfigCodec.encodeCards(cards),
        )
        val businesses = loadBusinesses(prefsManager)
        applyCardsViaApi(context, oldCards, cards, businesses)
    }

    fun refreshRuntimeFromPrefs(context: Context, prefsManager: PrefsManager) {
        val businesses = loadBusinesses(prefsManager)
        val preparedBusinesses = prepareBusinessesInManagedDir(context, businesses)
        cacheBusinessTemplatesInPrefs(prefsManager, businesses, preparedBusinesses)
        if (preparedBusinesses != businesses) {
            prefsManager.putString(
                ConfigKeys.REAR_WIDGET_BUSINESS_DATA,
                RearWidgetConfigCodec.encodeBusinesses(preparedBusinesses),
            )
        }
        val cards = loadCards(prefsManager)
        applyBusinessFilesViaApi(context, emptyList(), preparedBusinesses)
        applyCardsViaApi(context, emptyList(), cards, preparedBusinesses)
    }

    fun copyTemplateToManagedPath(
        context: Context,
        uri: Uri,
        businessNameHint: String,
    ): String? {
        return runCatching {
            val root = resolveManagedTemplateRoot(context)
            if (!root.exists()) root.mkdirs()

            val sourceName = queryDisplayName(context, uri)
            val extension = sourceName
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?: "json"
            val safeBusiness = sanitizeFileName(businessNameHint.ifBlank { "business" })
            val target = File(root, "${safeBusiness}_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            target.absolutePath
        }.getOrNull()
    }

    private fun applyBusinessFilesViaApi(
        context: Context,
        oldBusinesses: List<RearBusinessConfig>,
        newBusinesses: List<RearBusinessConfig>,
    ) {
        val oldByBusiness = oldBusinesses.associateBy { it.business }
        val newByBusiness = newBusinesses.associateBy { it.business }

        val staleBusinesses = oldByBusiness.keys - newByBusiness.keys
        staleBusinesses.forEach { business ->
            sendChannel(
                context,
                RearWidgetApi.Channel.KEY_UNREGISTER_BUSINESS_FILE,
                RearWidgetApi.buildUnregisterBusinessFilePayload(business),
            )
        }

        newBusinesses.forEach { item ->
            sendChannel(
                context,
                RearWidgetApi.Channel.KEY_REGISTER_BUSINESS_FILE,
                RearWidgetApi.buildRegisterBusinessFilePayload(item.business, item.filePath),
            )
        }
    }

    private fun applyCardsViaApi(
        context: Context,
        oldCards: List<RearCardConfig>,
        newCards: List<RearCardConfig>,
        businesses: List<RearBusinessConfig>,
    ) {
        val businessPathByName = businesses.associate { it.business to it.filePath }

        val oldPairs = oldCards.mapTo(LinkedHashSet()) { it.packageName to it.business }
        val newPairs = newCards.mapTo(LinkedHashSet()) { it.packageName to it.business }
        val allPairs = LinkedHashSet<Pair<String, String>>().apply {
            addAll(oldPairs)
            addAll(newPairs)
        }

        // 先清空受影响业务显示，避免残留旧卡片。
        allPairs.forEach { (pkg, biz) ->
            sendChannel(
                context,
                RearWidgetApi.Channel.KEY_DISABLE_BUSINESS_DISPLAY,
                RearWidgetApi.buildDisableBusinessDisplayPayload(business = biz, packageName = pkg),
            )
        }

        val enabledCards = newCards.filter { it.enabled }
        val enabledPairs = enabledCards.mapTo(LinkedHashSet()) { it.packageName to it.business }

        enabledPairs.forEach { (pkg, biz) ->
            val filePath = businessPathByName[biz]
            if (!filePath.isNullOrBlank()) {
                sendChannel(
                    context,
                    RearWidgetApi.Channel.KEY_REGISTER_BUSINESS,
                    RearWidgetApi.buildRegisterBusinessPayload(
                        business = biz,
                        filePath = filePath,
                        packageName = pkg,
                        defaultIndex = 0,
                        defaultPriority = 500,
                    ),
                )
            } else {
                sendChannel(
                    context,
                    RearWidgetApi.Channel.KEY_REGISTER_BUSINESS,
                    RearWidgetApi.buildRegisterBusinessPayloadWithoutFile(
                        business = biz,
                        packageName = pkg,
                        defaultIndex = 0,
                        defaultPriority = 500,
                    ),
                )
            }
        }

        (allPairs - enabledPairs).forEach { (pkg, biz) ->
            sendChannel(
                context,
                RearWidgetApi.Channel.KEY_UNREGISTER_BUSINESS,
                RearWidgetApi.buildUnregisterBusinessPayload(
                    business = biz,
                    packageName = pkg,
                ),
            )
        }

        enabledCards.forEachIndexed { index, card ->
            val payload = Bundle().apply {
                putString("title", card.title.ifBlank { card.business })
                putString("business", card.business)
                putString("__rear_card_id__", card.id)
            }
            val options = RearWidgetApi.NoticeOptions(
                sticky = true,
                disablePopup = true,
                showTimeTip = true,
                index = index,
                priority = card.priority,
            )
            sendChannel(
                context,
                RearWidgetApi.Channel.KEY_POST_NOTICE,
                RearWidgetApi.buildPostNoticePayload(
                    business = card.business,
                    packageName = card.packageName,
                    payload = payload,
                    options = options,
                ),
            )
        }
    }

    private fun sendChannel(context: Context, key: String, payload: String) {
        runCatching {
            context.dataChannel(TARGET_HOOK_PACKAGE).put(key, payload)
        }
    }

    private fun cleanupStaleManagedFiles(
        context: Context,
        oldBusinesses: List<RearBusinessConfig>,
        newBusinesses: List<RearBusinessConfig>,
    ) {
        val root = resolveManagedTemplateRoot(context)
        val rootNorm = normalizePath(root.absolutePath) ?: return
        val activePaths = newBusinesses.mapNotNull { normalizePath(it.filePath) }.toSet()
        val stalePaths = oldBusinesses.mapNotNull { normalizePath(it.filePath) }
            .filter { it !in activePaths }

        stalePaths.forEach { stalePath ->
            deleteIfManagedPath(stalePath, rootNorm)
        }

        if (root.exists() && root.isDirectory) {
            root.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val filePath = normalizePath(file.absolutePath) ?: return@forEach
                if (filePath !in activePaths) runCatching { file.delete() }
            }
        }
    }

    private fun resolveManagedTemplateRoot(context: Context): File {
        return File(context.filesDir, BUSINESS_TEMPLATE_DIR)
    }

    private fun prepareBusinessesInManagedDir(
        context: Context,
        businesses: List<RearBusinessConfig>,
    ): List<RearBusinessConfig> {
        val root = resolveManagedTemplateRoot(context)
        if (!root.exists()) root.mkdirs()
        val rootNorm = normalizePath(root.absolutePath)

        return businesses.map { item ->
            val currentNorm = normalizePath(item.filePath)
            val alreadyManaged = !rootNorm.isNullOrBlank() && !currentNorm.isNullOrBlank() &&
                    (currentNorm == rootNorm || currentNorm.startsWith("$rootNorm/")) && File(item.filePath).exists()
            if (alreadyManaged) return@map item

            val source = File(item.filePath)
            if (!source.exists() || !source.isFile) return@map item

            val extension =
                source.name.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "json"
            val target = File(
                root,
                "${sanitizeFileName(item.business)}_${System.currentTimeMillis()}.$extension"
            )
            runCatching {
                source.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure {
                return@map item
            }
            item.copy(filePath = target.absolutePath)
        }
    }

    private fun cacheBusinessTemplatesInPrefs(
        prefsManager: PrefsManager,
        oldBusinesses: List<RearBusinessConfig>,
        newBusinesses: List<RearBusinessConfig>,
    ) {
        val oldSet = oldBusinesses.mapTo(HashSet()) { it.business }
        val newSet = newBusinesses.mapTo(HashSet()) { it.business }
        (oldSet - newSet).forEach { business ->
            clearBusinessTemplateBlob(prefsManager, business)
        }

        newBusinesses.forEach { item ->
            upsertBusinessTemplateBlob(
                prefsManager = prefsManager,
                business = item.business,
                sourcePath = item.filePath,
            )
        }
    }

    private fun upsertBusinessTemplateBlob(
        prefsManager: PrefsManager,
        business: String,
        sourcePath: String,
    ) {
        val source = File(sourcePath)
        if (!source.exists() || !source.isFile) return

        val sourceSig = buildSourceSignature(source)
        val sourceKey = RearWidgetConfigCodec.businessBlobSourceKey(business)
        val blobKey = RearWidgetConfigCodec.businessBlobKey(business)
        val metaKey = RearWidgetConfigCodec.businessBlobMetaKey(business)

        val oldSourceSig = prefsManager.getString(sourceKey, "")
        val oldBlob = prefsManager.getString(blobKey, "")
        if (oldSourceSig == sourceSig && oldBlob.isNotBlank()) {
            return
        }

        val bytes = runCatching { source.readBytes() }.getOrNull() ?: return
        val newMeta = buildBlobMeta(bytes)
        val oldMeta = prefsManager.getString(metaKey, "")

        if (oldMeta == newMeta && oldBlob.isNotBlank()) {
            prefsManager.putString(sourceKey, sourceSig)
            return
        }

        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefsManager.putString(blobKey, encoded)
        prefsManager.putString(metaKey, newMeta)
        prefsManager.putString(sourceKey, sourceSig)
    }

    private fun clearBusinessTemplateBlob(prefsManager: PrefsManager, business: String) {
        prefsManager.putString(RearWidgetConfigCodec.businessBlobKey(business), "")
        prefsManager.putString(RearWidgetConfigCodec.businessBlobMetaKey(business), "")
        prefsManager.putString(RearWidgetConfigCodec.businessBlobSourceKey(business), "")
    }

    private fun deleteIfManagedPath(path: String, rootNorm: String) {
        val target = File(path)
        val targetNorm = normalizePath(target.absolutePath) ?: return
        val managed = targetNorm == rootNorm || targetNorm.startsWith("$rootNorm/")
        if (!managed) return
        runCatching {
            if (target.exists() && target.isFile) target.delete()
        }
    }

    private fun normalizePath(path: String): String? {
        return runCatching { File(path).absolutePath.replace('\\', '/') }.getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx < 0 || !cursor.moveToFirst()) return@use null
                    cursor.getString(idx)
                }
        }.getOrNull()
    }

    private fun sanitizeFileName(source: String): String {
        return source.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun buildSourceSignature(source: File): String {
        return "${normalizePath(source.absolutePath)}|${source.length()}|${source.lastModified()}"
    }

    private fun buildBlobMeta(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString(separator = "") { "%02x".format(it) }
        return "$hex:${bytes.size}"
    }
}
