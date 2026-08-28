package hk.uwu.reareye.hook.scopes.system.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

class DisableSubScreenHighLoadModeHook : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            val dualScreenCoverManagerRef = "com.android.server.power.DualScreenCoverManager"
                .toClass()
                .resolve()

            dualScreenCoverManagerRef.firstMethod {
                name = "updateHighLoadSceneMode"
                parameters(Int::class.java, Boolean::class.java)
                returnType = Void.TYPE
            }.hook().replaceUnit {
                val value = args(1).boolean()
                if (!value) {
                    invokeOriginal(*args)
                    return@replaceUnit
                }

                val packageName = instance.mainDisplayForegroundPackageName()
                if (packageName in prefs.getStringSet(
                        ConfigKeys.SUBSCREEN_HIGH_LOAD_MODE_DISABLED_APPS,
                    )
                ) {
                    result = null
                    if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                        YLog.debug("Skip subscreen high load mode package=$packageName")
                    }
                    return@replaceUnit
                }
                invokeOriginal(*args)
            }
        }
    }
}
