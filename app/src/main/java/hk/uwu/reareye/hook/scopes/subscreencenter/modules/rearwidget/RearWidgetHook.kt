@file:Suppress("UNCHECKED_CAST")

package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.util.Base64
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.repository.rearwidget.REAR_WIDGET_CARD_ONE_CONFIG_JSON_KEY
import hk.uwu.reareye.repository.rearwidget.RearBusinessExtraConfigRepository.getShowTimeTipForBusiness
import hk.uwu.reareye.repository.rearwidget.RearWidgetConfigCodec
import hk.uwu.reareye.repository.widgettemplate.WidgetTemplateConfigRepository
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.PrefsManager.Companion.getPrefsManager
import hk.uwu.reareye.widgetapi.IRearWidgetApiConnection
import hk.uwu.reareye.widgetapi.IRearWidgetApiService
import hk.uwu.reareye.widgetapi.RearWidgetActiveNotice
import hk.uwu.reareye.widgetapi.RearWidgetApiContract
import hk.uwu.reareye.widgetapi.RearWidgetNoticeOptions
import hk.uwu.reareye.widgetapi.RearWidgetNoticeTicket
import hk.uwu.reareye.widgetapi.RearWidgetTemplateConfigState
import hk.uwu.reareye.widgetapi.RearWidgetTemplateImagePreview
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

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
        private const val CARD_CONFIG_BASE =
            "/data/system/theme_magic/users/%s/subscreencenter/reareye_card_config"
        private const val CARD_ASSET_BASE =
            "/data/system/theme_magic/users/%s/subscreencenter/reareye_card_assets"
        private val BUILTIN_TEMPLATE_RELATIVE_PATHS = mapOf(
            "incall" to "phone",
            "alarm" to "alarm",
            "countdown" to "timer",
            "carHailing" to "car_hailing",
            "foodDelivery" to "food_delivery",
            "music" to "music",
            "xiaomiev" to "ev",
            "privacy" to "privacy",
            "stock" to "stock",
            "mihomeCamera" to "miHomeCamera",
        )
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
    private val deployedCardConfigMetaCache = ConcurrentHashMap<String, String>()
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
            val t2jRef = "t2.j".toClass().resolve()

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

            t2jRef.firstMethod {
                name = "J"
                parameterCount = 1
            }.hook().after {
                applyCardOneConfig(instance, args.getOrNull(0), "t2.j.J")
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

        override fun resolveTemplateImagePreview(
            business: String?,
            sourceFilePath: String?,
            imageValue: String?,
        ): Bundle {
            enforceCallerPermission()
            val preview = resolveTemplateImagePreviewModel(
                business = business?.trim().orEmpty(),
                sourcePath = sourceFilePath?.trim().orEmpty(),
                imageValue = imageValue?.trim().orEmpty(),
            )
            return preview?.toBundle() ?: Bundle()
        }

        override fun resolveTemplateConfigState(
            business: String?,
            sourceFilePath: String?,
            currentOneConfigJson: String?,
        ): Bundle {
            enforceCallerPermission()
            val state = resolveTemplateConfigStateModel(
                business = business?.trim().orEmpty(),
                sourcePath = sourceFilePath?.trim().orEmpty(),
                currentOneConfigJson = currentOneConfigJson?.trim(),
            )
            return state?.toBundle() ?: Bundle()
        }

        override fun importCardCustomImage(
            cardKey: String?,
            fieldName: String?,
            sourceUri: String?,
            displayNameHint: String?,
        ): String {
            enforceCallerPermission()
            return importCardCustomImageInternal(
                cardKey = cardKey?.trim().orEmpty(),
                fieldName = fieldName?.trim().orEmpty(),
                sourceUri = sourceUri?.trim().orEmpty(),
                displayNameHint = displayNameHint?.trim().orEmpty(),
            ).orEmpty()
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
                card.oneConfigJson?.takeIf { it.isNotBlank() }?.let {
                    putString(REAR_WIDGET_CARD_ONE_CONFIG_JSON_KEY, it)
                }
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

    private fun resolveCardConfigPath(cardKey: String): String {
        val userId = Process.myUid() / 100000
        val base = CARD_CONFIG_BASE.format(userId.toString())
        val safeKey = cardKey.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$base/$safeKey.json"
    }

    private fun resolveCardAssetDir(cardKey: String): File {
        val userId = Process.myUid() / 100000
        val base = CARD_ASSET_BASE.format(userId.toString())
        val safeKey = cardKey.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(base, safeKey)
    }

    private fun resolveBuiltinTemplatePath(business: String): String? {
        val userId = Process.myUid() / 100000
        val normalizedBusiness = normalizeBusinessName(business)
        val relative = BUILTIN_TEMPLATE_RELATIVE_PATHS[normalizedBusiness] ?: return null
        return "/data/system/theme_magic/users/$userId/subscreencenter/smart_assistant/$relative"
    }

    private fun normalizeBusinessName(raw: String): String {
        return when (raw.trim()) {
            "taxi", "car_hailing", "carHailing" -> "carHailing"
            "food_Delivery", "food_delivery", "foodDelivery" -> "foodDelivery"
            "miHomeCamera", "mihomeCamera" -> "mihomeCamera"
            "xiaomi_ev", "xiaomiev" -> "xiaomiev"
            else -> raw.trim()
        }
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

    private fun deployCardOneConfig(cardKey: String, json: String): String? {
        val normalizedJson = json.trim()
        if (normalizedJson.isBlank()) return null

        val target = resolveCardConfigPath(cardKey)
        val targetFile = File(target)
        val meta = "${normalizedJson.length}:${normalizedJson.hashCode()}"
        if (deployedCardConfigMetaCache[cardKey] == meta && targetFile.exists()) {
            return target
        }

        val ok = runCatching {
            targetFile.parentFile?.mkdirs()
            val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp.${Process.myPid()}")
            tmp.writeText(normalizedJson)
            if (targetFile.exists()) targetFile.delete()
            val moved = tmp.renameTo(targetFile)
            if (!moved) {
                tmp.copyTo(targetFile, overwrite = true)
                tmp.delete()
            }
            ensureReadable(targetFile)
            true
        }.getOrDefault(false)

        if (!ok) {
            debugLog("deploy card one config failed cardKey=$cardKey")
            return null
        }

        deployedCardConfigMetaCache[cardKey] = meta
        debugLog("deployed card one config cardKey=$cardKey -> $target")
        return target
    }

    private fun removeCardOneConfig(cardKey: String) {
        runCatching {
            deployedCardConfigMetaCache.remove(cardKey)
            val file = File(resolveCardConfigPath(cardKey))
            if (file.exists()) file.delete()
        }.onFailure {
            debugLog("remove card one config failed cardKey=$cardKey err=${it.message}")
        }
    }

    private fun applyCardOneConfig(owner: Any?, mamlView: Any?, hookPoint: String) {
        if (owner == null || mamlView == null) {
            debugLog("applyCardOneConfig skip hook=$hookPoint owner=${owner != null} view=${mamlView != null}")
            return
        }
        val extras = extractCardExtras(owner)
        if (extras == null) {
            debugLog("applyCardOneConfig missing extras hook=$hookPoint owner=${owner.javaClass.name}")
            return
        }

        val json = extras.getString(REAR_WIDGET_CARD_ONE_CONFIG_JSON_KEY)?.trim().orEmpty()
        val cardId = extras.getString("__rear_card_id__")?.trim().orEmpty()
        val packageName = extras.getString("package_name")?.trim().orEmpty()
        val business = extras.getString("business")?.trim().orEmpty()
        val cardKey = listOf(packageName, business, cardId)
            .filter { it.isNotBlank() }
            .joinToString("_")
            .ifBlank { "${packageName}_${business}".trim('_') }
            .ifBlank {
                debugLog("applyCardOneConfig missing key hook=$hookPoint pkg=$packageName biz=$business cardId=$cardId")
                return
            }

        debugLog(
            "applyCardOneConfig hook=$hookPoint cardKey=$cardKey hasJson=${json.isNotBlank()} jsonLength=${json.length} owner=${owner.javaClass.name} view=${mamlView.javaClass.name}"
        )
        val templatePath = extractFieldFromHierarchy(owner, "A") as? String

        if (json.isBlank()) {
            removeCardOneConfig(cardKey)
            return
        }

        val configPath = deployCardOneConfig(cardKey, json) ?: return
        applyCardOneConfigOnce(
            mamlView,
            json,
            configPath,
            cardKey,
            "$hookPoint/immediate",
            templatePath
        )
        (mamlView as? View)?.postDelayed({
            applyCardOneConfigOnce(
                mamlView,
                json,
                configPath,
                cardKey,
                "$hookPoint/post120",
                templatePath
            )
        }, 120L)
    }

    private fun applyCardOneConfigOnce(
        mamlView: Any,
        json: String,
        configPath: String,
        cardKey: String,
        stage: String,
        templatePath: String?,
    ) {
        runCatching {
            "com.miui.maml.widget.edit.WidgetEditSave".toClass().resolve().firstMethod {
                name = "restoreFromConfigPath"
                parameterCount = 2
            }.invoke(mamlView, configPath)
            applyHostOneConfig(mamlView, json)
            applyCompatOneConfig(mamlView, json)
            applyManifestDerivedVars(mamlView, templatePath)
            requestMamlRefresh(mamlView)
            debugLog("applied card one config cardKey=$cardKey stage=$stage path=$configPath jsonLength=${json.length}")
        }.onFailure {
            debugLog("apply card one config failed cardKey=$cardKey stage=$stage err=${it.message}")
        }
    }

    private fun extractCardExtras(owner: Any): Bundle? {
        extractFieldFromHierarchy(owner, "i")?.let { value ->
            val bundle = value as? Bundle
            if (bundle != null && bundle.hasCardConfigMarkers()) return bundle
        }

        var current: Class<*>? = owner.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(owner) as? Bundle ?: return@runCatching
                    if (value.hasCardConfigMarkers()) {
                        return value
                    }
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun extractFieldFromHierarchy(owner: Any, fieldName: String): Any? {
        var current: Class<*>? = owner.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { it.name == fieldName }?.let { field ->
                return runCatching {
                    field.isAccessible = true
                    field.get(owner)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    private fun Bundle.hasCardConfigMarkers(): Boolean {
        return containsKey(REAR_WIDGET_CARD_ONE_CONFIG_JSON_KEY) || containsKey("__rear_card_id__")
    }

    private fun applyHostOneConfig(mamlView: Any, json: String) {
        runCatching {
            val classLoader = mamlView.javaClass.classLoader
            val oneConfigClass = Class.forName(
                "com.miui.maml.widget.edit.OneConfig",
                false,
                classLoader,
            )
            val widgetEditSaveClass = Class.forName(
                "com.miui.maml.widget.edit.WidgetEditSave",
                false,
                classLoader,
            )

            val hostOneConfig = com.google.gson.Gson().fromJson(json, oneConfigClass) ?: return

            mamlView.javaClass.methods.firstOrNull {
                it.name == "setConfig" && it.parameterTypes.size == 1
            }?.invoke(mamlView, hostOneConfig)

            widgetEditSaveClass.methods.firstOrNull {
                it.name == "restoreMamlView" && it.parameterTypes.size == 2
            }?.invoke(null, hostOneConfig, mamlView)

            debugLog("apply host one config success jsonLength=${json.length} oneConfigClass=${oneConfigClass.name}")
        }.onFailure {
            debugLog("apply host one config failed err=${it.message}")
        }
    }

    private fun applyCompatOneConfig(mamlView: Any, json: String) {
        val oneConfig = WidgetTemplateConfigRepository.decodeOneConfig(json) ?: return
        val dropDownValues = oneConfig.dropDownSaveConfig.orEmpty()
        if (dropDownValues.isEmpty()) return

        runCatching {
            val putVariableString = mamlView.javaClass.methods.firstOrNull {
                it.name == "putVariableString" && it.parameterTypes.size == 3
            } ?: return
            val requestUpdate = mamlView.javaClass.methods.firstOrNull {
                it.name == "requestUpdate" && it.parameterTypes.isEmpty()
            }
            val sendCommand = mamlView.javaClass.methods.firstOrNull {
                it.name == "sendCommand" && it.parameterTypes.size == 1
            }

            dropDownValues.forEach { (key, value) ->
                putVariableString.invoke(mamlView, key, value, 1)
            }
            requestUpdate?.invoke(mamlView)
            sendCommand?.invoke(mamlView, "resume")
            sendCommand?.invoke(mamlView, "refresh_after_edit")
        }.onFailure {
            debugLog("apply compat one config failed err=${it.message}")
        }
    }

    private fun requestMamlRefresh(mamlView: Any) {
        runCatching {
            mamlView.javaClass.methods.firstOrNull {
                it.name == "requestUpdate" && it.parameterTypes.isEmpty()
            }?.invoke(mamlView)
            mamlView.javaClass.methods.firstOrNull {
                it.name == "sendCommand" && it.parameterTypes.size == 1
            }?.let { sendCommand ->
                sendCommand.invoke(mamlView, "resume")
                sendCommand.invoke(mamlView, "refresh_after_edit")
            }
        }.onFailure {
            debugLog("request maml refresh failed err=${it.message}")
        }
    }

    private data class ManifestVarDef(
        val name: String,
        val type: String,
        val expression: String,
    )

    private fun applyManifestDerivedVars(mamlView: Any, templatePath: String?) {
        val manifest = templatePath?.let(::readManifestText) ?: return
        val vars = parseManifestVarDefs(manifest)
        if (vars.isEmpty()) return

        val putString = mamlView.javaClass.methods.firstOrNull {
            it.name == "putVariableString" && it.parameterTypes.size == 3
        }
        val putNumber = mamlView.javaClass.methods.firstOrNull {
            it.name == "putVariableNumber" && it.parameterTypes.size == 3
        }
        val getString = mamlView.javaClass.methods.firstOrNull {
            it.name == "getVariableString" && it.parameterTypes.size == 1
        }
        val getNumber = mamlView.javaClass.methods.firstOrNull {
            it.name == "getVariableNumber" && it.parameterTypes.size == 1
        }
        val getObject = mamlView.javaClass.methods.firstOrNull {
            it.name == "getVariableObject" && it.parameterTypes.size == 1
        }
        if (putString == null || putNumber == null || getString == null || getNumber == null) return

        fun putDerivedString(key: String, value: String?) {
            if (value == null) return
            putString.invoke(mamlView, key, value, 1)
        }

        fun putDerivedNumber(key: String, value: Double?) {
            if (value == null) return
            putNumber.invoke(mamlView, key, value, 1)
        }

        val directStringAlias = Regex("^@([A-Za-z0-9_]+)$")
        val directNumberAlias = Regex("^#([A-Za-z0-9_]+)$")
        val alignExpr = Regex(
            """^ifelse\(\(#([A-Za-z0-9_]+)\s*==\s*0\),'left',ifelse\(\(#\1\s*==\s*1\),'center','right'\)\)$"""
        )
        val multiplyExpr = Regex(
            """^\(#([A-Za-z0-9_]+)\s*\*\s*#([A-Za-z0-9_]+)\)$"""
        )

        vars.forEach { varDef ->
            val expr = varDef.expression.trim()
            directStringAlias.matchEntire(expr)?.groupValues?.getOrNull(1)?.let { source ->
                putDerivedString(varDef.name, getString.invoke(mamlView, source) as? String)
            }
            directNumberAlias.matchEntire(expr)?.groupValues?.getOrNull(1)?.let { source ->
                val value = (getNumber.invoke(mamlView, source) as? Number)?.toDouble()
                putDerivedNumber(varDef.name, value)
            }
        }

        vars.forEach { varDef ->
            val expr = varDef.expression.trim()
            alignExpr.matchEntire(expr)?.groupValues?.getOrNull(1)?.let { source ->
                val alignValue = ((getNumber.invoke(mamlView, source) as? Number)?.toInt()) ?: 0
                val textAlign = when (alignValue) {
                    0 -> "left"
                    1 -> "center"
                    else -> "right"
                }
                putDerivedString(varDef.name, textAlign)
            }
            multiplyExpr.matchEntire(expr)?.let { match ->
                val left = (getNumber.invoke(mamlView, match.groupValues[1]) as? Number)?.toDouble()
                val right =
                    (getNumber.invoke(mamlView, match.groupValues[2]) as? Number)?.toDouble()
                if (left != null && right != null) {
                    putDerivedNumber(varDef.name, left * right)
                }
            }
            if (expr.contains("#bgUrl[0]") && expr.contains("@bgPath1")) {
                val selected =
                    (getObject?.invoke(mamlView, "bgUrl") as? DoubleArray) ?: doubleArrayOf()
                val bg1 = getString.invoke(mamlView, "bgPath1") as? String
                val bg2 = getString.invoke(mamlView, "bgPath2") as? String
                val bg3 = getString.invoke(mamlView, "bgPath3") as? String
                val resolved = when {
                    selected.getOrNull(0) == 1.0 -> bg1
                    selected.getOrNull(1) == 1.0 -> bg2
                    selected.getOrNull(2) == 1.0 -> bg3
                    else -> bg1
                }
                putDerivedString(varDef.name, resolved)
            }
        }
    }

    private fun parseManifestVarDefs(text: String): List<ManifestVarDef> {
        val regex =
            Regex("<Var\\s+[^>]*name=\"([^\"]+)\"[^>]*type=\"([^\"]+)\"[^>]*expression=\"([^\"]*)\"[^>]*/?>")
        return regex.findAll(text).map {
            ManifestVarDef(
                name = it.groupValues[1],
                type = it.groupValues[2],
                expression = it.groupValues[3],
            )
        }.toList()
    }

    private fun readManifestText(templatePath: String): String? {
        return runCatching {
            val file = File(templatePath)
            if (!file.exists()) return null
            if (file.isDirectory) {
                val manifest = File(file, "manifest.xml")
                if (!manifest.exists()) return null
                return manifest.readText()
            }
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.xml") ?: return@use null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }.getOrNull()
    }

    private fun resolveTemplateImagePreviewModel(
        business: String,
        sourcePath: String,
        imageValue: String,
    ): RearWidgetTemplateImagePreview? {
        if (imageValue.isBlank()) return null
        val templatePath = resolveTemplatePreviewPath(business, sourcePath) ?: return null
        debugLog(
            "resolveTemplateImagePreview business=$business normalized=${
                normalizeBusinessName(
                    business
                )
            } source=${sourcePath.ifBlank { "<builtin>" }} template=$templatePath value=$imageValue"
        )
        val imageBytes = loadTemplateImageBytes(templatePath, imageValue) ?: return null
        val previewBytes = compressPreviewBytes(imageBytes) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size, bounds)
        return RearWidgetTemplateImagePreview(
            imageValue = imageValue,
            templateSourcePath = sourcePath,
            previewBase64 = Base64.encodeToString(previewBytes, Base64.NO_WRAP),
            mimeType = "image/png",
            width = bounds.outWidth.coerceAtLeast(0),
            height = bounds.outHeight.coerceAtLeast(0),
        )
    }

    private fun resolveTemplateConfigStateModel(
        business: String,
        sourcePath: String,
        currentOneConfigJson: String?,
    ): RearWidgetTemplateConfigState? {
        val templatePath = resolveTemplatePreviewPath(business, sourcePath)
        if (templatePath == null) {
            debugLog(
                "resolveTemplateConfigState miss-path business=$business normalized=${
                    normalizeBusinessName(
                        business
                    )
                } source=${sourcePath.ifBlank { "<builtin>" }}"
            )
            return null
        }
        val schema = WidgetTemplateConfigRepository.loadSchema(templatePath)
        if (schema == null) {
            debugLog(
                "resolveTemplateConfigState miss-schema business=$business normalized=${
                    normalizeBusinessName(
                        business
                    )
                } template=$templatePath"
            )
            return null
        }
        debugLog(
            "resolveTemplateConfigState business=$business normalized=${
                normalizeBusinessName(
                    business
                )
            } source=${sourcePath.ifBlank { "<builtin>" }} template=$templatePath editable=${schema.editableItemCount}"
        )
        val oneConfig = WidgetTemplateConfigRepository.buildInitialOneConfig(
            schema = schema,
            existingJson = currentOneConfigJson,
        )
        return RearWidgetTemplateConfigState(
            templateSchemaJson = WidgetTemplateConfigRepository.encodeSchema(schema),
            oneConfigJson = WidgetTemplateConfigRepository.encodeOneConfig(oneConfig),
        )
    }

    private fun resolveTemplatePreviewPath(
        business: String,
        sourcePath: String,
    ): String? {
        val normalizedBusiness = normalizeBusinessName(business)
        val deployed = normalizedBusiness.takeIf { it.isNotBlank() }
            ?.let {
                RearWidgetRuntimeStore.getBusinessFile(it)
                    ?: RearWidgetRuntimeStore.getBusinessFile(business)
            }
            ?.takeIf { File(it).exists() }
        if (deployed != null) {
            debugLog("resolveTemplatePreviewPath deployed business=$business normalized=$normalizedBusiness path=$deployed")
            return deployed
        }

        val builtin = normalizedBusiness.takeIf { it.isNotBlank() }
            ?.let(::resolveBuiltinTemplatePath)
            ?.takeIf { File(it).exists() }
        if (builtin != null) {
            debugLog("resolveTemplatePreviewPath builtin business=$business normalized=$normalizedBusiness path=$builtin")
            return builtin
        }

        if (normalizedBusiness.isNotBlank() && sourcePath.isNotBlank()) {
            deployBusinessTemplate(normalizedBusiness, sourcePath)?.takeIf { File(it).exists() }
                ?.let {
                    debugLog("resolveTemplatePreviewPath deployed-from-source business=$business normalized=$normalizedBusiness source=$sourcePath path=$it")
                    return it
                }
        }

        sourcePath.takeIf { it.isNotBlank() && File(it).exists() }?.let {
            debugLog("resolveTemplatePreviewPath source business=$business normalized=$normalizedBusiness path=$it")
            return it
        }
        debugLog("resolveTemplatePreviewPath miss business=$business normalized=$normalizedBusiness source=${sourcePath.ifBlank { "<builtin>" }}")
        return null
    }

    private fun loadTemplateImageBytes(
        templatePath: String,
        imageValue: String,
    ): ByteArray? {
        val normalized = imageValue.trim().removePrefix("file://")
        if (normalized.isBlank()) return null

        val directFile = when {
            imageValue.startsWith("file://", ignoreCase = true) -> imageValue.toUri().path
            normalized.startsWith("/") -> normalized
            else -> null
        }?.let(::File)
        if (directFile != null && directFile.isFile) {
            return runCatching { directFile.readBytes() }.getOrNull()
        }

        val templateFile = File(templatePath)
        if (!templateFile.exists()) return null
        val relativeCandidates = linkedSetOf(
            normalized.removePrefix("/"),
            imageValue.trim().removePrefix("/"),
        ).filter { it.isNotBlank() }

        if (templateFile.isDirectory) {
            relativeCandidates.forEach { candidate ->
                val child = File(templateFile, candidate)
                if (child.isFile) {
                    return runCatching { child.readBytes() }.getOrNull()
                }
            }
            return null
        }

        return runCatching {
            ZipFile(templateFile).use { zip ->
                val entry = relativeCandidates.firstNotNullOfOrNull { candidate ->
                    zip.getEntry(candidate)
                        ?: zip.entries().asSequence().firstOrNull {
                            it.name.equals(candidate, ignoreCase = true)
                        }
                } ?: return@use null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        }.getOrNull()
    }

    private fun compressPreviewBytes(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = when {
            maxDimension <= 0 -> 1
            maxDimension <= 320 -> 1
            else -> {
                var sample = 1
                while (maxDimension / sample > 320) sample *= 2
                sample
            }
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        return ByteArrayOutputStream().use { output ->
            val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            if (!ok) return null
            output.toByteArray()
        }
    }

    private fun importCardCustomImageInternal(
        cardKey: String,
        fieldName: String,
        sourceUri: String,
        displayNameHint: String,
    ): String? {
        if (cardKey.isBlank() || fieldName.isBlank() || sourceUri.isBlank()) return null
        val context = hostContext ?: return null
        val uri = runCatching { sourceUri.toUri() }.getOrNull() ?: return null
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        val extension = displayNameHint
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?: "png"

        val assetDir = resolveCardAssetDir(cardKey)
        val safeField = fieldName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        runCatching {
            if (!assetDir.exists()) assetDir.mkdirs()
            assetDir.listFiles()?.forEach { child ->
                if (child.isFile && child.name.startsWith("${safeField}_")) {
                    child.delete()
                }
            }
        }

        val target = File(assetDir, "${safeField}_${System.currentTimeMillis()}.$extension")
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            ensureReadable(target)
            target.absolutePath
        }.getOrNull()
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
            append('|').append(
                payload.getString(REAR_WIDGET_CARD_ONE_CONFIG_JSON_KEY).orEmpty().hashCode()
            )
            append('|').append(notice.options.index ?: -1)
            append('|').append(notice.options.priority ?: -1)
            append('|').append(notice.options.sticky)
        }
    }
}
