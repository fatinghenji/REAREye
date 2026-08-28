package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.hook.utils.DexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.createDexKitCacheBridge
import hk.uwu.reareye.hook.utils.resolveDexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import hk.uwu.reareye.ui.config.ConfigKeys
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi

@OptIn(DexKitExperimentalApi::class)
class VideoVolumeHook : YukiBaseHooker() {
    companion object {
        private const val VIDEO_ELEMENT_LOAD_CACHE_KEY = "SSC_VIDEO_ELEMENT_LOAD_METHOD"
        private const val FALLBACK_VIDEO_ELEMENT_CLASS = "com.miui.maml.elements.video.VideoElement"
    }

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val versionCode =
                resolveHookPackageVersionCode(systemContext, appInfo.packageName, appInfo.sourceDir)
            val bridge = trackResource(
                createDexKitCacheBridge(
                packageName = appInfo.packageName,
                packageVersionCode = versionCode,
                sourceDir = appInfo.sourceDir,
                dataDir = appInfo.dataDir,
                )
            )
            val point = resolveVideoElementLoadMethod(bridge)
            val clz = point.className.toClass().resolve()
            clz.firstMethod {
                name = point.methodName
                parameterCount = 1
            }.hook().after {
                val vol = prefs.getFloat(
                    ConfigKeys.VIDEO_WALLPAPER_VOLUME,
                    ConfigKeys.VIDEO_WALLPAPER_VOLUME_DEFAULT,
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

    private fun resolveVideoElementLoadMethod(
        bridge: DexKitCacheBridge.RecyclableBridge,
    ): DexKitMethodInjectionPoint {
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = VIDEO_ELEMENT_LOAD_CACHE_KEY,
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/com/miui/maml/elements/video/VideoElement.java:78
            // VideoElement.load(Element) initializes IVideoHolder; the hook sets volume after it.
            findMethod {
                searchPackages("com.miui.maml.elements.video")
                matcher {
                    paramCount(1)
                    returnType = "void"
                    usingStrings("viewType", "loop", "scaleMode")
                }
            }.singleOrNull()
        } ?: DexKitMethodInjectionPoint(FALLBACK_VIDEO_ELEMENT_CLASS, "load")
    }
}
