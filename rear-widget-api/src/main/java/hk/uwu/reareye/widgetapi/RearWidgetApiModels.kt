package hk.uwu.reareye.widgetapi

import android.os.Bundle

data class RearWidgetBusinessSpec(
    val packageName: String,
    val business: String,
    val filePath: String,
    val defaultIndex: Int = 0,
    val defaultPriority: Int = 500,
)

data class RearWidgetNoticeOptions(
    val sticky: Boolean = false,
    val disablePopup: Boolean = true,
    val forcePopup: Boolean = false,
    val enableFloat: Boolean = false,
    val showTimeTip: Boolean = true,
    val index: Int? = null,
    val priority: Int? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBoolean(RearWidgetApiContract.BundleKeys.STICKY, sticky)
        putBoolean(RearWidgetApiContract.BundleKeys.DISABLE_POPUP, disablePopup)
        putBoolean(RearWidgetApiContract.BundleKeys.FORCE_POPUP, forcePopup)
        putBoolean(RearWidgetApiContract.BundleKeys.ENABLE_FLOAT, enableFloat)
        putBoolean(RearWidgetApiContract.BundleKeys.SHOW_TIME_TIP, showTimeTip)
        if (index != null) putInt(RearWidgetApiContract.BundleKeys.INDEX, index)
        if (priority != null) putInt(RearWidgetApiContract.BundleKeys.PRIORITY, priority)
    }

    companion object {
        fun fromBundle(bundle: Bundle?): RearWidgetNoticeOptions {
            if (bundle == null) return RearWidgetNoticeOptions()
            return RearWidgetNoticeOptions(
                sticky = bundle.getBoolean(RearWidgetApiContract.BundleKeys.STICKY, false),
                disablePopup = bundle.getBoolean(
                    RearWidgetApiContract.BundleKeys.DISABLE_POPUP,
                    true
                ),
                forcePopup = bundle.getBoolean(RearWidgetApiContract.BundleKeys.FORCE_POPUP, false),
                enableFloat = bundle.getBoolean(
                    RearWidgetApiContract.BundleKeys.ENABLE_FLOAT,
                    false
                ),
                showTimeTip = bundle.getBoolean(
                    RearWidgetApiContract.BundleKeys.SHOW_TIME_TIP,
                    true
                ),
                index = if (bundle.containsKey(RearWidgetApiContract.BundleKeys.INDEX)) {
                    bundle.getInt(RearWidgetApiContract.BundleKeys.INDEX)
                } else {
                    null
                },
                priority = if (bundle.containsKey(RearWidgetApiContract.BundleKeys.PRIORITY)) {
                    bundle.getInt(RearWidgetApiContract.BundleKeys.PRIORITY)
                } else {
                    null
                },
            )
        }
    }
}

data class RearWidgetNoticeTicket(
    val packageName: String,
    val business: String,
    val notificationId: Int,
    val compositeKey: String,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(RearWidgetApiContract.BundleKeys.PACKAGE_NAME, packageName)
        putString(RearWidgetApiContract.BundleKeys.BUSINESS, business)
        putInt(RearWidgetApiContract.BundleKeys.NOTIFICATION_ID, notificationId)
        putString(RearWidgetApiContract.BundleKeys.COMPOSITE_KEY, compositeKey)
    }

    companion object {
        fun fromBundle(bundle: Bundle?): RearWidgetNoticeTicket? {
            if (bundle == null) return null
            val packageName = bundle.getString(RearWidgetApiContract.BundleKeys.PACKAGE_NAME)
                ?.trim()
                .orEmpty()
            val business = bundle.getString(RearWidgetApiContract.BundleKeys.BUSINESS)
                ?.trim()
                .orEmpty()
            val compositeKey = bundle.getString(RearWidgetApiContract.BundleKeys.COMPOSITE_KEY)
                ?.trim()
                .orEmpty()
            if (packageName.isBlank() || business.isBlank() || compositeKey.isBlank()) return null
            if (!bundle.containsKey(RearWidgetApiContract.BundleKeys.NOTIFICATION_ID)) return null
            val notificationId = bundle.getInt(RearWidgetApiContract.BundleKeys.NOTIFICATION_ID)
            return RearWidgetNoticeTicket(packageName, business, notificationId, compositeKey)
        }
    }
}

data class RearWidgetActiveNotice(
    val ticket: RearWidgetNoticeTicket,
    val payload: Bundle,
    val options: RearWidgetNoticeOptions,
    val createdAt: Long = System.currentTimeMillis(),
)
