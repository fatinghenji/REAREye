package hk.uwu.reareye.repository.rearwidget

import hk.uwu.reareye.widgetapi.RearWidgetSceneRouteSpec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RearBusinessConfig(
    val id: String,
    val packageName: String,
    val business: String,
    val filePath: String,
    val defaultIndex: Int = 0,
    val defaultPriority: Int = 500,
    val renameable: Boolean = true,
    val downloadedFromStore: Boolean = false,
    val storeWidgetId: String? = null,
    val storeWidgetName: String? = null,
    val storeReleaseTag: String? = null,
    val storeReleaseAssetName: String? = null,
    val storeReleasePublishedAt: String? = null,
    val storeInstalledAt: String? = null,
)

data class RearCardConfig(
    val id: String,
    val title: String,
    val packageName: String,
    val business: String,
    val oneConfigJson: String? = null,
    val enabled: Boolean = true,
    val sticky: Boolean = true,
    val priority: Int = 500,
    val renameable: Boolean = true,
    val downloadedFromStore: Boolean = false,
    val storeWidgetId: String? = null,
    val storeWidgetName: String? = null,
    val storeReleaseTag: String? = null,
    val storeReleaseAssetName: String? = null,
    val storeReleasePublishedAt: String? = null,
)

data class RearWidgetSceneRouteConfig(
    val id: String,
    val packageName: String,
    val scene: String,
    val business: String,
)

object RearWidgetConfigCodec {
    const val EMPTY_ARRAY = "[]"
    private const val BUSINESS_BLOB_PREFIX = "rear_widget_business_blob_"
    private const val BUSINESS_BLOB_META_PREFIX = "rear_widget_business_blob_meta_"
    private const val BUSINESS_BLOB_SOURCE_PREFIX = "rear_widget_business_blob_source_"

    fun newBusinessId(packageName: String, business: String): String =
        "${packageName.trim()}::${business.trim()}"

    fun newSceneRouteId(packageName: String, scene: String): String =
        "${packageName.trim()}::scene::${RearWidgetSceneRouteSpec.normalizeScene(scene)}"

    fun newCardId(): String = UUID.randomUUID().toString()

    fun businessBlobKey(business: String): String =
        BUSINESS_BLOB_PREFIX + sanitizeBusinessKey(business)

    fun businessBlobMetaKey(business: String): String =
        BUSINESS_BLOB_META_PREFIX + sanitizeBusinessKey(business)

    fun businessBlobSourceKey(business: String): String =
        BUSINESS_BLOB_SOURCE_PREFIX + sanitizeBusinessKey(business)

    private fun sanitizeBusinessKey(business: String): String =
        business.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")

