package hk.uwu.reareye.hook.scopes

import hk.uwu.reareye.hook.core.HookEnvironment
import hk.uwu.reareye.hook.core.HookModule
import hk.uwu.reareye.hook.core.HookScope

/**
 * REAREye 业务 Scope 接口。
 *
 * Scope 只负责按当前目标上下文创建 HookModule 列表；isRearDevice 从当前上下文延迟读取，
 * 不再使用跨 system-server/application ClassLoader 的静态缓存。
 */
interface Scope : HookScope {
    /** 当前目标要安装的 Hook 模块实例。 */
    override val hooks: List<HookModule>

    /** 当前目标设备是否支持后屏功能。 */
    val isRearDevice: Boolean
        get() = HookEnvironment.requireContext().isRearDevice
}
