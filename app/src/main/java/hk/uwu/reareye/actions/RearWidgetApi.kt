@file:Suppress("UNCHECKED_CAST")

package hk.uwu.reareye.actions

import android.os.Bundle
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * App 与 Hook 共用的业务 API。
 *
 * - App 侧：组装 dataChannel 命令 payload
 * - Hook 侧：接收命令后更新内存路由、通知池，并提供渲染所需 Bundle
 */
object RearWidgetApi {

    object Channel {
        const val KEY_REGISTER_BUSINESS_FILE = "rear_register_business_file"
        const val KEY_UNREGISTER_BUSINESS_FILE = "rear_unregister_business_file"
        const val KEY_REGISTER_BUSINESS = "rear_register_business"
        const val KEY_UNREGISTER_BUSINESS = "rear_unregister_business"
        const val KEY_DISABLE_BUSINESS_DISPLAY = "rear_disable_business_display"
        const val KEY_POST_NOTICE = "rear_post_notice"
        const val KEY_UPDATE_NOTICE = "rear_update_notice"
        const val KEY_REMOVE_NOTICE = "rear_remove_notice"
        const val KEY_SYNC_STATE = "rear_sync_state"
        const val KEY_ACK = "rear_ack"

        const val OP_REGISTER_FILE = "register_file"
        const val OP_UNREGISTER_FILE = "unregister_file"
        const val OP_REGISTER = "register"
        const val OP_UNREGISTER = "unregister"
        const val OP_DISABLE_DISPLAY = "disable_display"
        const val OP_POST = "post"
        const val OP_UPDATE = "update"
        const val OP_REMOVE = "remove"
        const val OP_SYNC = "sync"
    }

    fun opFromChannelKey(key: String): String? = when (key) {
        Channel.KEY_REGISTER_BUSINESS_FILE -> Channel.OP_REGISTER_FILE
        Channel.KEY_UNREGISTER_BUSINESS_FILE -> Channel.OP_UNREGISTER_FILE
        Channel.KEY_REGISTER_BUSINESS -> Channel.OP_REGISTER
        Channel.KEY_UNREGISTER_BUSINESS -> Channel.OP_UNREGISTER
        Channel.KEY_DISABLE_BUSINESS_DISPLAY -> Channel.OP_DISABLE_DISPLAY
        Channel.KEY_POST_NOTICE -> Channel.OP_POST
        Channel.KEY_UPDATE_NOTICE -> Channel.OP_UPDATE
        Channel.KEY_REMOVE_NOTICE -> Channel.OP_REMOVE
        Channel.KEY_SYNC_STATE -> Channel.OP_SYNC
        else -> null
    }

    data class BusinessSpec(
        val packageName: String,
        val business: String,
        val filePath: String,
        val defaultIndex: Int = 0,
        val defaultPriority: Int = 500,
    )

    data class NoticeOptions(
        val sticky: Boolean = false,
        val disablePopup: Boolean = true,
        val forcePopup: Boolean = false,
        val enableFloat: Boolean = false,
        val showTimeTip: Boolean = true,
        val index: Int? = null,
        val priority: Int? = null,
    )

    data class NoticeTicket(
        val packageName: String,
        val business: String,
        val notificationId: Int,
        val compositeKey: String,
    )

    data class ActiveNotice(
        val ticket: NoticeTicket,
        val payload: Bundle,
        val options: NoticeOptions,
        val createdAt: Long = System.currentTimeMillis(),
    )

    @Volatile
    var defaultPackageName: String = "com.xiaomi.subscreencenter"

    internal val mapsDirty = AtomicBoolean(false)

    // 全局 business -> filePath 映射，可先注册文件再注册业务。
    private val businessFiles = ConcurrentHashMap<String, String>()

    private val routes = ConcurrentHashMap<String, MutableMap<String, BusinessSpec>>()
    private val notices = ConcurrentHashMap<String, ActiveNotice>()
    private val cardNoticeIdIndex = ConcurrentHashMap<String, Int>()
    private val cardNoticeCompositeIndex = ConcurrentHashMap<String, String>()
    private val idSeed = AtomicInteger(310000)

