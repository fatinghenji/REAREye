package hk.uwu.reareye.hook.scopes.thememanager

import hk.uwu.reareye.hook.core.HookModule
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.thememanager.modules.RearWallpaperThemeManagerSyncHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnlockTemplateMaximumLimitHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnlockVideoRestrictionsHook
import hk.uwu.reareye.hook.scopes.thememanager.modules.UnmuteVideoWallpaperHook

class ThemeManagerScope : Scope {
    override val hooks: List<HookModule> = buildList {
        if (isRearDevice) {
            addAll(
                listOf(
                    UnlockVideoRestrictionsHook(),
                    UnlockTemplateMaximumLimitHook(),
                    UnmuteVideoWallpaperHook(),
                    RearWallpaperThemeManagerSyncHook()
                )
            )
        } else {
            YLog.debug("This device is not support rear screen, skip load some features that this device is not supported")
        }
    }
}
