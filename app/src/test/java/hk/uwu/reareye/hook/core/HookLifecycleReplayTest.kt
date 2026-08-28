package hk.uwu.reareye.hook.core

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HookLifecycleReplayTest {
    @Test
    fun replayBindsCurrentApplicationAndDispatchesNewGenerationCallbacks() {
        val context = newContext()
        val module = LifecycleModule()
        val application = TestApplication("test.package")

        module.install(context, null)

        assertTrue(context.lifecycle.replayCurrentApplication(application))
        assertSame(application, context.appContext)
        assertEquals(1, module.attachCalls)
        assertEquals(1, module.createCalls)
    }

    @Test
    fun replayRejectsApplicationFromAnotherPackage() {
        val context = newContext()
        val module = LifecycleModule()
        val application = TestApplication("other.package")

        module.install(context, null)

        assertFalse(context.lifecycle.replayCurrentApplication(application))
        assertEquals(null, context.appContext)
        assertEquals(0, module.attachCalls)
        assertEquals(0, module.createCalls)
    }

    private fun newContext(): MutableHookContext = MutableHookContext(
        packageName = "test.package",
        processName = "test.package",
        appInfo = ApplicationInfo().apply {
            packageName = "test.package"
            processName = "test.package"
            uid = 10000
        },
        classLoader = TestApplication::class.java.classLoader!!,
        isSystemServer = false,
        prefs = NoopHookPrefs(),
        hooks = NoopHookRegistry(),
        logger = NoopHookLogger(),
        systemContextProvider = { error("system context is not used") },
    )

    private class LifecycleModule : HookModule() {
        var attachCalls = 0
        var createCalls = 0

        override fun onHook() {
            onAppLifecycle {
                attachBaseContext { attachCalls++ }
                onCreate { createCalls++ }
            }
        }
    }

    private class TestApplication(
        private val name: String,
    ) : Application() {
        override fun getPackageName(): String = name
        override fun getApplicationContext(): Context = this
    }

    private class NoopHookPrefs : HookPrefs {
        override fun getString(key: String, defaultValue: String) = defaultValue
        override fun getStringSet(key: String, defaultValue: Set<String>) = defaultValue
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun getLong(key: String, defaultValue: Long) = defaultValue
        override fun getFloat(key: String, defaultValue: Float) = defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
        override fun contains(key: String) = false
        override fun all(): Map<String, Any?> = emptyMap()
        override fun edit(): HookPrefsEditor = error("not required by this test")
    }

    private class NoopHookRegistry : HookRegistry {
        override fun install(
            id: String,
            executable: java.lang.reflect.Executable,
            hooker: io.github.libxposed.api.XposedInterface.Hooker,
        ): InstalledHook = error("not required by this test")

        override fun remove(id: String, hook: InstalledHook) = Unit
        override fun size(): Int = 0
        override fun unhookAll() = Unit
    }

    private class NoopHookLogger : HookLogger {
        override fun debug(message: String, throwable: Throwable?) = Unit
        override fun info(message: String, throwable: Throwable?) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