    fun install(defaultPkg: String) {
        defaultPackageName = defaultPkg
        routes.computeIfAbsent(defaultPkg) { linkedMapOf() }
        mapsDirty.set(true)
    }

    /**
     * 注册或覆盖 business 对应的模板文件路径。
     *
     * 流程建议：先调用本方法，再按需调用 registerBusinessWithoutFile。
     */
    fun registerBusinessFile(business: String, filePath: String) {
        businessFiles[business] = filePath
        // 若该 business 已绑定到某些包，顺带更新其 filePath。
        routes.forEach { (_, bizMap) ->
            val old = bizMap[business]
            if (old != null) bizMap[business] = old.copy(filePath = filePath)
        }
        mapsDirty.set(true)
    }

    fun getBusinessFile(business: String): String? =
        businessFiles[business] ?: routes.values.asSequence().mapNotNull { it[business]?.filePath }
            .firstOrNull()

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
        val spec = BusinessSpec(packageName, business, filePath, defaultIndex, defaultPriority)
        routes.computeIfAbsent(packageName) { linkedMapOf() }[business] = spec
        mapsDirty.set(true)
    }

    /**
     * 不传 filePath 的业务注册：将使用已登记的 business 文件路径。
     * 返回 false 表示未找到对应文件路径。
     */
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

    fun isBusinessRegistered(packageName: String = defaultPackageName, business: String): Boolean {
        return routes[packageName]?.containsKey(business) == true
    }

    fun unregisterBusiness(packageName: String = defaultPackageName, business: String) {
        routes[packageName]?.remove(business)
        mapsDirty.set(true)
    }

    fun listBusinesses(packageName: String = defaultPackageName): List<BusinessSpec> {
        return routes[packageName]?.values?.toList().orEmpty()
    }

    fun postNotice(
        business: String,
        payload: Bundle = Bundle(),
        options: NoticeOptions = NoticeOptions(),
        packageName: String = defaultPackageName,
    ): NoticeTicket {
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
            val ticket = NoticeTicket(packageName, business, id, key)
            notices[key] = ActiveNotice(ticket, Bundle(payload), merged)
            cardNoticeCompositeIndex[cardKey] = key
            return ticket
        }

        val id = idSeed.incrementAndGet()
        val key = "$packageName:$business:$id"
        val ticket = NoticeTicket(packageName, business, id, key)
        notices[key] = ActiveNotice(ticket, Bundle(payload), merged)
        return ticket
    }

    /** 启用并显示指定 business（语义化别名）。 */
    fun enableBusinessDisplay(
        business: String,
        payload: Bundle = Bundle(),
        options: NoticeOptions = NoticeOptions(),
        packageName: String = defaultPackageName,
    ): NoticeTicket = postNotice(business, payload, options, packageName)

    fun updateNotice(
        ticket: NoticeTicket,
        payload: Bundle? = null,
        options: NoticeOptions? = null
    ) {
        val old = notices[ticket.compositeKey] ?: return
        notices[ticket.compositeKey] = old.copy(
            payload = payload ?: old.payload,
            options = options ?: old.options,
        )
    }

    fun removeNotice(ticket: NoticeTicket) {
        val removed = notices.remove(ticket.compositeKey) ?: return
        val cardId = removed.payload.getString("__rear_card_id__")?.trim().orEmpty()
        if (cardId.isNotBlank()) {
            val cardKey = cardNoticeKey(ticket.packageName, ticket.business, cardId)
            cardNoticeCompositeIndex.remove(cardKey)
        }
    }

    /** 解除显示指定 ticket（语义化别名）。 */
    fun disableBusinessDisplay(ticket: NoticeTicket) {
        removeNotice(ticket)
    }

    /** 解除显示某包下某 business 的全部实例。 */
    fun disableBusinessDisplay(packageName: String = defaultPackageName, business: String): Int {
        val targets = notices.values
            .filter { it.ticket.packageName == packageName && it.ticket.business == business }
            .map { it.ticket }
        targets.forEach { removeNotice(it) }
        return targets.size
    }

    fun setSticky(ticket: NoticeTicket, sticky: Boolean) {
        val old = notices[ticket.compositeKey] ?: return
        notices[ticket.compositeKey] = old.copy(options = old.options.copy(sticky = sticky))
    }

    fun listNotices(): List<ActiveNotice> = notices.values.sortedByDescending { it.createdAt }

    fun getNotice(compositeKey: String): ActiveNotice? = notices[compositeKey]

    /** Hook 侧调用：将业务通知转为 SmartAssistant 可消费的 extras。 */
    fun buildDecoratedExtras(ticket: NoticeTicket): Bundle {
        val notice = notices[ticket.compositeKey]
            ?: error("Notice not found: ${ticket.compositeKey}")
        return buildDecoratedExtras(notice)
    }

    private fun buildDecoratedExtras(notice: ActiveNotice): Bundle {
        val ticket = notice.ticket
        val options = notice.options
        val out = Bundle(notice.payload)

        out.putString("package_name", ticket.packageName)
        out.putString("creator_package", ticket.packageName)
        out.putString("business", ticket.business)
        out.putInt("index", options.index ?: 0)
        out.putInt("priority", options.priority ?: 500)
        out.putInt("notification_id", ticket.notificationId)
        out.putInt("widget_id", ticket.notificationId)
        out.putString("composite_key", ticket.compositeKey)
        out.putLong("timestamp", System.currentTimeMillis())

        out.putBoolean("disable_popup", options.disablePopup)
        out.putBoolean("force_popup", options.forcePopup)
        out.putBoolean("enableFloat", options.enableFloat)
        out.putBoolean("show_time_tip", options.showTimeTip)
        out.putBoolean("__x_sticky__", options.sticky)

        out.putString("miui.rear.param", buildRearParamJson(ticket.business, options))
        out.putString("miui.focus.param", buildFocusParamJson(ticket.business, options))
        out.putString("__xposed_origin__", ticket.packageName)
        return out
    }

    /** dataChannel 收到命令后统一处理入口。 */
    fun handleChannelCommand(op: String, payloadJson: String): String {
        return runCatching {
            val obj = JSONObject(payloadJson)
            when (op) {
                Channel.OP_REGISTER_FILE -> {
                    registerBusinessFile(
                        business = obj.getString("business"),
                        filePath = obj.getString("filePath"),
                    )
                    ack(true, op, "ok")
                }

                Channel.OP_UNREGISTER_FILE -> {
                    unregisterBusinessFile(obj.getString("business"))
                    ack(true, op, "ok")
                }

                Channel.OP_REGISTER -> {
                    val pkg = obj.optString("packageName", defaultPackageName)
                    val biz = obj.getString("business")
                    val index = obj.optInt("defaultIndex", 0)
                    val priority = obj.optInt("defaultPriority", 500)
                    if (obj.has("filePath")) {
                        registerBusiness(
                            packageName = pkg,
                            business = biz,
                            filePath = obj.getString("filePath"),
                            defaultIndex = index,
                            defaultPriority = priority,
                        )
                    } else {
                        val ok = registerBusinessWithoutFile(
                            packageName = pkg,
                            business = biz,
                            defaultIndex = index,
                            defaultPriority = priority,
                        )
                        if (!ok) return@runCatching ack(
                            false,
                            op,
                            "filePath not found for business: $biz"
                        )
                    }
                    ack(true, op, "ok")
                }

                Channel.OP_UNREGISTER -> {
                    val pkg = obj.optString("packageName", defaultPackageName)
                    unregisterBusiness(pkg, obj.getString("business"))
                    ack(true, op, "ok")
                }

                Channel.OP_DISABLE_DISPLAY -> {
                    val pkg = obj.optString("packageName", defaultPackageName)
                    val count = disableBusinessDisplay(pkg, obj.getString("business"))
                    ack(true, op, "ok", JSONObject().put("removed", count))
                }

                Channel.OP_POST -> {
                    val pkg = obj.optString("packageName", defaultPackageName)
                    val ticket = postNotice(
                        packageName = pkg,
                        business = obj.getString("business"),
                        payload = jsonToBundle(obj.optJSONObject("payload")),
                        options = jsonToOptions(obj.optJSONObject("options")),
                    )
                    ack(
                        true,
                        op,
                        "ok",
                        JSONObject()
                            .put("packageName", ticket.packageName)
                            .put("business", ticket.business)
                            .put("notificationId", ticket.notificationId)
                            .put("compositeKey", ticket.compositeKey)
                    )
                }

                Channel.OP_UPDATE -> {
                    val ticket = jsonToTicket(obj.getJSONObject("ticket"))
                    val payload =
                        if (obj.has("payload")) jsonToBundle(obj.optJSONObject("payload")) else null
                    val options =
                        if (obj.has("options")) jsonToOptions(obj.optJSONObject("options")) else null
                    updateNotice(ticket, payload, options)
                    ack(true, op, "ok")
                }

                Channel.OP_REMOVE -> {
                    removeNotice(jsonToTicket(obj.getJSONObject("ticket")))
                    ack(true, op, "ok")
                }

                Channel.OP_SYNC -> ack(true, op, "ok")
                else -> ack(false, op, "unknown op: $op")
            }
        }.getOrElse { e ->
            ack(false, op, e.message ?: "unknown error")
        }
    }

    // -------- App 侧 payload 构造工具 --------

    fun buildRegisterBusinessFilePayload(
        business: String,
        filePath: String,
    ): String {
        return JSONObject()
            .put("business", business)
            .put("filePath", filePath)
            .toString()
    }

    fun buildRegisterBusinessPayload(
        business: String,
        filePath: String,
        packageName: String = defaultPackageName,
        defaultIndex: Int = 0,
        defaultPriority: Int = 500,
    ): String {
        return JSONObject()
            .put("packageName", packageName)
            .put("business", business)
            .put("filePath", filePath)
            .put("defaultIndex", defaultIndex)
            .put("defaultPriority", defaultPriority)
            .toString()
    }

    fun buildRegisterBusinessPayloadWithoutFile(
        business: String,
        packageName: String = defaultPackageName,
        defaultIndex: Int = 0,
        defaultPriority: Int = 500,
    ): String {
        return JSONObject()
            .put("packageName", packageName)
            .put("business", business)
            .put("defaultIndex", defaultIndex)
            .put("defaultPriority", defaultPriority)
            .toString()
    }

    fun buildUnregisterBusinessPayload(
        business: String,
        packageName: String = defaultPackageName,
    ): String {
        return JSONObject()
            .put("packageName", packageName)
            .put("business", business)
            .toString()
    }

    fun buildUnregisterBusinessFilePayload(business: String): String {
        return JSONObject()
            .put("business", business)
            .toString()
    }

    fun buildDisableBusinessDisplayPayload(
        business: String,
        packageName: String = defaultPackageName,
    ): String {
        return JSONObject()
            .put("packageName", packageName)
            .put("business", business)
            .toString()
    }

    fun buildPostNoticePayload(
        business: String,
        packageName: String = defaultPackageName,
        payload: Bundle = Bundle(),
        options: NoticeOptions = NoticeOptions(),
    ): String {
        return JSONObject()
            .put("packageName", packageName)
            .put("business", business)
            .put("payload", bundleToJson(payload))
            .put("options", optionsToJson(options))
            .toString()
    }

    fun buildUpdateNoticePayload(
        ticket: NoticeTicket,
        payload: Bundle? = null,
        options: NoticeOptions? = null,
    ): String {
        val root = JSONObject().put("ticket", ticketToJson(ticket))
        if (payload != null) root.put("payload", bundleToJson(payload))
        if (options != null) root.put("options", optionsToJson(options))
        return root.toString()
    }

    fun buildRemoveNoticePayload(ticket: NoticeTicket): String {
        return JSONObject().put("ticket", ticketToJson(ticket)).toString()
    }

    fun buildSyncPayload(): String = JSONObject().toString()

    fun parseAck(ackJson: String): JSONObject = JSONObject(ackJson)

    // -------- Hook 侧路由辅助 --------

    internal fun allPkgBusinesses(): Map<String, Set<String>> =
        routes.mapValues { it.value.keys.toSet() }

    internal fun allPackages(): Set<String> = routes.keys

    internal fun primaryBusinessByPkg(): Map<String, String> =
        routes.mapValues { (_, bizMap) -> bizMap.keys.firstOrNull().orEmpty() }

    internal fun allBusinessPath(): Map<String, String> {
        val out = HashMap<String, String>()
        out.putAll(businessFiles)
        routes.values.flatMap { it.values }.forEach { spec -> out[spec.business] = spec.filePath }
        return out
    }

    internal fun fallbackBusiness(packageName: String): String? {
        val latest = notices.values.asSequence()
            .filter { it.ticket.packageName == packageName }
            .sortedByDescending { it.createdAt }
            .firstOrNull()
        return latest?.ticket?.business ?: routes[packageName]?.keys?.firstOrNull()
    }

    // -------- JSON/Bundle 转换 --------

    private fun jsonToTicket(obj: JSONObject): NoticeTicket = NoticeTicket(
        packageName = obj.getString("packageName"),
        business = obj.getString("business"),
        notificationId = obj.getInt("notificationId"),
        compositeKey = obj.getString("compositeKey"),
    )

    private fun jsonToOptions(obj: JSONObject?): NoticeOptions {
        if (obj == null) return NoticeOptions()
        return NoticeOptions(
            sticky = obj.optBoolean("sticky", false),
            disablePopup = obj.optBoolean("disablePopup", true),
            forcePopup = obj.optBoolean("forcePopup", false),
            enableFloat = obj.optBoolean("enableFloat", false),
            showTimeTip = obj.optBoolean("showTimeTip", true),
            index = if (obj.has("index")) obj.optInt("index") else null,
            priority = if (obj.has("priority")) obj.optInt("priority") else null,
        )
    }

    private fun jsonToBundle(obj: JSONObject?): Bundle {
        val out = Bundle()
        if (obj == null) return out
        val it = obj.keys()
        while (it.hasNext()) {
            val key = it.next()
            when (val value = obj.opt(key)) {
                is String -> out.putString(key, value)
                is Int -> out.putInt(key, value)
                is Long -> out.putLong(key, value)
                is Boolean -> out.putBoolean(key, value)
                is Double -> out.putDouble(key, value)
                is JSONObject -> out.putString(key, value.toString())
                null -> Unit
                else -> out.putString(key, value.toString())
            }
        }
        return out
    }

    private fun bundleToJson(bundle: Bundle): JSONObject {
        val out = JSONObject()
        for (k in bundle.keySet()) {
            when (val v = bundle.get(k)) {
                is String, is Int, is Long, is Boolean, is Double -> out.put(k, v)
                is Bundle -> out.put(k, bundleToJson(v))
                null -> out.put(k, JSONObject.NULL)
                else -> out.put(k, v.toString())
            }
        }
        return out
    }

    private fun ticketToJson(ticket: NoticeTicket): JSONObject = JSONObject()
        .put("packageName", ticket.packageName)
        .put("business", ticket.business)
        .put("notificationId", ticket.notificationId)
        .put("compositeKey", ticket.compositeKey)

    private fun optionsToJson(options: NoticeOptions): JSONObject = JSONObject()
        .put("sticky", options.sticky)
        .put("disablePopup", options.disablePopup)
        .put("forcePopup", options.forcePopup)
        .put("enableFloat", options.enableFloat)
        .put("showTimeTip", options.showTimeTip)
        .put("index", options.index)
        .put("priority", options.priority)

    private fun buildRearParamJson(business: String, options: NoticeOptions): String {
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

    private fun buildFocusParamJson(business: String, options: NoticeOptions): String {
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

    private fun ack(ok: Boolean, op: String, message: String, data: JSONObject? = null): String {
        val root = JSONObject()
            .put("ok", ok)
            .put("op", op)
            .put("message", message)
        if (data != null) root.put("data", data)
        return root.toString()
    }

    private fun cardNoticeKey(packageName: String, business: String, cardId: String): String {
        return "$packageName:$business:$cardId"
    }
}
