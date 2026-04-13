package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.repository.rearwidget.RearBusinessExtraConfigRepository.getShowTimeTipForBusiness
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager

class ExtraTimeTipHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val clz = "m2.a".toClass().resolve()
            clz.constructor().build().hookAll().after {
                val ref = instance.asResolver()
                val moreDebug = prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)
                val bundle = ref.firstField {
                    name = "d"
                    type = Bundle::class.java
                }.get<Bundle>()
                if (bundle == null) {
                    if (moreDebug) {
                        YLog.debug("bundle is null ${args.joinToString { it.toString() }}")
                    }
                    return@after
                }
                val pm = prefs.getPrefsManager()
                val business = bundle.getString("business")
                if (business != null) {
                    if (moreDebug) {
                        YLog.debug("time tip process biz: $business")
                    }
                    val showTimeTip = pm.getShowTimeTipForBusiness(business)
                    ref.firstField {
                        name = "l"
                        type = Boolean::class.java
                    }.set(showTimeTip)
                    if (moreDebug) {
                        YLog.debug("time tip state biz=$business showTimeTip=$showTimeTip")
                    }
                } else {
                    if (moreDebug) {
                        YLog.debug(
                            "business is null ${
                                bundle.keySet()
                                    ?.joinToString(separator = "\n") { key ->
                                        @Suppress("DEPRECATION")
                                        "$key=${bundle.get(key)}"
                                    }
                            }"
                        )
                    }
                }
            }
        }
    }
}
