package hk.uwu.reareye.hook.scopes.thememanager.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.Modifiers
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

class UnlockTemplateMaximumLimitHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp("com.android.thememanager") {
            val rsDetailClz =
                "com.rearScreen.viewModel.RearScreenDetailViewModel".toClass().resolve()
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