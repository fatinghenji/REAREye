package hk.uwu.reareye.repository.rearwallpaper

data class RearWallpaperInfo(
    val wallpaperId: Int,
    val title: String,
    val name: String,
    val previewAvailable: Boolean,
    val previewSignature: String,
    val cachePath: String? = null,
)

data class RearWallpaperCatalog(
    val wallpapers: List<RearWallpaperInfo>,
    val currentIndex: Int,
    val currentWallpaperId: Int?,
)
