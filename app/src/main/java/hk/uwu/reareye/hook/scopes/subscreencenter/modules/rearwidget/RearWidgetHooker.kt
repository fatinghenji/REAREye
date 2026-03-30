@file:Suppress("UNCHECKED_CAST")

package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.util.Base64
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import hk.uwu.reareye.actions.RearWidgetApi
import hk.uwu.reareye.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.ui.config.ConfigKeys
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hook 侧实现：
 * - 通过 RearWidgetApi 的 channel 命令维护业务路由/通知
 * - 将运行时映射注入 p2.a / p2.c
 * - post/update 后立即触发注入，减少链路时序依赖
 */
class RearWidgetHooker : YukiBaseHooker() {

    companion object {
        private const val TAG = "REAREye-RearWidget"
        private const val TEMPLATE_BASE =
            "/data/system/theme_magic/users/%s/subscreencenter/smart_assistant"
    }

    private val appliedOnce = AtomicBoolean(false)
    private val startupBootstrapped = AtomicBoolean(false)
    private val channelBridgeRegistered = AtomicBoolean(false)
    private val deployedBlobMetaCache = ConcurrentHashMap<String, String>()
    private val injectedCardSignatureCache = ConcurrentHashMap<String, String>()
    private val injectedCompositeAt = ConcurrentHashMap<String, Long>()
    private val bootstrapRetryCount = AtomicInteger(0)
    private val managerEpoch = AtomicInteger(0)

    private var manager: Any? = null
    private var mainHandler: Handler? = null

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            debugLog("hook process=$processName")
            RearWidgetApi.install(packageName)
            registerChannelBridge()
            debugLog("onHook start")

            val appRef = "com.xiaomi.subscreencenter.SubScreenCenterApp".toClass().resolve()
            val d0Ref = "Z1.d0".toClass().resolve()
            val p2cRef = "p2.c".toClass().resolve()

            appRef.firstMethod {
                name = "attachBaseContext"
                parameterCount = 1
            }.hook().after {
                applyRuntimeMaps(force = true)
                debugLog("attachBaseContext applied runtime maps (defer bootstrap to manager init)")
            }

            d0Ref.firstMethod {
                name = "l"
                parameterCount = 1
            }.hook().after {
                val oldManager = manager
                manager = instance
                mainHandler = runCatching {
                    d0Ref.firstField { name = "E" }.get() as? Handler
                }.getOrNull()
                val managerChanged = oldManager !== manager
                if (managerChanged) {
                    managerEpoch.incrementAndGet()
                    injectedCardSignatureCache.clear()
                    injectedCompositeAt.clear()
                }

                if (!managerChanged && startupBootstrapped.get()) {
                    applyRuntimeMaps(force = true)
                    patchManagerAppGates(manager)
                    debugLog("captured manager unchanged, skip bootstrap/reinject")
                    return@after
                }

                val bootOk = bootstrapFromPrefsOnInit(force = false)
                if (!bootOk) scheduleBootstrapRetry()
                applyRuntimeMaps(force = true)
                patchManagerAppGates(manager)
                scheduleInjectAllActiveNotices()
                debugLog("captured manager=${manager != null}, handler=${mainHandler != null}")
            }

            d0Ref.firstMethod {
                name = "o"
                parameterCount = 1
            }.hook().after {
                patchManagerAppGates(instance)
            }

            p2cRef.firstMethod {
                name = "r"
                parameterCount = 2
            }.hook().after {
                val pkg = args[0] as? String ?: return@after
                if (result != null) return@after
                val biz = RearWidgetApi.fallbackBusiness(pkg) ?: return@after
                result = createU0b(biz, 0, 600)
                debugLog("p2.c.r fallback pkg=$pkg -> business=$biz")
            }

            p2cRef.firstMethod {
                name = "i"
                parameterCount = 2
            }.hook().after {
                val pkg = args[0] as? String ?: return@after
                val biz = args[1] as? String ?: return@after
                // business 文件映射是全局覆盖语义：只要注册了该 business 文件，就覆盖系统内置路径。
                val path = RearWidgetApi.getBusinessFile(biz) ?: return@after
                result = path
                debugLog("p2.c.i override path pkg=$pkg biz=$biz path=$path")
            }

            p2cRef.firstMethod {
                name = "k"
                parameterCount = 3
            }.hook().before {
                val pkg = args[0] as? String ?: return@before
                if (RearWidgetApi.allPkgBusinesses().containsKey(pkg)) {
                    result = true
                    debugLog("p2.c.k force pass pkg=$pkg")
                }
            }

