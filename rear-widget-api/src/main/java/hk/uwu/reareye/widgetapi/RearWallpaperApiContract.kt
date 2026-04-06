package hk.uwu.reareye.widgetapi

object RearWallpaperApiContract {
    const val SERVICE_PERMISSION = RearWidgetApiContract.SERVICE_PERMISSION
    const val HOOK_HOST_PACKAGE = RearWidgetApiContract.HOOK_HOST_PACKAGE
    const val ACTION_REQUEST_HOOK_SERVICE = "hk.uwu.reareye.wallpaperapi.REQUEST_HOOK_SERVICE"

    object Extras {
        const val BUNDLE = "bundle"
        const val BINDER = "binder"
        const val FORCE_SYNC = "forceSync"
    }

    object BundleKeys {
        const val ITEMS = "items"
        const val CURRENT_INDEX = "currentIndex"
        const val CURRENT_WALLPAPER_ID = "currentWallpaperId"
        const val WALLPAPER_ID = "wallpaperId"
        const val TITLE = "title"
        const val NAME = "name"
        const val PREVIEW_AVAILABLE = "previewAvailable"
        const val PREVIEW_SIGNATURE = "previewSignature"
    }
}
