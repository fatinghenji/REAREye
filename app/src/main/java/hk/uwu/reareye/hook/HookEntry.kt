package hk.uwu.reareye.hook

import androidx.annotation.Keep
import hk.uwu.reareye.hook.core.HookRuntimeImpl
import hk.uwu.reareye.hook.core.HookScope
import hk.uwu.reareye.hook.scopes.subscreencenter.SubscreenCenterScope
import hk.uwu.reareye.hook.scopes.system.SystemScope
import hk.uwu.reareye.hook.scopes.thememanager.ThemeManagerScope
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 现代模块入口。
 *
 * 入口只负责生命周期转发和 Scope 工厂；目标上下文、Hook 注册表及 DSL 行为全部交给
 * 项目内 HookRuntime，避免入口本身发展成跨目标状态容器。
 */
@Keep
class HookEntry : XposedModule() {
    private val runtime by lazy {
        HookRuntimeImpl(
            xposed = this,
            scopeFactories = listOf<() -> HookScope>(
                { SystemScope() },
                { SubscreenCenterScope() },
                { ThemeManagerScope() },
            ),
        )
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        runtime.onModuleLoaded(param)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        runtime.onSystemServerStarting(param)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        runtime.onPackageReady(param)
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean =
        runtime.onHotReloading(param)

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        runtime.onHotReloaded(param)
    }

}
