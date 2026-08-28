package hk.uwu.reareye.hook.scopes.system.modules

import android.view.MotionEvent
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

class DisableSubScreenDoubleTapSleepHook : YukiBaseHooker() {
    @Volatile
    private var focusedPackageName: String? = null

    override fun onHook() {
        loadSystem {
            val clz =
                "com.miui.server.input.gesture.multifingergesture.gesture.MiuiSubscreenDoubleTapGesture"
                    .toClass()
                    .resolve()
            val managerRef =
                "com.miui.server.input.gesture.multifingergesture.MiuiSubScreenMultiFingerGestureManager"
                    .toClass()
                    .resolve()

            managerRef.firstMethod {
                name = "onFocusedWindowChanged"
                parameterCount = 3
            }.hook().after {
                focusedPackageName = args(2).any().owningPackage()
            }

            clz.firstMethod {
                name = "onPointerEvent"
                returnType = Void.TYPE
                parameters(MotionEvent::class.java)
            }.hook().replaceUnit {
                val whitelist = prefs.getStringSet(
                    ConfigKeys.SUBSCREEN_DOUBLE_TAP_SLEEP_DISABLED_APPS,
                )
                val packageName = focusedPackageName ?: managerRef.firstMethod {
                    name = "getFocusedWindow"
                }.invoke().owningPackage()?.also {
                    focusedPackageName = it
                }
                if (packageName != null && packageName in whitelist) {
                    if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                        YLog.debug("Rejected subscreen double tap sleep gesture package=$packageName")
                    }
                    return@replaceUnit
                }
                invokeOriginal(*args)
            }
        }
    }

    private fun Any?.owningPackage(): String? {
        return runCatching {
            this?.asResolver()?.firstMethod {
                name = "getOwningPackage"
                returnType = String::class.java
            }?.invoke() as? String
        }.getOrNull()
    }
}
