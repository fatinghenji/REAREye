package hk.uwu.reareye.hook.core

/**
 * DexKit native ABI 初始化入口。
 *
 * 入口类初始化阶段不能直接调用 System.loadLibrary，否则设备缺少匹配 ABI 时会让整个
 * XposedModule 类加载失败。这里延迟到 module-loaded 生命周期，单次尝试并记录明确错误；
 * 后续依赖 DexKit 的模块仍会按 HookRuntime 的模块错误策略报告失败。
 */
object DexKitBootstrap {
    @Volatile
    private var loaded = false

    @Volatile
    private var attempted = false

    /** 尝试加载 DexKit ABI；重复调用只返回首次结果。 */
    @Synchronized
    fun ensureLoaded(logger: HookLogger): Boolean {
        if (loaded) return true
        if (attempted) return false
        attempted = true
        return runCatching {
            System.loadLibrary("dexkit")
            loaded = true
            logger.info("DexKit native ABI loaded")
            true
        }.onFailure {
            logger.error("DexKit native ABI initialization failed", it)
        }.getOrDefault(false)
    }
}