            p2cRef.firstMethod {
                name = "s"
                parameterCount = 10
            }.hook().after {
                applyRuntimeMaps(force = false)
                val out = result as? Bundle ?: return@after
                val key = out.getString("composite_key") ?: (args.getOrNull(1) as? String)
                val notice = key?.let { RearWidgetApi.getNotice(it) } ?: return@after
                out.putAll(RearWidgetApi.buildDecoratedExtras(notice.ticket))
            }
        }
    }

    fun onChannelMessage(channelKey: String, payload: String): String {
        val op = RearWidgetApi.opFromChannelKey(channelKey)
            ?: return JSONObject().put("ok", false)
                .put("message", "unknown channel key: $channelKey").toString()

        clearInjectCacheByOperation(op, payload)

        if (op == RearWidgetApi.Channel.OP_SYNC) {
            bootstrapFromPrefsOnInit(force = true)
        }

        val ack = executeApiOperation(op, payload, applyMaps = true, injectNow = true)
        debugLog("channel key=$channelKey op=$op ack=$ack")
        return ack
    }

    private fun executeApiOperation(
        op: String,
        payload: String,
        applyMaps: Boolean,
        injectNow: Boolean,
    ): String {
        val normalizedPayload = runCatching {
            normalizePayloadForHook(op, payload)
        }.getOrElse {
            return JSONObject()
                .put("ok", false)
                .put("op", op)
                .put("message", it.message ?: "normalize payload failed")
                .toString()
        }
        val ack = RearWidgetApi.handleChannelCommand(op, normalizedPayload)
        if (applyMaps) {
            applyRuntimeMaps(force = true)
            patchManagerAppGates(manager)
        }
        if (!injectNow) return ack

        when (op) {
            RearWidgetApi.Channel.OP_POST -> {
                val key = runCatching {
                    JSONObject(ack).optJSONObject("data")?.optString("compositeKey")
                }.getOrNull()
                if (!key.isNullOrBlank()) injectByCompositeKey(key)
            }

            RearWidgetApi.Channel.OP_UPDATE -> {
                val key = runCatching {
                    JSONObject(normalizedPayload).optJSONObject("ticket")?.optString("compositeKey")
                }.getOrNull()
                if (!key.isNullOrBlank()) injectByCompositeKey(key)
            }
        }
        return ack
    }

    private fun bootstrapFromPrefsOnInit(force: Boolean = false): Boolean {
        if (!force && startupBootstrapped.get()) return true

        val businessRaw = prefs.getString(
            ConfigKeys.REAR_WIDGET_BUSINESS_DATA,
            RearWidgetConfigCodec.EMPTY_ARRAY,
        )
        val cardRaw = prefs.getString(
            ConfigKeys.REAR_WIDGET_CARD_DATA,
            RearWidgetConfigCodec.EMPTY_ARRAY,
        )
        val businesses = RearWidgetConfigCodec.parseBusinesses(businessRaw)
        val cards = RearWidgetConfigCodec.parseCards(cardRaw).filter { it.enabled }
        if (!force && businesses.isEmpty() && cards.isEmpty()) {
            debugLog("bootstrap init skipped: no config yet")
            return false
        }

        val businessPathMap = LinkedHashMap<String, String>()
        var allOk = true
        businesses.forEach { item ->
            businessPathMap[item.business] = item.filePath
            val ack = executeApiOperation(
                op = RearWidgetApi.Channel.OP_REGISTER_FILE,
                payload = RearWidgetApi.buildRegisterBusinessFilePayload(
                    item.business,
                    item.filePath
                ),
                applyMaps = false,
                injectNow = false,
            )
            if (!isAckOk(ack)) {
                allOk = false
                debugLog("bootstrap register_file failed business=${item.business} ack=$ack")
            }
        }

        val uniquePairs = LinkedHashSet<Pair<String, String>>()
        cards.forEach { uniquePairs += (it.packageName to it.business) }

        // 重放前先清掉目标业务的旧展示，保证重复 bootstrap 不会叠加出重复卡片。
        uniquePairs.forEach { (pkg, biz) ->
            val ack = executeApiOperation(
                op = RearWidgetApi.Channel.OP_DISABLE_DISPLAY,
                payload = RearWidgetApi.buildDisableBusinessDisplayPayload(
                    business = biz,
                    packageName = pkg,
                ),
                applyMaps = false,
                injectNow = false,
            )
            if (!isAckOk(ack)) {
                allOk = false
                debugLog("bootstrap disable_display failed pkg=$pkg biz=$biz ack=$ack")
            }
        }

        uniquePairs.forEach { (pkg, biz) ->
            val payload = businessPathMap[biz]?.let { path ->
                RearWidgetApi.buildRegisterBusinessPayload(
                    business = biz,
                    filePath = path,
                    packageName = pkg,
                    defaultIndex = 0,
                    defaultPriority = 500,
                )
            } ?: RearWidgetApi.buildRegisterBusinessPayloadWithoutFile(
                business = biz,
                packageName = pkg,
                defaultIndex = 0,
                defaultPriority = 500,
            )
            val ack = executeApiOperation(
                op = RearWidgetApi.Channel.OP_REGISTER,
                payload = payload,
                applyMaps = false,
                injectNow = false,
            )
            if (!isAckOk(ack)) {
                allOk = false
                debugLog("bootstrap register failed pkg=$pkg biz=$biz ack=$ack")
            }
        }

        cards.forEachIndexed { index, card ->
            val payload = Bundle().apply {
                putString("title", card.title.ifBlank { card.business })
                putString("business", card.business)
                putString("__rear_card_id__", card.id)
            }
            val options = RearWidgetApi.NoticeOptions(
                sticky = true,
                disablePopup = true,
                showTimeTip = true,
                index = index,
                priority = card.priority,
            )
            val ack = executeApiOperation(
                op = RearWidgetApi.Channel.OP_POST,
                payload = RearWidgetApi.buildPostNoticePayload(
                    business = card.business,
                    packageName = card.packageName,
                    payload = payload,
                    options = options,
                ),
                applyMaps = false,
                injectNow = false,
            )
            if (!isAckOk(ack)) {
                allOk = false
                debugLog("bootstrap post failed pkg=${card.packageName} biz=${card.business} cardId=${card.id} ack=$ack")
            }
        }

        applyRuntimeMaps(force = true)
        if (allOk) {
            startupBootstrapped.set(true)
            bootstrapRetryCount.set(0)
        }
        debugLog("bootstrap init replay businesses=${businesses.size} enabledCards=${cards.size} force=$force ok=$allOk")
        return allOk
    }

    private fun scheduleBootstrapRetry() {
        val handler = mainHandler ?: return
        val retry = bootstrapRetryCount.incrementAndGet()
        if (retry > 5) {
            debugLog("bootstrap retry stop: max reached")
            return
        }

        val delay = 1200L * retry
        handler.postDelayed({
            if (startupBootstrapped.get()) return@postDelayed
            val ok = bootstrapFromPrefsOnInit(force = false)
            if (!ok) scheduleBootstrapRetry()
        }, delay)
        debugLog("bootstrap retry scheduled count=$retry delay=${delay}ms")
    }

    private fun isAckOk(ack: String): Boolean {
        return runCatching { JSONObject(ack).optBoolean("ok", false) }.getOrDefault(false)
    }

    private fun injectAllActiveNotices() {
        RearWidgetApi.listNotices().forEach { notice ->
            injectByCompositeKey(notice.ticket.compositeKey, true)
        }
    }

    private fun scheduleInjectAllActiveNotices() {
        val handler = mainHandler ?: return
        val epoch = managerEpoch.get()
        handler.postDelayed({
            if (epoch != managerEpoch.get()) return@postDelayed
            injectAllActiveNotices()
        }, 1200L)
        handler.postDelayed({
            if (epoch != managerEpoch.get()) return@postDelayed
            injectAllActiveNotices()
        }, 2800L)
    }

    private fun normalizePayloadForHook(op: String, payload: String): String {
        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return payload

        if (op == RearWidgetApi.Channel.OP_REGISTER_FILE || op == RearWidgetApi.Channel.OP_REGISTER) {
            val business = obj.optString("business").trim()
            val filePath = obj.optString("filePath").trim()
            if (business.isNotBlank() && filePath.isNotBlank()) {
                val deployedPath = deployBusinessTemplate(business, filePath)
                    ?: error("deploy template failed for business=$business source=$filePath")
                obj.put("filePath", deployedPath)
            }
        }

        if (op == RearWidgetApi.Channel.OP_UNREGISTER_FILE) {
            val business = obj.optString("business").trim()
            if (business.isNotBlank()) {
                removeDeployedBusinessTemplate(business)
            }
        }
        return obj.toString()
    }

    private fun registerChannelBridge() {
        if (!channelBridgeRegistered.compareAndSet(false, true)) return
        val keys = listOf(
            RearWidgetApi.Channel.KEY_REGISTER_BUSINESS_FILE,
            RearWidgetApi.Channel.KEY_UNREGISTER_BUSINESS_FILE,
            RearWidgetApi.Channel.KEY_REGISTER_BUSINESS,
            RearWidgetApi.Channel.KEY_UNREGISTER_BUSINESS,
            RearWidgetApi.Channel.KEY_DISABLE_BUSINESS_DISPLAY,
            RearWidgetApi.Channel.KEY_POST_NOTICE,
            RearWidgetApi.Channel.KEY_UPDATE_NOTICE,
            RearWidgetApi.Channel.KEY_REMOVE_NOTICE,
            RearWidgetApi.Channel.KEY_SYNC_STATE,
        )
        keys.forEach { key ->
            dataChannel.wait<String>(key) { payload ->
                val ack = onChannelMessage(key, payload)
                dataChannel.put(RearWidgetApi.Channel.KEY_ACK, ack)
            }
        }
        debugLog("registered dataChannel bridge keys=${keys.joinToString()}")
    }

    private fun injectByCompositeKey(compositeKey: String, force: Boolean = false) {
        val notice = RearWidgetApi.getNotice(compositeKey) ?: return
        val mgr = manager ?: return
        val handler = mainHandler ?: return

        val now = System.currentTimeMillis()
        val lastAt = injectedCompositeAt[compositeKey] ?: 0L
        if (!force && now - lastAt < 1200L) {
            debugLog("skip duplicate inject by composite window key=$compositeKey")
            return
        }

        val cardId = notice.payload.getString("__rear_card_id__")?.trim().orEmpty()
        if (cardId.isNotBlank()) {
            val cardKey = "${notice.ticket.packageName}:${notice.ticket.business}:$cardId"
            val signature = buildInjectSignature(notice)
            val old = injectedCardSignatureCache[cardKey]
            if (!force && old == signature) {
                debugLog("skip duplicate inject by card signature card=$cardKey")
                injectedCompositeAt[compositeKey] = now
                return
            }
            injectedCardSignatureCache[cardKey] = signature
        }

        runCatching {
            val extras = RearWidgetApi.buildDecoratedExtras(notice.ticket)
            val runnable = "Z1.m".toClass().resolve().firstConstructor {
                parameterCount = 5
            }.create(
                mgr,
                notice.ticket.notificationId,
                notice.ticket.packageName,
                notice.ticket.compositeKey,
                extras,
            ) as? Runnable ?: return
            handler.post(runnable)
            injectedCompositeAt[compositeKey] = now
            debugLog("injected ticket key=${notice.ticket.compositeKey} business=${notice.ticket.business}")
        }.onFailure {
            debugLog("inject failed key=$compositeKey err=${it.message}")
        }
    }

    private fun applyRuntimeMaps(force: Boolean) {
        if (!force && !RearWidgetApi.mapsDirty.get()) return
        if (!appliedOnce.compareAndSet(
                false,
                true
            ) && !force && !RearWidgetApi.mapsDirty.get()
        ) return

        val pkgBiz = RearWidgetApi.allPkgBusinesses()
        val pkgPrimary = RearWidgetApi.primaryBusinessByPkg()
        val bizPath = RearWidgetApi.allBusinessPath()

        replaceStaticMap("p2.a", "a") { map ->
            pkgPrimary.forEach { (pkg, biz) -> if (biz.isNotBlank()) map[pkg] = biz }
        }
        replaceStaticMap("p2.a", "c") { map ->
            pkgBiz.forEach { (pkg, set) -> map[pkg] = HashSet(set) }
        }
        replaceStaticMap("p2.a", "d") { map ->
            bizPath.forEach { (biz, path) -> map[biz] = path }
        }
        replaceStaticMap("p2.c", "d") { map ->
            pkgBiz.keys.forEach { pkg -> map[pkg] = null }
        }
        replaceStaticList("p2.c", "b") { list ->
            bizPath.keys.forEach { biz -> if (!list.contains(biz)) list.add(biz) }
        }

        RearWidgetApi.mapsDirty.set(false)
        dumpRuntimeMaps(bizPath)
    }

    private fun patchManagerAppGates(target: Any?) {
        val instance = target ?: return
        val pkgBiz = RearWidgetApi.allPkgBusinesses()
        if (pkgBiz.isEmpty()) return

        runCatching {
            @Suppress("UNCHECKED_CAST")
            val rSet = instance.asResolver().firstField { name = "r" }
                .get() as ConcurrentHashMap.KeySetView<String, *>

            @Suppress("UNCHECKED_CAST")
            val qMap = instance.asResolver().firstField { name = "q" }
                .get() as ConcurrentHashMap<String, Boolean>

            pkgBiz.forEach { (pkg, businesses) ->
                rSet.add(pkg)
                qMap[pkg] = true
                businesses.forEach { biz ->
                    qMap["${pkg}_$biz"] = true
                }
            }
            debugLog("patched manager app gates packages=${pkgBiz.keys}")
        }
    }

    @Suppress("SameParameterValue")
    private fun createU0b(business: String, index: Int, priority: Int): Any {
        return "U0.b".toClass().resolve().firstConstructor {
            parameterCount = 3
        }.create(
            business,
            index,
            priority,
        )
    }

    private fun replaceStaticMap(
        className: String,
        fieldName: String,
        mutate: (MutableMap<Any, Any?>) -> Unit,
    ) {
        val field = className.toClass().resolve().firstField { name = fieldName }
        val raw = field.get<Any>() ?: error("$className.$fieldName is null")
        val current = unwrapMutableMap(raw)
        val out = HashMap<Any, Any?>(current.size + 8)
        current.forEach { (k, v) -> if (k != null) out[k] = v }
        mutate(out)
        field.set(out)
    }

    @Suppress("SameParameterValue")
    private fun replaceStaticList(
        className: String,
        fieldName: String,
        mutate: (MutableList<Any>) -> Unit,
    ) {
        val field = className.toClass().resolve().firstField { name = fieldName }
        val raw = field.get<Any>() ?: error("$className.$fieldName is null")
        val current = unwrapMutableList(raw)
        val out = ArrayList<Any>(current.size + 16)
        current.forEach { if (it != null) out.add(it) }
        mutate(out)
        field.set(out)
    }

    private fun unwrapMutableMap(any: Any): MutableMap<*, *> {
        if (any is MutableMap<*, *>) {
            return runCatching {
                any.asResolver().firstField { name = "m" }.get<MutableMap<*, *>>() ?: any
            }.recoverCatching {
                any.asResolver().firstField { name = "isReadOnly" }.set(false)
                any
            }.getOrElse { any }
        }
        error("Not a map: ${any.javaClass.name}")
    }

    private fun unwrapMutableList(any: Any): MutableList<*> {
        if (any is MutableList<*>) {
            return runCatching {
                any.asResolver().firstField { name = "list" }.get<MutableList<*>>() ?: any
            }.recoverCatching {
                any.asResolver().firstField { name = "c" }.get<MutableList<*>>() ?: any
            }.recoverCatching {
                any.asResolver().firstField { name = "isReadOnly" }.set(false)
                any
            }.getOrElse { any }
        }
        error("Not a list: ${any.javaClass.name}")
    }

    private fun debugLog(message: String) {
        XposedBridge.log("[$TAG] $message")
    }

    private fun dumpRuntimeMaps(
        bizPath: Map<String, String>,
    ) {
        runCatching {
            val aMap = readStaticMap("p2.a", "a")
            val cMap = readStaticMap("p2.a", "c")
            val dMap = readStaticMap("p2.a", "d")
            val cPersistMap = readStaticMap("p2.c", "d")
            val cWhitelist = readStaticList("p2.c", "b").toSet()

            val missingInWhitelist = bizPath.keys.sorted().filter { it !in cWhitelist }
            debugLog(
                "dump p2.a.a=$aMap, p2.a.c=$cMap, p2.a.d=$dMap, " +
                        "p2.c.d=$cPersistMap, p2.c.b.missing=$missingInWhitelist"
            )
        }.onFailure {
            debugLog("dump failed: ${it.message}")
        }
    }

    private fun readStaticMap(className: String, fieldName: String): Map<String, Any?> {
        val raw = className.toClass().resolve().firstField { name = fieldName }.get<Any>()
            ?: return emptyMap()
        val map = unwrapMutableMap(raw)
        return map.entries.associate { (k, v) -> k.toString() to v }
    }

    @Suppress("SameParameterValue")
    private fun readStaticList(className: String, fieldName: String): List<String> {
        val raw = className.toClass().resolve().firstField { name = fieldName }.get<Any>()
            ?: return emptyList()
        return unwrapMutableList(raw).map { it.toString() }
    }

    private fun deployBusinessTemplate(business: String, sourcePath: String): String? {
        val source = sourcePath.trim()
        val target = resolveTemplatePath(business)
        val targetFile = File(target)

        val blobMeta = prefs.getString(RearWidgetConfigCodec.businessBlobMetaKey(business), "")
        if (blobMeta.isNotBlank() && deployedBlobMetaCache[business] == blobMeta && targetFile.exists()) {
            return target
        }

        if (blobMeta.isNotBlank()) {
            val encoded = prefs.getString(RearWidgetConfigCodec.businessBlobKey(business), "")
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val ok = runCatching {
                    targetFile.parentFile?.mkdirs()
                    val tmp =
                        File(targetFile.parentFile, "${targetFile.name}.tmp.${Process.myPid()}")
                    tmp.outputStream().use { it.write(bytes) }
                    if (targetFile.exists()) targetFile.delete()
                    val moved = tmp.renameTo(targetFile)
                    if (!moved) {
                        tmp.copyTo(targetFile, overwrite = true)
                        tmp.delete()
                    }
                    ensureReadable(targetFile)
                    true
                }.getOrDefault(false)

                if (ok) {
                    deployedBlobMetaCache[business] = blobMeta
                    debugLog("deployed business template from prefs business=$business -> $target size=${bytes.size}")
                    return target
                }
            }
            debugLog("deploy blob decode/write failed business=$business meta=$blobMeta")
        } else {
            debugLog("deploy blob missing business=$business")
        }

        val sourceFile = File(source)
        if (sourceFile.exists() && sourceFile.isFile) {
            val ok = runCatching {
                targetFile.parentFile?.mkdirs()
                sourceFile.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                ensureReadable(targetFile)
                true
            }.getOrDefault(false)
            if (ok) {
                debugLog("deployed business template from file business=$business source=$source -> $target")
                return target
            }
        }

        debugLog("deploy failed business=$business source=$source blobMeta=$blobMeta")
        return null
    }

    private fun resolveTemplatePath(business: String): String {
        val userId = Process.myUid() / 100000
        val base = TEMPLATE_BASE.format(userId.toString())
        val safeBiz = business.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$base/re_$safeBiz"
    }

    private fun removeDeployedBusinessTemplate(business: String) {
        runCatching {
            deployedBlobMetaCache.remove(business)
            val target = resolveTemplatePath(business)
            val file = File(target)
            if (file.exists() && file.delete()) {
                debugLog("removed stale deployed template business=$business path=$target")
            }
        }.onFailure {
            debugLog("remove stale deployed template failed business=$business err=${it.message}")
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun ensureReadable(file: File) {
        file.setReadable(true, false)
        file.parentFile?.setReadable(true, false)
        file.parentFile?.setExecutable(true, false)
    }

    private fun clearInjectCacheByOperation(op: String, payload: String) {
        when (op) {
            RearWidgetApi.Channel.OP_DISABLE_DISPLAY,
            RearWidgetApi.Channel.OP_UNREGISTER -> {
                val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return
                val pkg = obj.optString("packageName").trim()
                val biz = obj.optString("business").trim()
                if (pkg.isBlank() || biz.isBlank()) return
                val prefix = "$pkg:$biz:"
                injectedCardSignatureCache.keys.removeIf { it.startsWith(prefix) }
            }

            RearWidgetApi.Channel.OP_REMOVE -> {
                val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return
                val composite = obj.optJSONObject("ticket")?.optString("compositeKey").orEmpty()
                if (composite.isNotBlank()) injectedCompositeAt.remove(composite)
            }
        }
    }

    private fun buildInjectSignature(notice: RearWidgetApi.ActiveNotice): String {
        val payload = notice.payload
        return buildString {
            append(notice.ticket.compositeKey)
            append('|').append(payload.getString("title").orEmpty())
            append('|').append(payload.getString("business").orEmpty())
            append('|').append(notice.options.index ?: -1)
            append('|').append(notice.options.priority ?: -1)
            append('|').append(notice.options.sticky)
        }
    }
}
