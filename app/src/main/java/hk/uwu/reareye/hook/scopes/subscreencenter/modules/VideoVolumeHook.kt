package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.ui.config.ConfigKeys

class VideoVolumeHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val clz = "com.miui.maml.elements.video.VideoElement".toClass().resolve()
            clz.firstMethod {
                name = "load"
                parameterCount = 1
            }.hook().after {
                val vol = prefs.getFloat(
                    ConfigKeys.VIDEO_WALLPAPER_VOLUME,
                    ConfigKeys.VIDEO_WALLPAPER_VOLUME_DEFAULT
                )
                if (vol > 0f) {
                    val setVol = instance.asResolver().firstMethod {
                        name = "setVolume"
                        parameters(Float::class.java)
                    }
                    setVol.invoke(vol)
                    YLog.debug("Changed video volume to $vol")
                }
            }
        }
    }
}