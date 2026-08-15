package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import android.media.MediaMetadata
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.Modifiers
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.hook.utils.createDexKitCacheBridge
import hk.uwu.reareye.hook.utils.resolveDexKitClassValue
import hk.uwu.reareye.hook.utils.resolveDexKitFieldValue
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import hk.uwu.reareye.ui.config.ConfigKeys
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi

@OptIn(DexKitExperimentalApi::class)
class MusicControlWhitelistModule : YukiBaseHooker() {
    companion object {
        private const val TAG = "MusicControlWhitelist"
        private const val SMART_ASSISTANT_CONFIG_CLASS_CACHE_KEY =
            "SSC_MUSIC_WHITELIST_CONFIG_CLASS"
        private const val SMART_ASSISTANT_CONFIG_PRIMARY_MAP_FIELD_CACHE_KEY =
            "SSC_MUSIC_WHITELIST_CONFIG_PRIMARY_MAP_FIELD"
    }

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val versionCode = resolveHookPackageVersionCode(
                systemContext,
                appInfo.packageName,
                appInfo.sourceDir,
            )
            val bridge = trackResource(
                createDexKitCacheBridge(
                packageName = appInfo.packageName,
                packageVersionCode = versionCode,
                sourceDir = appInfo.sourceDir,
                dataDir = appInfo.dataDir,
                )
            )
            val configClassName = resolveSmartAssistantConfigClassName(bridge)
            val primaryMapFieldName = resolveSmartAssistantConfigPrimaryMapFieldName(
                bridge,
                configClassName,
            )
            val clz = configClassName.toClass().resolve()
            val field = clz.firstField {
                name = primaryMapFieldName
                type = Map::class.java
            }
            if (prefs.getBoolean(ConfigKeys.HOOK_MUSIC_CONTROLS_WHITELIST, true)) {
                replaceStaticMap(configClassName, primaryMapFieldName) {
                    prefs.getStringSet(ConfigKeys.MUSIC_CONTROLS_WHITELIST_APPS).forEach { app ->
                        it[app] = "music"
                    }
                }
                YLog.debug("Hooked SubscreenCenter whitelist ${field.get()}")
            }

            val musicControlListenerClz =
                "com.miui.maml.elements.MusicControlScreenElement$1".toClass().resolve()
            musicControlListenerClz.firstMethod {
                name = "onClientMetadataUpdate"
                returnType = Void.TYPE
                parameters(MediaMetadata::class.java)
            }.hook().after {
                if (!prefs.getBoolean(
                        ConfigKeys.HOOK_MUSIC_CONTROLS_FORCE_UPDATE,
                        false,
                    )
                ) return@after
                val i = instance.asResolver().firstField {
                    name = "this$0"
                }.get() ?: return@after
                val mRoot = i.asResolver().firstField {
                    name = "mRoot"
                    superclass()
                }.get() ?: return@after
                mRoot.asResolver().firstMethod {
                    name = "requestUpdate"
                }.invoke()
                if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                    YLog.debug("Request render controller to update metadata")
                }
            }
        }
    }

    private fun replaceStaticMap(
        className: String,
        fieldName: String,
        mutate: (MutableMap<Any, Any?>) -> Unit,
    ) {
        val field = className.toClass().resolve().firstField { name = fieldName }
        val raw = field.get<Any>() ?: error("$className.$fieldName is null")
        val current = unwrapMutableMap(raw)
        try {
            mutate(current)
        } catch (error: UnsupportedOperationException) {
            YLog.error(
                "[$TAG] Cannot mutate static map in place: $className.$fieldName " +
                        "(${raw.javaClass.name})",
                error,
            )
            throw error
        }
    }


    @Suppress("UNCHECKED_CAST")
    private fun unwrapMutableMap(any: Any): MutableMap<Any, Any?> {
        var current: Any = any
        repeat(32) {
            val map = current as? MutableMap<Any?, Any?>
            if (map != null) {
                try {
                    map.putAll(emptyMap())
                    return map as MutableMap<Any, Any?>
                } catch (error: UnsupportedOperationException) {
                    YLog.debug(
                        "map wrapper is not writable, resolving backing map " +
                                "class=${current.javaClass.name} err=${error.message}"
                    )
                }
            }

            val backing = current.asResolver().optional(silent = true).firstFieldOrNull {
                superclass()
                typeCondition = { type -> Map::class.java.isAssignableFrom(type) }
                modifiersNot(Modifiers.STATIC)
            }?.get<Any>()
            if (backing == null || backing === current) {
                val message = "Cannot resolve writable map backing field: ${current.javaClass.name}"
                YLog.error("[$TAG] $message")
                error(message)
            }
            current = backing
        }

        val message = "Map wrapper nesting exceeds resolver limit: ${any.javaClass.name}"
        YLog.error("[$TAG] $message")
        error(message)
    }

    private fun resolveSmartAssistantConfigClassName(
        bridge: DexKitCacheBridge.RecyclableBridge,
    ): String {
        return resolveDexKitClassValue(
            bridge = bridge,
            cacheKey = SMART_ASSISTANT_CONFIG_CLASS_CACHE_KEY,
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/P2/a.java
            // Original class in jadx: p2.a
            findClass {
                matcher {
                    usingStrings(
                        "com.android.incallui",
                        "com.xiaomi.music",
                        "com.xiaomi.smarthome",
                        "mihomeCamera",
                        "unified.music",
                    )
                }
            }.singleOrNull()
        } ?: error("DexKit failed to resolve smart assistant config class")
    }

    private fun resolveSmartAssistantConfigPrimaryMapFieldName(
        bridge: DexKitCacheBridge.RecyclableBridge,
        configClassName: String,
    ): String {
        return resolveDexKitFieldValue(
            bridge = bridge,
            cacheKey = SMART_ASSISTANT_CONFIG_PRIMARY_MAP_FIELD_CACHE_KEY,
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/P2/a.java:99
            // p2.a.c(String) reads the primary package->business map and checks "unified.music".
            findField {
                matcher {
                    declaredClass = configClassName
                    type = "java.util.Map"
                    readMethods {
                        add {
                            declaredClass = configClassName
                            paramTypes(String::class.java)
                            returnType = "boolean"
                            usingStrings("unified.music", "music")
                        }
                    }
                }
            }.singleOrNull()
        } ?: error("DexKit failed to resolve smart assistant primary map field")
    }
}
