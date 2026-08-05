package hk.uwu.reareye.hook.core

import com.highcapable.kavaref.resolver.ConstructorResolver
import com.highcapable.kavaref.resolver.MethodResolver
import com.highcapable.kavaref.resolver.base.MemberResolver
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个 MethodResolver/ConstructorResolver 的兼容 DSL。
 *
 * before/after/replace 可组合；参数通过 HookInvocation 的独立数组传递，原始调用通过
 * Origin Invoker 完成，避免把 Chain.proceed 误当成 invokeOriginal。
 */
class HookBuilder internal constructor(
    private val executable: Executable,
    private val context: HookContext,
    private val module: HookModule,
    private val autoInstall: Boolean,
) {
    private var beforeCallback: (HookInvocation.() -> Unit)? = null
    private var afterCallback: (HookInvocation.() -> Unit)? = null
    private var replaceAnyCallback: (HookInvocation.() -> Any?)? = null
    private var replaceUnitCallback: (HookInvocation.() -> Unit)? = null
    private var installed = false
    private var registration: HookRegistration? = null

    /** 注册 before 回调；autoInstall 模式会立即安装。 */
    fun before(callback: HookInvocation.() -> Unit): HookBuilder {
        beforeCallback = callback
        if (autoInstall) install()
        return this
    }

    /** 注册 after 回调；autoInstall 模式会立即安装。 */
    fun after(callback: HookInvocation.() -> Unit): HookBuilder {
        afterCallback = callback
        if (autoInstall) install()
        return this
    }

    /** 注册返回任意值的替换回调。 */
    fun replaceAny(callback: HookInvocation.() -> Any?): HookBuilder {
        replaceAnyCallback = callback
        replaceUnitCallback = null
        if (autoInstall) install()
        return this
    }

    /** 注册 Unit 替换回调；若回调自行 invokeOriginal，则保留原始结果。 */
    fun replaceUnit(callback: HookInvocation.() -> Unit): HookBuilder {
        replaceUnitCallback = callback
        replaceAnyCallback = null
        if (autoInstall) install()
        return this
    }

    /** 直接替换为常量。 */
    fun replaceTo(value: Any?): HookBuilder = replaceAny { value }

    /** 直接替换为 true。 */
    fun replaceToTrue(): HookBuilder = replaceTo(true)

    /** 直接替换为 false。 */
    fun replaceToFalse(): HookBuilder = replaceTo(false)

    internal fun install() {
        if (installed) return
        check(
            beforeCallback != null || afterCallback != null ||
                    replaceAnyCallback != null || replaceUnitCallback != null
        ) { "Hook callback is empty for ${executable.toGenericString()}" }
        val id = stableHookId(context, module, executable, callbackVariant())
        lateinit var current: HookRegistration
        val hooker = XposedInterface.Hooker { chain ->
            if (!context.isGenerationActive || !module.isGenerationActive) {
                return@Hooker chain.proceed()
            }
            HookEnvironment.withContext(context, module) {
                if (!context.isGenerationActive || !module.isGenerationActive) {
                    return@withContext chain.proceed()
                }
                val invocation = HookInvocation(chain, context) { current.remove() }
                execute(invocation)
                invocation.resultOrNull()
            }
        }
        current = HookRegistration(context.hooks, id, executable, hooker)
        current.install()
        registration = current
        installed = true
    }

    private fun execute(invocation: HookInvocation): Any? {
        beforeCallback?.let { callback ->
            runCatching { callback(invocation) }
                .onFailure { logCallbackFailure("before", it) }
        }

        var originalFailure: Throwable? = null
        if (!invocation.hasResult) {
            when {
                replaceAnyCallback != null -> {
                    try {
                        invocation.setResult(replaceAnyCallback!!.invoke(invocation))
                    } catch (throwable: Throwable) {
                        logCallbackFailure("replaceAny", throwable)
                        if (!invocation.originalCallAttempted) {
                            try {
                                invocation.proceed()
                            } catch (fallback: Throwable) {
                                originalFailure = invocation.throwable ?: fallback
                            }
                        } else {
                            originalFailure = invocation.throwable ?: throwable
                        }
                    }
                }

                replaceUnitCallback != null -> {
                    try {
                        replaceUnitCallback!!.invoke(invocation)
                        if (!invocation.hasResult && !invocation.originalCallAttempted) {
                            invocation.setResult(null)
                        }
                    } catch (throwable: Throwable) {
                        logCallbackFailure("replaceUnit", throwable)
                        if (!invocation.originalCallAttempted) {
                            try {
                                invocation.proceed()
                            } catch (fallback: Throwable) {
                                originalFailure = invocation.throwable ?: fallback
                            }
                        } else {
                            originalFailure = invocation.throwable ?: throwable
                        }
                    }
                }

                else -> {
                    try {
                        invocation.proceed()
                    } catch (throwable: Throwable) {
                        originalFailure = invocation.throwable ?: throwable
                    }
                }
            }
        }

        afterCallback?.let { callback ->
            runCatching { callback(invocation) }
                .onFailure { logCallbackFailure("after", it) }
        }

        if (invocation.hasResult) return invocation.resultOrNull()
        if (originalFailure != null) throw originalFailure
        return invocation.resultOrNull()
    }

    private fun logCallbackFailure(phase: String, throwable: Throwable) {
        context.logger.error(
            "Hook callback failed: phase=$phase module=${module.javaClass.name} " +
                    "package=${context.packageName} process=${context.processName} " +
                    "executable=${executable.toGenericString()}",
            throwable,
        )
    }

    private fun callbackVariant(): String = when {
        replaceAnyCallback != null || replaceUnitCallback != null -> "replace"
        beforeCallback != null && afterCallback != null -> "before-after"
        beforeCallback != null -> "before"
        afterCallback != null -> "after"
        else -> "empty"
    }
}

