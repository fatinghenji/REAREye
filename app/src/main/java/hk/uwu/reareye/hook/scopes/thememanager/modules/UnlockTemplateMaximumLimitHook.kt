package hk.uwu.reareye.hook.scopes.thememanager.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.Modifiers
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.hook.utils.createDexKitCacheBridge
import hk.uwu.reareye.hook.utils.resolveDexKitClassValue
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi

@OptIn(DexKitExperimentalApi::class)
class UnlockTemplateMaximumLimitHook : YukiBaseHooker() {
    companion object {
        private const val REAR_DETAIL_VIEW_MODEL_CLASS_CACHE_KEY = "TM_REAR_DETAIL_VIEW_MODEL_CLASS"
        private const val FALLBACK_REAR_DETAIL_VIEW_MODEL_CLASS =
            "com.rearScreen.viewModel.RearScreenDetailViewModel"
    }

    override fun onHook() {
        loadApp("com.android.thememanager") {
            val versionCode =
                resolveHookPackageVersionCode(systemContext, appInfo.packageName, appInfo.sourceDir)
            val bridge = createDexKitCacheBridge(
                packageName = appInfo.packageName,
                packageVersionCode = versionCode,
                sourceDir = appInfo.sourceDir,
                dataDir = appInfo.dataDir,
            )
            val rsDetailClz = resolveRearDetailViewModelClass(bridge).toClass().resolve()
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

    private fun resolveRearDetailViewModelClass(
        bridge: DexKitCacheBridge.RecyclableBridge,
    ): String {
        return resolveDexKitClassValue(
            bridge = bridge,
            cacheKey = REAR_DETAIL_VIEW_MODEL_CLASS_CACHE_KEY,
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/rearScreen/viewModel/RearScreenDetailViewModel.java:44
            // Constructor stores "rear:RearScreenDetailViewModel" and the NFC/template limits.
            findClass {
                searchPackages("com.rearScreen.viewModel")
                matcher {
                    usingStrings(
                        "RearScreenDetailViewModel",
                        "[换机日志] onResourceImportSuccessful: onlineId="
                    )
                }
            }.singleOrNull()
        } ?: FALLBACK_REAR_DETAIL_VIEW_MODEL_CLASS
    }
}