    fun parseBusinesses(raw: String?): List<RearBusinessConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<RearBusinessConfig>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").trim()
            val business = obj.optString("business").trim()
            val filePath = obj.optString("filePath").trim()
            if (packageName.isBlank() || business.isBlank() || filePath.isBlank()) continue
            val id = obj.optString("id").ifBlank { newBusinessId(packageName, business) }
            out += RearBusinessConfig(
                id = id,
                packageName = packageName,
                business = business,
                filePath = filePath,
                defaultIndex = obj.optInt("defaultIndex", 0),
                defaultPriority = obj.optInt("defaultPriority", 500),
                renameable = obj.optBoolean("renameable", true),
                downloadedFromStore = obj.optBoolean("downloadedFromStore", false),
                storeWidgetId = obj.optString("storeWidgetId").trim().ifBlank { null },
                storeWidgetName = obj.optString("storeWidgetName").trim().ifBlank { null },
                storeReleaseTag = obj.optString("storeReleaseTag").trim().ifBlank { null },
                storeReleaseAssetName = obj.optString("storeReleaseAssetName").trim()
                    .ifBlank { null },
                storeReleasePublishedAt = obj.optString("storeReleasePublishedAt").trim()
                    .ifBlank { null },
                storeInstalledAt = obj.optString("storeInstalledAt").trim().ifBlank { null },
            )
        }
        return out
    }

    fun parseCards(raw: String?): List<RearCardConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<RearCardConfig>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").trim()
            val business = obj.optString("business").trim()
            if (packageName.isBlank() || business.isBlank()) continue
            val title = obj.optString("title").trim().ifBlank { business }
            val id = obj.optString("id").ifBlank { newCardId() }
            out += RearCardConfig(
                id = id,
                title = title,
                packageName = packageName,
                business = business,
                oneConfigJson = obj.optString("oneConfigJson").trim().ifBlank { null },
                enabled = obj.optBoolean("enabled", true),
                sticky = obj.optBoolean("sticky", true),
                priority = obj.optInt("priority", 500),
                renameable = obj.optBoolean("renameable", true),
                downloadedFromStore = obj.optBoolean("downloadedFromStore", false),
                storeWidgetId = obj.optString("storeWidgetId").trim().ifBlank { null },
                storeWidgetName = obj.optString("storeWidgetName").trim().ifBlank { null },
                storeReleaseTag = obj.optString("storeReleaseTag").trim().ifBlank { null },
                storeReleaseAssetName = obj.optString("storeReleaseAssetName").trim()
                    .ifBlank { null },
                storeReleasePublishedAt = obj.optString("storeReleasePublishedAt").trim()
                    .ifBlank { null },
            )
        }
        return out
    }

    fun parseSceneRoutes(raw: String?): List<RearWidgetSceneRouteConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<RearWidgetSceneRouteConfig>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").trim()
            val scene = obj.optString("scene").trim()
            val business = obj.optString("business").trim()
            if (packageName.isBlank() || scene.isBlank() || business.isBlank()) continue
            val id = obj.optString("id").ifBlank { newSceneRouteId(packageName, scene) }
            out += RearWidgetSceneRouteConfig(
                id = id,
                packageName = packageName,
                scene = scene,
                business = business,
            )
        }
        return out
    }

    fun encodeBusinesses(list: List<RearBusinessConfig>): String =
        JSONArray().also { arr ->
            list.forEach { item ->
                arr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("packageName", item.packageName)
                        .put("business", item.business)
                        .put("filePath", item.filePath)
                        .put("defaultIndex", item.defaultIndex)
                        .put("defaultPriority", item.defaultPriority)
                        .put("renameable", item.renameable)
                        .put("downloadedFromStore", item.downloadedFromStore)
                        .put("storeWidgetId", item.storeWidgetId)
                        .put("storeWidgetName", item.storeWidgetName)
                        .put("storeReleaseTag", item.storeReleaseTag)
                        .put("storeReleaseAssetName", item.storeReleaseAssetName)
                        .put("storeReleasePublishedAt", item.storeReleasePublishedAt)
                        .put("storeInstalledAt", item.storeInstalledAt)
                )
            }
        }.toString()

    fun encodeCards(list: List<RearCardConfig>): String =
        JSONArray().also { arr ->
            list.forEach { item ->
                arr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("packageName", item.packageName)
                        .put("business", item.business)
                        .put("oneConfigJson", item.oneConfigJson)
                        .put("enabled", item.enabled)
                        .put("sticky", item.sticky)
                        .put("priority", item.priority)
                        .put("renameable", item.renameable)
                        .put("downloadedFromStore", item.downloadedFromStore)
                        .put("storeWidgetId", item.storeWidgetId)
                        .put("storeWidgetName", item.storeWidgetName)
                        .put("storeReleaseTag", item.storeReleaseTag)
                        .put("storeReleaseAssetName", item.storeReleaseAssetName)
                        .put("storeReleasePublishedAt", item.storeReleasePublishedAt)
                )
            }
        }.toString()

    fun encodeSceneRoutes(list: List<RearWidgetSceneRouteConfig>): String =
        JSONArray().also { arr ->
            list.forEach { item ->
                arr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("packageName", item.packageName)
                        .put("scene", item.scene)
                        .put("business", item.business)
                )
            }
        }.toString()
}
