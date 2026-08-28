package hk.uwu.reareye.repository.rearwidget

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager

object RearBusinessExtraConfigFields {
    const val HIDE_TIME_TIP = "hide_time_tip"
}

data class RearBusinessExtraConfig(
    val values: JsonObject = JsonObject(),
) {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return values.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: defaultValue
    }

    fun getShowTimeTipOrDefault(): Boolean {
        return !getBoolean(RearBusinessExtraConfigFields.HIDE_TIME_TIP, false)
    }

    fun withBoolean(key: String, value: Boolean): RearBusinessExtraConfig {
        val next = values.deepCopy()
        next.addProperty(key, value)
        return RearBusinessExtraConfig(next)
    }
}

data class RearBusinessExtraConfigEntry(
    val business: String,
    val config: RearBusinessExtraConfig,
)

object RearBusinessExtraConfigRepository {
    private const val STORE_VERSION = 1
    private val gson: Gson = GsonBuilder().create()
    private val defaultConfig = RearBusinessExtraConfig(
        JsonObject().apply {
            addProperty(RearBusinessExtraConfigFields.HIDE_TIME_TIP, false)
        }
    )

    fun getAllConfigs(prefsManager: PrefsManager): List<RearBusinessExtraConfigEntry> {
        return loadAllNormalized(prefsManager)
            .map { (business, config) ->
                RearBusinessExtraConfigEntry(
                    business = business,
                    config = config,
                )
            }
            .sortedBy { it.business.lowercase() }
    }

    fun getConfigForBusiness(
        prefsManager: PrefsManager,
        business: String
    ): RearBusinessExtraConfig {
        val map = loadAllNormalized(prefsManager)
        return map[business.trim()] ?: normalizeConfig(RearBusinessExtraConfig())
    }

    fun saveConfigForBusiness(
        prefsManager: PrefsManager,
        business: String,
        config: RearBusinessExtraConfig,
    ) {
        val normalizedBusiness = business.trim()
        if (normalizedBusiness.isBlank()) return
        val map = loadAllNormalized(prefsManager).toMutableMap()
        map[normalizedBusiness] = config
        saveAll(prefsManager, map)
    }

    fun updateConfigForBusiness(
        prefsManager: PrefsManager,
        business: String,
        transform: (RearBusinessExtraConfig) -> RearBusinessExtraConfig,
    ): RearBusinessExtraConfig {
        val current = getConfigForBusiness(prefsManager, business)
        val updated = transform(current)
        saveConfigForBusiness(prefsManager, business, updated)
        return updated
    }

    fun renameBusiness(
        prefsManager: PrefsManager,
        oldBusiness: String,
        newBusiness: String,
    ) {
        val normalizedOld = oldBusiness.trim()
        val normalizedNew = newBusiness.trim()
        if (normalizedOld.isBlank() || normalizedNew.isBlank() || normalizedOld == normalizedNew) return

        val map = loadAllNormalized(prefsManager).toMutableMap()
        val config = map.remove(normalizedOld) ?: return
        map[normalizedNew] = config
        saveAll(prefsManager, map)
    }

    fun removeConfigForBusiness(prefsManager: PrefsManager, business: String) {
        val normalizedBusiness = business.trim()
        if (normalizedBusiness.isBlank()) return
        val map = loadAllNormalized(prefsManager).toMutableMap()
        if (map.remove(normalizedBusiness) != null) saveAll(prefsManager, map)
    }

    private fun loadAllNormalized(prefsManager: PrefsManager): Map<String, RearBusinessExtraConfig> {
        val raw = prefsManager.getString(ConfigKeys.REAR_WIDGET_BUSINESS_EXTRA_CONFIG_DATA, "")
        return parseStore(raw).configByBusiness
    }

    private fun saveAll(
        prefsManager: PrefsManager,
        configByBusiness: Map<String, RearBusinessExtraConfig>
    ) {
        prefsManager.putString(
            ConfigKeys.REAR_WIDGET_BUSINESS_EXTRA_CONFIG_DATA,
            encodeStore(configByBusiness),
        )
    }

    private data class ParsedStore(
        val configByBusiness: Map<String, RearBusinessExtraConfig>,
    )

    private data class StoreModel(
        @SerializedName("version")
        val version: Int = STORE_VERSION,
        @SerializedName("items")
        val items: List<ItemModel> = emptyList(),
    )

    private data class ItemModel(
        @SerializedName("business")
        val business: String? = null,
        @SerializedName("config")
        val config: JsonObject? = null,
    )

    private fun parseStore(raw: String?): ParsedStore {
        if (raw.isNullOrBlank()) {
            return ParsedStore(emptyMap())
        }

        val parsedModel = runCatching {
            gson.fromJson(raw, StoreModel::class.java)
        }.getOrNull() ?: return ParsedStore(emptyMap())

        val configByBusiness = linkedMapOf<String, RearBusinessExtraConfig>()
        parsedModel.items.forEach { item ->
            val business = (item.business).orEmpty().trim()
            if (business.isBlank()) return@forEach

            val config = normalizeConfig(RearBusinessExtraConfig(item.config ?: JsonObject()))
            configByBusiness[business] = config
        }

        return ParsedStore(configByBusiness)
    }

    private fun encodeStore(configByBusiness: Map<String, RearBusinessExtraConfig>): String {
        val model = StoreModel(
            version = STORE_VERSION,
            items = configByBusiness.map { (business, config) ->
                ItemModel(
                    business = business,
                    config = normalizeConfig(config).values,
                )
            }
        )
        return gson.toJson(model)
    }

    private fun normalizeConfig(config: RearBusinessExtraConfig): RearBusinessExtraConfig {
        val normalized = config.values.deepCopy()
        val defaults = defaultConfig.values
        defaults.entrySet().forEach { (key, value) ->
            if (!normalized.has(key) || normalized.get(key).isJsonNull) {
                normalized.add(key, value.deepCopy())
            }
        }
        return RearBusinessExtraConfig(normalized)
    }

    fun PrefsManager.getShowTimeTipForBusiness(business: String): Boolean {
        val normalizedBusiness = business.trim()
        if (normalizedBusiness.isBlank()) return true
        return loadAllNormalized(this)[normalizedBusiness]?.getShowTimeTipOrDefault() ?: true
    }

    fun PrefsManager.getExtraConfig(business: String) = getConfigForBusiness(this, business)
}
