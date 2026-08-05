package hk.uwu.reareye.hook.scopes.system.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

class DisableRearScreenCoverHook : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            val clz = "com.android.server.power.DualScreenCoverManager".toClass().resolve()
            clz.firstMethod {
                name = "showCoverView"
                parameters(Int::class.java)
            }.hook().replaceUnit {
                val displayId = args(0).int()
                if (displayId == 1 && prefs.getBoolean(
                        ConfigKeys.HOOK_DISABLE_REAR_SCREEN_COVER,
                        false
                    )
                ) {
                    // 阻止显示cover view
                    YLog.debug("Rejected show cover view on rear screen")
                    return@replaceUnit
                } else {
                    invokeOriginal(displayId)
                }
            }
        }
    }
}