package hk.uwu.reareye.hook.scopes.system.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

/**
 * 放行向外接显示器（非默认屏）启动 Activity。
 *
 * HyperOS / Android 16+ 上 `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay`
 * 对非系统调用方（包括 shell / root）一律返回 false，导致 ARCast 等副屏工具
 * 无法把界面推到 AR 眼镜等外接屏上。此 Hook 在原判定返回 false 时，
 * 仅当目标不是默认屏（displayId != 0）才放行。
 */
class ExternalDisplayLaunchUnlockModule : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            "com.android.server.wm.ActivityTaskSupervisor".toClass().resolve()
                .firstMethod {
                    name = "isCallerAllowedToLaunchOnDisplay"
                    returnType = Boolean::class.java
                }.hook().after {
                    if (!prefs.getBoolean(ConfigKeys.ALLOW_EXTERNAL_DISPLAY_LAUNCH, true)) return@after
                    if (result<Boolean>() != false) return@after
                    val displayId = args(2).any() as? Int ?: return@after
                    if (displayId <= 0) return@after
                    resultTrue()
                    YLog.debug("Allowed activity launch on external display id=$displayId")
                }
        }
    }
}
