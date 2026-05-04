package hk.uwu.reareye.hook.scopes

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

interface Scope {
    val hooks: List<YukiBaseHooker>
    val isRearDevice
        get() = Companion.isRearDevice

    companion object {
        val isRearDevice: Boolean = "android.os.SystemProperties".toClass().resolve().firstMethod {
            name = "getInt"
            parameters(String::class.java, Int::class.java)
        }.invoke<Int>("persist.sys.multi_display_type", 1) == 6
    }
}