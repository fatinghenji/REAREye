package hk.uwu.reareye.hook.scopes.system.modules

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.BuildConfig
import hk.uwu.reareye.hook.core.HookPrefs
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker
import hk.uwu.reareye.ui.config.ConfigKeys
import java.io.File
import java.util.zip.ZipFile

class RustWrapperLaunchHook : YukiBaseHooker() {
    override fun onHook() {
        loadSystem {
            runCatching {
                YLog.debug("Rust wrapper launch hook installing")
                val rustProcessClass = "android.os.RustProcessImpl".toClass().resolve()
                rustProcessClass.firstMethod {
                    name = "startRustProcess"
                    parameters(
                        String::class.java, // processName
                        String::class.java, // packageName
                        Int::class.javaPrimitiveType!!, // uid
                        IntArray::class.java, // gids
                        Int::class.javaPrimitiveType!!, // runtimeFlags
                        Int::class.javaPrimitiveType!!, // mountExternal
                        Int::class.javaPrimitiveType!!, // targetSdkVersion
                        String::class.java, // seInfo
                        String::class.java, // abi
                        String::class.java, // instructionSet
                        String::class.java, // appDataDir
                        String::class.java, // invokeWith
                        Int::class.javaPrimitiveType!!, // zygotePolicyFlags
                        Boolean::class.javaPrimitiveType!!, // isTopApp
                        LongArray::class.java, // disabledCompatChanges
                        Map::class.java, // pkgDataInfoMap
                        Map::class.java, // allowlistedDataInfoList
                        Boolean::class.javaPrimitiveType!!, // bindMountAppsData
                        Boolean::class.javaPrimitiveType!!, // bindMountAppStorageDirs
                        Boolean::class.javaPrimitiveType!!, // bindMountOverrideSysprops
                        String::class.java, // binary
                        String::class.java, // envs
                        Long::class.javaPrimitiveType!! // seq
                    )
                }.hook().before {
                    YLog.debug("Rust wrapper startRustProcess called args=${args.size}")
                    if (args.size <= ENVS_INDEX) {
                        YLog.debug("Rust wrapper skip reason=args_size size=${args.size}")
                        return@before
                    }
                    val packageName = args[PACKAGE_INDEX] as? String ?: run {
                        YLog.debug("Rust wrapper skip reason=package_null")
                        return@before
                    }
                    val originalBinary = args[BINARY_INDEX] as? String ?: run {
                        YLog.debug("Rust wrapper skip package=$packageName reason=binary_null")
                        return@before
                    }
                    val abi = args[ABI_INDEX] as? String ?: ABI_ARM64
                    val originalEnv = args[ENVS_INDEX] as? String
                    YLog.debug(
                        "Rust wrapper candidate package=$packageName abi=$abi binary=$originalBinary " +
                                "env=${originalEnv.orEmpty()}"
                    )
                    val spec = WrapperRegistry.find(packageName, originalBinary) ?: run {
                        YLog.debug("Rust wrapper skip package=$packageName reason=no_spec")
                        return@before
                    }
                    val prepared = prepareWrapper(spec, originalBinary, abi) ?: run {
                        YLog.debug("Rust wrapper skip package=$packageName reason=prepare_failed")
                        return@before
                    }
                    val envValues =
                        spec.envProvider(prefs, originalBinary, prepared.originalBinaryForWrapper)
                    writeModuleEnv(
                        File(prepared.wrapperBinary).parentFile,
                        spec.moduleId,
                        envValues
                    )

                    args[BINARY_INDEX] = prepared.wrapperBinary
                    YLog.debug(
                        "Rust wrapper injected package=$packageName wrapper=${prepared.wrapperBinary} " +
                                "original=${prepared.originalBinaryForWrapper} moduleEnv=true envUnchanged=${originalEnv.orEmpty()}"
                    )
                }
                YLog.debug("Rust wrapper launch hook installed")
            }.onFailure {
                YLog.warn(it)
            }
        }
    }

