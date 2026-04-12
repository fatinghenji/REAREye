package hk.uwu.reareye.repository.rearwidget

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

const val REAR_WIDGET_CARD_ONE_CONFIG_JSON_KEY = "__reareye_card_one_config_json__"

data class RearWidgetLocalizedConfig(
    val displayTitle: String? = null,
    val contentDescription: String? = null,
)

sealed interface RearWidgetCardTemplateField {
    val tagName: String
    val name: String
    val displayTitle: String?
    val localizedTitles: Map<String, String>
    val group: Int
}

data class RearWidgetTextField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val valueList: List<String>,
    val editable: Boolean,
    val minLength: Int,
    val maxLength: Int,
    val hint: String,
    val localizedHints: Map<String, String>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "Text"
}

data class RearWidgetColorField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val values: List<String>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "Color"
}

data class RearWidgetColorGroupItem(
    val name: String,
    val values: List<String>,
)

data class RearWidgetColorGroupField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val uiType: Int,
    val items: List<RearWidgetColorGroupItem>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "ColorGroup"
}

data class RearWidgetRangeField(
    override val tagName: String,
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val defaultValue: Int,
    val from: Int,
    val to: Int,
    val uiType: Int = 0,
) : RearWidgetCardTemplateField

data class RearWidgetImageOption(
    val label: String?,
    val value: String,
)

data class RearWidgetImageSelectField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val values: List<RearWidgetImageOption>,
    val width: Int,
    val height: Int,
    val uiType: Int,
) : RearWidgetCardTemplateField {
    override val tagName: String = "ImageSelect"
}

data class RearWidgetImagePickField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val options: List<RearWidgetImageOption>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "ImagePick"
}

data class RearWidgetMultiImageOption(
    val label: String,
    val value: String,
    val valueDark: String?,
    val contentDescription: String?,
    val localizedConfigs: Map<String, RearWidgetLocalizedConfig>,
)

data class RearWidgetMultiImageSelectField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val single: Boolean,
    val width: Int,
    val height: Int,
    val uiType: Int,
    val shapeType: Int,
    val defaultSelection: List<Double>,
    val items: List<RearWidgetMultiImageOption>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "MultiImageSelect"
}

data class RearWidgetDropDownOption(
    val label: String,
    val value: String,
    val valueDark: String?,
    val contentDescription: String?,
    val localizedConfigs: Map<String, RearWidgetLocalizedConfig>,
)

data class RearWidgetDropDownField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val uiType: Int,
    val defaultIndex: Int,
    val items: List<RearWidgetDropDownOption>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "DropDown"
}

data class RearWidgetIntentExtra(
    val name: String,
    val type: String,
    val value: String,
)

data class RearWidgetIntentField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val action: String?,
    val packageName: String?,
    val className: String?,
    val uri: String?,
    val flags: Int,
    val uiType: Int,
    val returnValue: List<String>,
    val valueType: List<String>,
    val defaultValue: List<String>,
    val extras: List<RearWidgetIntentExtra>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "Intent"
}

data class RearWidgetDateField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val defaultDate: Long,
    val repeatName: String,
    val repeatValue: Int,
) : RearWidgetCardTemplateField {
    override val tagName: String = "SetDate"
}

data class RearWidgetOnOffField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val defaultOn: Boolean,
) : RearWidgetCardTemplateField {
    override val tagName: String = "OnOff"
}

data class RearWidgetCustomColorField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val values: List<String>,
    val defaultValue: String,
    val index: Int,
) : RearWidgetCardTemplateField {
    override val tagName: String = "CustomColor"
}

data class RearWidgetBackgroundModeField(
    override val tagName: String,
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val value: Int,
    val uriName: String,
    val uri: String,
    val videoName: String,
    val videoUri: String,
    val foregroundName: String,
    val foregroundUri: String,
    val resType: Int,
) : RearWidgetCardTemplateField

data class RearWidgetMultiModeImageListItem(
    val value: Int,
    val uri: String,
    val videoUri: String,
    val foregroundUri: String,
    val resType: Int,
)

data class RearWidgetMultiModeImageListField(
    override val name: String,
    override val displayTitle: String?,
    override val localizedTitles: Map<String, String>,
    override val group: Int,
    val size: Int,
    val items: List<RearWidgetMultiModeImageListItem>,
) : RearWidgetCardTemplateField {
    override val tagName: String = "MultiModeImageList"
}

data class RearWidgetAnimationField(
    override val name: String,
    override val displayTitle: String? = null,
    override val localizedTitles: Map<String, String> = emptyMap(),
    override val group: Int = 0,
    val x: Double,
    val y: Double,
    val scaleX: Double,
    val scaleY: Double,
    val rotation: Double,
    val displayScale: Double,
) : RearWidgetCardTemplateField {
    override val tagName: String = "AnimatVar"
}

