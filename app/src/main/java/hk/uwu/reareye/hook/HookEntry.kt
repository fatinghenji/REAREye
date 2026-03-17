package hk.uwu.reareye.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import hk.uwu.reareye.hook.scopes.Scope
import hk.uwu.reareye.hook.scopes.subscreencenter.SubscreenCenterScope
import hk.uwu.reareye.hook.scopes.system.SystemScope
import hk.uwu.reareye.hook.scopes.thememanager.ThemeManagerScope

@InjectYukiHookWithXposed(entryClassName = "HookEntrance")
class HookEntry : IYukiHookXposedInit {

    private val scopes = listOf(
        SystemScope(),
        SubscreenCenterScope(),
        ThemeManagerScope()
    )

    private fun List<Scope>.toHooks(): Array<YukiBaseHooker> {
        return this.flatMap { it.hooks }.toTypedArray()
    }

    override fun onInit() = configs {
        debugLog {
            tag = "REAREye"
        }
    }

    override fun onHook() {
        encase(*scopes.toHooks())
    }
}