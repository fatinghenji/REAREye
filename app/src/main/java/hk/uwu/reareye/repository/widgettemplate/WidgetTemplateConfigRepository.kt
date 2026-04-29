package hk.uwu.reareye.repository.widgettemplate

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import hk.uwu.reareye.repository.rearwidget.RearWidgetAnimationField
import hk.uwu.reareye.repository.rearwidget.RearWidgetAnimationSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetBackgroundModeField
import hk.uwu.reareye.repository.rearwidget.RearWidgetBackgroundModeSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetColorField
import hk.uwu.reareye.repository.rearwidget.RearWidgetColorGroupField
import hk.uwu.reareye.repository.rearwidget.RearWidgetColorGroupItem
import hk.uwu.reareye.repository.rearwidget.RearWidgetColorGroupSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetCustomColorField
import hk.uwu.reareye.repository.rearwidget.RearWidgetCustomColorSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetCustomEditLinkField
import hk.uwu.reareye.repository.rearwidget.RearWidgetDateField
import hk.uwu.reareye.repository.rearwidget.RearWidgetDateSetSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetDropDownField
import hk.uwu.reareye.repository.rearwidget.RearWidgetDropDownOption
import hk.uwu.reareye.repository.rearwidget.RearWidgetImageOption
import hk.uwu.reareye.repository.rearwidget.RearWidgetImagePickField
import hk.uwu.reareye.repository.rearwidget.RearWidgetImageSelectField
import hk.uwu.reareye.repository.rearwidget.RearWidgetIntentExtra
import hk.uwu.reareye.repository.rearwidget.RearWidgetIntentField
import hk.uwu.reareye.repository.rearwidget.RearWidgetIntentSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetLocalizedConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetMultiImageConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetMultiImageOption
import hk.uwu.reareye.repository.rearwidget.RearWidgetMultiImageSelectField
import hk.uwu.reareye.repository.rearwidget.RearWidgetMultiModeImageListField
import hk.uwu.reareye.repository.rearwidget.RearWidgetMultiModeImageListItem
import hk.uwu.reareye.repository.rearwidget.RearWidgetMultiModeImageListSaveConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetOnOffField
import hk.uwu.reareye.repository.rearwidget.RearWidgetOneConfig
import hk.uwu.reareye.repository.rearwidget.RearWidgetRangeField
import hk.uwu.reareye.repository.rearwidget.RearWidgetStringVarField
import hk.uwu.reareye.repository.rearwidget.RearWidgetTextField
import hk.uwu.reareye.repository.rearwidget.merge
import hk.uwu.reareye.repository.rearwidget.parseDateStringToMillis
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object WidgetTemplateConfigRepository {
    private const val VAR_CONFIG_NAME = "var_config.xml"

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(
            WidgetTemplateField::class.java,
            WidgetTemplateFieldJsonAdapter(),
        )
        .create()

    fun loadSchema(filePath: String): WidgetTemplateSchema? {
        val file = File(filePath)
        if (!file.exists()) return null

        if (file.isDirectory) {
            val varConfigFile = File(file, VAR_CONFIG_NAME)
            if (!varConfigFile.exists() || !varConfigFile.isFile) return null
            return runCatching {
                varConfigFile.inputStream().use { parseSchema(it, file.absolutePath) }
            }.getOrNull()
        }

        return runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(VAR_CONFIG_NAME) ?: return null
                zip.getInputStream(entry).use { parseSchema(it, file.absolutePath) }
            }
        }.getOrNull()
    }

    fun decodeOneConfig(raw: String?): WidgetTemplateOneConfig? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return runCatching {
            gson.fromJson(normalized, WidgetTemplateOneConfig::class.java)
        }.getOrNull()
    }

    fun decodeSchema(raw: String?): WidgetTemplateSchema? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return runCatching {
            gson.fromJson(normalized, WidgetTemplateSchema::class.java)
        }.getOrNull()
    }

    fun encodeOneConfig(config: WidgetTemplateOneConfig): String = gson.toJson(config)

    fun encodeSchema(schema: WidgetTemplateSchema): String = gson.toJson(schema)

    fun imagePreviewValues(schema: WidgetTemplateSchema): List<String> {
        return buildList {
            schema.items.forEach { item ->
                when (item) {
                    is RearWidgetImageSelectField -> item.values.forEach { add(it.value) }
                    is RearWidgetImagePickField -> item.options.forEach { add(it.value) }
                    is RearWidgetMultiImageSelectField -> item.items.forEach {
                        add(it.value)
                        it.valueDark?.let(::add)
                    }

                    is RearWidgetBackgroundModeField -> {
                        add(item.uri)
                        add(item.videoUri)
                        add(item.foregroundUri)
                    }

                    is RearWidgetMultiModeImageListField -> item.items.forEach {
                        add(it.uri)
                        add(it.videoUri)
                        add(it.foregroundUri)
                    }

                    else -> Unit
                }
            }
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun buildInitialOneConfig(
        schema: WidgetTemplateSchema,
        existingJson: String?,
    ): WidgetTemplateOneConfig {
        return buildDefaultOneConfig(schema).merge(decodeOneConfig(existingJson))
    }

    private fun parseSchema(
        inputStream: InputStream,
        sourcePath: String,
    ): WidgetTemplateSchema? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
            isCoalescing = true
        }
        val root = factory.newDocumentBuilder().parse(inputStream).documentElement ?: return null
        if (root.tagName != "WidgetConfig") return null

        val items = buildList {
            root.childElements().forEach { element ->
                parseField(element)?.let(::add)
            }
        }
        return WidgetTemplateSchema(sourcePath = sourcePath, items = items)
    }

    private fun parseField(element: Element): WidgetTemplateField? {
        return when (element.tagName) {
            "Text" -> parseTextField(element)
            "Color" -> parseColorField(element)
            "ColorGroup" -> parseColorGroupField(element)
            "FontSize" -> parseRangeField(element, "FontSize")
            "SeekBar" -> parseRangeField(element, "SeekBar")
            "Align" -> parseRangeField(element, "Align")
            "ImageSelect" -> parseImageSelectField(element)
            "ImagePick" -> parseImagePickField(element)
            "MultiImageSelect" -> parseMultiImageSelectField(element)
            "DropDown" -> parseDropDownField(element)
            "Intent" -> parseIntentField(element)
            "SetDate" -> parseDateField(element)
            "OnOff" -> parseOnOffField(element)
            "CustomColor" -> parseCustomColorField(element)
            "BackgroudMode" -> parseBackgroundModeField(element, "BackgroudMode")
            "MultiModeImage" -> parseBackgroundModeField(element, "MultiModeImage")
            "MultiModeImageList" -> parseMultiModeImageListField(element)
            "AnimatVar" -> parseAnimationField(element)
            "StringVar" -> parseStringVarField(element)
            "CustomEditLink" -> parseCustomEditLinkField(element)
            else -> null
        }
    }

    private fun parseTextField(element: Element): RearWidgetTextField {
        return RearWidgetTextField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            valueList = element.childElements()
                .filterNot { it.tagName == "Language" || it.tagName == "LanguageHint" }
                .mapNotNull { it.textContent?.trim()?.ifBlank { null } },
            editable = element.optionalAttr("editable") == "true",
            minLength = element.intAttr("minLength"),
            maxLength = element.intAttr("maxLength"),
            hint = element.optionalAttr("hint").orEmpty(),
            localizedHints = element.readHintTitles(),
        )
    }

    private fun parseColorField(element: Element): RearWidgetColorField {
        return RearWidgetColorField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            values = element.childElements("item")
                .mapNotNull { it.textContent?.trim()?.ifBlank { null } },
        )
    }

    private fun parseColorGroupField(element: Element): RearWidgetColorGroupField {
        return RearWidgetColorGroupField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            uiType = element.intAttr("uiType"),
            items = element.childElements("item").mapNotNull { child ->
                val name = child.optionalAttr("name")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val values = (child.optionalAttr("values") ?: child.optionalAttr("groups"))
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                RearWidgetColorGroupItem(name = name, values = values)
            },
        )
    }

    private fun parseRangeField(
        element: Element,
        tagName: String,
    ): RearWidgetRangeField {
        return RearWidgetRangeField(
            tagName = tagName,
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            defaultValue = element.intAttr("default"),
            from = element.intAttr("from"),
            to = element.intAttr("to"),
            uiType = element.intAttr("uiType"),
        )
    }

    private fun parseImageSelectField(element: Element): RearWidgetImageSelectField {
        return RearWidgetImageSelectField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            values = element.childElements("item").mapNotNull { item ->
                val value = item.textContent?.trim().orEmpty()
                if (value.isBlank()) return@mapNotNull null
                RearWidgetImageOption(label = item.optionalAttr("displayTitle"), value = value)
            },
            width = element.intAttr("width", 300),
            height = element.intAttr("height", 200),
            uiType = element.intAttr("uiType"),
        )
    }

    private fun parseImagePickField(element: Element): RearWidgetImagePickField {
        return RearWidgetImagePickField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            options = element.childElements("item").mapNotNull { item ->
                val value = item.textContent?.trim().orEmpty()
                if (value.isBlank()) return@mapNotNull null
                RearWidgetImageOption(label = item.optionalAttr("displayTitle"), value = value)
            },
        )
    }

    private fun parseMultiImageSelectField(element: Element): RearWidgetMultiImageSelectField {
        return RearWidgetMultiImageSelectField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            single = element.optionalAttr("single") == "true",
            width = element.intAttr("width", 300),
            height = element.intAttr("height", 200),
            uiType = element.intAttr("uiType"),
            shapeType = element.intAttr("shapeType"),
            defaultSelection = element.doubleListAttr("default"),
            items = element.childElements("item").map { item ->
                RearWidgetMultiImageOption(
                    label = item.optionalAttr("displayTitle").orEmpty(),
                    value = item.optionalAttr("value").orEmpty(),
                    valueDark = item.optionalAttr("valueDark"),
                    contentDescription = item.optionalAttr("contentDescription"),
                    localizedConfigs = item.readLocalizedConfigs(),
                )
            },
        )
    }

    private fun parseDropDownField(element: Element): RearWidgetDropDownField {
        return RearWidgetDropDownField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            uiType = element.intAttr("uiType"),
            defaultIndex = element.intAttr("default"),
            items = element.childElements("item").map { item ->
                RearWidgetDropDownOption(
                    label = item.optionalAttr("displayTitle").orEmpty(),
                    value = item.optionalAttr("value").orEmpty(),
                    valueDark = item.optionalAttr("valueDark"),
                    contentDescription = item.optionalAttr("contentDescription"),
                    localizedConfigs = item.readLocalizedConfigs(),
                )
            },
        )
    }

    private fun parseIntentField(element: Element): RearWidgetIntentField {
        return RearWidgetIntentField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            action = element.optionalAttr("action"),
            packageName = element.optionalAttr("package"),
            className = element.optionalAttr("class"),
            uri = element.optionalAttr("uri"),
            flags = element.intAttr("flags", -1),
            uiType = element.intAttr("uiType"),
            returnValue = element.csvAttr("returnValue"),
            valueType = element.csvAttr("valueType"),
            defaultValue = element.csvAttr("defaultValue"),
            extras = element.childElements("Extra").mapNotNull { child ->
                val name = child.optionalAttr("name")?.trim().orEmpty()
                val type = child.optionalAttr("type")?.trim().orEmpty()
                val value = child.optionalAttr("value")?.trim().orEmpty()
                if (name.isBlank() || type.isBlank() || value.isBlank()) return@mapNotNull null
                RearWidgetIntentExtra(name = name, type = type, value = value)
            },
        )
    }

    private fun parseDateField(element: Element): RearWidgetDateField {
        return RearWidgetDateField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            defaultDate = parseDateStringToMillis(element.optionalAttr("default"))
                ?: System.currentTimeMillis(),
            repeatName = element.optionalAttr("repeatVar").orEmpty(),
            repeatValue = element.intAttr("repeat"),
        )
    }

    private fun parseOnOffField(element: Element): RearWidgetOnOffField {
        val raw = element.optionalAttr("default") ?: "1"
        return RearWidgetOnOffField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            defaultOn = raw == "1" || raw.equals("true", ignoreCase = true),
        )
    }

    private fun parseCustomColorField(element: Element): RearWidgetCustomColorField {
        return RearWidgetCustomColorField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            values = element.childElements("item")
                .mapNotNull { it.textContent?.trim()?.ifBlank { null } },
            defaultValue = element.optionalAttr("default") ?: "auto",
            index = element.intAttr("index"),
        )
    }

    private fun parseBackgroundModeField(
        element: Element,
        tagName: String,
    ): RearWidgetBackgroundModeField {
        return RearWidgetBackgroundModeField(
            tagName = tagName,
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = element.readLanguageTitles(),
            group = element.intAttr("group"),
            value = element.intAttr("value"),
            uriName = element.optionalAttr("uriName").orEmpty(),
            uri = element.optionalAttr("uri").orEmpty(),
            videoName = element.optionalAttr("videoName").orEmpty(),
            videoUri = element.optionalAttr("videoUri").orEmpty(),
            foregroundName = element.optionalAttr("foregroundName").orEmpty(),
            foregroundUri = element.optionalAttr("foregroundUri").orEmpty(),
            resType = element.intAttr("resType"),
        )
    }

    private fun parseMultiModeImageListField(element: Element): RearWidgetMultiModeImageListField {
        return RearWidgetMultiModeImageListField(
            name = element.requiredAttr("name"),
            displayTitle = element.optionalAttr("displayTitle"),
            localizedTitles = emptyMap(),
            group = element.intAttr("group"),
            size = element.intAttr("size"),
            items = element.childElements("item").map { item ->
                RearWidgetMultiModeImageListItem(
                    value = item.intAttr("value"),
                    uri = item.optionalAttr("uri").orEmpty(),
                    videoUri = item.optionalAttr("videoUri").orEmpty(),
                    foregroundUri = item.optionalAttr("foregroundUri").orEmpty(),
                    resType = item.intAttr("resType"),
                )
            },
        )
    }

    private fun parseAnimationField(element: Element): RearWidgetAnimationField {
        return RearWidgetAnimationField(
            name = element.requiredAttr("name"),
            x = element.doubleAttr("x"),
            y = element.doubleAttr("y"),
            scaleX = element.doubleAttr("scaleX", 1.0),
            scaleY = element.doubleAttr("scaleY", 1.0),
            rotation = element.doubleAttr("rotation"),
            displayScale = element.doubleAttr("displayScale", 1.0),
        )
    }

    private fun parseStringVarField(element: Element): RearWidgetStringVarField {
        return RearWidgetStringVarField(
            name = element.requiredAttr("name"),
            value = element.optionalAttr("value").orEmpty(),
        )
    }

    private fun parseCustomEditLinkField(element: Element): RearWidgetCustomEditLinkField? {
        val deeplink = element.optionalAttr("deeplink")?.trim().orEmpty()
        if (deeplink.isBlank()) return null
        return RearWidgetCustomEditLinkField(deeplink = deeplink)
    }

    private fun buildDefaultOneConfig(schema: WidgetTemplateSchema): WidgetTemplateOneConfig {
        var config = WidgetTemplateOneConfig()
        schema.items.forEach { item ->
            config = when (item) {
                is RearWidgetTextField -> {
                    val default = item.valueList.firstOrNull().orEmpty()
                    if (default.isBlank() && !item.editable) config else config.putText(
                        item.name,
                        default
                    )
                }

                is RearWidgetColorField -> item.values.firstOrNull()?.let {
                    config.putColor(item.name, it)
                } ?: config

                is RearWidgetColorGroupField -> {
                    val colors = linkedMapOf<String, String>()
                    item.items.forEach { colorItem ->
                        colorItem.values.firstOrNull()?.let { colors[colorItem.name] = it }
                    }
                    if (colors.isEmpty()) config else config.copy(
                        colorGroupConfig = RearWidgetColorGroupSaveConfig(
                            name = item.name,
                            selectColors = colors,
                            index = 0,
                        )
                    )
                }

                is RearWidgetRangeField -> when (item.tagName) {
                    "FontSize" -> config.putTextSize(item.name, item.defaultValue)
                    "Align" -> config.putAlign(item.name, item.defaultValue)
                    else -> config.putSeekBar(item.name, item.defaultValue)
                }

                is RearWidgetImageSelectField -> item.values.firstOrNull()?.let {
                    config.putImage(item.name, it.value)
                } ?: config

                is RearWidgetImagePickField -> config.putImage(
                    item.name,
                    item.options.firstOrNull()?.value.orEmpty(),
                )

                is RearWidgetMultiImageSelectField -> {
                    val defaultSelection = if (item.defaultSelection.isNotEmpty()) {
                        item.defaultSelection
                    } else if (item.items.isNotEmpty()) {
                        List(item.items.size) { index -> if (index == 0) 1.0 else 0.0 }
                    } else {
                        emptyList()
                    }
                    if (defaultSelection.isEmpty()) config else config.copy(
                        multiImageConfig = RearWidgetMultiImageConfig(
                            name = item.name,
                            selectImages = defaultSelection,
                        )
                    )
                }

                is RearWidgetDropDownField -> {
                    val selected = item.items
                        .getOrNull(item.defaultIndex.coerceAtLeast(0))
                        ?.value
                        ?: item.items.firstOrNull()?.value
                    if (selected.isNullOrBlank()) config else config.putDropDown(
                        item.name,
                        selected
                    )
                }

                is RearWidgetIntentField -> {
                    val stringValues = linkedMapOf<String, String>()
                    val numberValues = linkedMapOf<String, Double>()
                    item.returnValue.forEachIndexed { index, key ->
                        val type = item.valueType.getOrNull(index)?.trim().orEmpty()
                        val value = item.defaultValue.getOrNull(index)?.trim().orEmpty()
                        if (key.isBlank()) return@forEachIndexed
                        if (type.isNumericLikeType()) {
                            parseNumericIntentValue(type, value)?.let { numberValues[key] = it }
                        } else if (type.equals("boolean", ignoreCase = true)) {
                            numberValues[key] =
                                if (value == "1" || value.equals("true", true)) 1.0 else 0.0
                        } else {
                            stringValues[key] = value
                        }
                    }
                    if (stringValues.isEmpty() && numberValues.isEmpty()) {
                        config
                    } else {
                        config.copy(
                            intentSaveConfig = mapOf(
                                item.name to RearWidgetIntentSaveConfig(
                                    name = item.name,
                                    returnValueString = stringValues,
                                    returnValueNumber = numberValues,
                                )
                            )
                        )
                    }
                }

                is RearWidgetDateField -> config.copy(
                    dateSetConfig = RearWidgetDateSetSaveConfig(
                        name = item.name,
                        date = item.defaultDate,
                        repeatName = item.repeatName,
                        repeatValue = item.repeatValue,
                    )
                )

                is RearWidgetOnOffField -> config.putOnOff(item.name, item.defaultOn)

                is RearWidgetCustomColorField -> {
                    val default = item.defaultValue.ifBlank { item.values.firstOrNull().orEmpty() }
                    if (default.isBlank()) config else config.copy(
                        customColorSaveConfig = RearWidgetCustomColorSaveConfig(
                            name = item.name,
                            value = default,
                            index = item.index,
                        )
                    )
                }

                is RearWidgetBackgroundModeField -> config.copy(
                    backgroundModeSaveConfig = RearWidgetBackgroundModeSaveConfig(
                        name = item.name,
                        value = item.value,
                        uriName = item.uriName,
                        uri = item.uri,
                        videoName = item.videoName,
                        videoUri = item.videoUri,
                        foregroundName = item.foregroundName,
                        foregroundUri = item.foregroundUri,
                        resType = item.resType,
                    )
                )

                is RearWidgetMultiModeImageListField -> config.copy(
                    multiModeImageListSaveConfig = RearWidgetMultiModeImageListSaveConfig(
                        name = item.name,
                        size = item.size,
                        itemList = item.items.map { child ->
                            RearWidgetBackgroundModeSaveConfig(
                                name = "",
                                value = child.value,
                                uriName = "",
                                uri = child.uri,
                                videoName = "",
                                videoUri = child.videoUri,
                                foregroundName = "",
                                foregroundUri = child.foregroundUri,
                                resType = child.resType,
                            )
                        }
                    )
                )

                is RearWidgetAnimationField -> config.copy(
                    animationSaveConfig = RearWidgetAnimationSaveConfig(
                        name = item.name,
                        x = item.x,
                        y = item.y,
                        scaleX = item.scaleX,
                        scaleY = item.scaleY,
                        rotation = item.rotation,
                        displayScale = item.displayScale,
                    )
                )

                is RearWidgetStringVarField -> config.putStringVar(item.name, item.value)
                is RearWidgetCustomEditLinkField -> config
            }
        }
        return config
    }

    private fun parseNumericIntentValue(type: String, raw: String): Double? {
        return when {
            type.equals("int", ignoreCase = true) -> raw.toIntOrNull()?.toDouble()
            type.equals("float", ignoreCase = true) -> raw.toFloatOrNull()?.toDouble()
            type.equals("double", ignoreCase = true) -> raw.toDoubleOrNull()
            type.equals("number", ignoreCase = true) -> raw.toDoubleOrNull()
            else -> null
        }
    }

    private fun String.isNumericLikeType(): Boolean {
        return equals("int", ignoreCase = true) ||
                equals("float", ignoreCase = true) ||
                equals("double", ignoreCase = true) ||
                equals("number", ignoreCase = true)
    }

    private fun Element.readLanguageTitles(): Map<String, String> {
        return childElements("Language")
            .mapNotNull { child ->
                val locale = child.optionalAttr("locale")?.trim().orEmpty()
                val title = child.optionalAttr("displayTitle")?.trim().orEmpty()
                if (locale.isBlank() || title.isBlank()) null else locale to title
            }
            .toMap(linkedMapOf())
    }

    private fun Element.readHintTitles(): Map<String, String> {
        return childElements("LanguageHint")
            .mapNotNull { child ->
                val locale = child.optionalAttr("locale")?.trim().orEmpty()
                val title = child.optionalAttr("displayTitle")?.trim().orEmpty()
                if (locale.isBlank() || title.isBlank()) null else locale to title
            }
            .toMap(linkedMapOf())
    }

    private fun Element.readLocalizedConfigs(): Map<String, RearWidgetLocalizedConfig> {
        return childElements("Language")
            .mapNotNull { child ->
                val locale = child.optionalAttr("locale")?.trim().orEmpty()
                if (locale.isBlank()) return@mapNotNull null
                locale to RearWidgetLocalizedConfig(
                    displayTitle = child.optionalAttr("displayTitle")?.trim()?.ifBlank { null },
                    contentDescription = child.optionalAttr("contentDescription")?.trim()
                        ?.ifBlank { null },
                )
            }
            .toMap(linkedMapOf())
    }

    private fun Element.childElements(tagName: String? = null): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            if (tagName == null || child.tagName == tagName) result += child
        }
        return result
    }

    private fun Element.requiredAttr(name: String): String = optionalAttr(name).orEmpty()

    private fun Element.optionalAttr(name: String): String? {
        return getAttribute(name)?.trim()?.ifBlank { null }
    }

    private fun Element.intAttr(name: String, defaultValue: Int = 0): Int {
        return optionalAttr(name)?.toIntOrNull() ?: defaultValue
    }

    private fun Element.doubleAttr(name: String, defaultValue: Double = 0.0): Double {
        return optionalAttr(name)?.toDoubleOrNull() ?: defaultValue
    }

    private fun Element.csvAttr(name: String): List<String> {
        return optionalAttr(name)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    private fun Element.doubleListAttr(name: String): List<Double> {
        return optionalAttr(name)
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank)?.toDoubleOrNull() }
            .orEmpty()
    }
}

