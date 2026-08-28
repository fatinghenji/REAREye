package hk.uwu.reareye.hook.core

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个目标上下文的 Hook 注册表。
 *
 * 注册表隔离 ClassLoader，并以稳定 ID 做原子替换；同一 ID 再次安装时调用 API 102 的
 * HookHandle.replaceHook，而不是叠加一个重复链。
 */
interface HookRegistry {
    /** 安装或原子替换指定 ID 的 Hook。 */
    fun install(id: String, executable: Executable, hooker: XposedInterface.Hooker): InstalledHook

    /** 仅移除仍然属于该 ID 的句柄，旧句柄不会误删新替换句柄。 */
    fun remove(id: String, hook: InstalledHook)

    /** 当前上下文已安装的 Hook 数量，用于诊断和测试。 */
    fun size(): Int

    /** 冻结当前目标代际并幂等移除全部句柄。 */
    fun unhookAll()

    /** 移除全部句柄并返回清理是否完整成功。 */
    fun unhookAllChecked(): Boolean {
        unhookAll()
        return true
    }

    /** 完成热重载替换，释放未能被新代接管的旧句柄。 */
    fun finishHotReloadReplacement(): Boolean = true
}

/** 项目内 Hook 句柄门面，隐藏 libxposed 句柄生命周期细节。 */
class InstalledHook internal constructor(
    val id: String,
    val executable: Executable,
    internal val delegate: XposedInterface.HookHandle,
) {
    private val removed = AtomicBoolean(false)

    /** 幂等移除底层 Hook；底层失败时保留可重试状态。 */
    fun unhook() {
        if (!removed.compareAndSet(false, true)) return
        try {
            delegate.unhook()
        } catch (throwable: Throwable) {
            removed.set(false)
            throw throwable
        }
    }
}

@SuppressLint("XposedNewApi")
internal class HookRegistryImpl(
    private val xposed: XposedInterface,
    private val logger: HookLogger,
    oldHookHandles: List<XposedInterface.HookHandle> = emptyList(),
) : HookRegistry, InvokerProvider {
    private val handles = ConcurrentHashMap<String, InstalledHook>()
    private val oldHandles = oldHookHandles.associateBy { it.id }
    private val replacedOldIds = HashSet<String>()
    private val releasedOldHandles = Collections.newSetFromMap(
        IdentityHashMap<XposedInterface.HookHandle, Boolean>(),
    )
    private var oldReplacementFinished = false

    override fun install(
        id: String,
        executable: Executable,
        hooker: XposedInterface.Hooker
    ): InstalledHook {
        require(id.isNotBlank()) { "Hook ID must not be blank" }
        return synchronized(handles) {
            try {
                val previous = handles[id]
                val current = if (previous != null) {
                    require(previous.executable == executable) {
                        "Hook ID collision for $id: ${previous.executable.toGenericString()} != " +
                                executable.toGenericString()
                    }
                    val delegate = previous.delegate.replaceHook(hooker)
                    InstalledHook(id, executable, delegate)
                } else {
                    val old = oldHandles[id]
                    if (old != null && old.executable == executable) {
                        try {
                            val delegate = old.replaceHook(hooker)
                            replacedOldIds += id
                            logger.info(
                                "Hot reload HookHandle replaced: id=$id executable=${executable.toGenericString()}"
                            )
                            InstalledHook(id, executable, delegate)
                        } catch (replaceFailure: Throwable) {
                            logger.error(
                                "Old HookHandle replacement failed; install a new handle: id=$id",
                                replaceFailure,
                            )
                            val delegate = xposed.hook(executable)
                                .setId(id)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(hooker)
                            InstalledHook(id, executable, delegate)
                        }
                    } else {
                        if (old != null) {
                            logger.error(
                                "Old HookHandle executable mismatch; install a new handle: id=$id",
                            )
                        }
                        val delegate = xposed.hook(executable)
                            .setId(id)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(hooker)
                        InstalledHook(id, executable, delegate)
                    }
                }
                handles[id] = current
                current
            } catch (throwable: Throwable) {
                logger.error(
                    "Hook installation failed: id=$id executable=${executable.toGenericString()}",
                    throwable,
                )
                throw throwable
            }
        }
    }

    override fun remove(id: String, hook: InstalledHook) {
        synchronized(handles) {
            if (handles[id]?.delegate !== hook.delegate) return
            hook.unhook()
            handles.remove(id, hook)
        }
    }

    override fun size(): Int = handles.size

    override fun unhookAll() {
        unhookAllChecked()
    }

    override fun unhookAllChecked(): Boolean {
        val snapshot = synchronized(handles) { handles.values.toList() }
        var success = true
        snapshot.forEach { hook ->
            runCatching {
                hook.unhook()
                synchronized(handles) { handles.remove(hook.id, hook) }
            }.onFailure {
                success = false
                logger.error("Hook removal failed during target freeze: id=${hook.id}", it)
            }
        }
        return success
    }

    override fun finishHotReloadReplacement(): Boolean {
        val stale = synchronized(handles) {
            if (oldReplacementFinished) return true
            oldHandles
                .filterKeys { it !in replacedOldIds }
                .values
                .filterNot(releasedOldHandles::contains)
                .toList()
        }
        logger.debug(
            "Hot reload HookHandle cleanup: replaced=${replacedOldIds.size} stale=${stale.size} " +
                    "active=${handles.size}"
        )
        var success = true
        stale.forEach { handle ->
            runCatching { handle.unhook() }
                .onSuccess {
                    synchronized(handles) { releasedOldHandles += handle }
                }
                .onFailure {
                    success = false
                    logger.error("Stale old HookHandle release failed: id=${handle.id}", it)
                }
        }
        if (success) {
            synchronized(handles) { oldReplacementFinished = true }
        }
        return success
    }

    override fun methodInvoker(method: java.lang.reflect.Method): XposedInterface.Invoker<*, java.lang.reflect.Method> =
        xposed.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN)

    override fun <T> constructorInvoker(
        constructor: java.lang.reflect.Constructor<T>,
    ): XposedInterface.CtorInvoker<T> =
        xposed.getInvoker(constructor).setType(XposedInterface.Invoker.Type.ORIGIN)
}

internal fun stableHookId(
    context: HookContext,
    module: HookModule,
    executable: Executable,
    variant: String,
): String {
    val member = executable.toGenericString()
        .replace(Regex("[^A-Za-z0-9_.:$-]"), "_")
    val owner = module::class.java.name.replace('$', '.')
    return "reareye:${context.targetId}:$owner:$variant:$member"
}
