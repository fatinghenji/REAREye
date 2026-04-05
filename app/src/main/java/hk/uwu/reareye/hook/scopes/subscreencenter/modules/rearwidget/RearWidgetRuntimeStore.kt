package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.os.Bundle
import hk.uwu.reareye.widgetapi.RearWidgetActiveNotice
import hk.uwu.reareye.widgetapi.RearWidgetBusinessSpec
import hk.uwu.reareye.widgetapi.RearWidgetNoticeOptions
import hk.uwu.reareye.widgetapi.RearWidgetNoticeTicket
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object RearWidgetRuntimeStore {

    @Volatile
    var defaultPackageName: String = "com.xiaomi.subscreencenter"

    val mapsDirty = AtomicBoolean(false)

    private val businessFiles = ConcurrentHashMap<String, String>()
    private val routes = ConcurrentHashMap<String, MutableMap<String, RearWidgetBusinessSpec>>()
    private val notices = ConcurrentHashMap<String, RearWidgetActiveNotice>()
    private val cardNoticeIdIndex = ConcurrentHashMap<String, Int>()
    private val cardNoticeCompositeIndex = ConcurrentHashMap<String, String>()
    private val idSeed = AtomicInteger(310000)

    fun install(defaultPkg: String) {
        defaultPackageName = defaultPkg
        routes.computeIfAbsent(defaultPkg) { linkedMapOf() }
        mapsDirty.set(true)
    }

    fun registerBusinessFile(business: String, filePath: String) {
        businessFiles[business] = filePath
        routes.forEach { (_, bizMap) ->
            val old = bizMap[business]
            if (old != null) bizMap[business] = old.copy(filePath = filePath)
        }
        mapsDirty.set(true)
    }

    fun getBusinessFile(business: String): String? {
        return businessFiles[business]
            ?: routes.values.firstNotNullOfOrNull { it[business]?.filePath }
    }

    fun unregisterBusinessFile(business: String) {
        businessFiles.remove(business)
        routes.forEach { (_, bizMap) -> bizMap.remove(business) }
        mapsDirty.set(true)
    }

    fun registerBusiness(
        packageName: String = defaultPackageName,
        business: String,
        filePath: String,
        defaultIndex: Int = 0,
        defaultPriority: Int = 500,
    ) {
        businessFiles[business] = filePath
        val spec =
            RearWidgetBusinessSpec(packageName, business, filePath, defaultIndex, defaultPriority)
        routes.computeIfAbsent(packageName) { linkedMapOf() }[business] = spec
        mapsDirty.set(true)
    }

    fun registerBusinessWithoutFile(
        packageName: String = defaultPackageName,
        business: String,
        defaultIndex: Int = 0,
        defaultPriority: Int = 500,
    ): Boolean {
        val filePath = getBusinessFile(business) ?: return false
        registerBusiness(packageName, business, filePath, defaultIndex, defaultPriority)
        return true
    }

    fun unregisterBusiness(packageName: String = defaultPackageName, business: String) {
        routes[packageName]?.remove(business)
        mapsDirty.set(true)
    }

    fun postNotice(
        business: String,
        payload: Bundle = Bundle(),
        options: RearWidgetNoticeOptions = RearWidgetNoticeOptions(),
        packageName: String = defaultPackageName,
    ): RearWidgetNoticeTicket {
        val spec = routes[packageName]?.get(business)
            ?: error("Business not registered: $packageName/$business")
        val merged = options.copy(
            index = options.index ?: spec.defaultIndex,
            priority = options.priority ?: spec.defaultPriority,
        )

        val cardId = payload.getString("__rear_card_id__")?.trim().orEmpty()
        if (cardId.isNotBlank()) {
            val cardKey = cardNoticeKey(packageName, business, cardId)
            val existingComposite = cardNoticeCompositeIndex[cardKey]
            if (!existingComposite.isNullOrBlank()) {
                val existing = notices[existingComposite]
                if (existing != null) {
                    notices[existingComposite] = existing.copy(
                        payload = Bundle(payload),
                        options = merged,
                    )
                    return existing.ticket
                }
                cardNoticeCompositeIndex.remove(cardKey)
            }

            val id = cardNoticeIdIndex.getOrPut(cardKey) { idSeed.incrementAndGet() }
            val key = "$packageName:$business:$id"
            val ticket = RearWidgetNoticeTicket(packageName, business, id, key)
            notices[key] = RearWidgetActiveNotice(ticket, Bundle(payload), merged)
            cardNoticeCompositeIndex[cardKey] = key
            return ticket
        }

        val id = idSeed.incrementAndGet()
        val key = "$packageName:$business:$id"
        val ticket = RearWidgetNoticeTicket(packageName, business, id, key)
        notices[key] = RearWidgetActiveNotice(ticket, Bundle(payload), merged)
        return ticket
    }

    fun updateNotice(
        ticket: RearWidgetNoticeTicket,
        payload: Bundle? = null,
        options: RearWidgetNoticeOptions? = null,
    ) {
        val old = notices[ticket.compositeKey] ?: return
        notices[ticket.compositeKey] = old.copy(
            payload = payload ?: old.payload,
            options = options ?: old.options,
        )
    }

    fun removeNotice(ticket: RearWidgetNoticeTicket) {
        val removed = notices.remove(ticket.compositeKey) ?: return
        val cardId = removed.payload.getString("__rear_card_id__")?.trim().orEmpty()
        if (cardId.isNotBlank()) {
            cardNoticeCompositeIndex.remove(
                cardNoticeKey(
                    ticket.packageName,
                    ticket.business,
                    cardId
                )
            )
        }
    }

    fun disableBusinessDisplay(packageName: String = defaultPackageName, business: String): Int {
        val targets = notices.values
            .filter { it.ticket.packageName == packageName && it.ticket.business == business }
            .map { it.ticket }
        targets.forEach(::removeNotice)
        return targets.size
    }

    fun listNotices(): List<RearWidgetActiveNotice> {
        return notices.values.sortedByDescending { it.createdAt }
    }

    fun getNotice(compositeKey: String): RearWidgetActiveNotice? = notices[compositeKey]

    fun buildDecoratedExtras(ticket: RearWidgetNoticeTicket): Bundle {
        val notice = notices[ticket.compositeKey]
            ?: error("Notice not found: ${ticket.compositeKey}")
        val options = notice.options
        return Bundle(notice.payload).apply {
            putString("package_name", ticket.packageName)
            putString("creator_package", ticket.packageName)
            putString("business", ticket.business)
            putInt("index", options.index ?: 0)
            putInt("priority", options.priority ?: 500)
            putInt("notification_id", ticket.notificationId)
            putInt("widget_id", ticket.notificationId)
            putString("composite_key", ticket.compositeKey)
            putLong("timestamp", System.currentTimeMillis())

            putBoolean("disable_popup", options.disablePopup)
            putBoolean("force_popup", options.forcePopup)
            putBoolean("enableFloat", options.enableFloat)
            putBoolean("show_time_tip", options.showTimeTip)
            putBoolean("__x_sticky__", options.sticky)

            if (getString("miui.rear.param").isNullOrBlank()) {
                putString("miui.rear.param", buildRearParamJson(ticket.business, options))
            }
            if (getString("miui.focus.param").isNullOrBlank()) {
                putString("miui.focus.param", buildFocusParamJson(ticket.business, options))
            }
            putString("__xposed_origin__", ticket.packageName)
        }
    }

    fun allPkgBusinesses(): Map<String, Set<String>> = routes.mapValues { it.value.keys.toSet() }

    fun primaryBusinessByPkg(): Map<String, String> =
        routes.mapValues { (_, bizMap) -> bizMap.keys.firstOrNull().orEmpty() }

    fun allBusinessPath(): Map<String, String> {
        val out = HashMap<String, String>()
        out.putAll(businessFiles)
        routes.values.flatMap { it.values }.forEach { spec -> out[spec.business] = spec.filePath }
        return out
    }

    fun fallbackBusiness(packageName: String): String? {
        val latest = notices.values.asSequence()
            .filter { it.ticket.packageName == packageName }
            .maxByOrNull { it.createdAt }
        return latest?.ticket?.business ?: routes[packageName]?.keys?.firstOrNull()
    }

    private fun buildRearParamJson(business: String, options: RearWidgetNoticeOptions): String {
        val v1 = JSONObject()
            .put("business", business)
            .put("index", options.index ?: 0)
            .put("priority", options.priority ?: 500)
            .put("disable_popup", options.disablePopup)
            .put("show_time_tip", options.showTimeTip)
            .put("swipe_out_screen_listener", false)
            .put("enableFloat", options.enableFloat)
        return JSONObject().put("rear_param_v1", v1).toString()
    }

    private fun buildFocusParamJson(business: String, options: RearWidgetNoticeOptions): String {
        return JSONObject()
            .put("business", business)
            .put("index", options.index ?: 0)
            .put("priority", options.priority ?: 500)
            .put("disable_popup", options.disablePopup)
            .put("show_time_tip", options.showTimeTip)
            .put("swipe_out_screen_listener", false)
            .put("enableFloat", options.enableFloat)
            .toString()
    }

    private fun cardNoticeKey(packageName: String, business: String, cardId: String): String {
        return "$packageName:$business:$cardId"
    }
}
