package hk.uwu.reareye.ui.config

import androidx.annotation.StringRes
import androidx.core.net.toUri
import hk.uwu.reareye.R

const val DEFAULT_REAR_STORE_API_BASE_URL = "https://rearstore-api.uwu.hk"
const val CLOUDFLARE_REAR_STORE_API_BASE_URL = "https://cf-rearstore-api.uwu.hk"

enum class StoreApiProvider(
    val value: Int,
    @param:StringRes val titleRes: Int,
    val fixedBaseUrl: String? = null,
) {
    ALIYUN(
        value = 0,
        titleRes = R.string.module_store_api_provider_aliyun,
        fixedBaseUrl = DEFAULT_REAR_STORE_API_BASE_URL,
    ),
    CLOUDFLARE(
        value = 1,
        titleRes = R.string.module_store_api_provider_cf,
        fixedBaseUrl = CLOUDFLARE_REAR_STORE_API_BASE_URL,
    ),
    CUSTOM(
        value = 99,
        titleRes = R.string.module_store_api_provider_custom,
    );

    companion object {
        val default = ALIYUN
        val selectableEntries = entries

        fun fromValue(value: Int): StoreApiProvider {
            return entries.firstOrNull { it.value == value } ?: default
        }
    }
}

data class RearStoreCustomDomainValidationResult(
    val normalizedDomain: String,
    val baseUrl: String,
)

fun normalizeRearStoreCustomDomainInput(input: String): String {
    return input.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

fun validateRearStoreCustomDomain(input: String): RearStoreCustomDomainValidationResult? {
    val normalized = normalizeRearStoreCustomDomainInput(input).lowercase()
    if (normalized.isEmpty()) return null
    if (normalized.contains("://")) return null
    if (normalized.any { it.isWhitespace() }) return null
    if (normalized.contains('/')) return null
    if (normalized.contains('?')) return null
    if (normalized.contains('#')) return null
    if (normalized.contains('@')) return null

    val parsed = "https://$normalized".toUri()
    val authority = parsed.encodedAuthority?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    if (parsed.scheme != "https") return null
    if (parsed.host.isNullOrBlank()) return null
    if (!parsed.path.isNullOrEmpty()) return null
    if (!parsed.query.isNullOrEmpty()) return null
    if (!parsed.fragment.isNullOrEmpty()) return null
    if (authority != normalized) return null

    return RearStoreCustomDomainValidationResult(
        normalizedDomain = authority,
        baseUrl = "https://$authority",
    )
}

fun resolveRearStoreApiBaseUrl(prefsManager: PrefsManager): String {
    val provider = StoreApiProvider.fromValue(
        prefsManager.getInt(
            ConfigKeys.MODULE_STORE_API_PROVIDER,
            StoreApiProvider.default.value,
        )
    )
    provider.fixedBaseUrl?.let { return it }

    val customDomain = prefsManager.getString(ConfigKeys.MODULE_STORE_API_CUSTOM_DOMAIN, "")
    return validateRearStoreCustomDomain(customDomain)?.baseUrl
        ?: StoreApiProvider.default.fixedBaseUrl
        ?: DEFAULT_REAR_STORE_API_BASE_URL
}
