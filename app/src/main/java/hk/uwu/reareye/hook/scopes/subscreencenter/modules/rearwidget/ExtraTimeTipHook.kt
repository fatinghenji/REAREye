package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.hook.utils.resolveDexKitInjectionPoint
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import hk.uwu.reareye.repository.rearwidget.RearBusinessExtraConfigRepository.getShowTimeTipForBusiness
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import org.luckypray.dexkit.DexKitBridge

class ExtraTimeTipHook : YukiBaseHooker() {
    companion object {
        private const val WIDGET_SPEC_CLASS_CACHE_KEY = "SSC_WIDGET_SPEC_CLASS"
        private const val FALLBACK_WIDGET_SPEC_CLASS = "m2.a"
    }

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val bridge = DexKitBridge.create(this.appInfo.sourceDir)
            onAppLifecycle {
                onCreate {
                    val versionCode = resolveHookPackageVersionCode(
                        systemContext,
                        appInfo.packageName,
                        appInfo.sourceDir
                    )
                    val clz = resolveWidgetSpecClass(bridge, versionCode).toClass().resolve()
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
    }

    private fun resolveWidgetSpecClass(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): String {
        val nativePrefs = prefs.native()
        return resolveDexKitInjectionPoint(
            bridge = bridge,
            cacheKey = WIDGET_SPEC_CLASS_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/com/bumptech/glide/d.java:138
            // ThemeDataParser creates new m2.a(...) specs from rearScreen/runtime.json.
            findMethod {
                matcher {
                    paramCount(1)
                    returnType = "java.util.List"
                    usingStrings(
                        "/data/system/theme_magic/users/\$user_id/rearScreen/runtime.json",
                        "/system/media/rearscreen/template/default/rearScreen.json",
                    )
                }
            }.singleOrNull()
                ?.invokes
                ?.singleOrNull { method -> method.isConstructor && method.paramCount >= 10 }
                ?.className
        } ?: FALLBACK_WIDGET_SPEC_CLASS
    }
}
