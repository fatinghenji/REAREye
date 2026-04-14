package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.hook.utils.DexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveDexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import hk.uwu.reareye.ui.config.ConfigKeys
import org.luckypray.dexkit.DexKitBridge

class VideoVolumeHook : YukiBaseHooker() {
    companion object {
        private const val VIDEO_ELEMENT_LOAD_CACHE_KEY = "SSC_VIDEO_ELEMENT_LOAD_METHOD"
        private const val FALLBACK_VIDEO_ELEMENT_CLASS = "com.miui.maml.elements.video.VideoElement"
    }

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val bridge = DexKitBridge.create(this.appInfo.sourceDir)
            val versionCode =
                resolveHookPackageVersionCode(systemContext, appInfo.packageName, appInfo.sourceDir)
            onAppLifecycle {
                onCreate {
                    val point = resolveVideoElementLoadMethod(bridge, versionCode)
                    val clz = point.className.toClass().resolve()
                    clz.firstMethod {
                        name = point.methodName
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
    }

    private fun resolveVideoElementLoadMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = VIDEO_ELEMENT_LOAD_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
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
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_VIDEO_ELEMENT_CLASS, "load")
    }
}
