package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import android.media.MediaMetadata
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import hk.uwu.reareye.ui.config.ConfigKeys

class MusicControlWhitelistModule : YukiBaseHooker() {
    val mediaIdCacheKey = "REAREYE_MUSIC_CONTROL_MEDIA_ID"
    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val clz = "p2.a".toClass().resolve()
            val field = clz.firstField {
                name = "a"
                type = Map::class.java
            }
            val map = buildMap<String, String> {
                @Suppress("UNCHECKED_CAST")
                putAll(field.get() as Map<String, String>)
                prefs.getStringSet(ConfigKeys.MUSIC_CONTROLS_WHITELIST_APPS).forEach {
                    put(it, "music")
                }
            }
            if (prefs.getBoolean(ConfigKeys.HOOK_MUSIC_CONTROLS_WHITELIST, true)) {
                field.set(map)
                XposedBridge.log("Hooked SubscreenCenter whitelist ${field.get()}")
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
                        false
                    )
                ) return@after
                val oldMediaId = XposedHelpers.getAdditionalInstanceField(instance, mediaIdCacheKey)
                val metadata = args(0).cast<MediaMetadata>()
                val mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                if (oldMediaId != mediaId) {
                    XposedHelpers.setAdditionalInstanceField(instance, mediaIdCacheKey, mediaId)
                    if (oldMediaId == null) return@after
                } else {
                    return@after
                }
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
                XposedBridge.log("Request render controller to update metadata")
            }
        }
    }
}