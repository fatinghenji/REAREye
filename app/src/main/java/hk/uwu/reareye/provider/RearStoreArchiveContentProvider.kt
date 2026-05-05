package hk.uwu.reareye.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import hk.uwu.reareye.repository.rearstore.RearStoreRepository
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import org.json.JSONArray
import org.json.JSONObject

class RearStoreArchiveContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = context ?: return singleJsonCursor(buildErrorPayload("Context unavailable"))
        val prefsManager = context.applicationContext.getPrefsManager()
        val mode = uri.getQueryParameter("mode")?.trim().orEmpty()
        val entry = uri.getQueryParameter("entry")?.trim()?.ifBlank { null }
        val payload = runCatching {
            val source = when (mode) {
                "store_id" -> {
                    val widgetId = uri.getQueryParameter("id")?.trim().orEmpty()
                    require(widgetId.isNotBlank()) { "Missing required query parameter: id" }
                    RearStoreRepository.resolveInstalledArchiveSourceByStoreWidgetId(
                        prefsManager,
                        widgetId
                    )
                        ?: error("Installed store widget not found")
                }

                "business_id" -> {
                    val businessId = uri.getQueryParameter("id")?.trim().orEmpty()
                    require(businessId.isNotBlank()) { "Missing required query parameter: id" }
                    RearStoreRepository.resolveInstalledArchiveSourceByBusinessConfigId(
                        prefsManager,
                        businessId,
                    ) ?: error("Installed business config not found")
                }

                else -> error("Unsupported mode: $mode")
            }

            JSONObject().apply {
                put("success", true)
                put("error", JSONObject.NULL)
                put("mode", mode)
                put("storeWidgetId", source.storeWidgetId)
                put("businessConfigId", source.businessConfigId)
                put("business", source.businessName)
                put("card", source.cardId)
                put("entry", entry)
                if (entry == null) {
                    put(
                        "entries",
                        JSONArray(RearStoreRepository.listInstalledArchiveEntries(source.filePath))
                    )
                    put("contentBase64", JSONObject.NULL)
                } else {
                    put(
                        "contentBase64",
                        RearStoreRepository.readInstalledArchiveFileBase64(
                            filePath = source.filePath,
                            entryName = entry,
                        )
                    )
                }
            }
        }.getOrElse { error ->
            buildErrorPayload(error.message ?: "Unknown error")
        }

        return singleJsonCursor(payload)
    }

    override fun getType(uri: Uri): String {
        return "application/json"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun singleJsonCursor(payload: JSONObject): Cursor {
        return MatrixCursor(arrayOf("json")).apply {
            addRow(arrayOf(payload.toString()))
        }
    }

    private fun buildErrorPayload(message: String): JSONObject {
        return JSONObject().apply {
            put("success", false)
            put("error", message)
            put("contentBase64", JSONObject.NULL)
            put("entries", JSONObject.NULL)
        }
    }
}
