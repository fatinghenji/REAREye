package hk.uwu.reareye.ui.config

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge

class PrefsManager(context: Context) {
    val prefs: YukiHookPrefsBridge = context.prefs()

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
}