data class RearWidgetStringVarField(
    override val name: String,
    override val displayTitle: String? = null,
    override val localizedTitles: Map<String, String> = emptyMap(),
    override val group: Int = 0,
    val value: String,
) : RearWidgetCardTemplateField {
    override val tagName: String = "StringVar"
}

data class RearWidgetCustomEditLinkField(
    override val name: String = "customEditLocalId",
    override val displayTitle: String? = null,
    override val localizedTitles: Map<String, String> = emptyMap(),
    override val group: Int = 0,
    val deeplink: String,
) : RearWidgetCardTemplateField {
    override val tagName: String = "CustomEditLink"
}

data class RearWidgetCardTemplateSchema(
    val sourcePath: String,
    val items: List<RearWidgetCardTemplateField>,
) {
    val editableItemCount: Int
        get() = items.count { it !is RearWidgetCustomEditLinkField }

    fun groupItems(): List<Pair<Int, List<RearWidgetCardTemplateField>>> {
        val grouped = linkedMapOf<Int, MutableList<RearWidgetCardTemplateField>>()
        items.forEach { item ->
            grouped.getOrPut(item.group) { mutableListOf() }.add(item)
        }
        return grouped.entries.map { it.key to it.value.toList() }
    }
}

class RearWidgetCardTemplateFieldJsonAdapter :
    JsonSerializer<RearWidgetCardTemplateField>,
    JsonDeserializer<RearWidgetCardTemplateField> {

    override fun serialize(
        src: RearWidgetCardTemplateField?,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        if (src == null) return JsonObject()
        return context.serialize(src, src.javaClass)
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): RearWidgetCardTemplateField {
        val obj = json?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw JsonParseException("RearWidgetCardTemplateField json is not an object")
        val targetClass = when (val tagName = obj.get("tagName")?.asString?.trim().orEmpty()) {
            "Text" -> RearWidgetTextField::class.java
            "Color" -> RearWidgetColorField::class.java
            "ColorGroup" -> RearWidgetColorGroupField::class.java
            "FontSize", "SeekBar", "Align" -> RearWidgetRangeField::class.java
            "ImageSelect" -> RearWidgetImageSelectField::class.java
            "ImagePick" -> RearWidgetImagePickField::class.java
            "MultiImageSelect" -> RearWidgetMultiImageSelectField::class.java
            "DropDown" -> RearWidgetDropDownField::class.java
            "Intent" -> RearWidgetIntentField::class.java
            "SetDate" -> RearWidgetDateField::class.java
            "OnOff" -> RearWidgetOnOffField::class.java
            "CustomColor" -> RearWidgetCustomColorField::class.java
            "BackgroudMode", "MultiModeImage" -> RearWidgetBackgroundModeField::class.java
            "MultiModeImageList" -> RearWidgetMultiModeImageListField::class.java
            "AnimatVar" -> RearWidgetAnimationField::class.java
            "StringVar" -> RearWidgetStringVarField::class.java
            "CustomEditLink" -> RearWidgetCustomEditLinkField::class.java
            else -> throw JsonParseException("Unknown RearWidgetCardTemplateField tagName=$tagName")
        }
        return context.deserialize(obj, targetClass)
    }
}

data class RearWidgetColorGroupSaveConfig(
    val name: String,
    val selectColors: Map<String, String>,
    val index: Int,
)

data class RearWidgetIntentSaveConfig(
    val name: String,
    val returnValueString: Map<String, String> = emptyMap(),
    val returnValueNumber: Map<String, Double> = emptyMap(),
)

data class RearWidgetMultiImageConfig(
    val name: String,
    val selectImages: List<Double>,
)

data class RearWidgetDateSetSaveConfig(
    val name: String,
    val date: Long,
    val repeatName: String?,
    val repeatValue: Int,
)

data class RearWidgetCustomColorSaveConfig(
    val name: String,
    val value: String,
    val index: Int,
)

data class RearWidgetBackgroundModeSaveConfig(
    val name: String,
    val value: Int,
    val uriName: String,
    val uri: String,
    val videoName: String,
    val videoUri: String,
    val foregroundName: String,
    val foregroundUri: String,
    val resType: Int,
)

data class RearWidgetMultiModeImageListSaveConfig(
    val name: String,
    val size: Int,
    val itemList: List<RearWidgetBackgroundModeSaveConfig>?,
)

data class RearWidgetAnimationSaveConfig(
    val name: String,
    val x: Double,
    val y: Double,
    val scaleX: Double,
    val scaleY: Double,
    val rotation: Double,
    val displayScale: Double,
)

