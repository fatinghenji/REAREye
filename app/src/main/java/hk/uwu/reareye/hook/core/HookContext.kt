package hk.uwu.reareye.hook.core

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个目标包/进程的 Hook 上下文。
 *
 * 上下文以 package、process 和 ClassLoader 三元组为边界，不允许在不同目标间复用 Hook
 * 注册表、Application 或模块实例；生命周期注册也属于当前目标代际。
 */
interface HookContext {
    /** 当前目标包名；system_server 使用 android。 */
    val packageName: String

    /** 当前目标进程名。 */
    val processName: String

    /** 当前目标 ApplicationInfo 快照。 */
    val appInfo: ApplicationInfo

    /** 当前目标 ClassLoader，所有目标类解析都必须绑定它。 */
    val classLoader: ClassLoader

    /** 是否为 system_server 生命周期。 */
    val isSystemServer: Boolean

    /** 目标 Application；仅在 attachBaseContext 后可用。 */
    val appContext: Context?

    /** 真实目标系统 Context；解析失败时记录日志并在应用目标回退到 appContext。 */
    val systemContext: Context

    /** 当前目标的 Application 生命周期注册表。 */
    val lifecycle: HookLifecycle

    /** 当前目标可读取的远程偏好适配。 */
    val prefs: HookPrefs

    /** 当前目标的 Hook 注册表。 */
    val hooks: HookRegistry

    /** 当前目标日志。 */
    val logger: HookLogger

    /** 当前设备是否支持后屏功能，按目标上下文延迟读取。 */
    val isRearDevice: Boolean

    /** 当前目标代际是否仍允许执行模块 Hook 回调。 */
    val isGenerationActive: Boolean
        get() = true

    /** 当前目标的稳定逻辑 ID，用于 Hook ID 和热重载状态键。 */
    val targetId: String
}

internal class MutableHookContext(
    override val packageName: String,
    override val processName: String,
    override val appInfo: ApplicationInfo,
    override val classLoader: ClassLoader,
    override val isSystemServer: Boolean,
    override val prefs: HookPrefs,
    override val hooks: HookRegistry,
    override val logger: HookLogger,
    private val systemContextProvider: () -> Context,
) : HookContext {
    private val lifecycleImpl = HookLifecycleImpl(this)
    private val generationActive = AtomicBoolean(true)

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var systemContextCache: Context? = null

    @Volatile
    private var boundApplication: Any? = null

    @Volatile
    private var rearDevice: Boolean? = null

    override val appContext: Context?
        get() = applicationContext

    override val systemContext: Context
        get() = systemContextCache ?: synchronized(this) {
            systemContextCache ?: runCatching { systemContextProvider() }
                .onFailure {
                    logger.error(
                        "Unable to resolve system context: package=$packageName process=$processName",
                        it,
                    )
                }
                .getOrElse {
                    applicationContext ?: throw IllegalStateException(
                        "System context unavailable for package=$packageName process=$processName",
                        it,
                    )
                }
                .also { systemContextCache = it }
        }

    override val lifecycle: HookLifecycle
        get() = lifecycleImpl

    override val isRearDevice: Boolean
        get() = rearDevice ?: synchronized(this) {
            rearDevice ?: detectRearDevice().also { rearDevice = it }
        }

    override val isGenerationActive: Boolean
        get() = generationActive.get()

    override val targetId: String = buildTargetId(packageName, processName, appInfo)

    /**
     * 绑定当前目标唯一的 Application。
     *
     * 多个共享 ClassLoader 的包事件会同时命中通用生命周期 Hook，因此必须先按 Context
     * 包名校验，再拒绝第二个不同 Application，避免跨目标误绑定。
     */
    fun bindApplication(application: Context, baseContext: Context? = null): Boolean {
        if (isSystemServer) return false
        val observedPackage = runCatching { baseContext?.packageName ?: application.packageName }
            .getOrNull()
        if (observedPackage != null && observedPackage != packageName) {
            logger.debug(
                "Ignore Application binding for another package: expected=$packageName actual=$observedPackage"
            )
            return false
        }
        synchronized(this) {
            val current = boundApplication
            if (current != null && current !== application) {
                logger.warn(
                    "Reject second Application binding: package=$packageName process=$processName"
                )
                return false
            }
            boundApplication = application
            applicationContext = application
            return true
        }
    }

    /** 冻结当前目标代际；后续 DSL Hooker 只能调用原方法，不得再进入旧模块回调。 */
    fun freezeLifecycle() {
        generationActive.set(false)
        lifecycle.freeze()
    }

    private fun detectRearDevice(): Boolean {
        return HookEnvironment.withContext(this) {
            val propertiesClass = classLoader.loadClass("android.os.SystemProperties")
            val getInt = propertiesClass.getDeclaredMethod(
                "getInt",
                String::class.java,
                Int::class.javaPrimitiveType!!,
            )
            getInt.isAccessible = true
            val value = getInt.invoke(null, "persist.sys.multi_display_type", 1) as Int
            value == 6
        }
    }
}

/** 创建 system_server 的最小 ApplicationInfo 快照。 */
internal fun systemServerApplicationInfo(): ApplicationInfo = ApplicationInfo().apply {
    packageName = "android"
    processName = "system_server"
}

