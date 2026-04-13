package hk.uwu.reareye.repository.rearwidget

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
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
    @SerializedName("displayTitle")
    val displayTitle: String? = null,
    @SerializedName("contentDescription")
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
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("valueList")
    val valueList: List<String>,
    @SerializedName("editable")
    val editable: Boolean,
    @SerializedName("minLength")
    val minLength: Int,
    @SerializedName("maxLength")
    val maxLength: Int,
    @SerializedName("hint")
    val hint: String,
    @SerializedName("localizedHints")
    val localizedHints: Map<String, String>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "Text"
}

data class RearWidgetColorField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("values")
    val values: List<String>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "Color"
}

data class RearWidgetColorGroupItem(
    @SerializedName("name")
    val name: String,
    @SerializedName("values")
    val values: List<String>,
)

data class RearWidgetColorGroupField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("uiType")
    val uiType: Int,
    @SerializedName("items")
    val items: List<RearWidgetColorGroupItem>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "ColorGroup"
}

data class RearWidgetRangeField(
    @SerializedName("tagName")
    override val tagName: String,
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("defaultValue")
    val defaultValue: Int,
    @SerializedName("from")
    val from: Int,
    @SerializedName("to")
    val to: Int,
    @SerializedName("uiType")
    val uiType: Int = 0,
) : RearWidgetCardTemplateField

data class RearWidgetImageOption(
    @SerializedName("label")
    val label: String?,
    @SerializedName("value")
    val value: String,
)

data class RearWidgetImageSelectField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("values")
    val values: List<RearWidgetImageOption>,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int,
    @SerializedName("uiType")
    val uiType: Int,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "ImageSelect"
}

data class RearWidgetImagePickField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("options")
    val options: List<RearWidgetImageOption>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "ImagePick"
}

data class RearWidgetMultiImageOption(
    @SerializedName("label")
    val label: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("valueDark")
    val valueDark: String?,
    @SerializedName("contentDescription")
    val contentDescription: String?,
    @SerializedName("localizedConfigs")
    val localizedConfigs: Map<String, RearWidgetLocalizedConfig>,
)

data class RearWidgetMultiImageSelectField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("single")
    val single: Boolean,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int,
    @SerializedName("uiType")
    val uiType: Int,
    @SerializedName("shapeType")
    val shapeType: Int,
    @SerializedName("defaultSelection")
    val defaultSelection: List<Double>,
    @SerializedName("items")
    val items: List<RearWidgetMultiImageOption>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "MultiImageSelect"
}

data class RearWidgetDropDownOption(
    @SerializedName("label")
    val label: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("valueDark")
    val valueDark: String?,
    @SerializedName("contentDescription")
    val contentDescription: String?,
    @SerializedName("localizedConfigs")
    val localizedConfigs: Map<String, RearWidgetLocalizedConfig>,
)

data class RearWidgetDropDownField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("uiType")
    val uiType: Int,
    @SerializedName("defaultIndex")
    val defaultIndex: Int,
    @SerializedName("items")
    val items: List<RearWidgetDropDownOption>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "DropDown"
}

data class RearWidgetIntentExtra(
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: String,
)

data class RearWidgetIntentField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("action")
    val action: String?,
    @SerializedName("packageName")
    val packageName: String?,
    @SerializedName("className")
    val className: String?,
    @SerializedName("uri")
    val uri: String?,
    @SerializedName("flags")
    val flags: Int,
    @SerializedName("uiType")
    val uiType: Int,
    @SerializedName("returnValue")
    val returnValue: List<String>,
    @SerializedName("valueType")
    val valueType: List<String>,
    @SerializedName("defaultValue")
    val defaultValue: List<String>,
    @SerializedName("extras")
    val extras: List<RearWidgetIntentExtra>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "Intent"
}

data class RearWidgetDateField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("defaultDate")
    val defaultDate: Long,
    @SerializedName("repeatName")
    val repeatName: String,
    @SerializedName("repeatValue")
    val repeatValue: Int,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "SetDate"
}

data class RearWidgetOnOffField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("defaultOn")
    val defaultOn: Boolean,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "OnOff"
}

data class RearWidgetCustomColorField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("values")
    val values: List<String>,
    @SerializedName("defaultValue")
    val defaultValue: String,
    @SerializedName("index")
    val index: Int,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "CustomColor"
}

data class RearWidgetBackgroundModeField(
    @SerializedName("tagName")
    override val tagName: String,
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("value")
    val value: Int,
    @SerializedName("uriName")
    val uriName: String,
    @SerializedName("uri")
    val uri: String,
    @SerializedName("videoName")
    val videoName: String,
    @SerializedName("videoUri")
    val videoUri: String,
    @SerializedName("foregroundName")
    val foregroundName: String,
    @SerializedName("foregroundUri")
    val foregroundUri: String,
    @SerializedName("resType")
    val resType: Int,
) : RearWidgetCardTemplateField

data class RearWidgetMultiModeImageListItem(
    @SerializedName("value")
    val value: Int,
    @SerializedName("uri")
    val uri: String,
    @SerializedName("videoUri")
    val videoUri: String,
    @SerializedName("foregroundUri")
    val foregroundUri: String,
    @SerializedName("resType")
    val resType: Int,
)

data class RearWidgetMultiModeImageListField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String?,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String>,
    @SerializedName("group")
    override val group: Int,
    @SerializedName("size")
    val size: Int,
    @SerializedName("items")
    val items: List<RearWidgetMultiModeImageListItem>,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "MultiModeImageList"
}

