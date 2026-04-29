package hk.uwu.reareye.hook.scopes.thememanager

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.thememanager.modules.RearWallpaperThemeManagerSyncHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnlockTemplateMaximumLimitHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnlockVideoRestrictionsHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnmuteVideoWallpaperHook

class ThemeManagerScope : Scope {
    override val hooks: List<YukiBaseHooker> = listOf(
        UnlockVideoRestrictionsHook(),
        UnlockTemplateMaximumLimitHook(),
        UnmuteVideoWallpaperHook(),
        RearWallpaperThemeManagerSyncHook()
    )
}
