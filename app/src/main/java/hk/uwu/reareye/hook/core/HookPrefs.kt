package hk.uwu.reareye.hook.core

import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import java.io.File

/** UI 与 Hook 通过 libxposed RemotePreferences 共享的唯一逻辑组名。 */
internal const val REMOTE_PREFS_GROUP = "hk.uwu.reareye_preferences"

/** RemotePreferences 中记录旧 Yuki v2 迁移完成状态的保留键。 */
internal const val LEGACY_PREFS_MIGRATION_STATUS_KEY = "__reareye_yuki_prefs_migration_status"

/** 旧 Yuki 偏好 v2 迁移完成标记，保留用于识别旧状态。 */
internal const val LEGACY_PREFS_MIGRATION_COMPLETED_V2 = "completed:v2"

/** authoritative full-map 修复完成标记，保留用于识别旧状态。 */
internal const val LEGACY_PREFS_MIGRATION_COMPLETED_V3 = "completed:v3"

/** 远程 blob 迁移完成标记；只有 v4 才允许跳过迁移。 */
internal const val LEGACY_PREFS_MIGRATION_COMPLETED_V4 = "completed:v4"

/**
 * libxposed RemoteFile 文件名校验。
 *
 * API 102 要求名称只能是单级安全文件名，不能包含路径分隔符或点；统一在 UI、迁移和 Hook
 * 读取边界校验，避免不同实现对同一引用产生不一致解释。
 */
object RemoteFileName {
    private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,128}")

    fun requireValid(name: String): String {
        require(SAFE_NAME.matches(name)) {
            "Invalid libxposed remote file name: $name"
        }
        return name
    }
}

/**
 * Hook 偏好接口。
 *
 * Hook 进程只能通过 libxposed 远程偏好读取模块设置，模块 UI 则通过同一接口接入
 * service RemotePreferences。接口保留当前代码实际使用的 get/edit/native/all 语义，避免业务模块
 * 感知底层存储实现。
 */
interface HookPrefs {
    /** 读取字符串值。 */
    fun getString(key: String, defaultValue: String = ""): String

    /** 读取字符串集合值。 */
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String>

    /** 读取整数值。 */
    fun getInt(key: String, defaultValue: Int): Int

    /** 读取长整数值。 */
    fun getLong(key: String, defaultValue: Long): Long

    /** 读取浮点值。 */
    fun getFloat(key: String, defaultValue: Float): Float

    /** 读取布尔值。 */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /** 判断键是否存在。 */
    fun contains(key: String): Boolean

    /** 返回可供 JSON/动态配置读取的快照。 */
    fun all(): Map<String, Any?>

    /**
     * 判断当前偏好是否已经连接到远程 service。
     *
     * 本地 SharedPreferences 适配默认视为已就绪；libxposed 远程适配会在 service 未绑定时返回
     * false，使 UI 不会把 service 不可用时的空默认值当作最终配置。
     */
    fun isRemoteReady(): Boolean = true

    /** UI/service 写入 RemoteFile；非远程实现必须显式失败。 */
    fun writeRemoteFile(name: String, bytes: ByteArray): Boolean =
        error("RemoteFile write is unavailable for this HookPrefs implementation: $name")

    /** UI/service 删除 RemoteFile；非远程实现必须显式失败。 */
    fun deleteRemoteFile(name: String): Boolean =
        error("RemoteFile delete is unavailable for this HookPrefs implementation: $name")

    /** UI/service 无快照清空整个 RemotePreferences 组，避免旧大 map 先过 Binder。 */
    fun clearRemotePreferences(): Boolean =
        error("RemotePreferences clear is unavailable for this HookPrefs implementation")

    /** Hook 侧复制 RemoteFile 到目标临时文件；非远程实现必须显式失败。 */
    fun copyRemoteFileTo(name: String, destination: File): Boolean =
        error("RemoteFile read is unavailable for this HookPrefs implementation: $name")

    /** 创建一次编辑事务。 */
    fun edit(): HookPrefsEditor