/** 构建跨 ClassLoader 代际稳定的目标标识。 */
internal fun buildTargetId(
    packageName: String,
    processName: String,
    appInfo: ApplicationInfo,
): String {
    val uid = appInfo.uid
    return "$packageName|$processName|$uid|${appInfo.sourceDir.orEmpty()}"
}

/** libxposed 宿主诊断快照；只保存 classloader-neutral 标量。 */
internal data class HookFrameworkInfo(
    val name: String,
    val apiVersion: Int,
    val version: String,
    val versionCode: Long,
    val properties: Long,
) {
    val supportsRemotePreferences: Boolean
        get() = properties and XposedInterface.PROP_CAP_REMOTE != 0L

    val supportsSystemServer: Boolean
        get() = properties and XposedInterface.PROP_CAP_SYSTEM != 0L

    fun diagnostics(): String =
        "name=$name api=$apiVersion version=$version versionCode=$versionCode properties=0x${
            properties.toString(
                16
            )
        } " +
                "capRemote=$supportsRemotePreferences capSystem=$supportsSystemServer"
}

/** 捕获 framework 元数据；单个诊断字段失败不会反向阻断目标 Hook 安装。 */
internal fun XposedInterface.captureFrameworkInfo(logger: HookLogger): HookFrameworkInfo {
    fun <T> read(name: String, fallback: T, block: () -> T): T = runCatching(block)
        .onFailure {
            logger.error(
                "Unable to read libxposed framework diagnostic: field=$name",
                it
            )
        }
        .getOrDefault(fallback)

    return HookFrameworkInfo(
        name = read("frameworkName", "<unavailable>") { frameworkName },
        apiVersion = read("apiVersion", -1) { apiVersion },
        version = read("frameworkVersion", "<unavailable>") { frameworkVersion },
        versionCode = read("frameworkVersionCode", -1L) { frameworkVersionCode },
        properties = read("frameworkProperties", 0L) { frameworkProperties },
    )
}

/**
 * 将 libxposed 远程偏好转换为项目内只读 HookPrefs。
 *
 * embedded/no-remote 框架既可能通过 capability 明示，也可能在调用时抛异常；两种情况都返回
 * 明确不可写的默认值视图，保证 TargetState 可以继续安装 Hook。
 */
internal fun XposedInterface.remoteHookPrefs(
    group: String,
    target: String,
    framework: HookFrameworkInfo,
    logger: HookLogger,
): HookPrefs = createRemoteHookPrefs(
    group = group,
    target = target,
    framework = framework,
    logger = logger,
    remoteProvider = { getRemotePreferences(group) },
    remoteFileProvider = { openRemoteFile(it) },
)

/** 可测试的远程偏好能力边界。 */
internal fun createRemoteHookPrefs(
    group: String,
    target: String,
    framework: HookFrameworkInfo,
    logger: HookLogger,
    remoteFileProvider: (String) -> android.os.ParcelFileDescriptor = {
        throw java.io.FileNotFoundException(it)
    },
    remoteProvider: () -> SharedPreferences,
): HookPrefs {
    require(group.isNotBlank()) { "Remote preference group must not be blank" }
    val diagnostics = framework.diagnostics()
    if (!framework.supportsRemotePreferences) {
        logger.warn(
            "Remote preferences unavailable; continue Hook installation with caller defaults: " +
                    "group=$group target=$target framework={$diagnostics}"
        )
        return UnavailableHookPrefs(group, target, diagnostics, logger)
    }
    return runCatching {
        ReadOnlyHookPrefs(
            delegate = SharedPreferencesHookPrefs(remoteProvider()),
            name = group,
            remoteFileProvider = remoteFileProvider,
            logger = logger,
        )
    }.onFailure {
        logger.error(
            "Remote preferences acquisition failed; continue Hook installation with caller defaults: " +
                    "group=$group target=$target framework={$diagnostics}",
            it,
        )
    }.getOrElse {
        UnavailableHookPrefs(group, target, diagnostics, logger)
    }
}

/** 使用目标 ClassLoader 解析 ActivityThread 的真实 system Context。 */
internal fun resolveSystemContext(classLoader: ClassLoader): Context {
    val activityThreadClass = classLoader.loadClass("android.app.ActivityThread")
    val currentThread = activityThreadClass.getDeclaredMethod("currentActivityThread")
        .apply { isAccessible = true }
        .invoke(null)
        ?: error("ActivityThread.currentActivityThread returned null")
    return activityThreadClass.getDeclaredMethod("getSystemContext")
        .apply { isAccessible = true }
        .invoke(currentThread) as? Context
        ?: error("ActivityThread.getSystemContext returned a non-Context value")
}

/** 从当前目标进程的 ActivityThread 获取新代可达 Application。 */
internal fun resolveCurrentApplication(classLoader: ClassLoader): Application? {
    val activityThreadClass = classLoader.loadClass("android.app.ActivityThread")
    val currentApplication = runCatching {
        activityThreadClass.getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Application
    }.getOrNull()
    if (currentApplication != null) return currentApplication

    val currentThread = activityThreadClass.getDeclaredMethod("currentActivityThread")
        .apply { isAccessible = true }
        .invoke(null)
        ?: return null
    return runCatching {
        activityThreadClass.getDeclaredMethod("getApplication")
            .apply { isAccessible = true }
            .invoke(currentThread) as? Application
    }.getOrNull()
}
