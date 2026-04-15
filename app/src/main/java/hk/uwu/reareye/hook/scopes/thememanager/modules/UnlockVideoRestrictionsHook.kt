package hk.uwu.reareye.hook.scopes.thememanager.modules

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.util.Size
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
        private const val VIDEO_TIMELINE_GET_INSTANCE_METHOD_CACHE_KEY =
            "TM_VIDEO_TIMELINE_GET_INSTANCE_METHOD"
        private const val VIDEO_TIMELINE_ATTACH_TEXTURE_METHOD_CACHE_KEY =
            "TM_VIDEO_TIMELINE_ATTACH_TEXTURE_METHOD"
        private const val VIDEO_TIMELINE_GET_DURATION_METHOD_CACHE_KEY =
            "TM_VIDEO_TIMELINE_GET_DURATION_METHOD"
        private const val VIDEO_TIMELINE_PREPARE_METHOD_CACHE_KEY =
            "TM_VIDEO_TIMELINE_PREPARE_METHOD"
        private const val VIDEO_TIMELINE_EXPORT_METHOD_CACHE_KEY =
            "TM_VIDEO_TIMELINE_EXPORT_METHOD"
        private const val VIDEO_TOAST_TEXT_METHOD_CACHE_KEY = "TM_VIDEO_TOAST_TEXT_METHOD"
        private const val VIDEO_OPERATION_CURRENT_TIME_METHOD_CACHE_KEY =
            "TM_VIDEO_OPERATION_CURRENT_TIME_METHOD"
        private const val VIDEO_CLIP_FRAME_LOAD_METHOD_CACHE_KEY =
            "TM_VIDEO_CLIP_FRAME_LOAD_METHOD"
        private const val VIDEO_HASH_STRING_METHOD_CACHE_KEY = "TM_VIDEO_HASH_STRING_METHOD"
        private const val VIDEO_EXPORT_CONFIG_SET_FPS_METHOD_CACHE_KEY =
            "TM_VIDEO_EXPORT_CONFIG_SET_FPS_METHOD"
        private const val VIDEO_GSON_SERIALIZE_METHOD_CACHE_KEY =
            "TM_VIDEO_GSON_SERIALIZE_METHOD"
        private const val VIDEO_FRAME_LOADER_CLASS_CACHE_KEY = "TM_VIDEO_FRAME_LOADER_CLASS"
        private const val VIDEO_EXPORT_CONFIG_CLASS_CACHE_KEY = "TM_VIDEO_EXPORT_CONFIG_CLASS"
        private const val VIDEO_EDIT_PLAY_VIEW_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_PLAY_VIEW_FIELD"
        private const val VIDEO_EDIT_CONFIG_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_CONFIG_FIELD"
        private const val VIDEO_EDIT_OPERATION_VIEW_FIELD_CACHE_KEY =
            "TM_VIDEO_EDIT_OPERATION_VIEW_FIELD"
        private const val VIDEO_EDIT_TRIM_IN_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_TRIM_IN_FIELD"
        private const val VIDEO_EDIT_TRIM_OUT_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_TRIM_OUT_FIELD"
        private const val VIDEO_EDIT_FRAME_LOADER_FIELD_CACHE_KEY =
            "TM_VIDEO_EDIT_FRAME_LOADER_FIELD"
        private const val VIDEO_EDIT_CLIP_FRAME_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_CLIP_FRAME_FIELD"
        private const val VIDEO_EDIT_CLIP_LISTENER_FIELD_CACHE_KEY =
            "TM_VIDEO_EDIT_CLIP_LISTENER_FIELD"
        private const val VIDEO_EDIT_VIDEO_URI_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_VIDEO_URI_FIELD"
        private const val VIDEO_EDIT_EXPORT_PATH_FIELD_CACHE_KEY = "TM_VIDEO_EDIT_EXPORT_PATH_FIELD"
        private const val FALLBACK_VIDEO_EDIT_ACTIVITY_CLASS =
            "com.android.thememanager.videoedit.VideoEditActivity"
        private const val FALLBACK_VIDEO_EDIT_FPS_RUNNABLE_CLASS =
            $$"com.android.thememanager.videoedit.VideoEditActivity$zy"
        private const val FALLBACK_VIDEO_EDITOR_CONFIG_BUILDER_CLASS =
            $$"com.android.thememanager.videoedit.VideoEditorConfig$k"
        private const val FALLBACK_VIDEO_DEPTH_CHECK_CLASS =
            $$"com.personalizedEditor.interceptor.VideoCheckForDepthInterceptor$checkVideo$2"
        private const val FALLBACK_VIDEO_TIMELINE_CLASS =
            "com.android.thememanager.videoedit.widget.s"
        private const val FALLBACK_VIDEO_TOAST_UTILS_CLASS =
            "com.android.thememanager.basemodule.utils.nmn5"
        private const val FALLBACK_VIDEO_OPERATION_VIEW_CLASS =
            "com.android.thememanager.videoedit.widget.SingleEditOperationView"
        private const val FALLBACK_VIDEO_CLIP_FRAME_VIEW_CLASS =
            "com.android.thememanager.videoedit.widget.ClipFrameView"
        private const val FALLBACK_VIDEO_CODER_UTILS_CLASS =
            "com.android.thememanager.basemodule.utils.CoderUtls"
        private const val FALLBACK_VIDEO_EXPORT_CONFIG_CLASS =
            "com.android.thememanager.videoedit.entity.toq"
        private const val FALLBACK_VIDEO_GSON_UTILS_CLASS =
            "com.android.thememanager.library.util.app.GsonUtils"
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val state = AtomicBoolean(false)

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
                    val timelineGetInstancePoint =
                        resolveVideoTimelineGetInstanceMethod(bridge, versionCode)
                    val timelineAttachTexturePoint =
                        resolveVideoTimelineAttachTextureMethod(bridge, versionCode)
                    val timelineGetDurationPoint =
                        resolveVideoTimelineGetDurationMethod(bridge, versionCode)
                    val timelinePreparePoint =
                        resolveVideoTimelinePrepareMethod(bridge, versionCode)
                    val timelineExportPoint =
                        resolveVideoTimelineExportMethod(bridge, versionCode)
                    val toastTextPoint = resolveVideoToastTextMethod(bridge, versionCode)
                    val operationCurrentTimePoint =
                        resolveVideoOperationCurrentTimeMethod(bridge, versionCode)
                    val clipFrameLoadPoint = resolveVideoClipFrameLoadMethod(bridge, versionCode)
                    val coderHashPoint = resolveVideoHashStringMethod(bridge, versionCode)
                    val exportConfigSetFpsPoint =
                        resolveVideoExportConfigSetFpsMethod(bridge, versionCode)
                    val gsonSerializePoint = resolveVideoGsonSerializeMethod(bridge, versionCode)
                    val videoEditClz = videoEditPoint.className.toClass()
                    val videoEditRef = videoEditClz.resolve()
                    val fpsLimitClz = fpsLimitPoint.className.toClass().resolve()
                    val editorCfgBuilderClz = editorConfigBuildPoint.className.toClass().resolve()
                    val checkDepthClz = checkDepthPoint.className.toClass().resolve()
                    val timelineClz = timelineGetInstancePoint.className.toClass()
                    val timelineRef = timelineClz.resolve()
                    val toastUtilsRef = toastTextPoint.className.toClass().resolve()
                    val coderUtilsRef = coderHashPoint.className.toClass().resolve()
                    val gsonUtilsClz = gsonSerializePoint.className.toClass().resolve()
                    val frameLoaderClassName = resolveDexKitInjectionPoint(
                        bridge = bridge,
                        cacheKey = VIDEO_FRAME_LOADER_CLASS_CACHE_KEY,
                        packageVersionCode = versionCode,
                        readCache = { key -> nativePrefs.getString(key) },
                        writeCache = { key, value ->
                            nativePrefs.edit().putString(key, value).apply()
                        },
                    ) {
                        findClass {
                            matcher {
                                usingStrings(
                                    "MiVideoFrameLoader",
                                    "loadFrameTime width=%d height=%d key=%s,timeMicros=%d,cost=%d",
                                )
                            }
                        }.singleOrNull()?.name?.split("$")[0]
                    } ?: error("DexKit failed to resolve video frame loader class")
                    val exportConfigClassName = resolveDexKitInjectionPoint(
                        bridge = bridge,
                        cacheKey = VIDEO_EXPORT_CONFIG_CLASS_CACHE_KEY,
                        packageVersionCode = versionCode,
                        readCache = { key -> nativePrefs.getString(key) },
                        writeCache = { key, value ->
                            nativePrefs.edit().putString(key, value).apply()
                        },
                    ) {
                        findClass {
                            searchPackages("com.android.thememanager.videoedit.entity")
                            matcher {
                                fields {
                                    addForType(Int::class.java)
                                    addForType(Size::class.java)
                                    addForType(Boolean::class.java)
                                    addForType(String::class.java)
                                }
                            }
                        }.singleOrNull()?.name
                    } ?: error("DexKit failed to resolve export config class")
                    val frameLoaderClz = frameLoaderClassName.toClass().resolve()
                    val exportConfigClz = exportConfigClassName.toClass().resolve()

                    fun resolveFieldName(
                        cacheKey: String,
                        fallbackField: String,
                        finder: DexKitBridge.() -> String?,
                    ): String {
                        return resolveDexKitInjectionPoint(
                            bridge = bridge,
                            cacheKey = cacheKey,
                            packageVersionCode = versionCode,
                            readCache = { key -> nativePrefs.getString(key) },
                            writeCache = { key, value ->
                                nativePrefs.edit().putString(key, value).apply()
                            },
                        ) {
                            finder()
                        } ?: fallbackField
                    }

                    val playViewFieldName = resolveFieldName(
                        VIDEO_EDIT_PLAY_VIEW_FIELD_CACHE_KEY,
                        "q",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "com.android.thememanager.videoedit.widget.VlogPlayView"
                            }
                        }.singleOrNull()?.name
                    }
                    val configFieldName = resolveFieldName(
                        VIDEO_EDIT_CONFIG_FIELD_CACHE_KEY,
                        "s",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "com.android.thememanager.videoedit.VideoEditorConfig"
                            }
                        }.singleOrNull()?.name
                    }
                    val operationViewFieldName = resolveFieldName(
                        VIDEO_EDIT_OPERATION_VIEW_FIELD_CACHE_KEY,
                        "n",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type =
                                    "com.android.thememanager.videoedit.widget.SingleEditOperationView"
                            }
                        }.singleOrNull()?.name
                    }
                    val trimInFieldName = resolveFieldName(
                        VIDEO_EDIT_TRIM_IN_FIELD_CACHE_KEY,
                        "i",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "long"
                                readMethods {
                                    add {
                                        declaredClass = videoEditPoint.className
                                        name = videoEditPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("onPlayViewCreated")
                                    }
                                    add {
                                        declaredClass = fpsLimitPoint.className
                                        name = fpsLimitPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("ExportConfig %s", "export videopath is ")
                                    }
                                }
                            }
                        }.singleOrNull()?.name
                    }
                    val trimOutFieldName = resolveFieldName(
                        VIDEO_EDIT_TRIM_OUT_FIELD_CACHE_KEY,
                        "z",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "long"
                                readMethods {
                                    add {
                                        declaredClass = fpsLimitPoint.className
                                        name = fpsLimitPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("ExportConfig %s", "export videopath is ")
                                    }
                                }
                                writeMethods {
                                    add {
                                        declaredClass = videoEditPoint.className
                                        name = videoEditPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("onPlayViewCreated")
                                    }
                                }
                            }
                        }.singleOrNull()?.name
                    }
                    val frameLoaderFieldName = resolveFieldName(
                        VIDEO_EDIT_FRAME_LOADER_FIELD_CACHE_KEY,
                        "p",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "com.android.thememanager.videoedit.y"
                            }
                        }.singleOrNull()?.name
                    }
                    val clipFrameFieldName = resolveFieldName(
                        VIDEO_EDIT_CLIP_FRAME_FIELD_CACHE_KEY,
                        "g",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "com.android.thememanager.videoedit.widget.ClipFrameView"
                            }
                        }.singleOrNull()?.name
                    }
                    val clipListenerFieldName = resolveFieldName(
                        VIDEO_EDIT_CLIP_LISTENER_FIELD_CACHE_KEY,
                        "j",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "com.android.thememanager.videoedit.widget.ClipFrameView\$zy"
                            }
                        }.singleOrNull()?.name
                    }
                    val videoUriFieldName = resolveFieldName(
                        VIDEO_EDIT_VIDEO_URI_FIELD_CACHE_KEY,
                        "y",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "java.lang.String"
                                readMethods {
                                    add {
                                        declaredClass = videoEditPoint.className
                                        name = videoEditPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("onPlayViewCreated")
                                    }
                                    add {
                                        declaredClass = fpsLimitPoint.className
                                        name = fpsLimitPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("ExportConfig %s", "export videopath is ")
                                    }
                                }
                            }
                        }.singleOrNull()?.name
                    }
                    val exportPathFieldName = resolveFieldName(
                        VIDEO_EDIT_EXPORT_PATH_FIELD_CACHE_KEY,
                        "c",
                    ) {
                        findField {
                            searchPackages("com.android.thememanager.videoedit")
                            matcher {
                                declaredClass = videoEditPoint.className
                                type = "java.lang.String"
                                writeMethods {
                                    add {
                                        declaredClass = fpsLimitPoint.className
                                        name = fpsLimitPoint.methodName
                                        paramCount(0)
                                        returnType = "void"
                                        usingStrings("ExportConfig %s", "export videopath is ")
                                    }
                                }
                            }
                        }.singleOrNull()?.name
                    }

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
                        val iRef = instance.asResolver()
                        val playViewRef = iRef.firstField {
                            name = playViewFieldName
                        }.get()!!.asResolver()
                        val videoConfig = iRef.firstField {
                            name = configFieldName
                        }.get()
                        val currentTrimIn = iRef.firstField {
                            name = trimInFieldName
                        }.get() as? Long ?: 0L
                        val operationViewRef = iRef.firstField {
                            name = operationViewFieldName
                        }.get()!!.asResolver()
                        val clipFrameRef = iRef.firstField {
                            name = clipFrameFieldName
                        }.get()!!.asResolver()
                        val videoUri = iRef.firstField {
                            name = videoUriFieldName
                        }.get()
                        val sInstance = timelineRef.firstMethod {
                            name = timelineGetInstancePoint.methodName
                            returnType = timelineClz
                        }.invoke()!!
                        sInstance.asResolver().firstMethod {
                            name = timelineAttachTexturePoint.methodName
                            returnType = Void.TYPE
                        }.invoke(playViewRef.firstMethod {
                            name = "getTextureView"
                        }.invoke(), videoConfig)
                        val duration: Long =
                            sInstance.asResolver().firstMethod {
                                name = timelineGetDurationPoint.methodName
                            }.invoke() as Long
                        val activity = instance<Activity>()
                        if (duration <= 0) {
                            toastUtilsRef.firstMethod { name = toastTextPoint.methodName }
                                .invoke(activity.resources.getString(2131888794))
                            Log.e("VideoEditActivity", "onPlayViewCreated: originDuration = 0")
                            activity.finish()
                            return@replaceUnit
                        }
                        iRef.firstField { name = trimOutFieldName }.set(duration)
                        operationViewRef.firstMethod { name = operationCurrentTimePoint.methodName }
                            .invoke(currentTrimIn)
                        operationViewRef.firstMethod { name = "setTotalTime" }.invoke(duration)
                        val yVar = frameLoaderClz.firstConstructor {
                            parameterCount = 0
                        }.create()
                        iRef.firstField { name = frameLoaderFieldName }.set(yVar)
                        clipFrameRef.firstMethod { name = "setVideoFrameLoader" }.invoke(yVar)
                        clipFrameRef.firstMethod { name = "setClipFrameListener" }
                            .invoke(iRef.firstField { name = clipListenerFieldName }.get())
                        clipFrameRef.firstMethod { name = clipFrameLoadPoint.methodName }.invoke(
                            videoUri,
                            duration,
                            duration
                        )
                        sInstance.asResolver().firstMethod {
                            name = timelinePreparePoint.methodName
                            parameters(Int::class.java)
                        }.invoke(currentTrimIn.toInt())
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
                        val yObj = iRef.firstField { name = videoUriFieldName }.get()
                        val cFieldRef = iRef.firstField { name = exportPathFieldName }
                        cFieldRef.set(
                            strF7l8 + (coderUtilsRef.firstMethod {
                                name = coderHashPoint.methodName
                            }.invoke(yObj) as String) + ".mp4"
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
                        val (outWidth, outHeight) = computeExportOutputSize(width, height, 1080)
                        val toqVar = exportConfigClz.firstConstructor {
                            parameterCount = 5
                        }.create(
                            true,
                            cFieldRef.get(),
                            Size(outWidth, outHeight),
                            (((((bitrate / (width * height)) * outWidth) * outHeight) / fps) * fps).toInt(),
                            0
                        )
                        toqVar.asResolver().firstMethod {
                            name = exportConfigSetFpsPoint.methodName
                        }.invoke(fps.toInt())
                        Log.d(
                            "VideoEditActivity",
                            String.format(
                                "ExportConfig %s",
                                gsonUtilsClz.firstMethod { name = gsonSerializePoint.methodName }
                                    .invoke(toqVar)
                            )
                        )
                        Log.d("lollipop", "export videopath is " + cFieldRef.get())
                        val qRef = timelineRef.firstMethod {
                            name = timelineGetInstancePoint.methodName
                        }.invoke()!!.asResolver()
                        qRef.firstMethod {
                            name = timelineExportPoint.methodName
                        }.invoke(
                            iRef.firstField { name = trimInFieldName }.get(),
                            iRef.firstField { name = trimOutFieldName }.get(),
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

    private fun computeExportOutputSize(
        originWidth: Int,
        originHeight: Int,
        maxWidth: Int
    ): Pair<Int, Int> {
        val rawWidth = if (originWidth > maxWidth) maxWidth else originWidth
        val rawHeight = if (originWidth > maxWidth) {
            kotlin.math.ceil(originHeight / (originWidth.toDouble() / maxWidth)).toInt()
        } else {
            originHeight
        }
        return ((rawWidth / 4) * 4) to ((rawHeight / 4) * 4)
    }

    private inline fun resolveCachedMethodPoint(
        bridge: DexKitBridge,
        packageVersionCode: Long,
        cacheKey: String,
        fallbackClass: String,
        fallbackMethod: String,
        crossinline finder: DexKitBridge.() -> DexKitMethodInjectionPoint?,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = cacheKey,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            finder()
        } ?: DexKitMethodInjectionPoint(fallbackClass, fallbackMethod)
    }

    private fun resolveVideoTimelineGetInstanceMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_TIMELINE_GET_INSTANCE_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_TIMELINE_CLASS,
            fallbackMethod = "q",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/s.java:39
            // Original method in jadx: videoedit.widget.s.q()
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_TIMELINE_CLASS
                    modifiers = Modifier.PUBLIC or Modifier.STATIC or Modifier.SYNCHRONIZED
                    paramCount(0)
                    returnType = FALLBACK_VIDEO_TIMELINE_CLASS
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoTimelineAttachTextureMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_TIMELINE_ATTACH_TEXTURE_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_TIMELINE_CLASS,
            fallbackMethod = "k",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/s.java:72
            // Original method in jadx: videoedit.widget.s.k(XmsTextureView, VideoEditorConfig)
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_TIMELINE_CLASS
                    paramTypes(
                        "com.xiaomi.milab.videosdk.XmsTextureView",
                        "com.android.thememanager.videoedit.VideoEditorConfig",
                    )
                    returnType = "void"
                    usingStrings("attachTexture", "mVideoTrack is  null", "mVideoClip is  null")
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoTimelineGetDurationMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_TIMELINE_GET_DURATION_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_TIMELINE_CLASS,
            fallbackMethod = "zy",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/s.java:167
            // Original method in jadx: videoedit.widget.s.zy()
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_TIMELINE_CLASS
                    paramCount(0)
                    returnType = "long"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoTimelinePrepareMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_TIMELINE_PREPARE_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_TIMELINE_CLASS,
            fallbackMethod = "s",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/s.java:148
            // Original method in jadx: videoedit.widget.s.s(int)
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_TIMELINE_CLASS
                    paramTypes("int")
                    returnType = "void"
                    usingStrings("prepareTimeline")
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoTimelineExportMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_TIMELINE_EXPORT_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_TIMELINE_CLASS,
            fallbackMethod = "toq",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/s.java:153
            // Original method in jadx: videoedit.widget.s.toq(long, long, entity.toq)
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_TIMELINE_CLASS
                    paramTypes(
                        "long",
                        "long",
                        "com.android.thememanager.videoedit.entity.toq",
                    )
                    returnType = "void"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoToastTextMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_TOAST_TEXT_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_TOAST_UTILS_CLASS,
            fallbackMethod = "q",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/basemodule/utils/nmn5.java:33
            // Original method in jadx: basemodule.utils.nmn5.q(String)
            findMethod {
                searchPackages("com.android.thememanager.basemodule.utils")
                matcher {
                    declaredClass = FALLBACK_VIDEO_TOAST_UTILS_CLASS
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramTypes(String::class.java)
                    returnType = "void"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoOperationCurrentTimeMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_OPERATION_CURRENT_TIME_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_OPERATION_VIEW_CLASS,
            fallbackMethod = "d2ok",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/SingleEditOperationView.java:55
            // Original method in jadx: SingleEditOperationView.d2ok(long)
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_OPERATION_VIEW_CLASS
                    paramTypes("long")
                    returnType = "void"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoClipFrameLoadMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_CLIP_FRAME_LOAD_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_CLIP_FRAME_VIEW_CLASS,
            fallbackMethod = "x2",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/widget/ClipFrameView.java:138
            // Original method in jadx: ClipFrameView.x2(String, long, long)
            findMethod {
                searchPackages("com.android.thememanager.videoedit.widget")
                matcher {
                    declaredClass = FALLBACK_VIDEO_CLIP_FRAME_VIEW_CLASS
                    paramTypes("java.lang.String", "long", "long")
                    returnType = "void"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoHashStringMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_HASH_STRING_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_CODER_UTILS_CLASS,
            fallbackMethod = "zy",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/basemodule/utils/CoderUtls.java:73
            // Original method in jadx: CoderUtls.zy(String)
            findMethod {
                searchPackages("com.android.thememanager.basemodule.utils")
                matcher {
                    declaredClass = FALLBACK_VIDEO_CODER_UTILS_CLASS
                    modifiers = Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL
                    paramTypes(String::class.java)
                    returnType = "java.lang.String"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoExportConfigSetFpsMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_EXPORT_CONFIG_SET_FPS_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_EXPORT_CONFIG_CLASS,
            fallbackMethod = "kja0",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/videoedit/entity/toq.java:76
            // Original method in jadx: entity.toq.kja0(int)
            findMethod {
                searchPackages("com.android.thememanager.videoedit.entity")
                matcher {
                    declaredClass = FALLBACK_VIDEO_EXPORT_CONFIG_CLASS
                    paramTypes("int")
                    returnType = "void"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }

    private fun resolveVideoGsonSerializeMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        return resolveCachedMethodPoint(
            bridge = bridge,
            packageVersionCode = packageVersionCode,
            cacheKey = VIDEO_GSON_SERIALIZE_METHOD_CACHE_KEY,
            fallbackClass = FALLBACK_VIDEO_GSON_UTILS_CLASS,
            fallbackMethod = "g",
        ) {
            // DexKit source anchor:
            // .tmp-ref/thememanager-jadx/sources/com/android/thememanager/library/util/app/GsonUtils.java:142
            // Original method in jadx: GsonUtils.g(Object)
            findMethod {
                searchPackages("com.android.thememanager.library.util.app")
                matcher {
                    declaredClass = FALLBACK_VIDEO_GSON_UTILS_CLASS
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramTypes(Any::class.java)
                    returnType = "java.lang.String"
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        }
    }
}
