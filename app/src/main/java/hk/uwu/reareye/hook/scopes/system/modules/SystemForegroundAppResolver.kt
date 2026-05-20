package hk.uwu.reareye.hook.scopes.system.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver

internal fun Any?.mainDisplayForegroundPackageName(): String? = runCatching {
    val instance = this ?: return null
    val powerManagerServiceImpl = instance.asResolver().firstField {
        name = "mPowerManagerServiceImpl"
    }.get<Any>() ?: return null

    powerManagerServiceImpl.asResolver().firstField {
        name = "mForegroundAppPackageName"
        type = String::class.java
    }.get<String>()
}.getOrNull()
