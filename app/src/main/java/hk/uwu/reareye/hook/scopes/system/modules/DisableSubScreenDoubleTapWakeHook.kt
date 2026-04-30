package hk.uwu.reareye.hook.scopes.system.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.ui.config.ConfigKeys

class DisableSubScreenDoubleTapWakeHook : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            val dualScreenCoverManagerRef = "com.android.server.power.DualScreenCoverManager"
                .toClass()
                .resolve()
            val windowProcessUtilsRef = "com.android.server.wm.WindowProcessUtils"
                .toClass()
                .resolve()

            dualScreenCoverManagerRef.firstMethod {
                name = "isScreenSkippedWakeup"
                parameters(Int::class.java, String::class.java, Int::class.java)
                returnType = Boolean::class.java
            }.hook().before {
                val groupId = args(0).int()
                val details = args(1).string()
                val packageName = windowProcessUtilsRef.firstMethod {
                    name = "getTopRunningActivityInfo"
                }.invoke().topRunningPackageName()
                if (groupId == 1 && details == WAKE_REASON_DOUBLE_TAP &&
                    packageName in prefs.getStringSet(
                        ConfigKeys.SUBSCREEN_DOUBLE_TAP_WAKE_DISABLED_APPS,
                    )
                ) {
                    result = true
                    if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                        YLog.debug("Skip subscreen double tap wake package=$packageName")
                    }
                }
            }
        }
    }

    companion object {
        private const val WAKE_REASON_DOUBLE_TAP = "android.policy:KEY"
    }
}

private fun Any?.topRunningPackageName() = (this as? Map<*, *>)?.get("packageName") as? String
