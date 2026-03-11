package hk.uwu.reareye.hook.scopes.system.modules

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

class BackgroundWhitelistModule : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            val ppRef = "com.android.server.am.ProcessPolicy".toClass().resolve()
            ppRef.firstMethod {
                name = "updateDynamicWhiteList"
                returnType = HashMap::class.java
                parameters(Context::class.java, Int::class.java)
            }.hook().after {
                if (prefs.getBoolean(ConfigKeys.HOOK_BACKGROUND_WHITELIST, true)) {
                    val r = result<HashMap<String, Boolean>>() ?: return@after
                    prefs.getStringSet(ConfigKeys.HOOK_BACKGROUND_WHITELIST).forEach {
                        r[it] = true
                    }
                    result = r
                }
            }

            ppRef.firstMethod {
                name = "systemReady"
                returnType = Void.TYPE
                parameters(Context::class.java)
            }.hook().after {
                if (prefs.getBoolean(ConfigKeys.HOOK_BACKGROUND_WHITELIST, true)) {
                    val method = instance.asResolver().firstMethod {
                        name = "updateApplicationLockedState"
                        returnType = Void.TYPE
                        parameters(String::class.java, Int::class.java, Boolean::class.java)
                    }
                    prefs.getStringSet(ConfigKeys.BACKGROUND_LOCK_APPS).forEach {
                        method.invoke(it, -100, true)
                    }
                }
            }
        }
    }
}