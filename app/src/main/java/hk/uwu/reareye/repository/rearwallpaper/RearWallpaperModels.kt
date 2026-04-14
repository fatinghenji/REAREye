package hk.uwu.reareye.repository.rearwallpaper

data class RearWallpaperInfo(
    val wallpaperId: Int,
    val title: String,
    val name: String,
    val previewAvailable: Boolean,
    val previewSignature: String,
    val description: String = "",
    val author: String = "",
    val designer: String = "",
    val resSubType: String = "",
    val imported: Boolean = false,
    val canEditMetadata: Boolean = false,
    val canDelete: Boolean = false,
    val editable: Boolean = false,
    val thirdParties: Boolean = false,
    val supportAon: Boolean = false,
    val cachePath: String? = null,
)

data class RearWallpaperCatalog(
    val wallpapers: List<RearWallpaperInfo>,
    val currentIndex: Int,
    val currentWallpaperId: Int?,
)

data class RearWallpaperMetadataOptions(
    val titleFallback: String,
    val titleZhCn: String,
    val descriptionFallback: String,
    val descriptionZhCn: String,
    val author: String,
    val designer: String,
    val category: String,
    val resSubType: String,
    val editable: Boolean,
    val thirdParties: Boolean,
    val supportAon: Boolean,
)

data class RearWallpaperOperationResult(
    val success: Boolean,
    val wallpaperId: Int? = null,
    val error: String? = null,
)
