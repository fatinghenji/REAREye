package hk.uwu.reareye.hook.scopes.thememanager

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnlockTemplateMaximumLimitHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnlockVideoRestrictionsHook

class ThemeManagerScope : Scope {
    override val hooks: List<YukiBaseHooker> = listOf(
        UnlockVideoRestrictionsHook(),
        UnlockTemplateMaximumLimitHook()
    )
}