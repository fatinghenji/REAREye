package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.util.Pair
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UnmuteVideoWallpaperHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.android.thememanager") {
            val ref = "com.android.thememanager.util.wx16".toClass().resolve()
            ref.firstMethod {
                name = "toq"
                parameters(File::class.java, File::class.java, File::class.java)
            }.hook().replaceAny {
                val input = args(0).cast<File>()!!
                val output = args(1).cast<File>()!!
                YLog.debug("Input path: ${input.absolutePath} length: ${input.length() / 1024.0}")
                YLog.debug("Output path: $output")
                if (input.absolutePath.contains("rear")) {
                    YLog.debug("Patch rear screen video wallpaper")
                    Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    return@replaceAny Pair(output, null)
                }
                return@replaceAny invokeOriginal(*args)
            }
        }
    }
}