data class RearWidgetAnimationField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String? = null,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String> = emptyMap(),
    @SerializedName("group")
    override val group: Int = 0,
    @SerializedName("x")
    val x: Double,
    @SerializedName("y")
    val y: Double,
    @SerializedName("scaleX")
    val scaleX: Double,
    @SerializedName("scaleY")
    val scaleY: Double,
    @SerializedName("rotation")
    val rotation: Double,
    @SerializedName("displayScale")
    val displayScale: Double,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "AnimatVar"
}

data class RearWidgetStringVarField(
    @SerializedName("name")
    override val name: String,
    @SerializedName("displayTitle")
    override val displayTitle: String? = null,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String> = emptyMap(),
    @SerializedName("group")
    override val group: Int = 0,
    @SerializedName("value")
    val value: String,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "StringVar"
}

data class RearWidgetCustomEditLinkField(
    @SerializedName("name")
    override val name: String = "customEditLocalId",
    @SerializedName("displayTitle")
    override val displayTitle: String? = null,
    @SerializedName("localizedTitles")
    override val localizedTitles: Map<String, String> = emptyMap(),
    @SerializedName("group")
    override val group: Int = 0,
    @SerializedName("deeplink")
    val deeplink: String,
) : RearWidgetCardTemplateField {
    @SerializedName("tagName")
    override val tagName: String = "CustomEditLink"
}

data class RearWidgetCardTemplateSchema(
    @SerializedName("sourcePath")
    val sourcePath: String,
    @SerializedName("items")
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
    @SerializedName("name")
    val name: String,
    @SerializedName("selectColors")
    val selectColors: Map<String, String>,
    @SerializedName("index")
    val index: Int,
)

data class RearWidgetIntentSaveConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("returnValueString")
    val returnValueString: Map<String, String> = emptyMap(),
    @SerializedName("returnValueNumber")
    val returnValueNumber: Map<String, Double> = emptyMap(),
)

data class RearWidgetMultiImageConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("selectImages")
    val selectImages: List<Double>,
)

data class RearWidgetDateSetSaveConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("date")
    val date: Long,
    @SerializedName("repeatName")
    val repeatName: String?,
    @SerializedName("repeatValue")
    val repeatValue: Int,
)

data class RearWidgetCustomColorSaveConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("index")
    val index: Int,
)

data class RearWidgetBackgroundModeSaveConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("value")
    val value: Int,
    @SerializedName("uriName")
    val uriName: String,
    @SerializedName("uri")
    val uri: String,
    @SerializedName("videoName")
    val videoName: String,
    @SerializedName("videoUri")
    val videoUri: String,
    @SerializedName("foregroundName")
    val foregroundName: String,
    @SerializedName("foregroundUri")
    val foregroundUri: String,
    @SerializedName("resType")
    val resType: Int,
)

data class RearWidgetMultiModeImageListSaveConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Int,
    @SerializedName("itemList")
    val itemList: List<RearWidgetBackgroundModeSaveConfig>?,
)

data class RearWidgetAnimationSaveConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("x")
    val x: Double,
    @SerializedName("y")
    val y: Double,
    @SerializedName("scaleX")
    val scaleX: Double,
    @SerializedName("scaleY")
    val scaleY: Double,
    @SerializedName("rotation")
    val rotation: Double,
    @SerializedName("displayScale")
    val displayScale: Double,
)

data class RearWidgetOneConfig(
    @SerializedName("textConfig")
    val textConfig: Map<String, String>? = null,
    @SerializedName("colorConfig")
    val colorConfig: Map<String, String>? = null,
    @SerializedName("colorGroupConfig")
    val colorGroupConfig: RearWidgetColorGroupSaveConfig? = null,
    @SerializedName("textSizeConfig")
    val textSizeConfig: Map<String, Int>? = null,
    @SerializedName("alignStyleConfig")
    val alignStyleConfig: Map<String, Int>? = null,
    @SerializedName("textFontConfig")
    val textFontConfig: Map<String, String>? = null,
    @SerializedName("imageConfig")
    val imageConfig: Map<String, String>? = null,
    @SerializedName("multiImageConfig")
    val multiImageConfig: RearWidgetMultiImageConfig? = null,
    @SerializedName("intentSaveConfig")
    @field:JsonAdapter(RearWidgetIntentSaveConfigMapAdapter::class)
    val intentSaveConfig: Map<String, RearWidgetIntentSaveConfig>? = null,
    @SerializedName("dateSetConfig")
    val dateSetConfig: RearWidgetDateSetSaveConfig? = null,
    @SerializedName("onOffConfig")
    val onOffConfig: Map<String, Boolean>? = null,
    @SerializedName("customColorSaveConfig")
    val customColorSaveConfig: RearWidgetCustomColorSaveConfig? = null,
    @SerializedName("backgroundModeSaveConfig")
    val backgroundModeSaveConfig: RearWidgetBackgroundModeSaveConfig? = null,
    @SerializedName("multiModeImageListSaveConfig")
    val multiModeImageListSaveConfig: RearWidgetMultiModeImageListSaveConfig? = null,
    @SerializedName("seekBarSaveConfig")
    val seekBarSaveConfig: Map<String, Int>? = null,
    @SerializedName("animationSaveConfig")
    val animationSaveConfig: RearWidgetAnimationSaveConfig? = null,
    @SerializedName("stringVarSaveConfig")
    val stringVarSaveConfig: Map<String, String>? = null,
    @SerializedName("dropDownSaveConfig")
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
