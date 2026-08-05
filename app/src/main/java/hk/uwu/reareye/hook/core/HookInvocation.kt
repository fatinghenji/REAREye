package hk.uwu.reareye.hook.core

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 一次 Hook 调用的可变上下文。
 *
 * libxposed Chain.getArgs() 返回不可变 List，因此这里复制出独立参数数组；proceed 和
 * invokeOriginal 会在调用前校验参数/返回类型，并解包 InvocationTargetException，保留旧
 * Yuki DSL 对原始异常的观察语义。
 */
class HookInvocation internal constructor(
    private val chain: XposedInterface.Chain,
    private val context: HookContext,
    private val synthetic: Boolean = false,
    private val removeHook: () -> Unit,
) {
    private var resultValue: Any? = null
    private var resultWasSet = false
    private var throwableValue: Throwable? = null

    /** 当前被 Hook 的可执行成员。 */
    val executable: Executable
        get() = chain.executable

    /** 当前成员所在类。 */
    val instanceClass: Class<*>
        get() = instanceOrNull?.javaClass ?: executable.declaringClass

    /** 当前实例；静态成员没有实例时立即失败。 */
    val instance: Any
        get() = instanceOrNull
            ?: error("Hooked member has no instance: ${executable.toGenericString()}")

    /** 当前实例；静态成员或构造器执行阶段可能返回 null。 */
    val instanceOrNull: Any?
        get() = chain.thisObject

    /** 按调用点泛型读取当前实例。 */
    @Suppress("UNCHECKED_CAST")
    fun <T> instance(): T = instance as T

    /** 按调用点泛型读取可空实例。 */
    @Suppress("UNCHECKED_CAST")
    fun <T> instanceOrNull(): T? = instanceOrNull as? T

    /** 当前目标 Application。 */
    val appContext
        get() = context.appContext

    /** 当前真实系统 Context；不可用时记录并失败。 */
    val systemContext
        get() = context.systemContext

    /** 当前目标包名。 */
    val packageName: String
        get() = context.packageName

    /** 当前目标进程名。 */
    val processName: String
        get() = context.processName

    /** 当前目标 ApplicationInfo。 */
    val appInfo
        get() = context.appInfo

    /** 当前目标 ClassLoader。 */
    val classLoader: ClassLoader
        get() = context.classLoader

    /** 当前目标偏好。 */
    val prefs: HookPrefs
        get() = context.prefs

    /** 当前目标日志。 */
    val logger: HookLogger
        get() = context.logger

    /** 当前设备是否为后屏设备。 */
    val isRearDevice: Boolean
        get() = context.isRearDevice

    /** 可变参数快照；修改后通过 proceed 或 invokeOriginal 显式传递。 */
    val args: Array<Any?> = chain.args.toTypedArray()

    /** 当前结果；赋值会阻止后续原始调用，并覆盖原始异常。 */
    var result: Any?
        get() = resultValue
        set(value) {
            setResult(value)
        }

    /** 当前原始调用/继续链产生的异常；after 可观察并通过设置 result 覆盖。 */
    val throwable: Throwable?
        get() = throwableValue

    /** 是否已经显式设置了结果或调用了继续/原始调用。 */
    internal val hasResult: Boolean
        get() = resultWasSet

    /** 是否已经尝试过原始调用；避免 invokeOriginal 失败后重复执行原方法。 */
    internal var originalCallAttempted: Boolean = false

    /** 泛型读取当前结果。 */
    @Suppress("UNCHECKED_CAST")
    fun <T> result(): T? = resultValue as? T

    /** 返回参数包装器，保留 args(index) DSL。 */
    fun args(index: Int): ArgumentValue {
        require(index in args.indices) {
            "Argument index $index out of bounds for ${args.size} arguments of ${executable.toGenericString()}"
        }
        return ArgumentValue(this, index)
    }

    /** 继续执行剩余 Hook 链和原方法。 */
    fun proceed(): Any? = proceed(args)

    /** 使用指定参数继续执行剩余 Hook 链和原方法。 */
    fun proceed(newArgs: Array<Any?>): Any? {
        validateArguments(newArgs)
        originalCallAttempted = true
        return try {
            val value = chain.proceed(newArgs.copyOf())
            setResult(value)
            value
        } catch (throwable: Throwable) {
            setThrowable(throwable)
            throw throwable
        }
    }

    /** 调用未经过任何 Hook 的原始成员。 */
    fun invokeOriginal(vararg newArgs: Any?): Any? {
        check(!synthetic) {
            "Synthetic lifecycle invocation cannot invoke the original member: ${executable.toGenericString()}"
        }
        val arguments: Array<Any?> = if (newArgs.isEmpty()) {
            args.copyOf()
        } else {
            newArgs.toList().toTypedArray()
        }
        validateArguments(arguments)
        originalCallAttempted = true
        if (executable is Method && !Modifier.isStatic(executable.modifiers) && instanceOrNull == null) {
            error("Non-static method has no receiver: ${executable.toGenericString()}")
        }
        return try {
            val value = when (val member = executable) {
                is Method -> {
                    val invoker = context.hooksInvoker(member)
                    invoker.invoke(instanceOrNull, *arguments)
                }

                is Constructor<*> -> {
                    val invoker = context.constructorInvoker(member)
                    invoker.newInstance(*arguments)
                }

                else -> error("Unsupported executable type: ${member::class.java.name}")
            }
            setResult(value)
            value
        } catch (wrapped: InvocationTargetException) {
            val cause = wrapped.targetException ?: wrapped.cause ?: wrapped
            setThrowable(cause)
            throw cause
        } catch (throwable: Throwable) {
            setThrowable(throwable)
            throw throwable
        }
    }

    /** 设置结果为 true。 */
    fun resultTrue() {
        setResult(true)
    }

    /** 设置结果为 false。 */
    fun resultFalse() {
        setResult(false)
    }

    /** 幂等移除当前 Hook。 */
    fun removeSelf() {
        removeHook()
    }

    internal fun setResult(value: Any?) {
        validateReturnValue(value)
        resultValue = value
        resultWasSet = true
        throwableValue = null
    }

    internal fun setThrowable(throwable: Throwable) {
        throwableValue = throwable
        resultWasSet = false
    }

    internal fun resultOrNull(): Any? = resultValue

    private fun validateArguments(values: Array<Any?>) {
        val parameterTypes = when (val member = executable) {
            is Method -> member.parameterTypes
            is Constructor<*> -> member.parameterTypes
            else -> error("Unsupported executable type: ${member::class.java.name}")
        }
        require(values.size == parameterTypes.size) {
            "Expected ${parameterTypes.size} arguments but received ${values.size} for ${executable.toGenericString()}"
        }
        parameterTypes.forEachIndexed { index, type ->
            val value = values[index]
            require(isCompatible(type, value)) {
                "Argument[$index] type ${value?.javaClass?.name ?: "null"} is incompatible with " +
                        "${type.name} for ${executable.toGenericString()}"
            }
        }
    }

    private fun validateReturnValue(value: Any?) {
        val method = executable as? Method ?: return
        val returnType = method.returnType
        if (returnType == Void.TYPE) {
            require(value == null) { "Void method cannot return ${value?.javaClass?.name ?: "null"}" }
            return
        }
        require(isCompatible(returnType, value)) {
            "Return value type ${value?.javaClass?.name ?: "null"} is incompatible with " +
                    "${returnType.name} for ${executable.toGenericString()}"
        }
    }

    private fun isCompatible(type: Class<*>, value: Any?): Boolean {
        if (value == null) return !type.isPrimitive
        if (!type.isPrimitive) return type.isInstance(value)
        return when (type) {
            Boolean::class.javaPrimitiveType -> value is Boolean
            Byte::class.javaPrimitiveType -> value is Byte
            Short::class.javaPrimitiveType -> value is Short
            Int::class.javaPrimitiveType -> value is Int
            Long::class.javaPrimitiveType -> value is Long
            Float::class.javaPrimitiveType -> value is Float
            Double::class.javaPrimitiveType -> value is Double
            Char::class.javaPrimitiveType -> value is Char
            else -> false
        }
    }

    /** 参数类型转换和修改器。 */
    class ArgumentValue internal constructor(
        private val invocation: HookInvocation,
        private val index: Int,
    ) {
        private val value: Any?
            get() = invocation.args[index]

        /** 以目标类型安全转换，失败返回 null。 */
        @Suppress("UNCHECKED_CAST")
        fun <T> cast(): T? = value as? T

        /** 转换为 Int。 */
        fun int(): Int =
            (value as? Number)?.toInt() ?: error("Argument[$index] is not a number: $value")

        /** 转换为 Long。 */
        fun long(): Long =
            (value as? Number)?.toLong() ?: error("Argument[$index] is not a number: $value")

        /** 转换为 Boolean。 */
        fun boolean(): Boolean =
            value as? Boolean ?: error("Argument[$index] is not Boolean: $value")

        /** 转换为 String。 */
        fun string(): String = value as? String ?: error("Argument[$index] is not String: $value")

        /** 返回原始对象。 */
        fun any(): Any? = value

        /** 返回数组并进行泛型检查。 */
        @Suppress("UNCHECKED_CAST")
        fun <T> array(): Array<T>? = value as? Array<T>

        /** 返回列表并进行泛型检查。 */
        @Suppress("UNCHECKED_CAST")
        fun <T> list(): List<T>? = value as? List<T>

        /** 替换参数。 */
        fun <T> set(newValue: T) {
            invocation.args[index] = newValue
        }

        /** 将参数设置为 null。 */
        fun setNull() {
            invocation.args[index] = null
        }

        /** 将参数设置为 true。 */
        fun setTrue() {
            invocation.args[index] = true
        }

        /** 将参数设置为 false。 */
        fun setFalse() {
            invocation.args[index] = false
        }
    }
}

