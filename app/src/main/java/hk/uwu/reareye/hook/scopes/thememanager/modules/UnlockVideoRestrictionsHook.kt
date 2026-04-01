package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.util.Size
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.ui.config.ConfigKeys
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class UnlockVideoRestrictionsHook : YukiBaseHooker() {
    @OptIn(ExperimentalAtomicApi::class)
    private val state = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    @SuppressLint("ResourceType")
    override fun onHook() {
        loadApp("com.android.thememanager") {
            val videoEditClz =
                "com.android.thememanager.videoedit.VideoEditActivity".toClass().resolve()
            val fpsLimitClz =
                $$"com.android.thememanager.videoedit.VideoEditActivity$zy".toClass().resolve()
            val editorCfgClz =
                "com.android.thememanager.videoedit.VideoEditorConfig".toClass()
            val editorCfgBuilderClz =
                $$"com.android.thememanager.videoedit.VideoEditorConfig$k".toClass().resolve()
            val checkDepthClz =
                $$"com.personalizedEditor.interceptor.VideoCheckForDepthInterceptor$checkVideo$2".toClass()
                    .resolve()

            val durationCropClz = $$"com.android.thememanager.util.uc$k$toq".toClass()

            editorCfgBuilderClz.firstMethod {
                returnType = editorCfgClz
            }.hook().before {
                if (!prefs.getBoolean(
                        ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS,
                        true
                    )
                ) return@before
                val ref = instance.asResolver()
                val isCallFromRearScreen = ref.field {
                    type = Boolean::class.java
                }.all { it.get() == true }
                if (isCallFromRearScreen) {
                    YLog.debug("Overwriting video editor max duration & frame-rate limitations")
                    // 视频长度
                    ref.firstField {
                        type = Long::class.java
                    }.set(Long.MAX_VALUE)
                    // 帧率限制 120帧给背屏够了
                    ref.firstField {
                        type = Int::class.java
                    }.set(120)
                }
            }

            checkDepthClz.firstMethod {
                name = "invokeSuspend"
            }.hook().after {
                if (!prefs.getBoolean(
                        ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS,
                        true
                    )
                ) return@after
                val ref = instance.asResolver()
                val videoCfg = ref.firstField {
                    name = $$"$videoConfig"
                }.get() ?: return@after
                if (videoCfg.asResolver().field {
                        type = Boolean::class.java
                    }.all { it.get() == true } && !state.load()) {
                    result = durationCropClz.resolve().firstField {
                        type = durationCropClz
                    }.get()
                } else {
                    state.store(false)
                }
            }
            // 修补视频编辑器
            videoEditClz.firstMethod {
                name = "nsb"
                returnType = Void.TYPE
            }.hook().replaceUnit {
                if (!prefs.getBoolean(ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS, true)) {
                    invokeOriginal()
                    return@replaceUnit
                }
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
                state.store(true)
            }

            // 修补帧率限制
            fpsLimitClz.firstMethod {
                name = "run"
            }.hook().replaceUnit {
                if (!prefs.getBoolean(ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS, true)) {
                    invokeOriginal()
                    return@replaceUnit
                }
                val strF7l8 =
                    "com.android.thememanager.settings.a9".toClass().resolve().firstMethod {
                        name = "f7l8"
                    }.invoke() as String
                val iVEA = instance.asResolver().firstField { name = "this$0" }.get()!!
                val iRef = iVEA.asResolver()
                val yObj = iRef.firstField { name = "y" }.get()
                val cFieldRef = iRef.firstField { name = "c" }
                cFieldRef.set(
                    strF7l8 + ("com.android.thememanager.basemodule.utils.CoderUtls".toClass()
                        .resolve()
                        .firstMethod { name = "zy" }
                        .invoke(yObj) as String) + ".mp4"
                )
                val frameRetriever = "com.xiaomi.milab.videosdk.FrameRetriever".toClass().resolve()
                    .firstConstructor().create().asResolver()
                frameRetriever.firstMethod { name = "setDataSource" }.invoke(yObj)
                val width = frameRetriever.firstMethod { name = "getWidth" }.invoke() as Int
                val height = frameRetriever.firstMethod { name = "getHeight" }.invoke() as Int
                val fps = frameRetriever.firstMethod { name = "getFPS" }.invoke() as Float
                val bitrate = frameRetriever.firstMethod { name = "getBitrate" }.invoke() as Long
                frameRetriever.firstMethod { name = "release" }.invoke()
                if (width <= 0 || height <= 0) {
                    iRef.firstMethod { name = "onExportFail" }.invoke()
                    return@replaceUnit
                }
                val pVarN2t = iRef.firstMethod { name = "n2t" }
                    .invoke(videoEditClz.firstField { name = "b" }.get(), width, height)!!
                    .asResolver()
                val k = pVarN2t.firstField { name = "k" }.get() as Int
                val toq = pVarN2t.firstField { name = "toq" }.get() as Int
                val toqVar = "com.android.thememanager.videoedit.entity.toq".toClass().resolve()
                    .firstConstructor {
                        parameterCount = 5
                    }.create(
                        true,
                        cFieldRef.get(),
                        Size(k, toq),
                        (((((bitrate / (width * height)) * k) * toq) / fps) * fps).toInt(),
                        0
                    )
                toqVar.asResolver().firstMethod { name = "kja0" }.invoke(fps.toInt())
                val gsonUtilsClz =
                    "com.android.thememanager.library.util.app.GsonUtils".toClass().resolve()
                Log.d(
                    "VideoEditActivity",
                    String.format(
                        "ExportConfig %s",
                        gsonUtilsClz.firstMethod { name = "g" }.invoke(toqVar)
                    )
                )
                Log.d("lollipop", "export videopath is " + cFieldRef.get())
                val qRef = "com.android.thememanager.videoedit.widget.s".toClass().resolve()
                    .firstMethod { name = "q" }.invoke()!!.asResolver()
                qRef.firstMethod {
                    name = "toq"
                }.invoke(
                    iRef.firstField { name = "i" }.get(),
                    iRef.firstField { name = "z" }.get(),
                    toqVar
                )
            }
        }
    }
}