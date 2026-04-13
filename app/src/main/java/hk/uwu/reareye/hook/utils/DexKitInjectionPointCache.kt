package hk.uwu.reareye.hook.utils

import com.highcapable.yukihookapi.hook.log.YLog
import org.luckypray.dexkit.DexKitBridge

private const val DEX_KIT_CACHE_SEPARATOR = ";;"

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