    private fun prepareWrapper(
        spec: WrapperSpec,
        originalBinary: String,
        abi: String
    ): PreparedWrapper? {
        YLog.debug("Rust wrapper prepare start package=${spec.packageName} binary=$originalBinary abi=$abi")
        val originalArchiveOrFile = originalBinary.substringBefore('!').let(::File)
        val isZipBinary = originalBinary.contains("!/")
        val targetDir = if (isZipBinary) {
            File(
                File(originalArchiveOrFile.parentFile ?: return null, "lib"),
                abi.toInstalledLibDirName()
            )
        } else {
            originalArchiveOrFile.parentFile ?: return null
        }
        YLog.debug("Rust wrapper targetDir=${targetDir.absolutePath} zipBinary=$isZipBinary sameDir=true")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            YLog.debug("Rust wrapper mkdir failed dir=${targetDir.absolutePath}")
            return null
        }

        val originalBinaryForWrapper = if (isZipBinary) {
            val extractedOriginal = File(targetDir, spec.originalLibName)
            if (!extractEntry(
                    originalArchiveOrFile,
                    "lib/$abi/${spec.originalLibName}",
                    extractedOriginal,
                    executable = false
                )
            ) {
                return null
            }
            extractedOriginal.absolutePath
        } else {
            originalArchiveOrFile.absolutePath
        }

        val moduleInfo = resolveModuleApplicationInfo() ?: run {
            YLog.debug("Rust wrapper prepare failed reason=module_info_null")
            return null
        }
        val moduleApk = File(moduleInfo.sourceDir ?: return null)
        val wrapperFile = File(targetDir, spec.wrapperLibName)
        YLog.debug("Rust wrapper moduleApk=${moduleApk.absolutePath} wrapper=${wrapperFile.absolutePath}")
        if (!extractEntry(
                moduleApk,
                "lib/$abi/${spec.wrapperLibName}",
                wrapperFile,
                executable = true
            )
        ) {
            return null
        }

        spec.dependencyLibNames.forEach { dependency ->
            val dependencyFile = File(targetDir, dependency)
            if (!extractEntry(
                    moduleApk,
                    "lib/$abi/$dependency",
                    dependencyFile,
                    executable = true
                )
            ) {
                return null
            }
        }

