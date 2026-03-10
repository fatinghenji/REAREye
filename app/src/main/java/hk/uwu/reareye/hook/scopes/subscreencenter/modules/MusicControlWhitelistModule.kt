package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
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
            if (prefs.getBoolean(ConfigKeys.HOOK_MUSIC_CONTROLS_WHITELIST)) {
                field.set(map)
                XposedBridge.log("Hooked SubscreenCenter whitelist ${field.get()}")
            }
        }
    }
}