/** 创建只用于热重放的生命周期调用，不持有旧代模块或旧目标对象。 */
internal fun syntheticLifecycleInvocation(
    context: HookContext,
    executable: Executable,
    thisObject: Any,
    args: Array<Any?>,
): HookInvocation = HookInvocation(
    chain = SyntheticLifecycleChain(executable, thisObject, args),
    context = context,
    synthetic = true,
    removeHook = {},
)

private class SyntheticLifecycleChain(
    private val executableValue: Executable,
    private val thisObjectValue: Any,
    initialArgs: Array<Any?>,
) : XposedInterface.Chain {
    private var arguments = initialArgs.copyOf()

    override fun getExecutable(): Executable = executableValue

    override fun getThisObject(): Any = thisObjectValue

    override fun getArgs(): MutableList<Any> = arguments.map {
        it ?: error("Synthetic lifecycle argument must not be null")
    }.toMutableList()

    override fun getArg(index: Int): Any = arguments[index]
        ?: error("Synthetic lifecycle argument must not be null: index=$index")

    override fun proceed(): Any? = null

    override fun proceed(args: Array<Any?>): Any? {
        arguments = args.copyOf()
        return null
    }

    override fun proceedWith(thisObject: Any): Any? = null

    override fun proceedWith(thisObject: Any, args: Array<Any>): Any? {
        arguments = args.map { it as Any? }.toTypedArray()
        return null
    }
}

private fun HookContext.hooksInvoker(method: Method): XposedInterface.Invoker<*, Method> =
    (hooks as? InvokerProvider)?.methodInvoker(method)
        ?: error("Hook context does not expose an origin invoker for ${method.toGenericString()}")

private fun <T> HookContext.constructorInvoker(
    constructor: Constructor<T>,
): XposedInterface.CtorInvoker<T> =
    (hooks as? InvokerProvider)?.constructorInvoker(constructor)
        ?: error("Hook context does not expose an origin invoker for ${constructor.toGenericString()}")

internal interface InvokerProvider {
    fun methodInvoker(method: Method): XposedInterface.Invoker<*, Method>
    fun <T> constructorInvoker(constructor: Constructor<T>): XposedInterface.CtorInvoker<T>
}
