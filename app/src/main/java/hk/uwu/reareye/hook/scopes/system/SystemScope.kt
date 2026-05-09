package hk.uwu.reareye.hook.scopes.system

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.system.modules.BackgroundWhitelistModule
import hk.uwu.reareye.hook.scopes.system.modules.CustomBoundsCompatModule
import hk.uwu.reareye.hook.scopes.system.modules.DisableRearScreenCoverHook
import hk.uwu.reareye.hook.scopes.system.modules.DisableSubScreenDoubleTapSleepHook
import hk.uwu.reareye.hook.scopes.system.modules.DisableSubScreenDoubleTapWakeHook
import hk.uwu.reareye.hook.scopes.system.modules.DisableSubScreenHighLoadModeHook
import hk.uwu.reareye.hook.scopes.system.modules.RearScreenActivityWhitelistModule
import hk.uwu.reareye.hook.scopes.system.modules.misc.GMSUnlockModule

class SystemScope : Scope {

    override val hooks: List<YukiBaseHooker> = buildList {
        add(GMSUnlockModule())
        add(CustomBoundsCompatModule())
        if (isRearDevice) {
            addAll(
                listOf(
                    RearScreenActivityWhitelistModule(),
                    BackgroundWhitelistModule(),
                    DisableRearScreenCoverHook(),
                    DisableSubScreenDoubleTapSleepHook(),
                    DisableSubScreenDoubleTapWakeHook(),
                    DisableSubScreenHighLoadModeHook(),
                )
            )
        } else {
            YLog.debug("This device is not support rear screen, skip load some features that this device is not supported")
        }
    }
}
