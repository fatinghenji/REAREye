package hk.uwu.reareye.hook.scopes.system

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.system.modules.BackgroundWhitelistModule
import hk.uwu.reareye.hook.scopes.system.modules.DisableRearScreenCoverHook
import hk.uwu.reareye.hook.scopes.system.modules.DisableSubScreenDoubleTapSleepHook
import hk.uwu.reareye.hook.scopes.system.modules.DisableSubScreenDoubleTapWakeHook
import hk.uwu.reareye.hook.scopes.system.modules.DisableSubScreenHighLoadModeHook
import hk.uwu.reareye.hook.scopes.system.modules.RearScreenActivityWhitelistModule
import hk.uwu.reareye.hook.scopes.system.modules.misc.GMSUnlockModule

class SystemScope : Scope {

    override val hooks: List<YukiBaseHooker> = listOf(
        RearScreenActivityWhitelistModule(),
        BackgroundWhitelistModule(),
        GMSUnlockModule(),
        DisableRearScreenCoverHook(),
        DisableSubScreenDoubleTapSleepHook(),
        DisableSubScreenDoubleTapWakeHook(),
        DisableSubScreenHighLoadModeHook()
    )
}