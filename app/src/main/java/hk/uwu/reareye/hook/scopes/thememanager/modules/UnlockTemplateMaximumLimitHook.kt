package hk.uwu.reareye.hook.scopes.thememanager.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.Modifiers
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.utils.resolveDexKitInjectionPoint
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import org.luckypray.dexkit.DexKitBridge

class UnlockTemplateMaximumLimitHook : YukiBaseHooker() {
    companion object {
        private const val REAR_DETAIL_VIEW_MODEL_CLASS_CACHE_KEY = "TM_REAR_DETAIL_VIEW_MODEL_CLASS"
        private const val FALLBACK_REAR_DETAIL_VIEW_MODEL_CLASS =
            "com.rearScreen.viewModel.RearScreenDetailViewModel"
    }

    override fun onHook() {
        loadApp("com.android.thememanager") {
            val bridge = DexKitBridge.create(this.appInfo.sourceDir)
            val versionCode =
                resolveHookPackageVersionCode(systemContext, appInfo.packageName, appInfo.sourceDir)
            onAppLifecycle {
                onCreate {
                    val rsDetailClz =
                        resolveRearDetailViewModelClass(bridge, versionCode).toClass().resolve()
                    rsDetailClz.firstConstructor().hook().after {
                        val ref = instance.asResolver()
                        ref.field {
                            type = Int::class.java
                            modifiers(Modifiers.PRIVATE, Modifiers.FINAL)
                        }.forEach {
                            it.set(Int.MAX_VALUE)
                        }
                    }
                }
            }
        }
    }

    private fun resolveRearDetailViewModelClass(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): String {
        val nativePrefs = prefs.native()
        return resolveDexKitInjectionPoint(
            bridge = bridge,
            cacheKey = REAR_DETAIL_VIEW_MODEL_CLASS_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/rearScreen/viewModel/RearScreenDetailViewModel.java:44
            // Constructor stores "rear:RearScreenDetailViewModel" and the NFC/template limits.
            findClass {
                searchPackages("com.rearScreen.viewModel")
                matcher {
                    usingStrings("rear:RearScreenDetailViewModel")
                }
            }.singleOrNull()?.name
        } ?: FALLBACK_REAR_DETAIL_VIEW_MODEL_CLASS
    }
}