    /** 获取底层兼容视图；当前实现返回自身，保留 native 语义。 */
    fun native(): HookPrefs = this

    /** 执行编辑事务并提交，兼容旧的 prefs.native().edit { ... } 写法。 */
    fun edit(action: HookPrefsEditor.() -> Unit) {
        edit().apply {
            action()
            apply()
        }
    }
}

/**
 * 偏好编辑器接口。
 *
 * 成员方法返回自身以保留 SharedPreferences.Editor 的链式调用形式。
 */
interface HookPrefsEditor {
    /** 写入字符串。 */
    fun putString(key: String, value: String?): HookPrefsEditor

    /** 写入字符串集合。 */
    fun putStringSet(key: String, value: Set<String>?): HookPrefsEditor

    /** 写入整数。 */
    fun putInt(key: String, value: Int): HookPrefsEditor

    /** 写入长整数。 */
    fun putLong(key: String, value: Long): HookPrefsEditor

    /** 写入浮点数。 */
    fun putFloat(key: String, value: Float): HookPrefsEditor

    /** 写入布尔值。 */
    fun putBoolean(key: String, value: Boolean): HookPrefsEditor

    /** 删除键。 */
    fun remove(key: String): HookPrefsEditor

    /** 清空偏好。 */
    fun clear(): HookPrefsEditor

    /** 同步提交并返回是否成功。 */
    fun commit(): Boolean

    /** 异步提交。 */
    fun apply()
}

/**
 * SharedPreferences 适配实现。
 *
 * 该实现同时用于 libxposed getRemotePreferences 返回的偏好和模块 UI 本地偏好，
 * 统一复制 StringSet/Map，避免调用方修改底层缓存。
 */
