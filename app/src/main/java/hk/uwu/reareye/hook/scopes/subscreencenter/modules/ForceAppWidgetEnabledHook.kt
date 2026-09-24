package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import android.content.ContentResolver
import android.provider.Settings
import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YukiBaseHooker
import org.luckypray.dexkit.annotations.DexKitExperimentalApi

@OptIn(DexKitExperimentalApi::class)
class ForceAppWidgetEnabledHook : YukiBaseHooker() {
    private companion object {
        const val PROPERTY_KEY = "persist.sys.app.widget.enable"
        const val SETTING_KEY = "subscreen_app_widget_enable"
    }

    override fun onHook() {
        loadApp(
            "com.xiaomi.subscreencenter",
            "com.android.thememanager",
            "com.miui.personalassistant"
        ) {
            "android.os.SystemProperties".toClass().resolve().firstMethod {
                name = "getBoolean"
                parameters(String::class.java, Boolean::class.javaPrimitiveType!!)
                returnType = Boolean::class.javaPrimitiveType!!
            }.hook().before {
                if (args[0] == PROPERTY_KEY) result = true
            }

            val secureSettings = Settings.Secure::class.java.resolve()
            secureSettings.firstMethod {
                name = "getInt"
                parameters(
                    ContentResolver::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                )
                returnType = Int::class.javaPrimitiveType!!
            }.hook().before {
                if (args[1] == SETTING_KEY) result = 1
            }
            secureSettings.firstMethod {
                name = "getInt"
                parameters(ContentResolver::class.java, String::class.java)
                returnType = Int::class.javaPrimitiveType!!
            }.hook().before {
                if (args[1] == SETTING_KEY) result = 1
            }
        }
    }
}