data class RearWidgetOneConfig(
    val textConfig: Map<String, String>? = null,
    val colorConfig: Map<String, String>? = null,
    val colorGroupConfig: RearWidgetColorGroupSaveConfig? = null,
    val textSizeConfig: Map<String, Int>? = null,
    val alignStyleConfig: Map<String, Int>? = null,
    val textFontConfig: Map<String, String>? = null,
    val imageConfig: Map<String, String>? = null,
    val multiImageConfig: RearWidgetMultiImageConfig? = null,
    @field:JsonAdapter(RearWidgetIntentSaveConfigMapAdapter::class)
    val intentSaveConfig: Map<String, RearWidgetIntentSaveConfig>? = null,
    val dateSetConfig: RearWidgetDateSetSaveConfig? = null,
    val onOffConfig: Map<String, Boolean>? = null,
    val customColorSaveConfig: RearWidgetCustomColorSaveConfig? = null,
    val backgroundModeSaveConfig: RearWidgetBackgroundModeSaveConfig? = null,
    val multiModeImageListSaveConfig: RearWidgetMultiModeImageListSaveConfig? = null,
    val seekBarSaveConfig: Map<String, Int>? = null,
    val animationSaveConfig: RearWidgetAnimationSaveConfig? = null,
    val stringVarSaveConfig: Map<String, String>? = null,
    val dropDownSaveConfig: Map<String, String>? = null,
)

class RearWidgetIntentSaveConfigMapAdapter :
    JsonSerializer<Map<String, RearWidgetIntentSaveConfig>>,
    JsonDeserializer<Map<String, RearWidgetIntentSaveConfig>> {

    override fun serialize(
        src: Map<String, RearWidgetIntentSaveConfig>?,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        if (src.isNullOrEmpty()) return JsonObject()
        if (src.size == 1) return context.serialize(src.values.first())

        val out = JsonObject()
        src.forEach { (key, value) ->
            out.add(key, context.serialize(value))
        }
        return out
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): Map<String, RearWidgetIntentSaveConfig> {
        val obj = json?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        if (obj.has("name")) {
            val config = context.deserialize<RearWidgetIntentSaveConfig>(
                obj,
                RearWidgetIntentSaveConfig::class.java,
            ) ?: return emptyMap()
            return mapOf(config.name to config)
        }

        val out = linkedMapOf<String, RearWidgetIntentSaveConfig>()
        obj.entrySet().forEach { (key, value) ->
            val config = context.deserialize<RearWidgetIntentSaveConfig>(
                value,
                RearWidgetIntentSaveConfig::class.java,
            ) ?: return@forEach
            out[key] = config
        }
        return out
    }
}

