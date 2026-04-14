package hk.uwu.reareye.hook.utils

import com.highcapable.yukihookapi.hook.log.YLog
import org.luckypray.dexkit.DexKitBridge

private const val DEX_KIT_CACHE_SEPARATOR = ";;"
private const val DEX_KIT_MEMBER_SEPARATOR = "::"

internal data class DexKitMethodInjectionPoint(
    val className: String,
    val methodName: String,
)

internal inline fun resolveDexKitInjectionPoint(
    bridge: DexKitBridge,
    cacheKey: String,
    packageVersionCode: Long,
    readCache: (String) -> String?,
    writeCache: (String, String) -> Unit,
    finder: DexKitBridge.() -> String?,
): String? {
    val cached = decodeDexKitInjectionCache(
        raw = readCache(cacheKey),
        expectedVersionCode = packageVersionCode,
    )
    if (cached != null) return cached

    val resolved = bridge.finder()?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    writeCache(cacheKey, encodeDexKitInjectionCache(packageVersionCode, resolved))
    YLog.debug("Save $cacheKey hook point $resolved")
    return resolved
}

internal inline fun resolveDexKitMethodInjectionPoint(
    bridge: DexKitBridge,
    cacheKey: String,
    packageVersionCode: Long,
    readCache: (String) -> String?,
    writeCache: (String, String) -> Unit,
    finder: DexKitBridge.() -> DexKitMethodInjectionPoint?,
): DexKitMethodInjectionPoint? {
    return resolveDexKitInjectionPoint(
        bridge = bridge,
        cacheKey = cacheKey,
        packageVersionCode = packageVersionCode,
        readCache = readCache,
        writeCache = writeCache,
    ) {
        finder()?.encode()
    }?.decodeDexKitMethodInjectionPoint()
}

private fun decodeDexKitInjectionCache(raw: String?, expectedVersionCode: Long): String? {
    val normalized = raw?.trim().orEmpty()
    if (normalized.isEmpty()) return null

    val separatorIndex = normalized.indexOf(DEX_KIT_CACHE_SEPARATOR)
    if (separatorIndex <= 0) return null

    val versionCode = normalized.substring(0, separatorIndex).toLongOrNull() ?: return null
    if (versionCode != expectedVersionCode) return null

    val point = normalized.substring(separatorIndex + DEX_KIT_CACHE_SEPARATOR.length).trim()
    return point.takeIf { it.isNotEmpty() }
}

private fun encodeDexKitInjectionCache(versionCode: Long, point: String): String {
    return "$versionCode$DEX_KIT_CACHE_SEPARATOR$point"
}

private fun DexKitMethodInjectionPoint.encode(): String {
    return "$className$DEX_KIT_MEMBER_SEPARATOR$methodName"
}

private fun String.decodeDexKitMethodInjectionPoint(): DexKitMethodInjectionPoint? {
    val separatorIndex = indexOf(DEX_KIT_MEMBER_SEPARATOR)
    if (separatorIndex <= 0) return null
    val className = substring(0, separatorIndex).trim()
    val methodName = substring(separatorIndex + DEX_KIT_MEMBER_SEPARATOR.length).trim()
    if (className.isEmpty() || methodName.isEmpty()) return null
    return DexKitMethodInjectionPoint(className, methodName)
}
