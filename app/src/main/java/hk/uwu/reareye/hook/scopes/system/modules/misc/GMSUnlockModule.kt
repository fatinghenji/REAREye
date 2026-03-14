package hk.uwu.reareye.hook.scopes.system.modules.misc

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

class GMSUnlockModule : YukiBaseHooker() {
    override fun onHook() {
        val clz = "com.android.server.SystemConfig".toClass().resolve()
        clz.constructor().build().hookAll().after {
            if (prefs.getBoolean(ConfigKeys.MISC_HOOK_GMS_UNLOCK, false)) {
                instance.asResolver().firstMethod {
                    name = "removeFeature"
                    returnType = Void.TYPE
                    parameters(String::class.java)
                }.apply {
                    invoke("cn.google.services")
                    invoke("com.google.android.feature.services_updater")
                }
            }
        }
    }
}