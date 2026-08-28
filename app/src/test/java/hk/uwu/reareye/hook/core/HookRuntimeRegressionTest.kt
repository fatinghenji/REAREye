package hk.uwu.reareye.hook.core

import android.app.AppComponentFactory
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.highcapable.kavaref.KavaRef.Companion.resolve
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HookRuntimeRegressionTest {
    @Test
    fun unavailableRemotePreferencesKeepTargetContextUsableAndRejectWritesWithLogs() {
        val logger = RecordingHookLogger()
        var remoteCalls = 0
        val framework = HookFrameworkInfo(
            name = "embedded-test",
            apiVersion = XposedInterface.API_102,
            version = "102-test",
            versionCode = 102,
            properties = XposedInterface.PROP_CAP_REMOTE,
        )
        val prefs = createRemoteHookPrefs(
            group = "hk.uwu.reareye_preferences",
            target = "test.package|test.package|10000|/data/app/test.apk",
            framework = framework,
            logger = logger,
        ) {
            remoteCalls++
            throw UnsupportedOperationException("embedded host has no remote preferences")
        }
        val context = MutableHookContext(
            packageName = "test.package",
            processName = "test.package",
            appInfo = testApplicationInfo("test.package", "test.package"),
            classLoader = javaClass.classLoader!!,
            isSystemServer = false,
            prefs = prefs,
            hooks = NoopHookRegistry(),
            logger = logger,
            systemContextProvider = { error("not used") },
        )

        assertNotNull(context)
        assertEquals(1, remoteCalls)
        assertEquals("fallback", context.prefs.getString("missing", "fallback"))
        assertEquals(setOf("default"), context.prefs.getStringSet("missing-set", setOf("default")))
        assertFalse(context.prefs.contains("missing"))

        val writeFailure = runCatching {
            context.prefs.edit().putBoolean("enabled", true)
        }.exceptionOrNull()
        assertTrue(writeFailure is IllegalStateException)
        assertTrue(logger.errors.any { it.contains("Rejected preference write") })
        assertTrue(logger.errors.any { it.contains("embedded-test") })
    }

    @Test
    fun oneCoreLifecycleOriginFailureDoesNotBlockBusinessModule() {
        val xposed = FakeXposedInterface(
            frameworkProperties = 0L,
            failHookIds = { it.contains("core:application.attachBaseContext.base") },
        )
        val runtime = HookRuntimeImpl(xposed, listOf({ RuntimeProbeScope() }))

        runtime.onPackageReady(object : XposedModuleInterface.PackageReadyParam {
            override fun getPackageName(): String = "com.xiaomi.subscreencenter"
            override fun getApplicationInfo(): ApplicationInfo =
                testApplicationInfo("com.xiaomi.subscreencenter", "com.xiaomi.subscreencenter")

            override fun isFirstPackage(): Boolean = true
            override fun getDefaultClassLoader(): ClassLoader = javaClass.classLoader!!
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
            override fun getAppComponentFactory(): AppComponentFactory = AppComponentFactory()
        })

        assertTrue(xposed.installedHandles.any { it.id.orEmpty().contains("RuntimeProbeModule") })
        assertTrue(xposed.logs.any { it.contains("Core lifecycle Hook rejected") })
        assertTrue(xposed.logs.any { it.contains("Hook target installed") && it.contains("hooks=") })
    }

    @Test
    fun hotReloadUsesSavedTargetDescriptorAndAtomicallyReplacesOldHandles() {
        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        val oldRuntime = HookRuntimeImpl(oldXposed, listOf({ RuntimeProbeScope() }))
        oldRuntime.onSystemServerStarting(object : XposedModuleInterface.SystemServerStartingParam {
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
        })
        val oldHandles = oldXposed.installedHandles.toList()
        assertTrue(oldHandles.isNotEmpty())

        var savedState: Any? = null
        val accepted = oldRuntime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
            override fun getExtras(): Bundle = Bundle()
            override fun setSavedInstanceState(state: Any?) {
                savedState = state
            }
        })
        assertTrue(oldXposed.logs.joinToString("\n"), accepted)
        assertTrue(savedState is Bundle)

        val newXposed = FakeXposedInterface(frameworkProperties = 0L)
        val newRuntime = HookRuntimeImpl(newXposed, listOf({ RuntimeProbeScope() }))
        newRuntime.onHotReloaded(object : XposedModuleInterface.HotReloadedParam {
            override fun isSystemServer(): Boolean = true
            override fun getProcessName(): String = "system_server"
            override fun getExtras(): Bundle = Bundle()
            override fun getSavedInstanceState(): Any = savedState!!
            override fun getOldHookHandles(): List<XposedInterface.HookHandle> = oldHandles
        })

        val replaced = oldHandles.filterIsInstance<FakeHookHandle>()
        assertTrue(replaced.all { it.replaceCalls == 1 })
        assertTrue(replaced.all { it.unhookCalls == 0 })
        assertTrue(newXposed.logs.any { it.contains("Hot reload target rebuilt") })
        assertTrue(newXposed.logs.any { it.contains("succeeded=1") && it.contains("failed=0") })
    }

    @Test
    fun replacingOldHandleChangesActuallyExecutedHookerBehavior() {
        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        HookRuntimeImpl(oldXposed, listOf({ RuntimeProbeScope() })).onSystemServerStarting(
            object : XposedModuleInterface.SystemServerStartingParam {
                override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
            }
        )
        val oldHandle = oldXposed.installedHandles.filterIsInstance<FakeHookHandle>().single()
        assertEquals("origin", oldHandle.invoke())

        val newXposed = FakeXposedInterface(frameworkProperties = 0L)
        val registry = HookRegistryImpl(newXposed, RecordingHookLogger(), listOf(oldHandle))
        registry.install(
            id = requireNotNull(oldHandle.id),
            executable = oldHandle.originExecutable,
            hooker = XposedInterface.Hooker { "replacement" },
        )

        assertEquals(1, oldHandle.replaceCalls)
        assertEquals("replacement", oldHandle.invoke())
    }

    @Test
    fun irreversibleCleanupFailureStillEntersNewGenerationInsteadOfReusingOldTarget() {
        val failingModule = CleanupFailingProbeModule()
        val xposed = FakeXposedInterface(frameworkProperties = 0L)
        val runtime = HookRuntimeImpl(xposed, listOf({ FixedScope(failingModule) }))
        runtime.onSystemServerStarting(object : XposedModuleInterface.SystemServerStartingParam {
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
        })

        val accepted = runtime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
            override fun getExtras(): Bundle = Bundle()
            override fun setSavedInstanceState(state: Any?) = Unit
        })

        assertTrue(accepted)
        assertEquals(1, failingModule.cleanupCalls)
        assertFalse(failingModule.gateOpen)
        assertTrue(xposed.logs.any { it.contains("commitFailures=1") })
        assertTrue(xposed.logs.any { it.contains("retain failed target state") })
    }

    @Test
    fun preservedFrozenOldHandleProceedsWithoutExecutingOldModuleCallback() {
        val countingModule = CountingProbeModule()
        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        val oldRuntime = HookRuntimeImpl(oldXposed, listOf({ FixedScope(countingModule) }))
        oldRuntime.onSystemServerStarting(object : XposedModuleInterface.SystemServerStartingParam {
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
        })
        val oldHandle = oldXposed.installedHandles.filterIsInstance<FakeHookHandle>().single()
        assertEquals("origin", oldHandle.invoke())
        assertEquals(1, countingModule.callbackCalls)

        assertTrue(oldRuntime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
            override fun getExtras(): Bundle = Bundle()
            override fun setSavedInstanceState(state: Any?) = Unit
        }))
        assertFalse(countingModule.isGenerationActive)

        HookRuntimeImpl(
            FakeXposedInterface(frameworkProperties = 0L),
            listOf({ RuntimeProbeScope() }),
        ).onHotReloaded(object : XposedModuleInterface.HotReloadedParam {
            override fun isSystemServer(): Boolean = true
            override fun getProcessName(): String = "system_server"
            override fun getExtras(): Bundle = Bundle()
            override fun getSavedInstanceState(): Any? = null
            override fun getOldHookHandles(): List<XposedInterface.HookHandle> = listOf(oldHandle)
        })

        assertEquals(0, oldHandle.unhookCalls)
        assertEquals("origin", oldHandle.invoke())
        assertEquals(1, countingModule.callbackCalls)
    }

    @Test
    fun missingOrDamagedSavedStatePreservesAllUnownedOldHandles() {
        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        val oldRuntime = HookRuntimeImpl(oldXposed, listOf({ RuntimeProbeScope() }))
        oldRuntime.onSystemServerStarting(
            object : XposedModuleInterface.SystemServerStartingParam {
                override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
            }
        )
        val oldHandles = oldXposed.installedHandles.filterIsInstance<FakeHookHandle>()
        var validSavedState: Any? = null
        assertTrue(oldRuntime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
            override fun getExtras(): Bundle = Bundle()
            override fun setSavedInstanceState(state: Any?) {
                validSavedState = state
            }
        }))
        val newXposed = FakeXposedInterface(frameworkProperties = 0L)
        val newRuntime = HookRuntimeImpl(newXposed, listOf({ RuntimeProbeScope() }))

        newRuntime.onHotReloaded(object : XposedModuleInterface.HotReloadedParam {
            override fun isSystemServer(): Boolean = true
            override fun getProcessName(): String = "system_server"
            override fun getExtras(): Bundle = Bundle()
            override fun getSavedInstanceState(): Any? = null
            override fun getOldHookHandles(): List<XposedInterface.HookHandle> = oldHandles
        })
        newRuntime.onHotReloaded(object : XposedModuleInterface.HotReloadedParam {
            override fun isSystemServer(): Boolean = true
            override fun getProcessName(): String = "system_server"
            override fun getExtras(): Bundle = Bundle()
            override fun getSavedInstanceState(): Any = Bundle()
            override fun getOldHookHandles(): List<XposedInterface.HookHandle> = oldHandles
        })
        newRuntime.onHotReloaded(object : XposedModuleInterface.HotReloadedParam {
            override fun isSystemServer(): Boolean = false
            override fun getProcessName(): String = "different_process"
            override fun getExtras(): Bundle = Bundle()
            override fun getSavedInstanceState(): Any = validSavedState!!
            override fun getOldHookHandles(): List<XposedInterface.HookHandle> = oldHandles
        })

        assertTrue(oldHandles.all { it.unhookCalls == 0 })
        assertTrue(newXposed.logs.any { it.contains("preserve unknown old handles") })
        assertTrue(newXposed.logs.any { it.contains("preserve old handles") })
    }

    @Test
    fun damagedOrDuplicateDescriptorSetRejectsWholeProcessWithoutReplacingHandles() {
        val countingModule = CountingProbeModule()
        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        val oldRuntime = HookRuntimeImpl(oldXposed, listOf({ FixedScope(countingModule) }))
        oldRuntime.onSystemServerStarting(object : XposedModuleInterface.SystemServerStartingParam {
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
        })
        val oldHandles = oldXposed.installedHandles.filterIsInstance<FakeHookHandle>()
        var validSavedState: Bundle? = null
        assertTrue(oldRuntime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
            override fun getExtras(): Bundle = Bundle()
            override fun setSavedInstanceState(state: Any?) {
                validSavedState = state as Bundle
            }
        }))

        val valid = requireNotNull(validSavedState)
        val countMismatch = Bundle(valid).apply {
            putInt("targetCount", 2)
        }
        val validTargets = requireNotNull(valid.getBundle("targets"))
        val firstTarget = requireNotNull(validTargets.getBundle("target:0"))
        val duplicateTargets = Bundle(validTargets).apply {
            putBundle("target:1", Bundle(firstTarget))
        }
        val duplicate = Bundle(valid).apply {
            putInt("targetCount", 2)
            putBundle("targets", duplicateTargets)
        }

        listOf(countMismatch, duplicate).forEach { invalidState ->
            val newXposed = FakeXposedInterface(frameworkProperties = 0L)
            HookRuntimeImpl(newXposed, listOf({ RuntimeProbeScope() })).onHotReloaded(
                object : XposedModuleInterface.HotReloadedParam {
                    override fun isSystemServer(): Boolean = true
                    override fun getProcessName(): String = "system_server"
                    override fun getExtras(): Bundle = Bundle()
                    override fun getSavedInstanceState(): Any = invalidState
                    override fun getOldHookHandles(): List<XposedInterface.HookHandle> = oldHandles
                }
            )
            assertTrue(newXposed.logs.any { it.contains("failed atomic validation") })
        }

        assertTrue(oldHandles.all { it.replaceCalls == 0 })
        assertTrue(oldHandles.all { it.unhookCalls == 0 })
        assertEquals("origin", oldHandles.single().invoke())
        assertEquals(0, countingModule.callbackCalls)
    }

    @Test
    fun applicationInfoAndApplicationClassLoaderRoundTripThroughSavedState() {
        val expectedClassLoader = javaClass.classLoader!!
        val appInfo = testApplicationInfo(
            "com.xiaomi.subscreencenter",
            "com.xiaomi.subscreencenter",
        ).apply {
            uid = 12345
            sourceDir = "/data/app/ssc/base.apk"
            publicSourceDir = "/data/app/ssc/public.apk"
            splitSourceDirs = arrayOf("/data/app/ssc/split_a.apk", "/data/app/ssc/split_b.apk")
            dataDir = "/data/user/0/com.xiaomi.subscreencenter"
            nativeLibraryDir = "/data/app/ssc/lib"
            className = ReloadTestApplication::class.java.name
        }
        val directState = Bundle().apply { putReloadApplicationInfo(appInfo) }
        val directRoundTrip = directState.readReloadApplicationInfo(appInfo.packageName)
        assertApplicationInfoEquals(appInfo, directRoundTrip)

        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        val oldRuntime = HookRuntimeImpl(oldXposed, listOf({ RuntimeProbeScope() }))
        oldRuntime.onPackageReady(object : XposedModuleInterface.PackageReadyParam {
            override fun getPackageName(): String = appInfo.packageName
            override fun getApplicationInfo(): ApplicationInfo = appInfo
            override fun isFirstPackage(): Boolean = true
            override fun getDefaultClassLoader(): ClassLoader = expectedClassLoader
            override fun getClassLoader(): ClassLoader = expectedClassLoader
            override fun getAppComponentFactory(): AppComponentFactory = AppComponentFactory()
        })
        var savedState: Any? = null
        assertTrue(oldRuntime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
            override fun getExtras(): Bundle = Bundle()
            override fun setSavedInstanceState(state: Any?) {
                savedState = state
            }
        }))

        var restoredContext: HookContext? = null
        val newXposed = FakeXposedInterface(frameworkProperties = 0L)
        HookRuntimeImpl(
            newXposed,
            listOf({ CapturingRuntimeProbeScope { restoredContext = it } }),
        ).onHotReloaded(object : XposedModuleInterface.HotReloadedParam {
            override fun isSystemServer(): Boolean = false
            override fun getProcessName(): String = appInfo.processName
            override fun getExtras(): Bundle = Bundle()
            override fun getSavedInstanceState(): Any = savedState!!
            override fun getOldHookHandles(): List<XposedInterface.HookHandle> =
                oldXposed.installedHandles
        })

        val restored = requireNotNull(restoredContext)
        assertSame(expectedClassLoader, restored.classLoader)
        assertApplicationInfoEquals(appInfo, restored.appInfo)
        assertFalse(newXposed.logs.any { it.contains("ClassLoader unavailable") })
    }

    @Test
    fun failedReloadTargetDoesNotCleanAlreadyRebuiltTarget() {
        val oldXposed = FakeXposedInterface(frameworkProperties = 0L)
        val oldRuntime = HookRuntimeImpl(oldXposed, listOf({ RuntimeProbeScope() }))
        oldRuntime.onSystemServerStarting(object : XposedModuleInterface.SystemServerStartingParam {
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
        })
        oldRuntime.onPackageReady(object : XposedModuleInterface.PackageReadyParam {
            override fun getPackageName(): String = "com.xiaomi.subscreencenter"
            override fun getApplicationInfo(): ApplicationInfo =
                testApplicationInfo("com.xiaomi.subscreencenter", "system_server")

            override fun isFirstPackage(): Boolean = true
            override fun getDefaultClassLoader(): ClassLoader = javaClass.classLoader!!
            override fun getClassLoader(): ClassLoader = javaClass.classLoader!!
            override fun getAppComponentFactory(): AppComponentFactory = AppComponentFactory()
        })
        val oldHandles = oldXposed.installedHandles.filterIsInstance<FakeHookHandle>()
        assertTrue(oldHandles.size > 1)

        var savedState: Any? = null
        assertTrue(
            oldRuntime.onHotReloading(object : XposedModuleInterface.HotReloadingParam {
                override fun getExtras(): Bundle = Bundle()
                override fun setSavedInstanceState(state: Any?) {
                    savedState = state
                }
            })
        )

        val newXposed = FakeXposedInterface(frameworkProperties = 0L)
        HookRuntimeImpl(newXposed, listOf({ RuntimeProbeScope() })).onHotReloaded(
            object : XposedModuleInterface.HotReloadedParam {
                override fun isSystemServer(): Boolean = true
                override fun getProcessName(): String = "system_server"
                override fun getExtras(): Bundle = Bundle()
                override fun getSavedInstanceState(): Any = savedState!!
                override fun getOldHookHandles(): List<XposedInterface.HookHandle> = oldHandles
            }
        )

        val successfulTargetHandles = oldHandles.filter {
            it.id.orEmpty().startsWith("reareye:android|system_server|")
        }
        val failedTargetHandles = oldHandles.filter {
            it.id.orEmpty().startsWith("reareye:com.xiaomi.subscreencenter|system_server|")
        }
        assertTrue(successfulTargetHandles.isNotEmpty())
        assertTrue(successfulTargetHandles.all { handle ->
            handle.replaceCalls == 1 && handle.unhookCalls == 0 &&
                    handle.replacementHandles.all { it.unhookCalls == 0 }
        })
        assertTrue(failedTargetHandles.isNotEmpty())
        assertTrue(failedTargetHandles.any { handle ->
            handle.unhookCalls > 0 || handle.replacementHandles.any { it.unhookCalls > 0 }
        })
        assertTrue(
            newXposed.logs.joinToString("\n"),
            newXposed.logs.any { it.contains("succeeded=1") && it.contains("failed=1") },
        )
    }

    @Test
    fun statusRefreshPublishesOnlyEffectiveChangesAndObserverCanBeRemoved() {
        val tracker = ModuleActivationTracker()
        var sourceState = ModuleActivationState.NO_RUNNING_TARGET
        val observed = mutableListOf<ModuleActivationState>()
        val observer: (ModuleActivationState) -> Unit = { observed += it }

        tracker.observe(observer)
        tracker.bind { sourceState }
        sourceState = ModuleActivationState.ACTIVE
        tracker.refresh()
        tracker.refresh()
        tracker.removeObserver(observer)
        sourceState = ModuleActivationState.NO_RUNNING_TARGET
        tracker.refresh()

        assertEquals(
            listOf(
                ModuleActivationState.SERVICE_UNAVAILABLE,
                ModuleActivationState.NO_RUNNING_TARGET,
                ModuleActivationState.ACTIVE,
            ),
            observed,
        )
        assertEquals(ModuleActivationState.NO_RUNNING_TARGET, tracker.current())
    }

    private class RuntimeProbeScope : HookScope {
        override val hooks: List<HookModule>
            get() = listOf(RuntimeProbeModule())
    }

    private class FixedScope(
        private val module: HookModule,
    ) : HookScope {
        override val hooks: List<HookModule>
            get() = listOf(module)
    }

    private class CapturingRuntimeProbeScope(
        private val capture: (HookContext) -> Unit,
    ) : HookScope {
        override val hooks: List<HookModule>
            get() {
                capture(HookEnvironment.requireContext())
                return listOf(RuntimeProbeModule())
            }
    }

    private open class RuntimeProbeModule : HookModule() {
        override fun onHook() {
            loadSystem { installProbeHook() }
            loadApp("com.xiaomi.subscreencenter") { installProbeHook() }
        }

        private fun installProbeHook() {
            RuntimeProbeOrigin::class.java.name.toClass().resolve().firstMethod {
                name = "value"
                parameterCount = 0
            }.hook().before { }
        }
    }

    private class CleanupFailingProbeModule : RuntimeProbeModule() {
        var cleanupCalls: Int = 0
        val gateOpen: Boolean
            get() = reloadGenerationGate.isOpen()

        override fun onReloading(): Boolean {
            cleanupCalls++
            return false
        }
    }

    private class CountingProbeModule : HookModule() {
        var callbackCalls: Int = 0

        override fun onHook() {
            loadSystem {
                RuntimeProbeOrigin::class.java.name.toClass().resolve().firstMethod {
                    name = "value"
                    parameterCount = 0
                }.hook().before { callbackCalls++ }
            }
        }
    }

    private class RuntimeProbeOrigin {
        fun value(): String = "origin"
    }

    private class ReloadTestApplication : Application() {
        override fun attachBaseContext(base: Context?) {
            super.attachBaseContext(base)
        }
    }

    private class FakeXposedInterface(
        private val frameworkProperties: Long,
        private val failHookIds: (String) -> Boolean = { false },
    ) : XposedInterface {
        val installedHandles = mutableListOf<XposedInterface.HookHandle>()
        val logs = mutableListOf<String>()

        override fun getApiVersion(): Int = XposedInterface.API_102
        override fun getFrameworkName(): String = "fake-libxposed"
        override fun getFrameworkVersion(): String = "102-test"
        override fun getFrameworkVersionCode(): Long = 102
        override fun getFrameworkProperties(): Long = frameworkProperties

        override fun hook(executable: Executable): XposedInterface.HookBuilder =
            FakeHookBuilder(executable, failHookIds, installedHandles)

        override fun hookClassInitializer(clazz: Class<*>): XposedInterface.HookBuilder =
            error("class initializer hooks are not used")

        override fun deoptimize(executable: Executable): Boolean = true

        override fun getInvoker(method: Method): XposedInterface.Invoker<*, Method> =
            error("origin invoker is not used")

        override fun <T : Any?> getInvoker(constructor: Constructor<T>): XposedInterface.CtorInvoker<T> =
            error("constructor invoker is not used")

        override fun log(level: Int, tag: String?, message: String) {
            logs += message
        }

        override fun log(level: Int, tag: String?, message: String, throwable: Throwable?) {
            logs += "$message: ${throwable?.javaClass?.simpleName.orEmpty()}: ${throwable?.message.orEmpty()}"
        }

        override fun getModuleApplicationInfo(): ApplicationInfo =
            testApplicationInfo("hk.uwu.reareye", "hk.uwu.reareye")

        override fun getRemotePreferences(name: String): SharedPreferences =
            throw UnsupportedOperationException("remote preferences unavailable")

        override fun listRemoteFiles(): Array<String> = emptyArray()

        @Throws(FileNotFoundException::class)
        override fun openRemoteFile(name: String): ParcelFileDescriptor =
            throw FileNotFoundException(name)
    }

    private class FakeHookBuilder(
        private val executable: Executable,
        private val failHookIds: (String) -> Boolean,
        private val installedHandles: MutableList<XposedInterface.HookHandle>,
    ) : XposedInterface.HookBuilder {
        private var id: String? = null

        override fun setPriority(priority: Int): XposedInterface.HookBuilder = this

        override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder =
            this

        override fun setId(id: String?): XposedInterface.HookBuilder {
            this.id = requireNotNull(id)
            return this
        }

        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            val hookId = requireNotNull(id)
            if (failHookIds(hookId)) error("rejected test origin $hookId")
            return FakeHookHandle(executable, hookId, hooker).also(installedHandles::add)
        }
    }

    private class FakeHookSlot(
        var hooker: XposedInterface.Hooker,
    )

    private class FakeHookHandle(
        val originExecutable: Executable,
        private val hookId: String,
        hooker: XposedInterface.Hooker,
        private val slot: FakeHookSlot = FakeHookSlot(hooker),
    ) : XposedInterface.HookHandle {
        var replaceCalls: Int = 0
        var unhookCalls: Int = 0
        val replacementHandles = mutableListOf<FakeHookHandle>()

        override fun getExecutable(): Executable = originExecutable
        override fun getId(): String = hookId

        fun invoke(originResult: Any = "origin"): Any =
            slot.hooker.intercept(FakeChain(originExecutable, originResult))

        override fun unhook() {
            unhookCalls++
        }

        override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            replaceCalls++
            slot.hooker = hooker
            return FakeHookHandle(
                originExecutable,
                hookId,
                hooker,
                slot
            ).also(replacementHandles::add)
        }
    }

    private class FakeChain(
        private val executable: Executable,
        private val originResult: Any,
        private var receiver: Any = RuntimeProbeOrigin(),
        private var arguments: Array<out Any> = emptyArray(),
    ) : XposedInterface.Chain {
        override fun getExecutable(): Executable = executable
        override fun getThisObject(): Any = receiver
        override fun getArgs(): List<Any> = arguments.toList()
        override fun getArg(index: Int): Any = arguments[index]
        override fun proceed(): Any = originResult
        override fun proceed(args: Array<out Any>): Any {
            arguments = args.copyOf()
            return originResult
        }

        override fun proceedWith(thisObject: Any): Any {
            receiver = thisObject
            return originResult
        }

        override fun proceedWith(thisObject: Any, args: Array<out Any>): Any {
            receiver = thisObject
            arguments = args.copyOf()
            return originResult
        }
    }

    private class RecordingHookLogger : HookLogger {
        val errors = mutableListOf<String>()

        override fun debug(message: String, throwable: Throwable?) = Unit
        override fun info(message: String, throwable: Throwable?) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
        override fun error(message: String, throwable: Throwable?) {
            errors += message
        }
    }

    private class NoopHookRegistry : HookRegistry {
        override fun install(
            id: String,
            executable: Executable,
            hooker: XposedInterface.Hooker,
        ): InstalledHook = error("not used")

        override fun remove(id: String, hook: InstalledHook) = Unit
        override fun size(): Int = 0
        override fun unhookAll() = Unit
    }

    companion object {
        private fun assertApplicationInfoEquals(
            expected: ApplicationInfo,
            actual: ApplicationInfo
        ) {
            assertEquals(expected.packageName, actual.packageName)
            assertEquals(expected.processName, actual.processName)
            assertEquals(expected.uid, actual.uid)
            assertEquals(expected.sourceDir, actual.sourceDir)
            assertEquals(expected.publicSourceDir, actual.publicSourceDir)
            assertArrayEquals(expected.splitSourceDirs, actual.splitSourceDirs)
            assertEquals(expected.dataDir, actual.dataDir)
            assertEquals(expected.nativeLibraryDir, actual.nativeLibraryDir)
            assertEquals(expected.className, actual.className)
        }

        private fun testApplicationInfo(packageName: String, processName: String): ApplicationInfo =
            ApplicationInfo().apply {
                this.packageName = packageName
                this.processName = processName
                uid = 10000
                sourceDir = "/data/app/$packageName/base.apk"
                publicSourceDir = sourceDir
                dataDir = "/data/user/0/$packageName"
                className = null
            }
    }
}