        if (!wrapperFile.canExecute()) wrapperFile.setExecutable(true, false)
        YLog.debug(
            "Rust wrapper prepare success wrapper=${wrapperFile.absolutePath} " +
                    "originalForWrapper=$originalBinaryForWrapper executable=${wrapperFile.canExecute()}"
        )
        return PreparedWrapper(
            wrapperBinary = wrapperFile.absolutePath,
            originalBinaryForWrapper = originalBinaryForWrapper,
        )
    }

    @SuppressLint("SetWorldReadable")
    private fun extractEntry(
        apk: File,
        entryName: String,
        outFile: File,
        executable: Boolean
    ): Boolean {
        return runCatching {
            ZipFile(apk).use { zip ->
                val entry = zip.getEntry(entryName) ?: run {
                    YLog.debug("Rust wrapper entry not found apk=${apk.absolutePath} entry=$entryName")
                    return false
                }
                val shouldWrite = !outFile.exists() || outFile.length() != entry.size ||
                        outFile.lastModified() < apk.lastModified()
                YLog.debug(
                    "Rust wrapper extract entry=$entryName out=${outFile.absolutePath} " +
                            "shouldWrite=$shouldWrite size=${entry.size} exists=${outFile.exists()}"
                )
                if (shouldWrite) {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile.setReadable(true, false)
                    outFile.setWritable(true, true)
                    if (executable) outFile.setExecutable(true, false)
                }
                true
            }
        }.onFailure {
            YLog.warn(it)
        }.getOrDefault(false)
    }

    private fun resolveModuleApplicationInfo(): ApplicationInfo? {
        return runCatching {
            val activityThreadClass = "android.app.ActivityThread".toClass().resolve()
            val currentActivityThread = activityThreadClass.firstMethod {
                name = "currentActivityThread"
            }.invoke<Any>()
            val systemContext = currentActivityThread?.asResolver()?.firstMethod {
                name = "getSystemContext"
            }?.invoke<android.content.Context>() ?: return null
            systemContext.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
        }.onFailure {
            YLog.warn(it)
        }.getOrNull()
    }

    @SuppressLint("SetWorldReadable")
    private fun writeModuleEnv(moduleDir: File?, moduleId: String, values: Map<String, String>) {
        if (moduleDir == null) return
        runCatching {
            if (!moduleDir.exists()) moduleDir.mkdirs()
            val configFile = File(moduleDir, MODULE_ENV_FILE.format(moduleId))
            val content =
                values.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
            if (!configFile.exists() || configFile.readText() != content) {
                configFile.writeText(content)
                configFile.setReadable(true, false)
                configFile.setWritable(true, true)
            }
            YLog.debug("Rust wrapper module env path=${configFile.absolutePath} content=$content")
        }.onFailure {
            YLog.warn(it)
        }
    }

    private data class PreparedWrapper(
        val wrapperBinary: String,
        val originalBinaryForWrapper: String,
    )

    private fun String.toInstalledLibDirName(): String {
        return when (this) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            else -> this
        }
    }

    private data class WrapperSpec(
        val moduleId: String,
        val packageName: String,
        val originalLibName: String,
        val wrapperLibName: String,
        val dependencyLibNames: List<String> = emptyList(),
        val envProvider: (HookPrefs, String, String) -> Map<String, String>,
    ) {
        fun matches(packageName: String, originalBinary: String): Boolean {
            return this.packageName == packageName && originalBinary.substringBefore('!')
                .endsWith(originalLibName)
        }
    }

    private object WrapperRegistry {
        private val specs = listOf(
            WrapperSpec(
                moduleId = MODULE_ID_WEATHER,
                packageName = "com.miui.weather2",
                originalLibName = "libweather_app.so",
                wrapperLibName = "libreareye_weather_hook.so",
                dependencyLibNames = emptyList(),
            ) { prefs, _, originalForWrapper ->
                mapOf(
                    ENV_ORIGINAL_BINARY to originalForWrapper,
                    ENV_DEVICE_LEVEL to prefs.getInt(ConfigKeys.WEATHER_DEVICE_LEVEL, 0).toString(),
                    ENV_UNLOCK_SUPER_BLUR to if (prefs.getBoolean(
                            ConfigKeys.WEATHER_UNLOCK_SUPER_BLUR,
                            false
                        )
                    ) "1" else "0"
                )
            },
            WrapperSpec(
                moduleId = MODULE_ID_GALLERY,
                packageName = "com.miui.gallery",
                originalLibName = "libapp_gallery.so",
                wrapperLibName = "libreareye_gallery_hook.so",
                dependencyLibNames = emptyList(),
            ) { prefs, _, originalForWrapper ->
                mapOf(
                    ENV_GALLERY_ORIGINAL_BINARY to originalForWrapper,
                    ENV_GALLERY_BACKUP_SERVER to prefs.getInt(ConfigKeys.GALLERY_BACKUP_SERVER, 0)
                        .toString(),
                    ENV_GALLERY_ENABLE_HDR_ENHANCED to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_HDR_ENHANCED
                    ),
                    ENV_GALLERY_ENABLE_PDF to booleanFlag(prefs, ConfigKeys.GALLERY_ENABLE_PDF),
                    ENV_GALLERY_ENABLE_OCR to booleanFlag(prefs, ConfigKeys.GALLERY_ENABLE_OCR),
                    ENV_GALLERY_ENABLE_OCR_FORM to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_OCR_FORM
                    ),
                    ENV_GALLERY_LONGER_TRASHBIN_TIME to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_LONGER_TRASHBIN_TIME
                    ),
                    ENV_GALLERY_TRASH_RETENTION_DAYS to prefs.getInt(
                        ConfigKeys.GALLERY_TRASH_RETENTION_DAYS,
                        365
                    ).toString(),
                    ENV_GALLERY_ENABLE_ID_PHOTO to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_ID_PHOTO
                    ),
                    ENV_GALLERY_ENABLE_PHOTO_MOVIE to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_PHOTO_MOVIE
                    ),
                    ENV_GALLERY_ENABLE_VIDEO_POST to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_VIDEO_POST
                    ),
                    ENV_GALLERY_ENABLE_VIDEO_EDITOR to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_VIDEO_EDITOR
                    ),
                    ENV_GALLERY_ENABLE_MAGIC_MATTING to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_MAGIC_MATTING
                    ),
                    ENV_GALLERY_ENABLE_PRINT to booleanFlag(prefs, ConfigKeys.GALLERY_ENABLE_PRINT),
                    ENV_GALLERY_ENABLE_PRIVACY_WATERMARK to booleanFlag(
                        prefs,
                        ConfigKeys.GALLERY_ENABLE_PRIVACY_WATERMARK
                    ),
                )
            }
        )

        fun find(packageName: String, originalBinary: String): WrapperSpec? {
            return specs.firstOrNull { it.matches(packageName, originalBinary) }
        }
    }

    companion object {
        private const val ABI_ARM64 = "arm64-v8a"

        private const val PACKAGE_INDEX = 1
        private const val ABI_INDEX = 8
        private const val BINARY_INDEX = 20
        private const val ENVS_INDEX = 21

        private const val MODULE_ENV_FILE = "reareye_%s.env"
        private const val MODULE_ID_WEATHER = "weather"
        private const val MODULE_ID_GALLERY = "gallery"

        private const val ENV_ORIGINAL_BINARY = "REAREYE_WEATHER_ORIGINAL_BINARY"
        private const val ENV_DEVICE_LEVEL = "REAREYE_WEATHER_DEVICE_LEVEL"
        private const val ENV_UNLOCK_SUPER_BLUR = "REAREYE_WEATHER_UNLOCK_SUPER_BLUR"
        private const val ENV_GALLERY_ORIGINAL_BINARY = "REAREYE_GALLERY_ORIGINAL_BINARY"
        private const val ENV_GALLERY_BACKUP_SERVER = "REAREYE_GALLERY_BACKUP_SERVER"
        private const val ENV_GALLERY_ENABLE_HDR_ENHANCED = "REAREYE_GALLERY_ENABLE_HDR_ENHANCED"
        private const val ENV_GALLERY_ENABLE_PDF = "REAREYE_GALLERY_ENABLE_PDF"
        private const val ENV_GALLERY_ENABLE_OCR = "REAREYE_GALLERY_ENABLE_OCR"
        private const val ENV_GALLERY_ENABLE_OCR_FORM = "REAREYE_GALLERY_ENABLE_OCR_FORM"
        private const val ENV_GALLERY_LONGER_TRASHBIN_TIME = "REAREYE_GALLERY_LONGER_TRASHBIN_TIME"
        private const val ENV_GALLERY_TRASH_RETENTION_DAYS = "REAREYE_GALLERY_TRASH_RETENTION_DAYS"
        private const val ENV_GALLERY_ENABLE_ID_PHOTO = "REAREYE_GALLERY_ENABLE_ID_PHOTO"
        private const val ENV_GALLERY_ENABLE_PHOTO_MOVIE = "REAREYE_GALLERY_ENABLE_PHOTO_MOVIE"
        private const val ENV_GALLERY_ENABLE_VIDEO_POST = "REAREYE_GALLERY_ENABLE_VIDEO_POST"
        private const val ENV_GALLERY_ENABLE_VIDEO_EDITOR = "REAREYE_GALLERY_ENABLE_VIDEO_EDITOR"
        private const val ENV_GALLERY_ENABLE_MAGIC_MATTING = "REAREYE_GALLERY_ENABLE_MAGIC_MATTING"
        private const val ENV_GALLERY_ENABLE_PRINT = "REAREYE_GALLERY_ENABLE_PRINT"
        private const val ENV_GALLERY_ENABLE_PRIVACY_WATERMARK =
            "REAREYE_GALLERY_ENABLE_PRIVACY_WATERMARK"


        private fun booleanFlag(
            prefs: HookPrefs,
            key: String,
        ): String {
            return if (prefs.getBoolean(key, false)) "1" else "0"
        }
    }
}
