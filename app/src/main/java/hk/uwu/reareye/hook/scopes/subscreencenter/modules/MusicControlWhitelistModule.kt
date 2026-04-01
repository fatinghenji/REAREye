package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import android.media.MediaMetadata
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.ui.config.ConfigKeys

class MusicControlWhitelistModule : YukiBaseHooker() {
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
                        false
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
                //YLog.debug("Request render controller to update metadata")
            }
        }
    }
}