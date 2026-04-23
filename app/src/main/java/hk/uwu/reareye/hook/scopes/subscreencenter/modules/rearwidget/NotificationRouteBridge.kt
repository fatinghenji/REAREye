package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

internal object NotificationRouteBridgeContract {
    const val HOOK_HOST_PACKAGE = "com.xiaomi.subscreencenter"
    const val SOURCE_HOST_PACKAGE = "com.android.systemui"
    const val CARD_ID_PREFIX = "__ordinary_channel__"

    object Action {
        const val REQUEST_BINDER = "hk.uwu.reareye.notification.channel_route.REQUEST_BINDER"
    }

    object Subchannel {
        const val NOTIFICATION_POSTED = "notification_posted"
        const val NOTIFICATION_REMOVED = "notification_removed"
    }

    object Keys {
        const val PACKAGE_NAME = "packageName"
        const val NOTIFICATION_ID = "notificationId"
        const val NOTIFICATION_KEY = "notificationKey"
        const val POST_TIME = "postTime"
        const val CHANNEL_ID = "channelId"
        const val TITLE = "title"
        const val TEXT = "text"
        const val BIG_TEXT = "bigText"
        const val SUB_TEXT = "subText"
        const val SHORT_CRITICAL_TEXT = "shortCriticalText"
        const val REMOVE_REASON = "removeReason"
    }
}

internal data class NotificationRouteSnapshot(
    val packageName: String,
    val notificationId: Int,
    val notificationKey: String?,
    val postTime: Long,
    val channelId: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val shortCriticalText: String?,
) {
    fun stableKey(): String {
        return notificationKey?.takeIf { it.isNotBlank() }
            ?: "$packageName:$notificationId:$postTime:$channelId"
    }

    fun cardId(): String {
        return "${NotificationRouteBridgeContract.CARD_ID_PREFIX}:${stableKey()}"
    }

    fun toBundle(): Bundle {
        return Bundle().apply {
            putString(NotificationRouteBridgeContract.Keys.PACKAGE_NAME, packageName)
            putInt(NotificationRouteBridgeContract.Keys.NOTIFICATION_ID, notificationId)
            putLong(NotificationRouteBridgeContract.Keys.POST_TIME, postTime)
            putString(NotificationRouteBridgeContract.Keys.CHANNEL_ID, channelId)
            putString(NotificationRouteBridgeContract.Keys.NOTIFICATION_KEY, notificationKey)
            putString(NotificationRouteBridgeContract.Keys.TITLE, title)
            putString(NotificationRouteBridgeContract.Keys.TEXT, text)
            putString(NotificationRouteBridgeContract.Keys.BIG_TEXT, bigText)
            putString(NotificationRouteBridgeContract.Keys.SUB_TEXT, subText)
            putString(
                NotificationRouteBridgeContract.Keys.SHORT_CRITICAL_TEXT,
                shortCriticalText,
            )
        }
    }

    fun toRemovalBundle(removeReason: Int): Bundle {
        return toBundle().apply {
            putInt(NotificationRouteBridgeContract.Keys.REMOVE_REASON, removeReason)
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle?): NotificationRouteSnapshot? {
            if (bundle == null) return null
            val packageName = bundle.getString(NotificationRouteBridgeContract.Keys.PACKAGE_NAME)
                ?.trim()
                .orEmpty()
            val channelId = bundle.getString(NotificationRouteBridgeContract.Keys.CHANNEL_ID)
                ?.trim()
                .orEmpty()
            if (packageName.isBlank() || channelId.isBlank()) return null
            if (!bundle.containsKey(NotificationRouteBridgeContract.Keys.NOTIFICATION_ID)) return null

            return NotificationRouteSnapshot(
                packageName = packageName,
                notificationId = bundle.getInt(NotificationRouteBridgeContract.Keys.NOTIFICATION_ID),
                notificationKey = bundle.getString(NotificationRouteBridgeContract.Keys.NOTIFICATION_KEY)
                    ?.trim()
                    ?.ifBlank { null },
                postTime = bundle.getLong(NotificationRouteBridgeContract.Keys.POST_TIME, 0L),
                channelId = channelId,
                title = bundle.getString(NotificationRouteBridgeContract.Keys.TITLE),
                text = bundle.getString(NotificationRouteBridgeContract.Keys.TEXT),
                bigText = bundle.getString(NotificationRouteBridgeContract.Keys.BIG_TEXT),
                subText = bundle.getString(NotificationRouteBridgeContract.Keys.SUB_TEXT),
                shortCriticalText = bundle.getString(
                    NotificationRouteBridgeContract.Keys.SHORT_CRITICAL_TEXT,
                ),
            )
        }

        fun fromStatusBarNotification(
            sbn: StatusBarNotification,
            requirePlainExtras: Boolean = true,
        ): NotificationRouteSnapshot? {
            val packageName = sbn.packageName?.trim().orEmpty()
            val channelId = sbn.notification.channelId?.trim()?.ifBlank { null } ?: return null
            if (packageName.isBlank()) return null

            val extras = sbn.notification.extras ?: Bundle.EMPTY
            if (requirePlainExtras) {
                if (!extras.getString("miui.focus.param").isNullOrBlank()) return null
                if (!extras.getString("miui.rear.param").isNullOrBlank()) return null
            }

            return NotificationRouteSnapshot(
                packageName = packageName,
                notificationId = sbn.id,
                notificationKey = sbn.key?.trim()?.ifBlank { null },
                postTime = sbn.postTime,
                channelId = channelId,
                title = extractTextCandidate(
                    extras,
                    Notification.EXTRA_TITLE,
                    Notification.EXTRA_TITLE_BIG,
                ),
                text = extractTextCandidate(extras, Notification.EXTRA_TEXT),
                bigText = extractTextCandidate(extras, Notification.EXTRA_BIG_TEXT),
                subText = extractTextCandidate(
                    extras,
                    Notification.EXTRA_SUB_TEXT,
                    Notification.EXTRA_SUMMARY_TEXT,
                ),
                shortCriticalText = extractTextCandidate(extras, "android.shortCriticalText"),
            )
        }

        fun identityKeyFor(sbn: StatusBarNotification): String? {
            return fromStatusBarNotification(sbn, requirePlainExtras = false)?.stableKey()
        }

        private fun extractTextCandidate(extras: Bundle, vararg keys: String): String? {
            keys.forEach { key ->
                val text = extras.getCharSequence(key)?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) return text
            }
            return null
        }
    }
}