fun RearWidgetCardTemplateField.resolvedTitle(locale: Locale = Locale.getDefault()): String {
    localeLookupKeys(locale).forEach { key ->
        localizedTitles[key]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return displayTitle?.takeIf { it.isNotBlank() } ?: name
}

fun RearWidgetTextField.resolvedHint(locale: Locale = Locale.getDefault()): String {
    localeLookupKeys(locale).forEach { key ->
        localizedHints[key]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return hint
}

fun RearWidgetMultiImageOption.resolvedLabel(locale: Locale = Locale.getDefault()): String {
    localeLookupKeys(locale).forEach { key ->
        val localized = localizedConfigs[key]
        localized?.displayTitle?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return label.ifBlank { value }
}

fun RearWidgetDropDownOption.resolvedLabel(locale: Locale = Locale.getDefault()): String {
    localeLookupKeys(locale).forEach { key ->
        val localized = localizedConfigs[key]
        localized?.displayTitle?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return label.ifBlank { value }
}

fun RearWidgetOneConfig.merge(overrides: RearWidgetOneConfig?): RearWidgetOneConfig {
    if (overrides == null) return this
    return copy(
        textConfig = mergedMap(textConfig, overrides.textConfig),
        colorConfig = mergedMap(colorConfig, overrides.colorConfig),
        colorGroupConfig = overrides.colorGroupConfig ?: colorGroupConfig,
        textSizeConfig = mergedMap(textSizeConfig, overrides.textSizeConfig),
        alignStyleConfig = mergedMap(alignStyleConfig, overrides.alignStyleConfig),
        textFontConfig = mergedMap(textFontConfig, overrides.textFontConfig),
        imageConfig = mergedMap(imageConfig, overrides.imageConfig),
        multiImageConfig = overrides.multiImageConfig ?: multiImageConfig,
        intentSaveConfig = mergedMap(intentSaveConfig, overrides.intentSaveConfig),
        dateSetConfig = overrides.dateSetConfig ?: dateSetConfig,
        onOffConfig = mergedMap(onOffConfig, overrides.onOffConfig),
        customColorSaveConfig = overrides.customColorSaveConfig ?: customColorSaveConfig,
        backgroundModeSaveConfig = overrides.backgroundModeSaveConfig ?: backgroundModeSaveConfig,
        multiModeImageListSaveConfig = overrides.multiModeImageListSaveConfig
            ?: multiModeImageListSaveConfig,
        seekBarSaveConfig = mergedMap(seekBarSaveConfig, overrides.seekBarSaveConfig),
        animationSaveConfig = overrides.animationSaveConfig ?: animationSaveConfig,
        stringVarSaveConfig = mergedMap(stringVarSaveConfig, overrides.stringVarSaveConfig),
        dropDownSaveConfig = mergedMap(dropDownSaveConfig, overrides.dropDownSaveConfig),
    )
}

private fun <T> mergedMap(
    base: Map<String, T>?,
    overrides: Map<String, T>?,
): Map<String, T>? {
    if (base.isNullOrEmpty()) return overrides?.takeIf { it.isNotEmpty() }
    if (overrides.isNullOrEmpty()) return base
    return LinkedHashMap<String, T>(base.size + overrides.size).apply {
        putAll(base)
        putAll(overrides)
    }
}

private fun localeLookupKeys(locale: Locale): List<String> {
    val language = locale.language.takeIf { it.isNotBlank() }
    val country = locale.country.takeIf { it.isNotBlank() }
    val tag = locale.toLanguageTag().replace('-', '_')
    return buildList {
        if (tag.isNotBlank()) add(tag)
        if (language != null && country != null) add("${language}_${country}")
        if (language != null) add(language)
    }.distinct()
}

fun parseDateStringToMillis(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    return runCatching {
        LocalDate.parse(value, dateFormatter)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

fun formatDateMillis(value: Long): String {
    return runCatching {
        Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dateFormatter)
    }.getOrDefault("")
}

fun isValidDateString(raw: String): Boolean {
    return try {
        LocalDate.parse(raw.trim(), dateFormatter)
        true
    } catch (_: DateTimeParseException) {
        false
    }
}

fun RearWidgetOneConfig.putTextValue(name: String, value: String): RearWidgetOneConfig {
    return copy(textConfig = textConfig.updated(name, value))
}

fun RearWidgetOneConfig.putColorValue(name: String, value: String): RearWidgetOneConfig {
    return copy(colorConfig = colorConfig.updated(name, value))
}

fun RearWidgetOneConfig.putTextSizeValue(name: String, value: Int): RearWidgetOneConfig {
    return copy(textSizeConfig = textSizeConfig.updated(name, value))
}

fun RearWidgetOneConfig.putAlignValue(name: String, value: Int): RearWidgetOneConfig {
    return copy(alignStyleConfig = alignStyleConfig.updated(name, value))
}

fun RearWidgetOneConfig.putImageValue(name: String, value: String): RearWidgetOneConfig {
    return copy(imageConfig = imageConfig.updated(name, value))
}

fun RearWidgetOneConfig.putSeekBarValue(name: String, value: Int): RearWidgetOneConfig {
    return copy(seekBarSaveConfig = seekBarSaveConfig.updated(name, value))
}

fun RearWidgetOneConfig.putOnOffValue(name: String, value: Boolean): RearWidgetOneConfig {
    return copy(onOffConfig = onOffConfig.updated(name, value))
}

fun RearWidgetOneConfig.putStringVarValue(name: String, value: String): RearWidgetOneConfig {
    return copy(stringVarSaveConfig = stringVarSaveConfig.updated(name, value))
}

fun RearWidgetOneConfig.putIntentConfig(
    name: String,
    config: RearWidgetIntentSaveConfig,
): RearWidgetOneConfig {
    return copy(intentSaveConfig = intentSaveConfig.updated(name, config))
}

fun RearWidgetOneConfig.putDropDownValue(name: String, value: String): RearWidgetOneConfig {
    return copy(dropDownSaveConfig = dropDownSaveConfig.updated(name, value))
}

fun RearWidgetOneConfig.textValue(name: String): String? = textConfig?.get(name)

fun RearWidgetOneConfig.colorValue(name: String): String? = colorConfig?.get(name)

fun RearWidgetOneConfig.textSizeValue(name: String): Int? = textSizeConfig?.get(name)

fun RearWidgetOneConfig.alignValue(name: String): Int? = alignStyleConfig?.get(name)

fun RearWidgetOneConfig.imageValue(name: String): String? = imageConfig?.get(name)

fun RearWidgetOneConfig.seekBarValue(name: String): Int? = seekBarSaveConfig?.get(name)

fun RearWidgetOneConfig.onOffValue(name: String): Boolean? = onOffConfig?.get(name)

fun RearWidgetOneConfig.stringVarValue(name: String): String? = stringVarSaveConfig?.get(name)

fun RearWidgetOneConfig.dropDownValue(name: String): String? = dropDownSaveConfig?.get(name)

private fun <T> Map<String, T>?.updated(name: String, value: T): Map<String, T> {
    return LinkedHashMap(this.orEmpty()).apply {
        put(name, value)
    }
}
