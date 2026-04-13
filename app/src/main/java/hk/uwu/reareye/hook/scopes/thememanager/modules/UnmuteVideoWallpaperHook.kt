package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.os.Build
import android.util.Pair
import androidx.annotation.RequiresApi
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.hook.utils.resolveDexKitInjectionPoint
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UnmuteVideoWallpaperHook : YukiBaseHooker() {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onHook() {
        loadApp("com.android.thememanager") {
            val bridge = DexKitBridge.create(this.appInfo.sourceDir)
            val demuxerCacheKey = "VIDEO_AUDIO_DEMUXER_CLZ"
            onAppLifecycle {
                onCreate {
                    val pm = systemContext.packageManager
                    val info = pm.getPackageInfo(appInfo.packageName, 0)

                    val nativePrefs = prefs.native()
                    val durationCropMatchResult = resolveDexKitInjectionPoint(
                        bridge = bridge,
                        cacheKey = demuxerCacheKey,
                        packageVersionCode = info.longVersionCode,
                        readCache = nativePrefs::getString,
                        writeCache = { key, value ->
                            nativePrefs.edit().putString(key, value).apply()
                        },
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
                                            File::class.java
                                        )
                                        paramCount(3)
                                    }
                                }
                            }
                        }.singleOrNull()?.name
                    }
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
                                StandardCopyOption.REPLACE_EXISTING
                            )
                            return@replaceAny Pair(output, null)
                        }
                        return@replaceAny invokeOriginal(*args)
                    }
                }
            }
        }
    }
}
