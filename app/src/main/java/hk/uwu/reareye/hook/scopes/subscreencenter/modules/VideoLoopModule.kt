package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys

class VideoLoopModule : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val videoElRef = "com.miui.maml.elements.video.VideoElement".toClass().resolve()
            videoElRef.firstMethod {
                name = "getLooping"
            }.hook().replaceAny {
                if (prefs.getBoolean(ConfigKeys.HOOK_VIDEO_LOOPING, false)) {
                    return@replaceAny true
                }
                return@replaceAny invokeOriginal()
            }
        }
    }
}