package hk.uwu.reareye.ui.config

/**
 * 经过固定路径校验的旧 Yuki 偏好文件路径。
 *
 * 构造入口只接受 root 发现命令返回的路径形态，调用方不能把任意外部路径传给 root 读取器。
 */
@JvmInline
internal value class LegacyPreferencePath private constructor(
    val value: String,
) {
    companion object {
        private const val ROOT_PREFIX = "/data/misc/apexdata/"
        private const val FILE_SUFFIX = "/prefs/hk.uwu.reareye/hk.uwu.reareye_preferences.xml"

        /** 从固定发现命令的单行输出创建并校验路径。 */
        internal fun fromDiscoveredPath(rawPath: String): LegacyPreferencePath {
            require(rawPath.isNotEmpty()) { "Legacy preference path is empty" }
            require(rawPath.startsWith(ROOT_PREFIX)) {
                "Legacy preference path is outside the apexdata root"
            }
            require(rawPath.endsWith(FILE_SUFFIX)) {
                "Legacy preference path has an unexpected suffix"
            }
            require('\u0000' !in rawPath && '\r' !in rawPath && '\n' !in rawPath) {
                "Legacy preference path contains a control character"
            }

            val middle = rawPath.substring(
                ROOT_PREFIX.length,
                rawPath.length - FILE_SUFFIX.length,
            )
            val segments = middle.split('/')
            require(segments.isNotEmpty() && segments.all(::isSafePathSegment)) {
                "Legacy preference path contains an unsafe apexdata segment"
            }
            return LegacyPreferencePath(rawPath)
        }

        /** 判断路径段是否可安全地交给固定的 shell 命令。 */
        private fun isSafePathSegment(segment: String): Boolean {
            if (segment.isEmpty() || segment == "." || segment == "..") return false
            return segment.all { character ->
                character in 'a'..'z' ||
                        character in 'A'..'Z' ||
                        character in '0'..'9' ||
                        character == '_' ||
                        character == '-'
            }
        }
    }
}

/**
 * root 复制目标。
 *
 * 目标始终是当前应用自己的 shared_prefs 目录中的随机临时文件，且 owner 使用当前应用 UID。
 * 该类型同时负责验证目标路径，避免 root runner 接收任意文件路径。
 */
internal class LegacyPreferenceCopyDestination private constructor(
    val tempName: String,
    val value: String,
    val ownerUid: Int,
) {
    fun validate() {
        require(TEMP_NAME_PATTERN.matches(tempName)) {
            "Legacy preference temporary name is unsafe"
        }
        require(ownerUid > 0) { "Legacy preference copy owner UID must be positive" }
        require(isSafeAbsoluteDataPath(value)) {
            "Legacy preference copy destination is outside the app data directory"
        }
        require(value.endsWith("/shared_prefs/$tempName.xml")) {
            "Legacy preference copy destination has an unexpected suffix"
        }
    }

    companion object {
        private const val TEMP_NAME_PREFIX = "__reareye_yuki_import_"
        private val TEMP_NAME_PATTERN = Regex("${TEMP_NAME_PREFIX}[A-Za-z0-9-]{32}")

        /** 为当前应用生成一个新的临时 SharedPreferences 目标。 */
        internal fun create(context: android.content.Context): LegacyPreferenceCopyDestination {
            val applicationContext = context.applicationContext
            val dataDir = applicationContext.applicationInfo.dataDir
            require(isSafeAbsoluteDataPath(dataDir)) {
                "Current application data directory is not safe for root preference import"
            }
            val sharedPreferencesDirectory = java.io.File(dataDir, "shared_prefs")
            require(sharedPreferencesDirectory.exists() || sharedPreferencesDirectory.mkdirs()) {
                "Unable to create application shared_prefs directory"
            }
            require(sharedPreferencesDirectory.isDirectory) {
                "Application shared_prefs path is not a directory"
            }

            val tempName = TEMP_NAME_PREFIX + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
            return LegacyPreferenceCopyDestination(
                tempName = tempName,
                value = "$dataDir/shared_prefs/$tempName.xml",
                ownerUid = android.os.Process.myUid(),
            ).also { it.validate() }
        }

        private fun isSafeAbsoluteDataPath(path: String): Boolean {
            if (!path.startsWith("/data/") || path.endsWith('/')) return false
            if ('\u0000' in path || '\r' in path || '\n' in path) return false
            val segments = path.split('/').drop(1)
            return segments.isNotEmpty() && segments.all { segment ->
                segment.isNotEmpty() &&
                        segment != "." &&
                        segment != ".." &&
                        segment.all { character ->
                            character in 'a'..'z' ||
                                    character in 'A'..'Z' ||
                                    character in '0'..'9' ||
                                    character == '_' ||
                                    character == '-' ||
                                    character == '.'
                        }
            }
        }
    }
}

/**
 * 一次 root 读取得到的旧 Yuki 偏好快照。
 *
 * [sourcePath] 仅用于诊断和审计，配置值本身不会被日志输出。values 直接来自 Android
 * SharedPreferences.all，因此保留 SharedPreferences 支持的原生值类型和值。
 */
internal data class LegacyPreferenceSnapshot(
    val sourcePath: LegacyPreferencePath,
    val values: Map<String, Any?>,
)

/**
 * 旧 Yuki 偏好来源接口。
 *
 * 生产实现通过 root 动态发现并读取文件；其它实现可以提供固定快照，但迁移器只接受已经
 * 完成类型判定的 SharedPreferences 值，不再依赖 XML 解析器。
 */
internal fun interface LegacyPreferenceSource {
    /** 读取并返回唯一旧偏好来源。 */
    fun read(): LegacyPreferenceSnapshot
}
