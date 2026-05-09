package hk.uwu.reareye.hook.scopes.system.modules

import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.repository.bounds.CustomBoundsCompatAppConfig
import hk.uwu.reareye.repository.bounds.CustomBoundsCompatConfigCodec
import hk.uwu.reareye.repository.bounds.CustomBoundsMode
import hk.uwu.reareye.ui.config.ConfigKeys
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CustomBoundsCompatModule : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            val activityRecordImplClass = runCatching {
                "com.android.server.wm.ActivityRecordImpl".toClass().resolve()
            }.getOrElse {
                YLog.warn("ActivityRecordImpl not found, skip custom rear bounds hook")
                return@loadSystem
            }

            activityRecordImplClass.firstMethod {
                name = "resolveOverrideConfiguration"
                parameterCount = 2
            }.hook().after {
                val moreDebug = prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)
                val parentConfig = args(0).cast<Configuration>() ?: return@after
                val resolvedConfig = args(1).cast<Configuration>() ?: return@after
                val activityRecord = instance.field<Any>("mAr") ?: run {
                    if (moreDebug) YLog.debug("[$TAG] skip reason=no_activity_record")
                    return@after
                }
                val packageName = activityRecord.field<String>("packageName") ?: run {
                    if (moreDebug) YLog.debug("[$TAG] skip reason=no_package")
                    return@after
                }
                val config = CustomBoundsCompatHookConfig.find(
                    raw = prefs.getString(
                        ConfigKeys.CUSTOM_BOUNDS_COMPAT_CONFIG_DATA,
                        CustomBoundsCompatConfigCodec.EMPTY_ARRAY,
                    ),
                    packageName = packageName,
                ) ?: run {
                    if (moreDebug) YLog.debug("[$TAG] skip package=$packageName reason=no_config")
                    return@after
                }
                if (!config.enabled) {
                    if (moreDebug) YLog.debug("[$TAG] skip package=$packageName reason=disabled")
                    return@after
                }

                val displayId = activityRecord.call<Int>("getDisplayId") ?: -1
                if (displayId != TARGET_DISPLAY_ID) {
                    if (moreDebug) YLog.debug("[$TAG] skip package=$packageName reason=display_id displayId=$displayId")
                    return@after
                }
                if (activityRecord.call<Boolean>("inMultiWindowMode") == true) {
                    if (moreDebug) YLog.debug("[$TAG] skip package=$packageName reason=multi_window")
                    return@after
                }

                val parentBounds = getWindowConfigurationBounds(parentConfig)
                if (parentBounds == null || parentBounds.isEmpty) {
                    if (moreDebug) YLog.debug("[$TAG] skip package=$packageName reason=empty_parent_bounds bounds=$parentBounds")
                    return@after
                }

                val compatBounds = when (config.mode) {
                    CustomBoundsMode.EXACT_INSETS -> computeInsetsBounds(parentBounds, config)
                    CustomBoundsMode.CUSTOM_RATIO -> computeCompatBounds(
                        parentBounds = parentBounds,
                        aspectRatio = config.aspectRatio,
                        gravity = config.gravity,
                        scale = config.scale,
                    )

                    CustomBoundsMode.AUTO_RATIO -> computeCompatBounds(
                        parentBounds = parentBounds,
                        aspectRatio = CustomBoundsCompatConfigCodec.defaultAutoRatio(
                            parentBounds.width(),
                            parentBounds.height(),
                        ),
                        gravity = config.gravity,
                        scale = config.scale,
                    )
                }
                applyResolvedConfiguration(
                    config = resolvedConfig,
                    bounds = compatBounds,
                    densityDpi = config.densityDpi.takeIf { it > 0 } ?: parentConfig.densityDpi,
                    rotation = config.rotationDegrees,
                )
                if (moreDebug) {
                    YLog.debug(
                        "[$TAG] apply package=$packageName displayId=$displayId parent=$parentBounds bounds=$compatBounds mode=${config.mode} ratio=${config.aspectRatio} insets=${config.insetLeft},${config.insetTop},${config.insetRight},${config.insetBottom} gravity=${config.gravity} scale=${config.scale} dpi=${config.densityDpi} rotation=${config.rotationDegrees}"
                    )
                }
            }
        }
    }

    private fun computeInsetsBounds(parentBounds: Rect, config: CustomBoundsCompatAppConfig): Rect {
        val left = parentBounds.left + config.insetLeft
        val top = parentBounds.top + config.insetTop
        val right = (parentBounds.right - config.insetRight).coerceAtLeast(left + 1)
        val bottom = (parentBounds.bottom - config.insetBottom).coerceAtLeast(top + 1)
        return Rect(left, top, right, bottom)
    }

    private fun computeCompatBounds(
        parentBounds: Rect,
        aspectRatio: Float,
        gravity: Int,
        scale: Float,
    ): Rect {
        val displaySize = Point(parentBounds.width(), parentBounds.height())
        val width = displaySize.x
        val height = displaySize.y
        val containerRatio = if (height > 0) width.toFloat() / height else 1f
        val targetRatio = aspectRatio.takeIf { it > 0f } ?: 1f

        val unscaledWidth: Int
        val unscaledHeight: Int
        if (targetRatio >= containerRatio) {
            unscaledWidth = width
            unscaledHeight = (width / targetRatio).roundToInt().coerceAtLeast(1)
        } else {
            unscaledHeight = height
            unscaledWidth = (height * targetRatio).roundToInt().coerceAtLeast(1)
        }

        val scaledWidth = (unscaledWidth * scale).roundToInt().coerceIn(1, width)
        val scaledHeight = (unscaledHeight * scale).roundToInt().coerceIn(1, height)
        val left = when (gravity and HORIZONTAL_GRAVITY_MASK) {
            LEFT -> 0
            RIGHT -> width - scaledWidth
            else -> ((width - scaledWidth) / 2f).roundToInt()
        }
        val top = when (gravity and VERTICAL_GRAVITY_MASK) {
            TOP -> 0
            BOTTOM -> height - scaledHeight
            else -> ((height - scaledHeight) / 2f).roundToInt()
        }
        return Rect(
            parentBounds.left + left,
            parentBounds.top + top,
            parentBounds.left + left + scaledWidth,
            parentBounds.top + top + scaledHeight,
        )
    }

    private fun applyResolvedConfiguration(
        config: Configuration,
        bounds: Rect,
        densityDpi: Int,
        rotation: Int,
    ) {
        config.densityDpi = densityDpi
        val windowConfiguration = config.asResolver()
            .firstField { name = "windowConfiguration" }
            .get<Any>() ?: return
        windowConfiguration.call<Unit>("setBounds", bounds)
        windowConfiguration.call<Unit>("setAppBounds", bounds)
        windowConfiguration.call<Unit>("setMaxBounds", bounds)
        if (rotation != CustomBoundsCompatConfigCodec.ROTATION_FOLLOW_SYSTEM) {
            val surfaceRotation = rotation.toSurfaceRotation()
            windowConfiguration.call<Unit>("setRotation", surfaceRotation)
            windowConfiguration.call<Unit>("setDisplayRotation", surfaceRotation)
        }
        updateScreenDp(config, bounds)
    }

    private fun updateScreenDp(config: Configuration, bounds: Rect) {
        if (bounds.isEmpty || config.densityDpi <= 0) return

        val density = config.densityDpi / 160f
        val widthDp = (bounds.width() / density).roundToInt()
        val heightDp = (bounds.height() / density).roundToInt()
        config.setHiddenIntField("compatScreenWidthDp", widthDp)
        config.screenWidthDp = widthDp
        config.setHiddenIntField("compatScreenHeightDp", heightDp)
        config.screenHeightDp = heightDp
        config.orientation = if (bounds.width() <= bounds.height()) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        val shortSizeDp = min(widthDp, heightDp)
        val longSizeDp = max(widthDp, heightDp)
        config.setHiddenIntField("compatSmallestScreenWidthDp", shortSizeDp)
        config.smallestScreenWidthDp = shortSizeDp
        reduceScreenLayout(config.screenLayout, longSizeDp, shortSizeDp)?.let {
            config.screenLayout = it
        }
    }

    private fun Configuration.setHiddenIntField(name: String, value: Int) {
        runCatching {
            asResolver().firstField { this.name = name }.set(value)
        }
    }

    private fun reduceScreenLayout(screenLayout: Int, longSizeDp: Int, shortSizeDp: Int): Int? {
        return runCatching {
            val resolver = Configuration::class.java.resolve()
            val reset = resolver.firstMethod {
                name = "resetScreenLayout"
                parameterCount = 1
            }.invoke<Int>(screenLayout)
            resolver.firstMethod {
                name = "reduceScreenLayout"
                parameterCount = 3
            }.invoke<Int>(reset, longSizeDp, shortSizeDp)
        }.getOrNull()
    }

    private fun getWindowConfigurationBounds(config: Configuration): Rect? = runCatching {
        config.asResolver()
            .firstField { name = "windowConfiguration" }
            .get<Any>()
            ?.call<Rect>("getBounds")
    }.getOrNull()

    private fun Int.toSurfaceRotation(): Int = when (this) {
        90 -> 1
        180 -> 2
        270 -> 3
        else -> 0
    }

    private fun <T> Any?.field(name: String): T? = runCatching {
        this?.asResolver()?.firstField { this.name = name }?.get<T>()
    }.getOrNull()

    private fun <T> Any?.call(name: String, vararg args: Any?): T? = runCatching {
        this?.asResolver()?.firstMethod {
            this.name = name
            parameterCount = args.size
        }?.invoke<T>(*args)
    }.getOrNull()

    private object CustomBoundsCompatHookConfig {
        @Volatile
        private var lastRaw: String? = null

        @Volatile
        private var lastConfigs: Map<String, CustomBoundsCompatAppConfig> = emptyMap()

        fun find(raw: String, packageName: String?): CustomBoundsCompatAppConfig? {
            if (packageName.isNullOrBlank()) return null
            if (raw != lastRaw) {
                lastConfigs = CustomBoundsCompatConfigCodec.parse(raw)
                    .associateBy { it.packageName }
                lastRaw = raw
            }
            return lastConfigs[packageName]
        }
    }

    private companion object {
        const val TAG = "CustomRearBounds"
        const val TARGET_DISPLAY_ID = 1
        const val HORIZONTAL_GRAVITY_MASK = 7
        const val VERTICAL_GRAVITY_MASK = 112
        const val LEFT = 3
        const val RIGHT = 5
        const val TOP = 48
        const val BOTTOM = 80
    }
}