class SharedPreferencesHookPrefs(
    private val delegate: SharedPreferences,
) : HookPrefs {
    override fun getString(key: String, defaultValue: String): String =
        delegate.getString(key, defaultValue) ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        delegate.getStringSet(key, defaultValue)?.toSet() ?: defaultValue.toSet()

    override fun getInt(key: String, defaultValue: Int): Int = delegate.getInt(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        delegate.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        delegate.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        delegate.getBoolean(key, defaultValue)

    override fun contains(key: String): Boolean = delegate.contains(key)

    override fun all(): Map<String, Any?> = delegate.all.mapValues { (_, value) ->
        when (value) {
            is Set<*> -> value.toSet()
            else -> value
        }
    }

    override fun edit(): HookPrefsEditor = SharedPreferencesHookPrefsEditor(delegate.edit())

    /** 暴露给需要 SharedPreferences API 的适配点，核心 Hook 不直接依赖它。 */
    fun sharedPreferences(): SharedPreferences = delegate
}

/**
 * Hook 进程中的只读远程偏好视图。
 *
 * libxposed 的远程偏好对象本身带有 SharedPreferences.Editor 能力，但目标进程只能读取
 * UI/service 发布的配置。编辑器在第一次写入时记录并抛出异常，避免业务模块悄悄把运行时状态
 * 写回远程配置；需要持久化的配置必须经 UI/service/Binder 完成。
 */
class ReadOnlyHookPrefs(
    private val delegate: HookPrefs,
    private val name: String,
    private val remoteFileProvider: (String) -> ParcelFileDescriptor,
    private val logger: HookLogger,
) : HookPrefs {
    override fun getString(key: String, defaultValue: String): String =
        delegate.getString(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        delegate.getStringSet(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int = delegate.getInt(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        delegate.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        delegate.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        delegate.getBoolean(key, defaultValue)

    override fun contains(key: String): Boolean = delegate.contains(key)

    override fun all(): Map<String, Any?> = delegate.all().mapValues { (_, value) ->
        when (value) {
            is Set<*> -> value.toSet()
            else -> value
        }
    }

    override fun copyRemoteFileTo(name: String, destination: File): Boolean {
        val safeName = runCatching { RemoteFileName.requireValid(name) }
            .onFailure { logger.error("Rejected invalid remote file read name: $name", it) }
            .getOrNull() ?: return false
        if (destination.isDirectory) {
            val failure =
                IllegalArgumentException("RemoteFile destination is a directory: ${destination.absolutePath}")
            logger.error("Unable to copy remote file to directory: name=$safeName", failure)
            return false
        }
        return runCatching {
            destination.parentFile?.mkdirs()
            ParcelFileDescriptor.AutoCloseInputStream(remoteFileProvider(safeName)).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            true
        }.onFailure {
            runCatching { if (destination.exists()) destination.delete() }
                .onFailure { cleanupFailure ->
                    logger.warn(
                        "Unable to remove failed remote file destination: ${destination.absolutePath}",
                        cleanupFailure
                    )
                }
            logger.error(
                "Unable to copy remote file: name=$safeName destination=${destination.absolutePath}",
                it
            )
        }.getOrDefault(false)
    }

    override fun writeRemoteFile(name: String, bytes: ByteArray): Boolean =
        rejectRemoteFileWrite("writeRemoteFile($name)")

    override fun deleteRemoteFile(name: String): Boolean =
        rejectRemoteFileWrite("deleteRemoteFile($name)")

    private fun rejectRemoteFileWrite(operation: String): Nothing {
        val violation = IllegalStateException(
            "Hook process cannot modify remote files: name=$name operation=$operation",
        )
        logger.error("Rejected remote file write: name=$name operation=$operation", violation)
        throw violation
    }

    override fun edit(): HookPrefsEditor = ReadOnlyHookPrefsEditor(name)
}

private class ReadOnlyHookPrefsEditor(
    private val name: String,
) : HookPrefsEditor {
    private fun reject(operation: String): Nothing {
        val violation = IllegalStateException(
            "Hook process cannot modify remote preferences: name=$name operation=$operation"
        )
        YLog.error("Rejected remote preference write: name=$name operation=$operation", violation)
        throw violation
    }

    override fun putString(key: String, value: String?): HookPrefsEditor =
        reject("putString($key)")

    override fun putStringSet(key: String, value: Set<String>?): HookPrefsEditor =
        reject("putStringSet($key)")

    override fun putInt(key: String, value: Int): HookPrefsEditor = reject("putInt($key)")

    override fun putLong(key: String, value: Long): HookPrefsEditor = reject("putLong($key)")

    override fun putFloat(key: String, value: Float): HookPrefsEditor = reject("putFloat($key)")

    override fun putBoolean(key: String, value: Boolean): HookPrefsEditor =
        reject("putBoolean($key)")

    override fun remove(key: String): HookPrefsEditor = reject("remove($key)")

    override fun clear(): HookPrefsEditor = reject("clear")

    override fun commit(): Boolean = reject("commit")

    override fun apply() = reject("apply")
}

/**
 * Hook 宿主不提供远程偏好能力时的显式降级视图。
 *
 * 读取只能返回调用方给出的默认值，绝不伪造已有配置；任何写入都会记录目标诊断并立即失败。
 */
internal class UnavailableHookPrefs(
    private val name: String,
    private val target: String,
    private val framework: String,
    private val logger: HookLogger,
) : HookPrefs {
    override fun getString(key: String, defaultValue: String): String = defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        defaultValue.toSet()

    override fun getInt(key: String, defaultValue: Int): Int = defaultValue

    override fun getLong(key: String, defaultValue: Long): Long = defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float = defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue

    override fun contains(key: String): Boolean = false

    override fun all(): Map<String, Any?> = emptyMap()

    override fun writeRemoteFile(name: String, bytes: ByteArray): Boolean =
        unavailableRemoteFile("write", name, bytes.size)

    override fun deleteRemoteFile(name: String): Boolean = unavailableRemoteFile("delete", name, 0)

    override fun copyRemoteFileTo(name: String, destination: File): Boolean = unavailableRemoteFile(
        "read",
        name,
        destination.absolutePath.length,
    )

    private fun unavailableRemoteFile(operation: String, name: String, size: Int): Nothing {
        val failure = IllegalStateException(
            "Remote file capability unavailable: name=$name operation=$operation framework={$framework}",
        )
        logger.error(
            "Rejected remote file operation: name=$name operation=$operation size=$size " +
                    "target=$target framework={$framework}",
            failure,
        )
        throw failure
    }

    override fun edit(): HookPrefsEditor =
        UnavailableHookPrefsEditor(name, target, framework, logger)
}

private class UnavailableHookPrefsEditor(
    private val name: String,
    private val target: String,
    private val framework: String,
    private val logger: HookLogger,
) : HookPrefsEditor {
    private fun reject(operation: String): Nothing {
        val failure = IllegalStateException(
            "Remote preferences unavailable: name=$name target=$target operation=$operation framework={$framework}"
        )
        logger.error(
            "Rejected preference write because remote capability is unavailable: " +
                    "name=$name target=$target operation=$operation framework={$framework}",
            failure,
        )
        throw failure
    }

    override fun putString(key: String, value: String?): HookPrefsEditor = reject("putString($key)")

    override fun putStringSet(key: String, value: Set<String>?): HookPrefsEditor =
        reject("putStringSet($key)")

    override fun putInt(key: String, value: Int): HookPrefsEditor = reject("putInt($key)")

    override fun putLong(key: String, value: Long): HookPrefsEditor = reject("putLong($key)")

    override fun putFloat(key: String, value: Float): HookPrefsEditor = reject("putFloat($key)")

    override fun putBoolean(key: String, value: Boolean): HookPrefsEditor =
        reject("putBoolean($key)")

    override fun remove(key: String): HookPrefsEditor = reject("remove($key)")

    override fun clear(): HookPrefsEditor = reject("clear")

    override fun commit(): Boolean = reject("commit")

    override fun apply() = reject("apply")
}

/**
 * 远程偏好快照来源，用于隔离 service 生命周期并提供可控的远程偏好代际。
 */
internal fun interface RemotePreferencesProvider {
    /** 按逻辑偏好组名取得当前 service 代际的快照。 */
    fun snapshot(name: String): RemotePreferencesSnapshot?
}

/**
 * 模块 UI 的远程偏好适配。
 *
 * 适配器只委托 libxposed RemotePreferences。service 不可用时读取返回调用方默认值，写入通过
 * 显式失败编辑器记录诊断并抛出异常，绝不把配置写入本地文件。旧 Yuki 数据迁移由独立的 root
 * 迁移协调器按完成状态控制。Hook 进程不使用此类，仍然只通过 XposedModule.getRemotePreferences
 * 读取目标偏好。
 */
class XposedRemoteHookPrefs private constructor(
    private val name: String,
    private val remotePreferencesProvider: RemotePreferencesProvider,
) : HookPrefs {
    /** 使用生产环境的 libxposed service 远程偏好来源。 */
    constructor(name: String) : this(
        name = name,
        remotePreferencesProvider = RemotePreferencesProvider { preferenceName ->
            XposedModuleStatus.remotePreferencesSnapshot(preferenceName)
        },
    )

    companion object {
        /** 使用指定快照来源创建适配器，便于验证远程 service 可用性和代际变化。 */
        internal fun fromProvider(
            name: String,
            remotePreferencesProvider: RemotePreferencesProvider,
        ): XposedRemoteHookPrefs = XposedRemoteHookPrefs(
            name,
            remotePreferencesProvider,
        )
    }

    override fun getString(key: String, defaultValue: String): String =
        remotePreferences()?.getString(key, defaultValue) ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        remotePreferences()?.getStringSet(key, defaultValue)?.toSet() ?: defaultValue.toSet()

    override fun getInt(key: String, defaultValue: Int): Int =
        remotePreferences()?.getInt(key, defaultValue) ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long =
        remotePreferences()?.getLong(key, defaultValue) ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        remotePreferences()?.getFloat(key, defaultValue) ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        remotePreferences()?.getBoolean(key, defaultValue) ?: defaultValue

    override fun contains(key: String): Boolean = remotePreferences()?.contains(key) == true

    override fun all(): Map<String, Any?> = remotePreferences()?.all?.mapValues { (_, value) ->
        when (value) {
            is Set<*> -> value.toSet()
            else -> value
        }
    } ?: emptyMap()

    override fun isRemoteReady(): Boolean = remotePreferences() != null

    override fun writeRemoteFile(name: String, bytes: ByteArray): Boolean =
        XposedModuleStatus.writeRemoteFile(name, bytes)

    override fun deleteRemoteFile(name: String): Boolean =
        XposedModuleStatus.deleteRemoteFile(name)

    override fun clearRemotePreferences(): Boolean =
        XposedModuleStatus.deleteRemotePreferences(name)

    override fun copyRemoteFileTo(name: String, destination: File): Boolean =
        error("RemoteFile read is only available in the target Hook process: name=$name")

    override fun edit(): HookPrefsEditor = remotePreferences()?.let { remote ->
        SharedPreferencesHookPrefsEditor(remote.edit())
    } ?: UnavailableRemoteHookPrefsEditor(name)

    private fun remotePreferences(): SharedPreferences? = runCatching {
        remotePreferencesProvider.snapshot(name)?.preferences
    }.onFailure {
        YLog.warn("Unable to access remote preferences: name=$name", it)
    }.getOrNull()
}

private class UnavailableRemoteHookPrefsEditor(
    private val name: String,
) : HookPrefsEditor {
    private fun reject(operation: String): Nothing {
        val failure = IllegalStateException(
            "Remote preferences unavailable: name=$name operation=$operation",
        )
        YLog.error(
            "Rejected preference write because remote capability is unavailable: " +
                    "name=$name operation=$operation",
            failure,
        )
        throw failure
    }

    override fun putString(key: String, value: String?): HookPrefsEditor = reject("putString($key)")

    override fun putStringSet(key: String, value: Set<String>?): HookPrefsEditor =
        reject("putStringSet($key)")

    override fun putInt(key: String, value: Int): HookPrefsEditor = reject("putInt($key)")

    override fun putLong(key: String, value: Long): HookPrefsEditor = reject("putLong($key)")

    override fun putFloat(key: String, value: Float): HookPrefsEditor = reject("putFloat($key)")

    override fun putBoolean(key: String, value: Boolean): HookPrefsEditor =
        reject("putBoolean($key)")

    override fun remove(key: String): HookPrefsEditor = reject("remove($key)")

    override fun clear(): HookPrefsEditor = reject("clear")

    override fun commit(): Boolean = reject("commit")

    override fun apply() = reject("apply")
}

private class SharedPreferencesHookPrefsEditor(
    private val delegate: SharedPreferences.Editor,
) : HookPrefsEditor {
    override fun putString(key: String, value: String?): HookPrefsEditor {
        delegate.putString(key, value)
        return this
    }

    override fun putStringSet(key: String, value: Set<String>?): HookPrefsEditor {
        delegate.putStringSet(key, value)
        return this
    }

    override fun putInt(key: String, value: Int): HookPrefsEditor {
        delegate.putInt(key, value)
        return this
    }

    override fun putLong(key: String, value: Long): HookPrefsEditor {
        delegate.putLong(key, value)
        return this
    }

    override fun putFloat(key: String, value: Float): HookPrefsEditor {
        delegate.putFloat(key, value)
        return this
    }

    override fun putBoolean(key: String, value: Boolean): HookPrefsEditor {
        delegate.putBoolean(key, value)
        return this
    }

    override fun remove(key: String): HookPrefsEditor {
        delegate.remove(key)
        return this
    }

    override fun clear(): HookPrefsEditor {
        delegate.clear()
        return this
    }

    override fun commit(): Boolean = delegate.commit()

    override fun apply() {
        delegate.apply()
    }
}
