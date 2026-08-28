package hk.uwu.reareye.hook.core

/**
 * 当前目标上下文的线程边界。
 *
 * KavaRef 的无参 toClass/resolve 调用需要绑定目标 ClassLoader；每个 Hook 回调和模块
 * 安装阶段都通过这里设置线程上下文，避免跨应用静态缓存污染。
 */
internal object HookEnvironment {
    private val context = ThreadLocal<HookContext?>()
    private val module = ThreadLocal<HookModule?>()

    /** 当前线程正在安装或执行的 Hook 上下文。 */
    fun currentContext(): HookContext? = context.get()

    /** 要求当前线程已经进入目标上下文，否则立即失败。 */
    fun requireContext(): HookContext =
        context.get() ?: error("HookContext is not bound to the current thread")

    /** 要求当前线程正在执行某个 HookModule。 */
    fun requireModule(): HookModule =
        module.get() ?: error("HookModule is not bound to the current thread")

    /** 在目标 ClassLoader 和模块边界内运行代码，并始终恢复线程状态。 */
    fun <T> withContext(target: HookContext, owner: HookModule? = null, block: () -> T): T {
        val previousContext = context.get()
        val previousModule = module.get()
        val previousLoader = Thread.currentThread().contextClassLoader
        context.set(target)
        if (owner != null) module.set(owner)
        Thread.currentThread().contextClassLoader = target.classLoader
        return try {
            block()
        } finally {
            Thread.currentThread().contextClassLoader = previousLoader
            if (owner == null) module.set(previousModule) else module.set(previousModule)
            context.set(previousContext)
        }
    }
}
