package hk.uwu.reareye.ui.config

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge

class PrefsManager(val prefs: YukiHookPrefsBridge) {

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return prefs.getBoolean(key, defValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getStringSet(key: String, defValue: Set<String>): Set<String> {
        return prefs.getStringSet(key, defValue)
    }

    fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    fun getString(key: String, defValue: String = ""): String {
        return prefs.getString(key, defValue)
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, defValue: Int): Int {
        return prefs.getInt(key, defValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getFloat(key: String, defValue: Float): Float {
        return prefs.getFloat(key, defValue)
    }

    fun getRequirementValue(key: String): Any? {
        val nativePrefs = prefs.native()
        if (!nativePrefs.contains(key)) return null
        return nativePrefs.all()[key]
    }

    fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    companion object {
        fun Context.getPrefsManager() = PrefsManager(this.prefs())
        fun YukiHookPrefsBridge.getPrefsManager() = PrefsManager(this)
    }
}