private fun RearWidgetOneConfig.putText(name: String, value: String): RearWidgetOneConfig {
    return copy(textConfig = textConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putColor(name: String, value: String): RearWidgetOneConfig {
    return copy(colorConfig = colorConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putTextSize(name: String, value: Int): RearWidgetOneConfig {
    return copy(textSizeConfig = textSizeConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putAlign(name: String, value: Int): RearWidgetOneConfig {
    return copy(alignStyleConfig = alignStyleConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putImage(name: String, value: String): RearWidgetOneConfig {
    return copy(imageConfig = imageConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putSeekBar(name: String, value: Int): RearWidgetOneConfig {
    return copy(seekBarSaveConfig = seekBarSaveConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putOnOff(name: String, value: Boolean): RearWidgetOneConfig {
    return copy(onOffConfig = onOffConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putStringVar(name: String, value: String): RearWidgetOneConfig {
    return copy(stringVarSaveConfig = stringVarSaveConfig.updated(name, value))
}

private fun RearWidgetOneConfig.putDropDown(name: String, value: String): RearWidgetOneConfig {
    return copy(dropDownSaveConfig = dropDownSaveConfig.updated(name, value))
}

private fun <T> Map<String, T>?.updated(name: String, value: T): Map<String, T> {
    return LinkedHashMap(this.orEmpty()).apply {
        put(name, value)
    }
}
