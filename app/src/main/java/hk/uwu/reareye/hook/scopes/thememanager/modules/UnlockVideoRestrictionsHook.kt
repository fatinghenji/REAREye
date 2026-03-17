package hk.uwu.reareye.hook.scopes.thememanager.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import hk.uwu.reareye.ui.config.ConfigKeys

class UnlockVideoRestrictionsHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.android.thememanager") {
            val editorCfgClz =
                "com.android.thememanager.videoedit.VideoEditorConfig".toClass()
            val editorCfgBuilderClz =
                $$"com.android.thememanager.videoedit.VideoEditorConfig$k".toClass().resolve()
            editorCfgBuilderClz.firstMethod {
                returnType = editorCfgClz
            }.hook().before {
                if (!prefs.getBoolean(
                        ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS,
                        true
                    )
                ) return@before
                val isCallFromRearScreen = editorCfgBuilderClz.field {
                    type = Boolean::class.java
                }.all { it.get() == true }
                if (isCallFromRearScreen) {
                    XposedBridge.log("Overwriting video editor max duration & frame-rate limitations")
                    // 视频长度
                    editorCfgBuilderClz.firstField {
                        type = Long::class.java
                    }.set(Long.MAX_VALUE)
                    // 帧率限制 120帧给背屏够了
                    editorCfgBuilderClz.firstField {
                        type = Int::class.java
                    }.set(120)
                }
            }
        }
    }
}