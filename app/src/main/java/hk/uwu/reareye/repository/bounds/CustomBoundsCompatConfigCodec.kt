package hk.uwu.reareye.repository.bounds

import org.json.JSONArray
import org.json.JSONObject

data class CustomBoundsCompatAppConfig(
    val packageName: String,
    val enabled: Boolean = true,
    val mode: CustomBoundsMode = CustomBoundsMode.AUTO_RATIO,
    val aspectRatio: Float = 0f,
    val gravity: Int = CustomBoundsCompatConfigCodec.DEFAULT_GRAVITY,
    val scale: Float = CustomBoundsCompatConfigCodec.DEFAULT_SCALE,
    val densityDpi: Int = 0,
    val rotationDegrees: Int = CustomBoundsCompatConfigCodec.ROTATION_FOLLOW_SYSTEM,
    val insetLeft: Int = 0,
    val insetTop: Int = 0,
    val insetRight: Int = 0,
    val insetBottom: Int = 0,
)

enum class CustomBoundsMode {
    AUTO_RATIO,
    CUSTOM_RATIO,
    EXACT_INSETS;

    companion object {
        fun fromString(value: String?): CustomBoundsMode? {
            return when (value?.trim()?.lowercase()) {
                "auto_ratio" -> AUTO_RATIO
                "custom_ratio" -> CUSTOM_RATIO
                "exact_insets" -> EXACT_INSETS
                else -> null
            }
        }
    }
}

object CustomBoundsCompatConfigCodec {
    const val EMPTY_ARRAY = "[]"
    const val DEFAULT_GRAVITY = 17
    const val DEFAULT_SCALE = 1.0f
    const val ROTATION_FOLLOW_SYSTEM = -1

    private val supportedRotations = setOf(
        ROTATION_FOLLOW_SYSTEM,
        0,
        90,
        180,
        270,
    )

    fun parse(raw: String?): List<CustomBoundsCompatAppConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<CustomBoundsCompatAppConfig>()
        val seenPackages = HashSet<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName").trim()
            if (packageName.isBlank() || !seenPackages.add(packageName)) continue
            val aspectRatio = obj.optDouble("aspectRatio", 0.0)
                .toFloat()
                .takeIf { it > 0f }
                ?: 0f
            val parsedMode = CustomBoundsMode.fromString(
                if (obj.has("mode")) obj.optString("mode") else null
            )
            val mode = parsedMode ?: when {
                obj.optInt("insetLeft", 0) != 0 || obj.optInt("insetTop", 0) != 0 ||
                        obj.optInt("insetRight", 0) != 0 || obj.optInt("insetBottom", 0) != 0 -> {
                    CustomBoundsMode.EXACT_INSETS
                }

                aspectRatio > 0f -> CustomBoundsMode.CUSTOM_RATIO
                else -> CustomBoundsMode.AUTO_RATIO
            }
            out += CustomBoundsCompatAppConfig(
                packageName = packageName,
                enabled = obj.optBoolean("enabled", true),
                mode = mode,
                aspectRatio = aspectRatio,
                gravity = obj.optInt("gravity", DEFAULT_GRAVITY),
                scale = obj.optDouble("scale", DEFAULT_SCALE.toDouble())
                    .toFloat()
                    .takeIf { it > 0f }
                    ?: DEFAULT_SCALE,
                densityDpi = obj.optInt("densityDpi", 0).coerceAtLeast(0),
                rotationDegrees = normalizeRotation(
                    obj.optInt(
                        "rotationDegrees",
                        ROTATION_FOLLOW_SYSTEM
                    )
                ),
                insetLeft = obj.optInt("insetLeft", 0).coerceAtLeast(0),
                insetTop = obj.optInt("insetTop", 0).coerceAtLeast(0),
                insetRight = obj.optInt("insetRight", 0).coerceAtLeast(0),
                insetBottom = obj.optInt("insetBottom", 0).coerceAtLeast(0),
            )
        }
        return out
    }

    fun encode(configs: List<CustomBoundsCompatAppConfig>): String =
        JSONArray().also { arr ->
            configs
                .asSequence()
                .filter { it.packageName.isNotBlank() }
                .distinctBy { it.packageName }
                .sortedBy { it.packageName.lowercase() }
                .forEach { item ->
                    arr.put(
                        JSONObject()
                            .put("packageName", item.packageName.trim())
                            .put("enabled", item.enabled)
                            .put("mode", item.mode.name.lowercase())
                            .put("aspectRatio", item.aspectRatio.toDouble())
                            .put("gravity", item.gravity)
                            .put("scale", item.scale.toDouble())
                            .put("densityDpi", item.densityDpi)
                            .put("rotationDegrees", normalizeRotation(item.rotationDegrees))
                            .put("insetLeft", item.insetLeft)
                            .put("insetTop", item.insetTop)
                            .put("insetRight", item.insetRight)
                            .put("insetBottom", item.insetBottom)
                    )
                }
        }.toString()

    fun normalizeForPackages(
        configs: List<CustomBoundsCompatAppConfig>,
        packageNames: Set<String>,
    ): List<CustomBoundsCompatAppConfig> {
        val byPackage = configs.associateBy { it.packageName }
        return packageNames
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
            .map { packageName ->
                byPackage[packageName] ?: CustomBoundsCompatAppConfig(packageName = packageName)
            }
            .toList()
    }

    fun defaultAutoRatio(parentWidth: Int, parentHeight: Int): Float {
        val longSide = maxOf(parentWidth, parentHeight)
        val shortSide = minOf(parentWidth, parentHeight)
        return if (longSide > 0) shortSide.toFloat() / longSide else 1f
    }

    fun normalizeRotation(rotationDegrees: Int): Int {
        return if (rotationDegrees in supportedRotations) {
            rotationDegrees
        } else {
            ROTATION_FOLLOW_SYSTEM
        }
    }
}
