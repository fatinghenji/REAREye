package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys


class UnlockVideoRestrictionsHook : YukiBaseHooker() {
    @SuppressLint("ResourceType")
    override fun onHook() {
        loadApp("com.android.thememanager") {
            if (!prefs.getBoolean(
                    ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS,
                    true
                )
            ) return
            val videoEditClz =
                "com.android.thememanager.videoedit.VideoEditActivity".toClass().resolve()
            videoEditClz.firstMethod {
                name = "nsb"
                returnType = Void.TYPE
            }.hook().replaceUnit {
                val sClz = "com.android.thememanager.videoedit.widget.s".toClass()
                val sRef = sClz.resolve()
                val iRef = instance.asResolver()
                val sInstance = sRef.firstMethod {
                    name = "q"
                    returnType = sClz
                }.invoke()!!
                sInstance.asResolver().firstMethod {
                    name = "k"
                    returnType = Void.TYPE
                }.invoke(iRef.firstField {
                    name = "q"
                }.get()!!.asResolver().firstMethod {
                    name = "getTextureView"
                }.invoke(), iRef.firstField { name = "s" }.get())
                val duration: Long =
                    sInstance.asResolver().firstMethod { name = "zy" }.invoke() as Long
                val activity = instance<Activity>()
                if (duration <= 0) {
                    val nmn5Ref =
                        "com.android.thememanager.basemodule.utils.nmn5".toClass().resolve()
                    nmn5Ref.firstMethod { name = "q" }
                        .invoke(activity.resources.getString(2131888794))
                    Log.e("VideoEditActivity", "onPlayViewCreated: originDuration = 0")
                    activity.finish()
                    return@replaceUnit
                }
                iRef.firstField { name = "z" }.set(duration)
                iRef.firstField { name = "r" }.set(duration)
                val nRef = iRef.firstField { name = "n" }.get()!!.asResolver()
                val iVar = iRef.firstField { name = "i" }.get() as Long
                nRef.firstMethod { name = "d2ok" }.invoke(iVar)
                nRef.firstMethod { name = "setTotalTime" }.invoke(duration)
                val yClz = "com.android.thememanager.videoedit.y".toClass().resolve()
                val yVar = yClz.firstConstructor().create()
                iRef.firstField { name = "p" }.set(yVar)
                val gRef = iRef.firstField { name = "g" }.get()!!.asResolver()
                gRef.firstMethod { name = "setVideoFrameLoader" }.invoke(yVar)
                gRef.firstMethod { name = "setClipFrameListener" }
                    .invoke(iRef.firstField { name = "j" }.get())
                gRef.firstMethod { name = "x2" }.invoke(
                    iRef.firstField { name = "y" }.get(),
                    duration,
                    duration
                )
                sInstance.asResolver().firstMethod {
                    name = "s"
                    parameters(Int::class.java)
                }.invoke(iVar.toInt())
            }
        }
    }
}