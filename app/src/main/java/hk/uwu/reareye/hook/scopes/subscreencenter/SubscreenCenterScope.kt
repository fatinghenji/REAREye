package hk.uwu.reareye.hook.scopes.subscreencenter

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.MusicControlWhitelistModule
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.VideoLoopModule

class SubscreenCenterScope : Scope {
    override val hooks: List<YukiBaseHooker> = listOf(
        MusicControlWhitelistModule(),
        VideoLoopModule()
    )
}