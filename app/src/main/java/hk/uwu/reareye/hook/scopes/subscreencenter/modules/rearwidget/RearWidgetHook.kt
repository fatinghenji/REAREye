@file:Suppress("UNCHECKED_CAST")

package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.util.Base64
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.repository.rearwidget.RearBusinessExtraConfigRepository.getShowTimeTipForBusiness
import hk.uwu.reareye.repository.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import hk.uwu.reareye.widgetapi.IRearWidgetApiConnection
import hk.uwu.reareye.widgetapi.IRearWidgetApiService
import hk.uwu.reareye.widgetapi.RearWidgetActiveNotice
import hk.uwu.reareye.widgetapi.RearWidgetApiContract
import hk.uwu.reareye.widgetapi.RearWidgetNoticeOptions
import hk.uwu.reareye.widgetapi.RearWidgetNoticeTicket
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RearWidgetHook : YukiBaseHooker() {

    private data class OperationOutcome(
        val injectCompositeKey: String? = null,
        val ejectTicket: RearWidgetNoticeTicket? = null,
        val ejectBusiness: Pair<String, String>? = null,
    )

    companion object {
        private const val TAG = "REAREye-RearWidget"
        private const val TEMPLATE_BASE =
            "/data/system/theme_magic/users/%s/subscreencenter/smart_assistant"
        private val INTERNAL_BUSINESSES = listOf(
            "incall",
            "carHailing",
            "foodDelivery",
            "music",
            "xiaomiev",
            "privacy",
            "stock",
            "travel",
            "movie",
            "mishow",
            "mihomeCamera"
        )
    }

    private val appliedOnce = AtomicBoolean(false)
    private val startupBootstrapped = AtomicBoolean(false)
    private val bootstrapReceiverRegistered = AtomicBoolean(false)
    private val deployedBlobMetaCache = ConcurrentHashMap<String, String>()
    private val injectedCardSignatureCache = ConcurrentHashMap<String, String>()
    private val injectedCompositeAt = ConcurrentHashMap<String, Long>()
    private val bootstrapRetryCount = AtomicInteger(0)
    private val managerEpoch = AtomicInteger(0)

    private var manager: Any? = null
    private var mainHandler: Handler? = null
    private var hostContext: Context? = null

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            debugLog("hook process=$processName")
            RearWidgetRuntimeStore.install(packageName)
            debugLog("onHook start")

            val appRef = "com.xiaomi.subscreencenter.SubScreenCenterApp".toClass().resolve()
            val d0Ref = "Z1.d0".toClass().resolve()
            val persistenceRef = "H.d".toClass().resolve()
            val p2cRef = "p2.c".toClass().resolve()
            val z1mRef = "Z1.m".toClass().resolve()

            appRef.firstMethod {
                name = "attachBaseContext"
                parameterCount = 1
            }.hook().after {
                hostContext = (args[0] as? Context)?.applicationContext ?: (args[0] as? Context)
                registerHookBootstrapReceiver()
                applyRuntimeMaps(force = true)
                debugLog("attachBaseContext applied runtime maps and waiting for preset release")
            }

            persistenceRef.firstConstructor {
                parameterCount = 0
            }.hook().after {
                schedulePostPresetBootstrap(instance)
                debugLog("PersistenceManager created, scheduled custom widget restore after preset release")
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
                    scheduleInjectAllActiveNotices()
                    debugLog("captured manager unchanged, skip bootstrap and reinject active notices")
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
                val biz = RearWidgetRuntimeStore.fallbackBusiness(pkg) ?: return@after
                result = createU0b(biz, 0, 600)
                debugLog("p2.c.r fallback pkg=$pkg -> business=$biz")
            }

            p2cRef.firstMethod {
                name = "i"
                parameterCount = 2
            }.hook().after {
                val pkg = args[0] as? String ?: return@after
                val biz = args[1] as? String ?: return@after
                // business 文件映射是全局覆盖 只要注册了该 business 文件 就覆盖系统内置路径
                val path = RearWidgetRuntimeStore.getBusinessFile(biz) ?: return@after
                result = path
                debugLog("p2.c.i override path pkg=$pkg biz=$biz path=$path")
            }

            p2cRef.firstMethod {
                name = "k"
                parameterCount = 3
            }.hook().before {
                val pkg = args[0] as? String ?: return@before
                if (RearWidgetRuntimeStore.allPkgBusinesses().containsKey(pkg)) {
                    result = true
                    debugLog("p2.c.k force pass pkg=$pkg")
                }
            }

            z1mRef.firstMethod {
                name = "run"
                parameterCount = 0
            }.hook().before {
                allowSelfDescribedNotificationPackage(instance)
            }

            p2cRef.firstMethod {
                name = "s"
                parameterCount = 10
            }.hook().after {
                applyRuntimeMaps(force = false)
                val out = result as? Bundle ?: return@after
                val key = out.getString("composite_key") ?: (args.getOrNull(1) as? String)
                val notice = key?.let { RearWidgetRuntimeStore.getNotice(it) } ?: return@after
                out.putAll(RearWidgetRuntimeStore.buildDecoratedExtras(notice.ticket))
            }
        }
    }

    private val hookBinder = object : IRearWidgetApiService.Stub() {
        override fun registerBusinessFile(business: String?, filePath: String?) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            val normalizedFilePath = filePath?.trim().orEmpty()
            if (normalizedBusiness.isBlank() || normalizedFilePath.isBlank()) return
            dispatchOperation(
                op = RearWidgetApiContract.Operation.REGISTER_FILE,
                action = {
                    val deployedPath =
                        deployBusinessTemplate(normalizedBusiness, normalizedFilePath)
                            ?: error("deploy template failed for business=$normalizedBusiness source=$normalizedFilePath")
                    RearWidgetRuntimeStore.registerBusinessFile(normalizedBusiness, deployedPath)
                    OperationOutcome()
                }
            )
        }

        override fun unregisterBusinessFile(business: String?) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            if (normalizedBusiness.isBlank()) return
            dispatchOperation(
                op = RearWidgetApiContract.Operation.UNREGISTER_FILE,
                action = {
                    RearWidgetRuntimeStore.unregisterBusinessFile(normalizedBusiness)
                    removeDeployedBusinessTemplate(normalizedBusiness)
                    OperationOutcome()
                }
            )
        }

        override fun registerBusiness(
            targetPackage: String?,
            business: String?,
            filePath: String?,
            defaultIndex: Int,
            defaultPriority: Int,
        ) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            val normalizedFilePath = filePath?.trim().orEmpty()
            if (normalizedBusiness.isBlank() || normalizedFilePath.isBlank()) return
            dispatchOperation(
                op = RearWidgetApiContract.Operation.REGISTER,
                action = {
                    val deployedPath =
                        deployBusinessTemplate(normalizedBusiness, normalizedFilePath)
                            ?: error("deploy template failed for business=$normalizedBusiness source=$normalizedFilePath")
                    RearWidgetRuntimeStore.registerBusiness(
                        packageName = normalizeTargetPackage(targetPackage),
                        business = normalizedBusiness,
                        filePath = deployedPath,
                        defaultIndex = defaultIndex,
                        defaultPriority = defaultPriority,
                    )
                    OperationOutcome()
                }
            )
        }

        override fun registerBusinessWithoutFile(
            targetPackage: String?,
            business: String?,
            defaultIndex: Int,
            defaultPriority: Int,
        ) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            if (normalizedBusiness.isBlank()) return
            dispatchOperation(
                op = RearWidgetApiContract.Operation.REGISTER,
                action = {
                    val registered = RearWidgetRuntimeStore.registerBusinessWithoutFile(
                        packageName = normalizeTargetPackage(targetPackage),
                        business = normalizedBusiness,
                        defaultIndex = defaultIndex,
                        defaultPriority = defaultPriority,
                    )
                    check(registered) { "filePath not found for business: $normalizedBusiness" }
                    OperationOutcome()
                }
            )
        }

        override fun unregisterBusiness(targetPackage: String?, business: String?) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            if (normalizedBusiness.isBlank()) return
            val packageName = normalizeTargetPackage(targetPackage)
            dispatchOperation(
                op = RearWidgetApiContract.Operation.UNREGISTER,
                action = {
                    RearWidgetRuntimeStore.unregisterBusiness(packageName, normalizedBusiness)
                    OperationOutcome(ejectBusiness = packageName to normalizedBusiness)
                }
            )
        }

        override fun disableBusinessDisplay(targetPackage: String?, business: String?) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            if (normalizedBusiness.isBlank()) return
            val packageName = normalizeTargetPackage(targetPackage)
            dispatchOperation(
                op = RearWidgetApiContract.Operation.DISABLE_DISPLAY,
                action = {
                    RearWidgetRuntimeStore.disableBusinessDisplay(packageName, normalizedBusiness)
                    OperationOutcome(ejectBusiness = packageName to normalizedBusiness)
                }
            )
        }

        override fun postNotice(
            targetPackage: String?,
            business: String?,
            payload: Bundle?,
            options: Bundle?,
        ) {
            enforceCallerPermission()
            val normalizedBusiness = business?.trim().orEmpty()
            if (normalizedBusiness.isBlank()) return
            val noticeOptions = RearWidgetNoticeOptions.fromBundle(options)
            dispatchOperation(
                op = RearWidgetApiContract.Operation.POST,
                action = {
                    val ticket = RearWidgetRuntimeStore.postNotice(
                        packageName = normalizeTargetPackage(targetPackage),
                        business = normalizedBusiness,
                        payload = payload ?: Bundle(),
                        options = noticeOptions,
                    )
                    OperationOutcome(injectCompositeKey = ticket.compositeKey)
                }
            )
        }

        override fun updateNotice(
            ticket: Bundle?,
            payload: Bundle?,
            options: Bundle?,
            updatePayload: Boolean,
            updateOptions: Boolean,
        ) {
            enforceCallerPermission()
            val noticeTicket = RearWidgetNoticeTicket.fromBundle(ticket) ?: return
            val payloadArg = if (updatePayload) payload ?: Bundle() else null
            val optionsArg = if (updateOptions) {
                RearWidgetNoticeOptions.fromBundle(options)
            } else {
                null
            }
            dispatchOperation(
                op = RearWidgetApiContract.Operation.UPDATE,
                action = {
                    RearWidgetRuntimeStore.updateNotice(noticeTicket, payloadArg, optionsArg)
                    OperationOutcome(injectCompositeKey = noticeTicket.compositeKey)
                }
            )
        }

        override fun removeNotice(ticket: Bundle?) {
            enforceCallerPermission()
            val noticeTicket = RearWidgetNoticeTicket.fromBundle(ticket) ?: return
            dispatchOperation(
                op = RearWidgetApiContract.Operation.REMOVE,
                action = {
                    RearWidgetRuntimeStore.removeNotice(noticeTicket)
                    OperationOutcome(ejectTicket = noticeTicket)
                }
            )
        }

        override fun syncState() {
            enforceCallerPermission()
            bootstrapFromPrefsOnInit(force = true)
            applyRuntimeMaps(force = true)
            patchManagerAppGates(manager)
            scheduleInjectAllActiveNotices()
        }
    }

    private val hookBootstrapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RearWidgetApiContract.ACTION_REQUEST_HOOK_SERVICE) return
            val callbackBinder = intent
                .getBundleExtra(RearWidgetApiContract.Extras.BUNDLE)
                ?.getBinder(RearWidgetApiContract.Extras.BINDER)
            val callback = IRearWidgetApiConnection.Stub.asInterface(callbackBinder)
            val forceSync = intent.getBooleanExtra(RearWidgetApiContract.Extras.FORCE_SYNC, false)
            if (forceSync) {
                bootstrapFromPrefsOnInit(force = true)
            }
            runCatching {
                callback?.onServiceConnected(hookBinder)
            }.onFailure {
                debugLog("reply hook binder failed err=${it.message}")
            }
        }
    }

    private fun registerHookBootstrapReceiver() {
        if (!bootstrapReceiverRegistered.compareAndSet(false, true)) return
        val ctx = hostContext ?: run {
            bootstrapReceiverRegistered.set(false)
            return
        }
        val ok = runCatching {
            val filter = IntentFilter(RearWidgetApiContract.ACTION_REQUEST_HOOK_SERVICE)
            ContextCompat.registerReceiver(
                ctx,
                hookBootstrapReceiver,
                filter,
                RearWidgetApiContract.SERVICE_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED
            )
            true
        }.onFailure {
            bootstrapReceiverRegistered.set(false)
            debugLog("register bootstrap receiver failed err=${it.message}")
        }.getOrDefault(false)
        if (ok) {
            debugLog("register bootstrap receiver success")
        }
    }

    private fun dispatchOperation(op: String, action: () -> OperationOutcome) {
        val outcome = action()
        clearInjectCache(op, outcome)
        applyRuntimeMaps(force = true)
        patchManagerAppGates(manager)
        outcome.injectCompositeKey?.let { injectByCompositeKey(it) }
        outcome.ejectTicket?.let { ejectByTicket(it) }
        outcome.ejectBusiness?.let { (pkg, biz) -> ejectBusinessDisplay(pkg, biz) }
    }

    private fun enforceCallerPermission() {
        val ctx = hostContext
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        if (ctx == null) {
            throw SecurityException("context not ready for permission check")
        }
        val granted = ctx.checkPermission(
            RearWidgetApiContract.SERVICE_PERMISSION,
            Binder.getCallingPid(),
            uid,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw SecurityException(
                "caller uid=$uid requires ${RearWidgetApiContract.SERVICE_PERMISSION}"
            )
        }
    }

    private fun normalizeTargetPackage(targetPackage: String?): String {
        return targetPackage?.trim().takeUnless { it.isNullOrBlank() }
            ?: RearWidgetRuntimeStore.defaultPackageName
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
        val stickyCards = cards.filter { it.sticky }
        val prefsManager = prefs.getPrefsManager()
        if (!force && businesses.isEmpty() && cards.isEmpty()) {
            debugLog("bootstrap init skipped: no config yet")
            return false
        }

        val businessPathMap = LinkedHashMap<String, String>()
        businesses.forEach { item ->
            val deployedPath = deployBusinessTemplate(item.business, item.filePath)
                ?: run {
                    debugLog("bootstrap register_file failed business=${item.business} deploy failed")
                    return false
                }
            businessPathMap[item.business] = deployedPath
            RearWidgetRuntimeStore.registerBusinessFile(item.business, deployedPath)
        }

        val uniquePairs = LinkedHashSet<Pair<String, String>>()
        cards.forEach { uniquePairs += (it.packageName to it.business) }

        // 重放前先清掉目标业务的旧展示 保证重复 bootstrap 不会叠加出重复卡片
        uniquePairs.forEach { (pkg, biz) ->
            RearWidgetRuntimeStore.disableBusinessDisplay(pkg, biz)
        }

        uniquePairs.forEach { (pkg, biz) ->
            val ok = businessPathMap[biz]?.let { path ->
                RearWidgetRuntimeStore.registerBusiness(
                    packageName = pkg,
                    business = biz,
                    filePath = path,
                    defaultIndex = 0,
                    defaultPriority = 500,
                )
                true
            } ?: RearWidgetRuntimeStore.registerBusinessWithoutFile(
                packageName = pkg,
                business = biz,
                defaultIndex = 0,
                defaultPriority = 500,
            )
            if (!ok) {
                debugLog("bootstrap register failed pkg=$pkg biz=$biz")
                return false
            }
        }

        stickyCards.forEachIndexed { index, card ->
            val payload = Bundle().apply {
                putString("title", card.title.ifBlank { card.business })
                putString("business", card.business)
                putString("__rear_card_id__", card.id)
            }
            val options = RearWidgetNoticeOptions(
                sticky = card.sticky,
                disablePopup = true,
                showTimeTip = prefsManager.getShowTimeTipForBusiness(card.business),
                index = index,
                priority = card.priority,
            )
            runCatching {
                RearWidgetRuntimeStore.postNotice(
                    business = card.business,
                    packageName = card.packageName,
                    payload = payload,
                    options = options,
                )
            }.onFailure {
                debugLog("bootstrap post failed pkg=${card.packageName} biz=${card.business} cardId=${card.id} err=${it.message}")
                return false
            }
        }

        applyRuntimeMaps(force = true)
        startupBootstrapped.set(true)
        bootstrapRetryCount.set(0)
        debugLog("bootstrap init replay businesses=${businesses.size} enabledCards=${cards.size} stickyCards=${stickyCards.size} force=$force ok=true")
        return true
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

    private fun injectAllActiveNotices() {
        RearWidgetRuntimeStore.listNotices().forEach { notice ->
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

    private fun schedulePostPresetBootstrap(persistenceManager: Any?) {
        val handler = runCatching {
            persistenceManager?.asResolver()?.firstField { name = "c" }?.get() as? Handler
        }.getOrNull() ?: return

        handler.post {
            runCatching {
                bootstrapFromPrefsOnInit(force = true)
                applyRuntimeMaps(force = true)
                patchManagerAppGates(manager)
                debugLog("restored custom widget templates after preset release")
            }.onFailure {
                debugLog("post-preset bootstrap failed err=${it.message}")
            }
        }
    }

    private fun injectByCompositeKey(compositeKey: String, force: Boolean = false) {
        val notice = RearWidgetRuntimeStore.getNotice(compositeKey) ?: return
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
            val extras = RearWidgetRuntimeStore.buildDecoratedExtras(notice.ticket)
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

    private fun ejectByTicket(ticket: RearWidgetNoticeTicket) {
        val mgr = manager ?: return
        val handler = mainHandler ?: return
        runCatching {
            handler.post {
                runCatching {
                    mgr.asResolver().firstMethod {
                        name = "p"
                        parameterCount = 3
                    }.invoke(ticket.notificationId, ticket.packageName, 0)
                    debugLog("ejected ticket key=${ticket.compositeKey}")
                }.onFailure {
                    debugLog("eject failed key=${ticket.compositeKey} err=${it.message}")
                }
            }
        }.onFailure {
            debugLog("eject schedule failed key=${ticket.compositeKey} err=${it.message}")
        }
    }

    private fun ejectBusinessDisplay(packageName: String, business: String) {
        val mgr = manager ?: return
        val handler = mainHandler ?: return
        val prefix = "$packageName:$business:"
        injectedCardSignatureCache.keys.removeIf { it.startsWith(prefix) }
        injectedCompositeAt.keys.removeIf { it.startsWith(prefix) }
        runCatching {
            handler.post {
                runCatching {
                    mgr.asResolver().firstMethod {
                        name = "v"
                        parameterCount = 2
                    }.invoke(packageName, business)
                    debugLog("ejected business display pkg=$packageName biz=$business")
                }.onFailure {
                    debugLog("eject business display failed pkg=$packageName biz=$business err=${it.message}")
                }
            }
        }.onFailure {
            debugLog("eject schedule failed pkg=$packageName biz=$business err=${it.message}")
        }
    }

    private fun applyRuntimeMaps(force: Boolean) {
        if (!force && !RearWidgetRuntimeStore.mapsDirty.get()) return
        if (!appliedOnce.compareAndSet(
                false,
                true
            ) && !force && !RearWidgetRuntimeStore.mapsDirty.get()
        ) return

        val pkgBiz = RearWidgetRuntimeStore.allPkgBusinesses()
        val pkgPrimary = RearWidgetRuntimeStore.primaryBusinessByPkg()
        val bizPath = RearWidgetRuntimeStore.allBusinessPath()

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

        RearWidgetRuntimeStore.mapsDirty.set(false)
        dumpRuntimeMaps(bizPath)
    }

    private fun patchManagerAppGates(target: Any?) {
        val instance = target ?: return
        val pkgBiz = RearWidgetRuntimeStore.allPkgBusinesses()
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

    private fun allowSelfDescribedNotificationPackage(runnable: Any) {
        if (!prefs.getBoolean(ConfigKeys.HOOK_ALLOW_REAR_FOCUS_NOTICES, false)) return
        val ref = runnable.asResolver()
        val owner = runCatching {
            ref.firstField { name = "c" }.get<Any>()
        }.getOrNull() ?: return
        if (owner.javaClass.name != "Z1.d0") return

        val packageName = runCatching {
            ref.firstField { name = "d" }.get<String>()
        }.getOrNull()?.trim().orEmpty()
        if (packageName.isBlank()) return

        val extras = runCatching {
            ref.firstField { name = "f" }.get<Bundle>()
        }.getOrNull() ?: return
        if (extras.isEmpty) return

        val business = parseBusinessFromParams(packageName, extras) ?: return

        if (INTERNAL_BUSINESSES.contains(business)) return

        logNoWidgetPathIfNeeded(packageName, business, extras)

        runCatching {
            @Suppress("UNCHECKED_CAST")
            val rSet = owner.asResolver().firstField { name = "r" }
                .get() as ConcurrentHashMap.KeySetView<String, *>

            @Suppress("UNCHECKED_CAST")
            val qMap = owner.asResolver().firstField { name = "q" }
                .get() as ConcurrentHashMap<String, Boolean>

            rSet.add(packageName)
            qMap[packageName] = true
            qMap["${packageName}_$business"] = true
            debugLog("dynamic allow pkg=$packageName biz=$business")
        }.onFailure {
            debugLog("dynamic allow failed pkg=$packageName biz=$business err=${it.message}")
        }
    }

    private fun parseBusinessFromParams(packageName: String, extras: Bundle): String? {
        val parser = runCatching {
            "L1.a".toClass().resolve().firstMethod {
                name = "y"
                parameterCount = 1
            }.invoke(extras)
        }.getOrNull() ?: return null

        val parsed = runCatching {
            "p2.c".toClass().resolve().firstMethod {
                name = "r"
                parameterCount = 2
            }.invoke(packageName, parser)
        }.getOrNull() ?: return null

        return runCatching {
            parsed.asResolver().firstField { name = "c" }.get<String>()
        }.getOrNull()?.trim()?.ifBlank { null }
    }

    private fun logNoWidgetPathIfNeeded(packageName: String, business: String, extras: Bundle) {
        val hasRemoteView =
            extras.containsKey("miui.rear.rv") || extras.containsKey("miui.rear.rvAOD")
        if (hasRemoteView) return

        val builtInSupported = runCatching {
            "p2.a".toClass().resolve().firstMethod {
                name = "d"
                parameterCount = 2
            }.invoke<Boolean>(packageName, business) ?: false
        }.getOrDefault(false)
        if (builtInSupported) return

        val widgetPath = runCatching {
            "p2.c".toClass().resolve().firstMethod {
                name = "i"
                parameterCount = 2
            }.invoke<String>(packageName, business)
        }.getOrNull()

        if (widgetPath.isNullOrBlank()) {
            YLog.debug("[$TAG] No widget path pkg=$packageName business=$business")
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
        if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
            YLog.debug("[$TAG] $message")
        }
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

    private fun clearInjectCache(op: String, outcome: OperationOutcome) {
        when (op) {
            RearWidgetApiContract.Operation.DISABLE_DISPLAY,
            RearWidgetApiContract.Operation.UNREGISTER -> {
                val (pkg, biz) = outcome.ejectBusiness ?: return
                val prefix = "$pkg:$biz:"
                injectedCardSignatureCache.keys.removeIf { it.startsWith(prefix) }
                injectedCompositeAt.keys.removeIf { it.startsWith(prefix) }
            }

            RearWidgetApiContract.Operation.REMOVE -> {
                val composite = outcome.ejectTicket?.compositeKey.orEmpty()
                if (composite.isNotBlank()) injectedCompositeAt.remove(composite)
            }
        }
    }

    private fun buildInjectSignature(notice: RearWidgetActiveNotice): String {
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
