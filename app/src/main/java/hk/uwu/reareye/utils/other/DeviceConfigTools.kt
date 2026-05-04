package hk.uwu.reareye.utils.other

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import hk.uwu.reareye.utils.RootHelper.executeRootCommand
import hk.uwu.reareye.utils.RootHelper.hasRootAccess

object DeviceConfigTools {

    // 我就说我设备名字怎么就对不上了，这玩意还要 Root 获取，破烂
    val getdevice =
        if (hasRootAccess()) executeRootCommand("getprop persist.private.device_name") else Pair(
            20,
            "No Root Permission"
        )
    val deviceName = if (getdevice.first == 0) getdevice.second else "No Root Permission"


    val androidVersion: String = getSystemProperties("ro.build.version.release")

    val marketName by lazy {

        val marketName: String = getSystemProperties("ro.product.marketname")

        if (marketName.isNotEmpty()) titleFirstChar(marketName) else titleFirstChar(Build.BRAND) + " " + Build.MODEL

    }

    fun titleFirstChar(st: String): String {
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

    fun getSubScreenVersion(context: Context): String {
        val packageManager = context.packageManager
        return try {
            val packageInfo = packageManager.getPackageInfo("com.xiaomi.subscreencenter", 0)
            packageInfo.versionName.toString()
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }
}