/** 一次为多个成员安装同一回调组合的构建器。 */
class HookBatchBuilder internal constructor(
    private val executables: List<Executable>,
    private val context: HookContext,
    private val module: HookModule,
    private val autoInstall: Boolean,
) {
    private val builders = executables.map { HookBuilder(it, context, module, autoInstall) }

    /** 为全部成员注册 before。 */
    fun before(callback: HookInvocation.() -> Unit): HookBatchBuilder {
        builders.forEach { it.before(callback) }
        return this
    }

    /** 为全部成员注册 after。 */
    fun after(callback: HookInvocation.() -> Unit): HookBatchBuilder {
        builders.forEach { it.after(callback) }
        return this
    }

    /** 为全部成员注册任意值替换。 */
    fun replaceAny(callback: HookInvocation.() -> Any?): HookBatchBuilder {
        builders.forEach { it.replaceAny(callback) }
        return this
    }

    /** 为全部成员注册 Unit 替换。 */
    fun replaceUnit(callback: HookInvocation.() -> Unit): HookBatchBuilder {
        builders.forEach { it.replaceUnit(callback) }
        return this
    }

    /** 直接为全部成员替换常量。 */
    fun replaceTo(value: Any?): HookBatchBuilder {
        builders.forEach { it.replaceTo(value) }
        return this
    }

    /** 直接为全部成员替换 true。 */
    fun replaceToTrue(): HookBatchBuilder = replaceTo(true)

    /** 直接为全部成员替换 false。 */
    fun replaceToFalse(): HookBatchBuilder = replaceTo(false)

    internal fun install() {
        builders.forEach { it.install() }
    }
}

internal fun createHookBuilder(
    resolver: MemberResolver<*, *>,
    autoInstall: Boolean,
): HookBuilder {
    val context = HookEnvironment.requireContext()
    val module = HookEnvironment.requireModule()
    val member = when (resolver) {
        is MethodResolver<*> -> resolver.self
        is ConstructorResolver<*> -> resolver.self
        else -> error("Unsupported resolver type: ${resolver.javaClass.name}")
    }
    return HookBuilder(member, context, module, autoInstall)
}

internal fun createHookBatchBuilder(
    resolvers: Iterable<MemberResolver<*, *>>,
    autoInstall: Boolean,
): HookBatchBuilder {
    val context = HookEnvironment.requireContext()
    val module = HookEnvironment.requireModule()
    val members = resolvers.map {
        when (it) {
            is MethodResolver<*> -> it.self
            is ConstructorResolver<*> -> it.self
            else -> error("Unsupported resolver type: ${it.javaClass.name}")
        }
    }
    return HookBatchBuilder(members, context, module, autoInstall)
}

/** 为 KavaRef 单个方法/构造器解析结果安装 Hook。 */
fun MemberResolver<*, *>.hook(): HookBuilder = createHookBuilder(this, autoInstall = true)

/** 在配置块完成后一次性安装单成员 Hook。 */
fun MemberResolver<*, *>.hook(initiate: HookBuilder.() -> Unit): HookBuilder =
    createHookBuilder(this, autoInstall = false).apply(initiate).also { it.install() }

/** 为 KavaRef 构造器列表/成员列表安装同一 Hook。 */
fun Iterable<MemberResolver<*, *>>.hookAll(): HookBatchBuilder =
    createHookBatchBuilder(this, autoInstall = true)

/** 在配置块完成后一次性安装全部成员 Hook。 */
fun Iterable<MemberResolver<*, *>>.hookAll(initiate: HookBatchBuilder.() -> Unit): HookBatchBuilder =
    createHookBatchBuilder(this, autoInstall = false).apply(initiate).also { it.install() }

internal class HookRegistration(
    private val registry: HookRegistry,
    private val id: String,
    private val executable: Executable,
    private val hooker: XposedInterface.Hooker,
) {
    private val removed = AtomicBoolean(false)

    @Volatile
    private var installedHook: InstalledHook? = null

    fun install() {
        installedHook = registry.install(id, executable, hooker)
    }

    fun remove() {
        if (!removed.compareAndSet(false, true)) return
        installedHook?.let { registry.remove(id, it) }
    }
}
