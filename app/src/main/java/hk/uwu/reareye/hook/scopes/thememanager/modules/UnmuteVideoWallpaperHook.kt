package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.util.Pair
import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.hook.utils.createDexKitCacheBridge
import hk.uwu.reareye.hook.utils.resolveDexKitClassValue
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@OptIn(DexKitExperimentalApi::class)
class UnmuteVideoWallpaperHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.android.thememanager") {
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
            val durationCropMatchResult = resolveDemuxerClassName(bridge)
            val ref =
                (durationCropMatchResult ?: "com.android.thememanager.util.wx16").toClass()
                    .resolve()

            ref.firstMethod {
                parameters(File::class.java, File::class.java, File::class.java)
            }.hook().replaceAny {
                val input = args(0).cast<File>()!!
                val output = args(1).cast<File>()!!
                YLog.debug("Input path: ${input.absolutePath} length: ${input.length() / 1024.0}")
                YLog.debug("Output path: $output")
                if (input.absolutePath.contains("rear")) {
                    YLog.debug("Patch rear screen video wallpaper")
                    Files.copy(
                        input.toPath(),
                        output.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    return@replaceAny Pair(output, null)
                }
                return@replaceAny invokeOriginal(*args)
            }
        }
    }

    private fun resolveDemuxerClassName(
        bridge: DexKitCacheBridge.RecyclableBridge
    ): String? {
        return resolveDexKitClassValue(
            bridge = bridge,
            cacheKey = "VIDEO_AUDIO_DEMUXER_CLZ",
        ) {
            findClass {
                searchPackages("com.android.thememanager.util")
                matcher {
                    modifiers = Modifier.PUBLIC
                    fields {
                        addForType(String::class.java)
                        addForType(Int::class.java)
                        count = 4
                    }
                    methods {
                        add {
                            paramTypes(
                                File::class.java,
                                File::class.java,
                                File::class.java,
                            )
                            paramCount(3)
                        }
                    }
                }
            }
                .singleOrNull()
        }
    }
}
