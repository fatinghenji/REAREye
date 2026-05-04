package hk.uwu.reareye.utils.other

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import hk.uwu.reareye.utils.RootHelper.executeRootCommand
import hk.uwu.reareye.utils.RootHelper.hasRootAccess

object DeviceConfigTools {

    // 我就说我设备名字怎么就对不上了，这玩意还要 Root 获取，破烂
    val getdevice =
        if (hasRootAccess()) executeRootCommand("getprop persist.private.device_name") else Pair(
            20,
            "无法获取Root来获取设备名字"
        )
    val deviceName = if (getdevice.first == 0) getdevice.second else "无法获取Root来获取设备名字"


    val androidVersion: String = getSystemProperties("ro.build.version.release")

    val marketName by lazy {

        val marketName: String = getSystemProperties("ro.product.marketname")

        if (marketName.isNotEmpty()) bigtextone(marketName) else bigtextone(Build.BRAND) + " " + Build.MODEL

    }

    fun bigtextone(st: String): String {
        val formattedBrand = st.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        return formattedBrand
    }

    @SuppressLint("PrivateApi")

    fun getSystemProperties(key: String): String {
        val ret: String = try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java).invoke(null, key) as String
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (_: Exception) {
            ""
        }
        return ret
    }

    fun getSubSceenVersion(context: Context): String {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo("com.xiaomi.subscreencenter", 0)
            return packageInfo.versionName.toString()
        } catch (e: Exception) {
            Log.e("getSubSceenVersion", e.message,e)
            return "无法获取小米分屏版本"
        }
    }
}