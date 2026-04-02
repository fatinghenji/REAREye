package hk.uwu.reareye.hook.scopes.subscreencenter

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.MusicControlWhitelistModule
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.VideoLoopModule
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.VideoVolumeHook
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.lyrics.LyriconHook
import hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget.RearWidgetHook

class SubscreenCenterScope : Scope {
    override val hooks: List<YukiBaseHooker> = listOf(
        MusicControlWhitelistModule(),
        VideoLoopModule(),
        RearWidgetHook(),
        LyriconHook(),
        VideoVolumeHook()
    )
}
