package hk.uwu.reareye.hook.scopes.system

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.system.modules.BackgroundWhitelistModule
import hk.uwu.reareye.hook.scopes.system.modules.RearScreenActivityWhitelistModule

class SystemScope : Scope {

    override val hooks: List<YukiBaseHooker> = listOf(
        RearScreenActivityWhitelistModule(),
        BackgroundWhitelistModule()
    )
}