package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.hook.utils.DexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveDexKitInjectionPoint
import hk.uwu.reareye.hook.utils.resolveDexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import hk.uwu.reareye.ui.config.ConfigKeys
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class UnlockVideoRestrictionsHook : YukiBaseHooker() {
    companion object {
        private const val VIDEO_EDIT_PLAY_CREATED_METHOD_CACHE_KEY =
            "TM_VIDEO_EDIT_PLAY_CREATED_METHOD"
        private const val VIDEO_EDIT_FPS_LIMIT_METHOD_CACHE_KEY = "TM_VIDEO_EDIT_FPS_LIMIT_METHOD"
        private const val VIDEO_EDITOR_CONFIG_BUILD_METHOD_CACHE_KEY =
            "TM_VIDEO_EDITOR_CONFIG_BUILD_METHOD"
        private const val VIDEO_DEPTH_CHECK_METHOD_CACHE_KEY = "TM_VIDEO_DEPTH_CHECK_METHOD"
        private const val FALLBACK_VIDEO_EDIT_ACTIVITY_CLASS =
            "com.android.thememanager.videoedit.VideoEditActivity"
        private const val FALLBACK_VIDEO_EDIT_FPS_RUNNABLE_CLASS =
            $$"com.android.thememanager.videoedit.VideoEditActivity$zy"
        private const val FALLBACK_VIDEO_EDITOR_CONFIG_BUILDER_CLASS =
            $$"com.android.thememanager.videoedit.VideoEditorConfig$k"
        private const val FALLBACK_VIDEO_DEPTH_CHECK_CLASS =
            $$"com.personalizedEditor.interceptor.VideoCheckForDepthInterceptor$checkVideo$2"
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val state = AtomicBoolean(false)

    @RequiresApi(Build.VERSION_CODES.P)
    @OptIn(ExperimentalAtomicApi::class)
    @SuppressLint("ResourceType")
    override fun onHook() {
        loadApp("com.android.thememanager") {
            val bridge = DexKitBridge.create(this.appInfo.sourceDir)
            val durationCropCacheKey = "DURATION_CROP_CLZ"
            val historyHelperCacheKey = "HISTORY_HELPER_CLZ"
            val versionCode = resolveHookPackageVersionCode(
                systemContext,
                appInfo.packageName,
                appInfo.sourceDir,
            )

            onAppLifecycle {
                onCreate {
                    val nativePrefs = prefs.native()
                    val videoEditPoint = resolveVideoEditPlayCreatedMethod(bridge, versionCode)
                    val fpsLimitPoint = resolveVideoEditFpsLimitMethod(bridge, versionCode)
                    val editorConfigBuildPoint =
                        resolveVideoEditorConfigBuildMethod(bridge, versionCode)
                    val checkDepthPoint = resolveVideoDepthCheckMethod(bridge, versionCode)
                    val videoEditClz = videoEditPoint.className.toClass()
                    val videoEditRef = videoEditClz.resolve()
                    val fpsLimitClz = fpsLimitPoint.className.toClass().resolve()
                    val editorCfgBuilderClz = editorConfigBuildPoint.className.toClass().resolve()
                    val checkDepthClz = checkDepthPoint.className.toClass().resolve()

                    val durationCropMatchResult = resolveDexKitInjectionPoint(
                        bridge = bridge,
                        cacheKey = durationCropCacheKey,
                        packageVersionCode = versionCode,
                        readCache = nativePrefs::getString,
                        writeCache = { key, value ->
                            nativePrefs.edit().putString(key, value).apply()
                        },
                    ) {
                        findClass {
                            searchPackages("com.android.thememanager.util")
                            matcher {
                                modifiers = Modifier.PUBLIC or Modifier.FINAL
                                fieldCount(1)
                                methods {
                                    add {
                                        name = "toString"
                                        returnType(String::class.java)
                                        usingStrings("DurationCrop")
                                    }
                                }
                            }
                        }.singleOrNull()?.name
                    }
                    val durationCropClz = (durationCropMatchResult
                        ?: $$"com.android.thememanager.util.uc$k$toq").toClass()

                    val historyHelperResult = resolveDexKitInjectionPoint(
                        bridge = bridge,
                        cacheKey = historyHelperCacheKey,
                        packageVersionCode = versionCode,
                        readCache = nativePrefs::getString,
                        writeCache = { key, value ->
                            nativePrefs.edit().putString(key, value).apply()
                        },
                    ) {
                        findClass {
                            searchPackages("com.android.thememanager.settings")
                            matcher {
                                modifiers = Modifier.PUBLIC
                                fields {
                                    addForType(String::class.java)
                                    addForType(Any::class.java)
                                    count = 2
                                }
                                usingStrings("updateVideoResource")
                            }
                        }
                            .singleOrNull()
                            ?.name
                    }
                    val historyHelperClz =
                        (historyHelperResult ?: "com.android.thememanager.settings.a9").toClass()

                    checkDepthClz.firstMethod {
                        name = checkDepthPoint.methodName
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
                    videoEditRef.firstMethod {
                        name = videoEditPoint.methodName
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
                        name = fpsLimitPoint.methodName
                    }.hook().replaceUnit {
                        if (!prefs.getBoolean(ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS, true)) {
                            invokeOriginal()
                            return@replaceUnit
                        }
                        val strF7l8 =
                            historyHelperClz.resolve().firstMethod {
                                returnType = String::class.java
                                parameterCount = 0
                            }.invoke() as String
                        val iVEA = instance.asResolver().firstField { type = videoEditClz }.get()!!
                        val iRef = iVEA.asResolver()
                        val yObj = iRef.firstField { name = "y" }.get()
                        val cFieldRef = iRef.firstField { name = "c" }
                        cFieldRef.set(
                            strF7l8 + ("com.android.thememanager.basemodule.utils.CoderUtls".toClass()
                                .resolve()
                                .firstMethod { name = "zy" }
                                .invoke(yObj) as String) + ".mp4"
                        )
                        val frameRetriever =
                            "com.xiaomi.milab.videosdk.FrameRetriever".toClass().resolve()
                                .firstConstructor().create().asResolver()
                        frameRetriever.firstMethod { name = "setDataSource" }.invoke(yObj)
                        val width = frameRetriever.firstMethod { name = "getWidth" }.invoke() as Int
                        val height =
                            frameRetriever.firstMethod { name = "getHeight" }.invoke() as Int
                        val fps = frameRetriever.firstMethod { name = "getFPS" }.invoke() as Float
                        val bitrate =
                            frameRetriever.firstMethod { name = "getBitrate" }.invoke() as Long
                        frameRetriever.firstMethod { name = "release" }.invoke()
                        if (width <= 0 || height <= 0) {
                            iRef.firstMethod { name = "onExportFail" }.invoke()
                            return@replaceUnit
                        }
                        val pVarN2t = iRef.firstMethod {
                            parameters(Int::class.java, Int::class.java, Int::class.java)
                        }.invoke(videoEditRef.firstField { name = "b" }.get(), width, height)!!
                            .asResolver()
                        val k = pVarN2t.firstField { name = "k" }.get() as Int
                        val toq = pVarN2t.firstField { name = "toq" }.get() as Int
                        val toqVar =
                            "com.android.thememanager.videoedit.entity.toq".toClass().resolve()
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
                            "com.android.thememanager.library.util.app.GsonUtils".toClass()
                                .resolve()
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

                    editorCfgBuilderClz.firstMethod {
                        name = editorConfigBuildPoint.methodName
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
                            ref.firstField {
                                type = Long::class.java
                            }.set(Long.MAX_VALUE)
                            ref.firstField {
                                type = Int::class.java
                            }.set(120)
                        }
                    }
                }
            }
        }
    }

    private fun resolveVideoEditPlayCreatedMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = VIDEO_EDIT_PLAY_CREATED_METHOD_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/VideoEditActivity.java:132
            // VideoEditActivity.ebn()/onPlayViewCreated path logs "onPlayViewCreated".
            findMethod {
                searchPackages("com.android.thememanager.videoedit")
                matcher {
                    returnType = "void"
                    usingStrings("onPlayViewCreated")
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_VIDEO_EDIT_ACTIVITY_CLASS, "nsb")
    }

    private fun resolveVideoEditFpsLimitMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = VIDEO_EDIT_FPS_LIMIT_METHOD_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/VideoEditActivity.java:110
            // VideoEditActivity.zy.run() builds ExportConfig and caps fps at 30.
            findMethod {
                searchPackages("com.android.thememanager.videoedit")
                matcher {
                    name = "run"
                    paramCount(0)
                    returnType = "void"
                    usingStrings("ExportConfig %s", "export videopath is ")
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_VIDEO_EDIT_FPS_RUNNABLE_CLASS, "run")
    }

    private fun resolveVideoEditorConfigBuildMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = VIDEO_EDITOR_CONFIG_BUILD_METHOD_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/VideoEditorConfig.java:42
            // VideoEditorConfig.Builder.k() creates the config that carries duration/fps limits.
            findMethod {
                searchPackages("com.android.thememanager.videoedit")
                matcher {
                    paramCount(0)
                    returnType = "com.android.thememanager.videoedit.VideoEditorConfig"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_VIDEO_EDITOR_CONFIG_BUILDER_CLASS, "k")
    }

    private fun resolveVideoDepthCheckMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = VIDEO_DEPTH_CHECK_METHOD_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/personalizedEditor/interceptor/VideoCheckForDepthInterceptor$checkVideo$2.java:41
            // invokeSuspend() returns duration/ratio/fps validation results for rear video wallpapers.
            findMethod {
                searchPackages("com.personalizedEditor.interceptor")
                matcher {
                    name = "invokeSuspend"
                    paramCount(1)
                    usingStrings(
                        "checkVideo: gallery return data is null",
                        "checkVideo: is horizontal video",
                    )
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_VIDEO_DEPTH_CHECK_CLASS, "invokeSuspend")
    }
}
