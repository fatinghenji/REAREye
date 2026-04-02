package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.rearwidget.RearBusinessExtraConfigFields
import hk.uwu.reareye.rearwidget.RearBusinessExtraConfigRepository.getExtraConfig
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager

class ExtraTimeTipHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val clz = "m2.a".toClass().resolve()
            clz.constructor().build().hookAll().after {
                val ref = instance.asResolver()
                val bundle = ref.firstField {
                    name = "d"
                    type = Bundle::class.java
                }.get<Bundle>() ?: return@after
                val pm = prefs.getPrefsManager()
                val business = bundle.getString("business")
                if (business != null) {
                    val moreDebug = prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)
                    if (moreDebug) {
                        YLog.debug("time tip process biz: $business")
                    }
                    val extraCfg = pm.getExtraConfig(business)
                    if (extraCfg.getBoolean(RearBusinessExtraConfigFields.HIDE_TIME_TIP, false)) {
                        ref.firstField {
                            name = "l"
                            type = Boolean::class.java
                        }.set(false)
                        if (moreDebug) {
                            YLog.debug("hide time tip: $business")
                        }
                    }
                }
            }
        }
    }
}