package hk.uwu.reareye.hook.scopes.system.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import hk.uwu.reareye.ui.config.ConfigKeys

class RearScreenActivityWhitelistModule : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            val asiRef = "com.android.server.wm.ActivityStarterImpl".toClass().resolve()
            asiRef
                .firstMethod {
                    name = "isShouldShowOnRearDisplay"
                    returnType = Boolean::class.java
                }.hook().before {
                    if (prefs.getBoolean(ConfigKeys.HOOK_ACTIVITIES_WHITELIST)) return@before
                    val whitelist = prefs.getStringSet(ConfigKeys.ACTIVITIES_WHITELIST_APPS)
                    val field = asiRef.firstField {
                        name = "REAR_SCREEN_METADATA_WHITE_LIST"
                        type = Set::class.java
                    }
                    val set = field.get<HashSet<String>>() ?: return@before
                    set.clear()
                    set.add("com.retroarch")
                    set.addAll(whitelist)
                    XposedBridge.log("Injected Activities Whitelist")
                }
        }
    }
}