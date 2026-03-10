package hk.uwu.reareye.hook.scopes

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

interface Scope {
    val hooks: List<YukiBaseHooker>
}