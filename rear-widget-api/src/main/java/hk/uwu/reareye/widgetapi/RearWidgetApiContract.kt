package hk.uwu.reareye.widgetapi

object RearWidgetApiContract {
    const val SERVICE_PERMISSION = "hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API"
    const val HOOK_HOST_PACKAGE = "com.xiaomi.subscreencenter"
    const val ACTION_REQUEST_HOOK_SERVICE = "hk.uwu.reareye.widgetapi.REQUEST_HOOK_SERVICE"

    object Extras {
        const val BUNDLE = "bundle"
        const val BINDER = "binder"
        const val FORCE_SYNC = "forceSync"
    }

    object BundleKeys {
        const val PACKAGE_NAME = "packageName"
        const val BUSINESS = "business"
        const val NOTIFICATION_ID = "notificationId"
        const val COMPOSITE_KEY = "compositeKey"

        const val STICKY = "sticky"
        const val DISABLE_POPUP = "disablePopup"
        const val FORCE_POPUP = "forcePopup"
        const val ENABLE_FLOAT = "enableFloat"
        const val SHOW_TIME_TIP = "showTimeTip"
        const val INDEX = "index"
        const val PRIORITY = "priority"
    }
}
