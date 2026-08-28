package hk.uwu.reareye.hook.core

import android.app.Application
import android.app.Instrumentation
import android.content.ContextWrapper

/**
 * Application 生命周期回调注册表。
 *
 * 生命周期回调必须在目标 Application 首次进入 attachBaseContext 前完成注册；运行时通过
 * Application.attachBaseContext、Instrumentation.callApplicationOnCreate 和
 * Application.onTerminate 的统一适配点分发，而不是在回调发生后才补装模块。
 */
interface HookLifecycle {
    /** 注册一个模块的生命周期回调；模块实例只属于当前目标代际。 */
    fun register(
        owner: HookModule,
        onAttach: (HookInvocation.() -> Unit)?,
        onCreate: (HookInvocation.() -> Unit)?,
        onTerminate: (HookInvocation.() -> Unit)?,
    )

    /** 在 attachBaseContext 完成后分发回调。 */
    fun dispatchAttach(invocation: HookInvocation)

    /** 在 Instrumentation.callApplicationOnCreate 完成后分发回调。 */
    fun dispatchCreate(invocation: HookInvocation)

    /**
     * 热重载后绑定当前目标 Application，并用合成 invocation 重放 attach/create 回调。
     * Application 必须来自当前目标进程，不能传递旧代模块对象。
     */
    fun replayCurrentApplication(application: Application): Boolean = false

    /** 在 Application.onTerminate 完成后分发回调。 */
    fun dispatchTerminate(invocation: HookInvocation)

    /** 冻结并移除当前代际的回调，避免旧模块对象被新代际引用。 */
    fun freeze()
}

internal class HookLifecycleImpl(
    private val context: HookContext,
) : HookLifecycle {
    private data class Registration(
        val owner: HookModule,
        val onAttach: (HookInvocation.() -> Unit)?,
        val onCreate: (HookInvocation.() -> Unit)?,
        val onTerminate: (HookInvocation.() -> Unit)?,
    )

    private val registrations = ArrayList<Registration>()
    private var frozen = false

    override fun register(
        owner: HookModule,
        onAttach: (HookInvocation.() -> Unit)?,
        onCreate: (HookInvocation.() -> Unit)?,
        onTerminate: (HookInvocation.() -> Unit)?,
    ) {
        check(!frozen) { "Cannot register lifecycle callback after target generation is frozen" }
        check(onAttach != null || onCreate != null || onTerminate != null) {
            "At least one lifecycle callback is required for ${owner.javaClass.name}"
        }
        synchronized(registrations) {
            registrations += Registration(owner, onAttach, onCreate, onTerminate)
        }
    }

    override fun dispatchAttach(invocation: HookInvocation) {
        dispatch(invocation, "attachBaseContext") { it.onAttach }
    }

    override fun dispatchCreate(invocation: HookInvocation) {
        dispatch(invocation, "onCreate") { it.onCreate }
    }

    override fun replayCurrentApplication(application: Application): Boolean {
        if (frozen) {
            context.logger.error(
                "Cannot replay Application lifecycle after target freeze: " +
                        "package=${context.packageName} process=${context.processName}"
            )
            return false
        }
        val mutableContext = context as? MutableHookContext
            ?: run {
                context.logger.error(
                    "Cannot bind replay Application on immutable HookContext: " +
                            "package=${context.packageName} process=${context.processName}"
                )
                return false
            }
        val bound = runCatching { mutableContext.bindApplication(application) }
            .onFailure {
                context.logger.error(
                    "Replay Application binding failed: package=${context.packageName} " +
                            "process=${context.processName}",
                    it,
                )
            }
            .getOrDefault(false)
        if (!bound) {
            context.logger.error(
                "Replay Application binding rejected: expected=${context.packageName} " +
                        "actual=${runCatching { application.packageName }.getOrNull()}"
            )
            return false
        }
        return runCatching {
            val attachExecutable = ContextWrapper::class.java.getDeclaredMethod(
                "attachBaseContext",
                android.content.Context::class.java,
            )
            val createExecutable = Instrumentation::class.java.getDeclaredMethod(
                "callApplicationOnCreate",
                Application::class.java,
            )
            dispatchAttach(
                syntheticLifecycleInvocation(
                    context = context,
                    executable = attachExecutable,
                    thisObject = application,
                    args = arrayOf(application),
                )
            )
            dispatchCreate(
                syntheticLifecycleInvocation(
                    context = context,
                    executable = createExecutable,
                    thisObject = application,
                    args = arrayOf(application),
                )
            )
            true
        }.onFailure {
            context.logger.error(
                "Replay current Application lifecycle failed: package=${context.packageName} " +
                        "process=${context.processName}",
                it,
            )
        }.getOrDefault(false)
    }

    override fun dispatchTerminate(invocation: HookInvocation) {
        dispatch(invocation, "onTerminate") { it.onTerminate }
    }

    override fun freeze() {
        synchronized(registrations) {
            frozen = true
            registrations.clear()
        }
    }

    private fun dispatch(
        invocation: HookInvocation,
        phase: String,
        callbackSelector: (Registration) -> (HookInvocation.() -> Unit)?,
    ) {
        val snapshot = synchronized(registrations) { registrations.toList() }
        snapshot.forEach { registration ->
            val callback = callbackSelector(registration) ?: return@forEach
            HookEnvironment.withContext(context, registration.owner) {
                try {
                    callback(invocation)
                } catch (throwable: Throwable) {
                    context.logger.error(
                        "Lifecycle callback failed: phase=$phase module=${registration.owner.javaClass.name} " +
                                "package=${context.packageName} process=${context.processName}",
                        throwable,
                    )
                }
            }
        }
    }
}
