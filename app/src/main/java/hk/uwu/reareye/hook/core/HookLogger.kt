package hk.uwu.reareye.hook.core

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Hook 运行时日志接口。
 *
 * 运行时必须在安装失败、目标解析失败或回调失败时留下可观测日志；将日志抽成接口，
 * 使核心逻辑不依赖具体宿主环境，同时允许入口把日志转发给 libxposed。
 */
interface HookLogger {
    /** 输出调试信息，用于记录目标和 Hook 安装路径。 */
    fun debug(message: String, throwable: Throwable? = null)

    /** 输出普通运行信息，用于记录生命周期和功能分发。 */
    fun info(message: String, throwable: Throwable? = null)

    /** 输出警告信息，用于记录可继续但需要关注的异常状态。 */
    fun warn(message: String, throwable: Throwable? = null)

    /** 输出错误信息，用于记录即将抛出或已经失败的操作。 */
    fun error(message: String, throwable: Throwable? = null)
}

/**
 * libxposed API 102 日志实现。
 *
 * 只依赖 XposedInterface.log，不依赖旧 rovo89 Xposed 类型，确保 Hook 进程和模块 UI
 * 的日志边界可以独立替换。
 */
class XposedHookLogger(
    private val xposed: XposedInterface,
    private val tag: String = "REAREye",
) : HookLogger {
    override fun debug(message: String, throwable: Throwable?) =
        write(Log.DEBUG, message, throwable)

    override fun info(message: String, throwable: Throwable?) = write(Log.INFO, message, throwable)

    override fun warn(message: String, throwable: Throwable?) = write(Log.WARN, message, throwable)

    override fun error(message: String, throwable: Throwable?) =
        write(Log.ERROR, message, throwable)

    private fun write(level: Int, message: String, throwable: Throwable?) {
        if (throwable == null) {
            xposed.log(level, tag, message)
        } else {
            xposed.log(level, tag, message, throwable)
        }
    }
}

/**
 * 未连接 Xposed 入口时的日志实现。
 *
 * 该实现仅供模块普通进程初始化阶段使用；一旦 XposedModule 加载完成，入口必须调用
 * [YLog.install] 切换到 [XposedHookLogger]。
 */
private class AndroidHookLogger : HookLogger {
    override fun debug(message: String, throwable: Throwable?) {
        Log.d("REAREye", message, throwable)
    }

    override fun info(message: String, throwable: Throwable?) {
        Log.i("REAREye", message, throwable)
    }

    override fun warn(message: String, throwable: Throwable?) {
        Log.w("REAREye", message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        Log.e("REAREye", message, throwable)
    }
}

/**
 * YLog 兼容门面。
 *
 * 现有 Hook 模块只依赖 debug/info/warn/error 四个级别，因此门面保留这些调用习惯，
 * 但底层已经完全映射到项目内日志接口，不再暴露 YukiHookAPI 类型。
 */
object YLog {
    @Volatile
    private var logger: HookLogger = AndroidHookLogger()

    /** 将日志输出切换到当前 libxposed 入口。 */
    fun install(logger: HookLogger) {
        this.logger = logger
    }

    /** 输出 debug 文本或异常。 */
    fun debug(message: Any?) = logger.debug(message.toString())

    /** 输出 info 文本或异常。 */
    fun info(message: Any?) = logger.info(message.toString())

    /** 输出 warn 文本或异常。 */
    fun warn(message: Any?) {
        if (message is Throwable) logger.warn(message.message.orEmpty(), message)
        else logger.warn(message.toString())
    }

    /** 输出 error 文本或异常。 */
    fun error(message: Any?) {
        if (message is Throwable) logger.error(message.message.orEmpty(), message)
        else logger.error(message.toString())
    }

    /** 输出带异常对象的 debug 文本。 */
    fun debug(message: Any?, throwable: Throwable?) = logger.debug(message.toString(), throwable)

    /** 输出带异常对象的 info 文本。 */
    fun info(message: Any?, throwable: Throwable?) = logger.info(message.toString(), throwable)

    /** 输出带异常对象的 warn 文本。 */
    fun warn(message: Any?, throwable: Throwable?) = logger.warn(message.toString(), throwable)

    /** 输出带异常对象的 error 文本。 */
    fun error(message: Any?, throwable: Throwable?) = logger.error(message.toString(), throwable)
}
