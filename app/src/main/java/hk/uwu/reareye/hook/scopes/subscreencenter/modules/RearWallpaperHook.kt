@file:Suppress("UNCHECKED_CAST")

package hk.uwu.reareye.hook.scopes.subscreencenter.modules

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.view.isEmpty
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.hook.utils.DexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveDexKitInjectionPoint
import hk.uwu.reareye.hook.utils.resolveDexKitMethodInjectionPoint
import hk.uwu.reareye.hook.utils.resolveHookPackageVersionCode
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.widgetapi.IRearWallpaperApiConnection
import hk.uwu.reareye.widgetapi.IRearWallpaperApiService
import hk.uwu.reareye.widgetapi.RearWallpaperApiContract
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleCodec
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class RearWallpaperHook : YukiBaseHooker() {

    companion object {
        private const val TAG = "REAREye-RearWallpaper"
        private const val RETRY_SWITCH_DELAY_MS = 350L
        private const val IMPORT_RES_PREFIX = "reareye_import_"
        private const val IMPORT_RES_TYPE = "REAREye"
        private const val DEFAULT_RES_SUB_TYPE = "reareye_import"
        private const val SELECTED_WALLPAPER_ID_CACHE_KEY = "REAR_WALLPAPER_SELECTED_ID"
        private const val MAX_IMPORT_BYTES = 200L * 1024L * 1024L
        private const val MAX_PREVIEW_BYTES = 20L * 1024L * 1024L
        private const val PREVIEW_CAPTURE_DELAY_MS = 700L
        private const val PREVIEW_CAPTURE_TIMEOUT_MS = 3000L
        private const val OFFSCREEN_CAPTURE_INITIAL_DELAY_MS = 450L
        private const val OFFSCREEN_CAPTURE_RETRY_INTERVAL_MS = 120L
        private const val OFFSCREEN_CAPTURE_TIMEOUT_MS = 4500L
        private const val MAIN_PANEL_SAVE_SELECTION_METHOD_CACHE_KEY =
            "SSC_MAIN_PANEL_SAVE_SELECTION_METHOD"
        private const val MAIN_PANEL_SELECT_METHOD_CACHE_KEY = "SSC_MAIN_PANEL_SELECT_METHOD"
        private const val SUBSCREEN_WIDGET_FACTORY_METHOD_CACHE_KEY = "SSC_WIDGET_FACTORY_METHOD"
        private const val PREF_STORE_CLASS_CACHE_KEY = "SSC_PREF_STORE_CLASS"
        private const val WALLPAPER_RUNTIME_LIST_METHOD_CACHE_KEY =
            "SSC_WALLPAPER_RUNTIME_LIST_METHOD"
        private const val DEVICE_CONFIG_CLASS_CACHE_KEY = "SSC_DEVICE_CONFIG_CLASS"
        private const val FALLBACK_MAIN_PANEL_CLASS = "com.xiaomi.subscreencenter.MainPanel"
        private const val FALLBACK_WIDGET_CLASS = "t2.r"
        private const val FALLBACK_PREF_STORE_CLASS = "Z1.S"
        private const val FALLBACK_WALLPAPER_RUNTIME_CLASS = "com.bumptech.glide.d"
        private const val FALLBACK_DEVICE_CONFIG_CLASS = "r2.e"

        @Volatile
        private var cachedNextSwitchAtMillis: Long = Long.MIN_VALUE

        @Volatile
        private var cachedScheduleConfig: ScheduleConfig? = null
    }

    private data class WallpaperEntry(
        val wallpaperId: Int,
        val title: String,
        val name: String,
        val description: String,
        val author: String,
        val designer: String,
        val resSubType: String,
        val imported: Boolean,
        val editable: Boolean,
        val thirdParties: Boolean,
        val supportAon: Boolean,
        val previewPath: String?,
        val previewSignature: String,
        val widget: Any,
    )

    private data class RuntimeWallpaperRecord(
        val item: JSONObject,
        val resId: String,
        val applyId: String,
        val wallpaperId: Int,
        val resLocalPath: String?,
        val metaPath: String?,
        val metaSnapshotPath: String?,
        val previewPath: String?,
        val imported: Boolean,
        val position: Int,
    )

    private data class MetadataValues(
        val titleFallback: String,
        val titleZhCn: String,
        val descriptionFallback: String,
        val descriptionZhCn: String,
        val author: String,
        val designer: String,
        val category: String,
        val resSubType: String,
        val editable: Boolean,
        val thirdParties: Boolean,
        val supportAon: Boolean,
    )

    private data class ResolvedScheduleItem(
        val wallpaperId: Int,
        val runtimeIndex: Int,
        val delayMs: Long,
    )

    private data class ScheduleConfig(
        val enabled: Boolean,
        val scheduleData: String,
    )

    private data class SwitchResult(
        val exists: Boolean,
        val applied: Boolean,
    )

    private val bootstrapReceiverRegistered = AtomicBoolean(false)
    private var hostContext: Context? = null
    private var mainPanel: Any? = null
    private var mainHandler: Handler? = null
    private var dexKitBridge: DexKitBridge? = null
    private var dexKitVersionCode: Long = 0L
    private var schedulerTask: Runnable? = null
    private val runtimeLock = Any()

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
            val bridge = DexKitBridge.create(this.appInfo.sourceDir)
            dexKitBridge = bridge
            dexKitVersionCode = resolveHookPackageVersionCode(
                context = systemContext,
                packageName = appInfo.packageName,
                sourceDir = appInfo.sourceDir,
            )
            val appRef = "com.xiaomi.subscreencenter.SubScreenCenterApp".toClass().resolve()
            val launcherRef = "com.xiaomi.subscreencenter.SubScreenLauncher".toClass().resolve()

            appRef.firstMethod {
                name = "attachBaseContext"
                parameterCount = 1
            }.hook().after {
                hostContext = (args[0] as? Context)?.applicationContext ?: (args[0] as? Context)
                registerHookBootstrapReceiver()
            }

            launcherRef.firstMethod {
                name = "onCreate"
                parameterCount = 1
            }.hook().after {
                runCatching {
                    capturePanels(instance)
                    refreshSchedule(forceApply = true)
                }.onFailure {
                    YLog.warn(it)
                }
            }
            onAppLifecycle {
                onCreate {
                    val saveSelectionPoint =
                        resolveMainPanelSaveSelectionMethod(bridge, dexKitVersionCode)

                    launcherRef.firstMethod {
                        name = "onResume"
                        parameterCount = 0
                    }.hook().after {
                        runCatching {
                            capturePanels(instance)
                            refreshSchedule(forceApply = true)
                        }.onFailure {
                            YLog.warn(it)
                        }
                    }

                    launcherRef.firstMethod {
                        name = "onPause"
                        parameterCount = 0
                    }.hook().before {
                        debugLog("launcher onPause keep scheduler nextAt=${readNextSwitchAt()}")
                    }

                    launcherRef.firstMethod {
                        name = "onDestroy"
                        parameterCount = 0
                    }.hook().before {
                        stopScheduler()
                        mainPanel = null
                        mainHandler = null
                    }

                    // Decompiled source: .tmp-ref/decompiled-jadx/sources/com/xiaomi/subscreencenter/MainPanel.java:331
                    // MainPanel.F() persists the selected index to Z1.S with key "user_select".
                    // The hook mirrors that index as a stable wallpaper id so injected imports at
                    // the top of the list do not shift the selected wallpaper on import/restart.
                    // DexKit anchor: the same method contains string "Save user select, new index = ".
                    saveSelectionPoint.className.toClass().resolve().firstMethod {
                        name = saveSelectionPoint.methodName
                        parameterCount = 0
                    }.hook().after {
                        updateSelectedWallpaperIdFromPanel(instance)
                    }
                }
            }
        }
    }

    private val hookBinder = object : IRearWallpaperApiService.Stub() {
        override fun getCatalog(): Bundle {
            enforceCallerPermission()
            return buildCatalogBundle()
        }

        override fun getPreview(wallpaperId: Int): ByteArray? {
            enforceCallerPermission()
            val entry =
                loadWallpaperEntries().firstOrNull { it.wallpaperId == wallpaperId } ?: return null
            return loadPreviewBytes(entry.previewPath)
        }

        override fun switchWallpaper(wallpaperId: Int): Boolean {
            enforceCallerPermission()
            return switchWallpaperInternal(wallpaperId).exists
        }

        override fun syncSchedule(enabled: Boolean, scheduleData: String?): Boolean {
            enforceCallerPermission()
            updateScheduleConfig(enabled, scheduleData)
            persistNextSwitchAt(0L)
            refreshSchedule(forceApply = true)
            return true
        }

        override fun importWallpaperPackage(
            packageUri: String?,
            displayNameHint: String?,
            metadataUri: String?,
            previewUri: String?,
            options: Bundle?,
        ): Bundle {
            enforceCallerPermission()
            return importWallpaperPackageInternal(
                packageUri = packageUri,
                displayNameHint = displayNameHint,
                metadataUri = metadataUri,
                previewUri = previewUri,
                options = options,
            )
        }

        override fun updateWallpaperMetadata(
            wallpaperId: Int,
            previewUri: String?,
            options: Bundle?,
        ): Bundle {
            enforceCallerPermission()
            return updateWallpaperMetadataInternal(wallpaperId, previewUri, options)
        }

        override fun generateWallpaperPreview(wallpaperId: Int): Bundle {
            enforceCallerPermission()
            return generateWallpaperPreviewInternal(wallpaperId)
        }

        override fun deleteWallpaper(wallpaperId: Int): Bundle {
            enforceCallerPermission()
            return deleteWallpaperInternal(wallpaperId)
        }
    }

    private fun resolveMainPanelSaveSelectionMethod(
        bridge: DexKitBridge,
        packageVersionCode: Long,
    ): DexKitMethodInjectionPoint {
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = MAIN_PANEL_SAVE_SELECTION_METHOD_CACHE_KEY,
            packageVersionCode = packageVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/com/xiaomi/subscreencenter/MainPanel.java:331
            // MainPanel.F() contains "Save user select, new index = " and writes "user_select".
            findMethod {
                searchPackages("com.xiaomi.subscreencenter")
                matcher {
                    paramCount(0)
                    returnType = "void"
                    usingStrings("Save user select, new index = ", "user_select")
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_MAIN_PANEL_CLASS, "F")
    }

    private val hookBootstrapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RearWallpaperApiContract.ACTION_REQUEST_HOOK_SERVICE) return
            val callbackBinder = intent
                .getBundleExtra(RearWallpaperApiContract.Extras.BUNDLE)
                ?.getBinder(RearWallpaperApiContract.Extras.BINDER)
            val callback = IRearWallpaperApiConnection.Stub.asInterface(callbackBinder)
            val forceSync =
                intent.getBooleanExtra(RearWallpaperApiContract.Extras.FORCE_SYNC, false)
            if (forceSync) {
                refreshSchedule(forceApply = true)
            }
            runCatching {
                callback?.onServiceConnected(hookBinder)
            }.onFailure(YLog::error)
        }
    }

    private fun registerHookBootstrapReceiver() {
        if (!bootstrapReceiverRegistered.compareAndSet(false, true)) return
        val ctx = hostContext ?: run {
            bootstrapReceiverRegistered.set(false)
            return
        }
        runCatching {
            ContextCompat.registerReceiver(
                ctx,
                hookBootstrapReceiver,
                IntentFilter(RearWallpaperApiContract.ACTION_REQUEST_HOOK_SERVICE),
                RearWallpaperApiContract.SERVICE_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure {
            bootstrapReceiverRegistered.set(false)
            YLog.error(it)
        }
    }

    private fun enforceCallerPermission() {
        val ctx = hostContext
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        if (ctx == null) {
            throw SecurityException("context not ready for permission check")
        }
        val granted = ctx.checkPermission(
            RearWallpaperApiContract.SERVICE_PERMISSION,
            Binder.getCallingPid(),
            uid,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw SecurityException(
                "caller uid=$uid requires ${RearWallpaperApiContract.SERVICE_PERMISSION}"
            )
        }
    }

    private fun buildCatalogBundle(): Bundle {
        val entries = loadWallpaperEntries()
        val currentIndex = readCurrentSelectionIndex(entries.lastIndex)
        val currentWallpaperId = entries.getOrNull(currentIndex)?.wallpaperId
        val itemBundles = ArrayList<Bundle>(entries.size)
        entries.forEach { entry ->
            itemBundles += Bundle().apply {
                putInt(RearWallpaperApiContract.BundleKeys.WALLPAPER_ID, entry.wallpaperId)
                putString(RearWallpaperApiContract.BundleKeys.TITLE, entry.title)
                putString(RearWallpaperApiContract.BundleKeys.NAME, entry.name)
                putString(RearWallpaperApiContract.BundleKeys.DESCRIPTION, entry.description)
                putString(RearWallpaperApiContract.BundleKeys.AUTHOR, entry.author)
                putString(RearWallpaperApiContract.BundleKeys.DESIGNER, entry.designer)
                putString(RearWallpaperApiContract.BundleKeys.RES_SUB_TYPE, entry.resSubType)
                putBoolean(RearWallpaperApiContract.BundleKeys.IMPORTED, entry.imported)
                putBoolean(RearWallpaperApiContract.BundleKeys.CAN_EDIT_METADATA, entry.imported)
                putBoolean(RearWallpaperApiContract.BundleKeys.CAN_DELETE, entry.imported)
                putBoolean(RearWallpaperApiContract.BundleKeys.EDITABLE, entry.editable)
                putBoolean(RearWallpaperApiContract.BundleKeys.THIRD_PARTIES, entry.thirdParties)
                putBoolean(RearWallpaperApiContract.BundleKeys.SUPPORT_AON, entry.supportAon)
                putBoolean(
                    RearWallpaperApiContract.BundleKeys.PREVIEW_AVAILABLE,
                    !entry.previewPath.isNullOrBlank(),
                )
                putString(
                    RearWallpaperApiContract.BundleKeys.PREVIEW_SIGNATURE,
                    entry.previewSignature
                )
            }
        }
        return Bundle().apply {
            putParcelableArrayList(RearWallpaperApiContract.BundleKeys.ITEMS, itemBundles)
            putInt(RearWallpaperApiContract.BundleKeys.CURRENT_INDEX, currentIndex)
            if (currentWallpaperId != null) {
                putInt(RearWallpaperApiContract.BundleKeys.CURRENT_WALLPAPER_ID, currentWallpaperId)
            }
        }
    }

    private fun capturePanels(launcherInstance: Any?) {
        val resolver = launcherInstance?.asResolver() ?: return
        mainPanel = runCatching {
            resolver.firstField { name = "y" }.get()
        }.getOrNull()
        mainHandler = runCatching {
            resolver.firstField { name = "c0" }.get() as? Handler
        }.getOrNull()
    }

    private fun refreshSchedule(forceApply: Boolean) {
        stopScheduler()
        val scheduleConfig = readScheduleConfig()
        if (!scheduleConfig.enabled) {
            persistNextSwitchAt(0L)
            debugLog("refreshSchedule disabled force=$forceApply")
            return
        }

        val entries = loadWallpaperEntries()
        if (entries.isEmpty()) {
            persistNextSwitchAt(0L)
            debugLog("refreshSchedule skipped: no wallpaper entries force=$forceApply")
            return
        }

        val resolvedSchedule = loadResolvedSchedule(entries, scheduleConfig)
        if (resolvedSchedule.isEmpty()) {
            persistNextSwitchAt(0L)
            debugLog("refreshSchedule skipped: resolved schedule empty force=$forceApply")
            return
        }

        val now = System.currentTimeMillis()
        val currentIndex = readCurrentSelectionIndex(entries.lastIndex)
        val currentId = entries.getOrNull(currentIndex)?.wallpaperId
        val currentPos = resolvedSchedule.indexOfFirst { it.wallpaperId == currentId }
        val nextAt = readNextSwitchAt()
        debugLog(
            "refreshSchedule force=$forceApply currentIndex=$currentIndex currentId=$currentId currentPos=$currentPos nextAt=$nextAt schedule=${resolvedSchedule.joinToString { "${it.wallpaperId}:${it.delayMs}" }}"
        )

        if (currentPos < 0) {
            val first = resolvedSchedule.first()
            val result = switchToResolved(first, entries)
            val nextAt = now + if (result.applied) first.delayMs else RETRY_SWITCH_DELAY_MS
            persistNextSwitchAt(nextAt)
            debugLog("refreshSchedule no current match -> switch first=${first.wallpaperId} applied=${result.applied} nextAt=$nextAt")
            scheduleAt(nextAt)
            return
        }

        val currentItem = resolvedSchedule[currentPos]
        if (nextAt <= 0L) {
            val dueAt = now + currentItem.delayMs
            persistNextSwitchAt(dueAt)
            debugLog("refreshSchedule initialized nextAt=$dueAt current=${currentItem.wallpaperId} delay=${currentItem.delayMs}")
            scheduleAt(dueAt)
            return
        }

        if (nextAt <= now) {
            val nextPos = (currentPos + 1).floorMod(resolvedSchedule.size)
            val result = switchToResolved(resolvedSchedule[nextPos], entries)
            val dueAt = now + if (result.applied) {
                resolvedSchedule[nextPos].delayMs
            } else {
                RETRY_SWITCH_DELAY_MS
            }
            persistNextSwitchAt(dueAt)
            debugLog("refreshSchedule due -> switch next=${resolvedSchedule[nextPos].wallpaperId} applied=${result.applied} dueAt=$dueAt")
            scheduleAt(dueAt)
            return
        }

        if (forceApply) {
            debugLog("refreshSchedule force keep existing nextAt=$nextAt")
            scheduleAt(nextAt)
            return
        }

        debugLog("refreshSchedule waiting nextAt=$nextAt delay=${nextAt - now}")
        scheduleAt(nextAt)
    }

    private fun scheduleAt(triggerAt: Long) {
        stopScheduler()
        val handler = mainHandler ?: return
        val delayMs = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
        debugLog("scheduleAt triggerAt=$triggerAt delayMs=$delayMs")
        schedulerTask = Runnable {
            debugLog("scheduleAt fired triggerAt=$triggerAt now=${System.currentTimeMillis()}")
            refreshSchedule(forceApply = false)
        }
        handler.postDelayed(schedulerTask!!, delayMs)
    }

    private fun stopScheduler() {
        schedulerTask?.let { task -> mainHandler?.removeCallbacks(task) }
        if (schedulerTask != null) debugLog("stopScheduler removed pending task")
        schedulerTask = null
    }

    private fun loadResolvedSchedule(
        entries: List<WallpaperEntry> = loadWallpaperEntries(),
        scheduleConfig: ScheduleConfig = readScheduleConfig(),
    ): List<ResolvedScheduleItem> {
        val byId = entries.associateBy { it.wallpaperId }
        return RearWallpaperScheduleCodec.parse(
            scheduleConfig.scheduleData
        ).mapNotNull { item ->
            val entry = byId[item.wallpaperId] ?: return@mapNotNull null
            ResolvedScheduleItem(
                wallpaperId = item.wallpaperId,
                runtimeIndex = entries.indexOf(entry),
                delayMs = item.delayMs,
            )
        }
    }

    private fun switchWallpaperInternal(wallpaperId: Int): SwitchResult {
        val entries = loadWallpaperEntries()
        val target = entries.firstOrNull { it.wallpaperId == wallpaperId }
            ?: return SwitchResult(exists = false, applied = false)
        val result = switchToResolved(
            item = ResolvedScheduleItem(
                wallpaperId = wallpaperId,
                runtimeIndex = entries.indexOf(target),
                delayMs = RearWallpaperScheduleCodec.DEFAULT_DELAY_MS,
            ),
            entries = entries,
        )
        resetNextSwitchAtForCurrent(wallpaperId, entries)
        debugLog("switchWallpaperInternal wallpaperId=$wallpaperId exists=true applied=${result.applied}")
        return result
    }

    private fun switchToResolved(
        item: ResolvedScheduleItem,
        entries: List<WallpaperEntry>
    ): SwitchResult {
        if (entries.isEmpty()) return SwitchResult(exists = false, applied = false)
        if (isMainPanelEditing()) {
            debugLog("switchToResolved blocked by editMode wallpaperId=${item.wallpaperId}")
            return SwitchResult(exists = true, applied = false)
        }

        val targetIndex = item.runtimeIndex.coerceIn(0, entries.lastIndex)
        persistSelectionIndex(targetIndex)
        persistSelectedWallpaperId(item.wallpaperId)
        debugLog("switchToResolved wallpaperId=${item.wallpaperId} runtimeIndex=${item.runtimeIndex} targetIndex=$targetIndex")

        val widgets = entries.map { it.widget }
        var applied = false
        mainPanel?.let { panel ->
            applied = dispatchSelection(panel, widgets, targetIndex) || false
        }
        debugLog("switchToResolved result wallpaperId=${item.wallpaperId} applied=$applied main=${mainPanel != null}")
        return SwitchResult(exists = true, applied = applied)
    }

    private fun isMainPanelEditing(): Boolean {
        val panel = mainPanel ?: return false
        return runCatching {
            panel.asResolver().firstField { name = "m" }.get() as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun dispatchSelection(panel: Any, widgets: List<Any>, index: Int): Boolean {
        val action = Runnable {
            runCatching {
                val selectPoint = resolveMainPanelSelectMethod()
                panel.asResolver().firstMethod {
                    name = selectPoint.methodName
                    parameterCount = 2
                }.invoke(widgets, index)
                debugLog("dispatchSelection success panel=${panel.javaClass.name} index=$index widgets=${widgets.size}")
            }.onFailure(YLog::error)
        }
        val handler = mainHandler
        return if (handler != null) {
            handler.post(action)
        } else {
            action.run()
            true
        }
    }

    private fun updateSelectedWallpaperIdFromPanel(panel: Any?) {
        val resolver = panel?.asResolver() ?: return
        runCatching {
            val index = resolver.firstField { name = "l" }.get() as? Int ?: return
            val specs = resolver.firstField { name = "i" }.get() as? List<*> ?: return
            val selectedId = specs.getOrNull(index)?.wallpaperSpecId() ?: return
            persistSelectedWallpaperId(selectedId)
            debugLog("updateSelectedWallpaperIdFromPanel index=$index wallpaperId=$selectedId")
        }.onFailure(YLog::warn)
    }

    private fun loadWallpaperEntries(): List<WallpaperEntry> {
        val specList = loadWallpaperSpecs()
        if (specList.isEmpty()) return emptyList()

        val localeSuffix = readLocalePreviewSuffix()
        val runtimeRecords = readRuntimeRecords().associateBy { it.wallpaperId }
        return buildList {
            specList.forEach { spec ->
                val widget = createWallpaperWidget(spec) ?: return@forEach

                val resolver = spec.asResolver()
                val extras = runCatching {
                    resolver.firstField { name = "d" }.get() as? Bundle
                }.getOrNull()
                val previewPath = extras.resolvePreviewPath(localeSuffix)
                val wallpaperId = runCatching {
                    resolver.firstField { name = "a" }.get() as Int
                }.getOrDefault(0)
                val runtimeRecord = runtimeRecords[wallpaperId]
                val metadata = runtimeRecord?.readMetadataValues()
                add(
                    WallpaperEntry(
                        wallpaperId = wallpaperId,
                        title = metadata?.category
                            ?: extras?.getString("title").orEmpty().ifBlank { "Wallpaper" },
                        name = metadata?.preferredTitle()
                            ?: extras?.getString("resName").orEmpty().ifBlank { "unknown" },
                        description = metadata?.preferredDescription().orEmpty(),
                        author = metadata?.author.orEmpty(),
                        designer = metadata?.designer.orEmpty(),
                        resSubType = metadata?.resSubType.orEmpty(),
                        imported = runtimeRecord?.imported ?: false,
                        editable = metadata?.editable ?: false,
                        thirdParties = metadata?.thirdParties ?: false,
                        supportAon = metadata?.supportAon ?: false,
                        previewPath = previewPath,
                        previewSignature = buildPreviewSignature(previewPath),
                        widget = widget,
                    )
                )
            }
        }
    }

    private fun createWallpaperWidget(spec: Any): Any? {
        return runCatching {
            val factoryPoint = resolveWidgetFactoryMethod()
            factoryPoint.className.toClass().resolve().firstMethod {
                name = factoryPoint.methodName
                parameterCount = 1
            }.invoke(spec)
        }.onFailure(YLog::warn).getOrNull()
    }

    private fun resolveWidgetFactoryMethod(): DexKitMethodInjectionPoint {
        val bridge = dexKitBridge ?: return DexKitMethodInjectionPoint(FALLBACK_WIDGET_CLASS, "g")
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = SUBSCREEN_WIDGET_FACTORY_METHOD_CACHE_KEY,
            packageVersionCode = dexKitVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/t2/r.java:44
            // t2.r.g(m2.a) creates a widget wrapper and copies snapshotPath metadata.
            findMethod {
                searchPackages("t2")
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramCount(1)
                    usingStrings("snapshotPath_", "snapshotPath")
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_WIDGET_CLASS, "g")
    }

    private fun resolveMainPanelSelectMethod(): DexKitMethodInjectionPoint {
        val bridge =
            dexKitBridge ?: return DexKitMethodInjectionPoint(FALLBACK_MAIN_PANEL_CLASS, "d")
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = MAIN_PANEL_SELECT_METHOD_CACHE_KEY,
            packageVersionCode = dexKitVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/com/xiaomi/subscreencenter/MainPanel.java:356
            // MainPanel.d(List, int) logs "onSubScreenWidgetChanged" and applies a new widget list.
            findMethod {
                searchPackages("com.xiaomi.subscreencenter")
                matcher {
                    paramCount(2)
                    returnType = "void"
                    usingStrings(
                        "SubScreenWidgets is empty, at least one needs to be provided !!!",
                        "onSubScreenWidgetChanged, new widgets size = ",
                    )
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_MAIN_PANEL_CLASS, "d")
    }

    private fun resolvePrefStore(): Any? {
        return runCatching {
            resolvePrefStoreClass().toClass().resolve().firstField { name = "a" }.get()
        }.getOrNull()
    }

    private fun resolvePrefStoreClass(): String {
        val bridge = dexKitBridge ?: return FALLBACK_PREF_STORE_CLASS
        val nativePrefs = prefs.native()
        return resolveDexKitInjectionPoint(
            bridge = bridge,
            cacheKey = PREF_STORE_CLASS_CACHE_KEY,
            packageVersionCode = dexKitVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/com/xiaomi/subscreencenter/MainPanel.java:331
            // MainPanel.F() calls the static preference store field used as Z1.S.a in jadx.
            findMethod {
                searchPackages("com.xiaomi.subscreencenter")
                matcher {
                    paramCount(0)
                    usingStrings("Save user select, new index = ", "user_select")
                }
            }.singleOrNull()
                ?.usingFields
                ?.firstOrNull { field -> field.field.typeName.endsWith(".d") || field.field.name == "a" }
                ?.field
                ?.className
        } ?: FALLBACK_PREF_STORE_CLASS
    }

    private fun resolveWallpaperRuntimeListMethod(): DexKitMethodInjectionPoint {
        val bridge =
            dexKitBridge ?: return DexKitMethodInjectionPoint(FALLBACK_WALLPAPER_RUNTIME_CLASS, "G")
        val nativePrefs = prefs.native()
        return resolveDexKitMethodInjectionPoint(
            bridge = bridge,
            cacheKey = WALLPAPER_RUNTIME_LIST_METHOD_CACHE_KEY,
            packageVersionCode = dexKitVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/com/bumptech/glide/d.java:105
            // com.bumptech.glide.d.G(boolean) reads rearScreen/runtime.json and returns m2.a specs.
            findMethod {
                matcher {
                    paramCount(1)
                    returnType = "java.util.List"
                    usingStrings(
                        "/data/system/theme_magic/users/\$user_id/rearScreen/runtime.json",
                        "/system/media/rearscreen/template/default/rearScreen.json",
                    )
                }
            }.singleOrNull()?.let { DexKitMethodInjectionPoint(it.className, it.name) }
        } ?: DexKitMethodInjectionPoint(FALLBACK_WALLPAPER_RUNTIME_CLASS, "G")
    }

    private fun resolveDeviceConfigClass(): String {
        val bridge = dexKitBridge ?: return FALLBACK_DEVICE_CONFIG_CLASS
        val nativePrefs = prefs.native()
        return resolveDexKitInjectionPoint(
            bridge = bridge,
            cacheKey = DEVICE_CONFIG_CLASS_CACHE_KEY,
            packageVersionCode = dexKitVersionCode,
            readCache = nativePrefs::getString,
            writeCache = { key, value -> nativePrefs.edit().putString(key, value).apply() },
        ) {
            // DexKit source anchor:
            // .tmp-ref/decompiled-jadx/sources/r2/e.java:20
            // r2.e owns rear-screen Point k and color flag m = SystemProperties("vendor.wallpaper.color.flag").
            findClass {
                searchPackages("r2")
                matcher {
                    usingStrings("vendor.wallpaper.color.flag")
                }
            }.singleOrNull()?.name
        } ?: FALLBACK_DEVICE_CONFIG_CLASS
    }

    private fun loadWallpaperSpecs(): List<Any> {
        val prefStore = resolvePrefStore()

        val persisted = runCatching {
            prefStore?.asResolver()?.firstMethod {
                name = "e"
                parameterCount = 1
            }?.invoke(false) as? List<Any>
        }.getOrNull().orEmpty()

        val runtime = runCatching {
            val runtimeListPoint = resolveWallpaperRuntimeListMethod()
            runtimeListPoint.className.toClass().resolve().firstMethod {
                name = runtimeListPoint.methodName
                parameterCount = 1
            }.invoke(true) as? List<Any>
        }.getOrNull().orEmpty()

        if (persisted.isEmpty()) {
            normalizeSelectedWallpaperIndex(
                merged = runtime,
                persisted = emptyList(),
                runtime = runtime,
            )
            return runtime
        }
        if (runtime.isEmpty()) {
            normalizeSelectedWallpaperIndex(
                merged = persisted,
                persisted = persisted,
                runtime = emptyList(),
            )
            return persisted
        }

        val runtimeById = runtime.mapNotNull { spec ->
            spec.wallpaperSpecId()?.let { id -> id to spec }
        }.toMap()
        val importedRuntimeIds = readRuntimeRecords()
            .asSequence()
            .filter { it.imported }
            .sortedByDescending { it.position }
            .map { it.wallpaperId }
            .toList()

        val seenIds = HashSet<Int>()
        val merged = buildList {
            importedRuntimeIds.forEach { id ->
                val spec = runtimeById[id] ?: return@forEach
                if (seenIds.add(id)) add(spec)
            }
            persisted.forEach { spec ->
                val id = spec.wallpaperSpecId() ?: return@forEach
                if (seenIds.add(id)) add(spec)
            }
            runtime.forEach { spec ->
                val id = spec.wallpaperSpecId() ?: return@forEach
                if (seenIds.add(id)) add(spec)
            }
        }
        normalizeSelectedWallpaperIndex(
            merged = merged,
            persisted = persisted,
            runtime = runtime,
        )
        return merged
    }

    private fun normalizeSelectedWallpaperIndex(
        merged: List<Any>,
        persisted: List<Any>,
        runtime: List<Any>,
    ) {
        if (merged.isEmpty()) return
        val rawIndex = readRawSelectionIndex()
        val cachedSelectedId = readSelectedWallpaperId()
        val selectedId = cachedSelectedId
            ?: persisted.getOrNull(rawIndex)?.wallpaperSpecId()
            ?: runtime.getOrNull(rawIndex)?.wallpaperSpecId()
            ?: merged.getOrNull(rawIndex.coerceIn(0, merged.lastIndex))?.wallpaperSpecId()
            ?: return
        val normalizedIndex = merged.indexOfFirst { it.wallpaperSpecId() == selectedId }
        if (normalizedIndex < 0) {
            if (cachedSelectedId == selectedId) clearSelectedWallpaperId()
            return
        }
        if (normalizedIndex != rawIndex.coerceIn(0, merged.lastIndex)) {
            persistSelectionIndex(normalizedIndex)
            debugLog("normalizeSelectedWallpaperIndex raw=$rawIndex normalized=$normalizedIndex wallpaperId=$selectedId")
        }
        persistSelectedWallpaperId(selectedId)
    }

    private fun readRawSelectionIndex(): Int {
        return runCatching {
            val store = resolvePrefStore() ?: return@runCatching 0
            store.asResolver().firstMethod {
                name = "c"
                parameterCount = 3
            }.invoke(Int::class.javaPrimitiveType!!, 0, "user_select") as? Int ?: 0
        }.getOrDefault(0)
    }

    private fun readCurrentSelectionIndex(maxIndex: Int): Int {
        val index = readRawSelectionIndex()
        if (maxIndex < 0) return -1
        return index.coerceIn(0, maxIndex)
    }

    private fun persistSelectionIndex(index: Int) {
        runCatching {
            val store = resolvePrefStore() ?: return
            store.asResolver().firstMethod {
                name = "j"
                parameterCount = 2
            }.invoke(index, "user_select")
        }.onFailure(YLog::error)
    }

    private fun readSelectedWallpaperId(): Int? {
        val value = prefs.native().getInt(SELECTED_WALLPAPER_ID_CACHE_KEY, Int.MIN_VALUE)
        return value.takeIf { it != Int.MIN_VALUE }
    }

    private fun persistSelectedWallpaperId(wallpaperId: Int) {
        prefs.native().edit {
            putInt(SELECTED_WALLPAPER_ID_CACHE_KEY, wallpaperId)
            apply()
        }
    }

    private fun clearSelectedWallpaperId() {
        prefs.native().edit {
            remove(SELECTED_WALLPAPER_ID_CACHE_KEY)
            apply()
        }
    }

    private fun resetNextSwitchAtForCurrent(wallpaperId: Int, entries: List<WallpaperEntry>) {
        if (!readScheduleConfig().enabled) {
            persistNextSwitchAt(0L)
            debugLog("resetNextSwitchAtForCurrent disabled wallpaperId=$wallpaperId")
            return
        }
        val resolved = loadResolvedSchedule(entries)
        val current = resolved.firstOrNull { it.wallpaperId == wallpaperId }
        if (current == null) {
            persistNextSwitchAt(0L)
            debugLog("resetNextSwitchAtForCurrent missing wallpaperId=$wallpaperId")
            return
        }
        val nextAt = System.currentTimeMillis() + current.delayMs
        persistNextSwitchAt(nextAt)
        debugLog("resetNextSwitchAtForCurrent wallpaperId=$wallpaperId nextAt=$nextAt delay=${current.delayMs}")
        scheduleAt(nextAt)
    }

    private fun readNextSwitchAt(): Long {
        val cached = cachedNextSwitchAtMillis
        if (cached != Long.MIN_VALUE) return cached
        val persisted = prefs.native().getLong(ConfigKeys.REAR_WALLPAPER_SCHEDULE_NEXT_AT, 0L)
        cachedNextSwitchAtMillis = persisted
        return persisted
    }

    private fun persistNextSwitchAt(timestamp: Long) {
        cachedNextSwitchAtMillis = timestamp
        prefs.native().edit {
            putLong(ConfigKeys.REAR_WALLPAPER_SCHEDULE_NEXT_AT, timestamp)
            apply()
        }
        debugLog("persistNextSwitchAt=$timestamp")
    }

    private fun readScheduleConfig(): ScheduleConfig {
        cachedScheduleConfig?.let { return it }
        val config = ScheduleConfig(
            enabled = prefs.getBoolean(ConfigKeys.REAR_WALLPAPER_SCHEDULE_ENABLED, false),
            scheduleData = prefs.getString(
                ConfigKeys.REAR_WALLPAPER_SCHEDULE_DATA,
                RearWallpaperScheduleCodec.EMPTY_ARRAY,
            ).ifBlank { RearWallpaperScheduleCodec.EMPTY_ARRAY },
        )
        cachedScheduleConfig = config
        return config
    }

    private fun updateScheduleConfig(enabled: Boolean, scheduleData: String?) {
        cachedScheduleConfig = ScheduleConfig(
            enabled = enabled,
            scheduleData = scheduleData?.takeIf { it.isNotBlank() }
                ?: RearWallpaperScheduleCodec.EMPTY_ARRAY,
        )
    }

    private fun importWallpaperPackageInternal(
        packageUri: String?,
        displayNameHint: String?,
        metadataUri: String?,
        previewUri: String?,
        options: Bundle?,
    ): Bundle {
        val context =
            hostContext ?: return operationResult(false, error = "host context is not ready")
        val sourceUri = packageUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return operationResult(false, error = "package uri is empty")
        val sourceName = displayNameHint?.takeIf { it.isNotBlank() } ?: "wallpaper.mrc"
        if (!sourceName.endsWith(".mrc", ignoreCase = true) &&
            !sourceName.endsWith(".zip", ignoreCase = true)
        ) {
            return operationResult(false, error = "only .mrc or .zip packages are supported")
        }

        return runCatching {
            synchronized(runtimeLock) {
                val now = System.currentTimeMillis()
                val resId = "$IMPORT_RES_PREFIX${now}_${UUID.randomUUID().shortId()}"
                val applyId = UUID.randomUUID().shortId()
                val targetDir = File(resolveRuntimeRoot(), "${resId}_${applyId}")
                val packageFile = File(targetDir, "rearscreen_${resId}_${applyId}.mrc")
                val metadataFile = File(targetDir, "$resId.mrm")

                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    return@synchronized operationResult(
                        false,
                        error = "failed to create runtime dir"
                    )
                }

                val packageSize = copyUriToFileLimited(context, sourceUri, packageFile)
                validateMamlPackage(packageFile)
                val extractedPreviewPath = extractPreviewFromPackage(packageFile, targetDir)
                val previewPath = previewUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Uri::parse)
                    ?.let { copyPreviewImageFromUri(context, it, targetDir, "preview_imported") }
                    ?: extractedPreviewPath

                val providedMetadata = metadataUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Uri::parse)
                    ?.let { readJsonFromUri(context, it) }
                val packageMetadata = readDescriptionMetadata(packageFile)
                val metadataValues = resolveMetadataValues(
                    options = options,
                    source = providedMetadata?.toMetadataValues(sourceName) ?: packageMetadata,
                    displayNameHint = sourceName,
                )
                val metadataJson = buildMetadataJson(
                    base = providedMetadata,
                    resId = resId,
                    packageFile = packageFile,
                    metadataFile = metadataFile,
                    previewPath = previewPath,
                    packageSize = packageSize,
                    updatedAt = now,
                    values = metadataValues,
                )
                writeTextAtomically(metadataFile, metadataJson.toString(2))

                val runtimeArray = readRuntimeArray()
                val position = maxRuntimePosition(runtimeArray) + 1
                val item = buildRuntimeItem(
                    resId = resId,
                    applyId = applyId,
                    packagePath = packageFile.absolutePath,
                    metadataPath = metadataFile.absolutePath,
                    previewPath = previewPath,
                    position = position,
                    updatedAt = now,
                    values = metadataValues,
                )
                runtimeArray.put(item)
                writeRuntimeArray(runtimeArray)
                ensureReadableRecursive(targetDir)
                refreshRuntimePanels()
                operationResult(true, wallpaperId = (resId + applyId).hashCode())
            }
        }.getOrElse {
            YLog.error(it)
            operationResult(false, error = it.message ?: "import failed")
        }
    }

    private fun updateWallpaperMetadataInternal(
        wallpaperId: Int,
        previewUri: String?,
        options: Bundle?,
    ): Bundle {
        return runCatching {
            synchronized(runtimeLock) {
                val context = hostContext ?: return@synchronized operationResult(
                    false,
                    error = "host context is not ready",
                )
                val runtimeArray = readRuntimeArray()
                val index = findRuntimeItemIndex(runtimeArray, wallpaperId)
                if (index < 0) {
                    return@synchronized operationResult(
                        false,
                        error = "wallpaper is not in runtime list"
                    )
                }
                val item = runtimeArray.getJSONObject(index)
                val record = item.toRuntimeRecord() ?: return@synchronized operationResult(
                    false,
                    error = "runtime item is invalid",
                )
                if (!record.imported) {
                    return@synchronized operationResult(
                        false,
                        error = "only REAREye imported wallpapers can be edited",
                    )
                }

                val packageFile = record.resLocalPath?.let(::File)
                    ?: return@synchronized operationResult(false, error = "package path is missing")
                val metadataFile = record.metaPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?: File(packageFile.parentFile, "${record.resId}.mrm")
                val previewPath = previewUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Uri::parse)
                    ?.let {
                        copyPreviewImageFromUri(
                            context = context,
                            uri = it,
                            targetDir = metadataFile.parentFile ?: packageFile.parentFile,
                            fileNamePrefix = "preview_imported_${System.currentTimeMillis()}",
                        )
                    }
                    ?: record.previewPath
                val sourceMetadata = readJsonFile(metadataFile)
                val currentValues = record.readMetadataValues()
                val values = resolveMetadataValues(
                    options = options,
                    source = currentValues,
                    displayNameHint = record.resId,
                )
                val metadataJson = buildMetadataJson(
                    base = sourceMetadata,
                    resId = record.resId,
                    packageFile = packageFile,
                    metadataFile = metadataFile,
                    previewPath = previewPath,
                    packageSize = packageFile.length(),
                    updatedAt = System.currentTimeMillis(),
                    values = values,
                )
                writeTextAtomically(metadataFile, metadataJson.toString(2))
                applyMetadataToRuntimeItem(
                    item = item,
                    metadataPath = metadataFile.absolutePath,
                    previewPath = previewPath,
                    values = values,
                )
                writeRuntimeArray(runtimeArray)
                ensureReadableRecursive(metadataFile.parentFile ?: metadataFile)
                refreshRuntimePanels()
                operationResult(true, wallpaperId = wallpaperId)
            }
        }.getOrElse {
            YLog.error(it)
            operationResult(false, error = it.message ?: "metadata update failed")
        }
    }

    private fun generateWallpaperPreviewInternal(wallpaperId: Int): Bundle {
        return runCatching {
            synchronized(runtimeLock) {
                val runtimeArray = readRuntimeArray()
                val index = findRuntimeItemIndex(runtimeArray, wallpaperId)
                if (index < 0) {
                    return@synchronized operationResult(
                        false,
                        error = "wallpaper is not in runtime list"
                    )
                }
                val item = runtimeArray.getJSONObject(index)
                val record = item.toRuntimeRecord() ?: return@synchronized operationResult(
                    false,
                    error = "runtime item is invalid",
                )
                if (!record.imported) {
                    return@synchronized operationResult(
                        false,
                        error = "only REAREye imported wallpapers can generate previews",
                    )
                }

                val packageFile = record.resLocalPath?.let(::File)
                    ?: return@synchronized operationResult(false, error = "package path is missing")
                val metadataFile = record.metaPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?: File(packageFile.parentFile, "${record.resId}.mrm")
                val previewFile = File(
                    metadataFile.parentFile ?: packageFile.parentFile,
                    "preview_generated_${System.currentTimeMillis()}.jpg",
                )
                val previewPath = captureWallpaperPreviewToFile(wallpaperId, previewFile)
                val sourceMetadata = readJsonFile(metadataFile)
                val values = record.readMetadataValues()
                val metadataJson = buildMetadataJson(
                    base = sourceMetadata,
                    resId = record.resId,
                    packageFile = packageFile,
                    metadataFile = metadataFile,
                    previewPath = previewPath,
                    packageSize = packageFile.length(),
                    updatedAt = System.currentTimeMillis(),
                    values = values,
                )
                writeTextAtomically(metadataFile, metadataJson.toString(2))
                applyMetadataToRuntimeItem(
                    item = item,
                    metadataPath = metadataFile.absolutePath,
                    previewPath = previewPath,
                    values = values,
                )
                writeRuntimeArray(runtimeArray)
                ensureReadableRecursive(metadataFile.parentFile ?: metadataFile)
                refreshRuntimePanels()
                operationResult(true, wallpaperId = wallpaperId)
            }
        }.getOrElse {
            YLog.error(it)
            operationResult(false, error = it.message ?: "preview generation failed")
        }
    }

    private fun deleteWallpaperInternal(wallpaperId: Int): Bundle {
        return runCatching {
            synchronized(runtimeLock) {
                val runtimeArray = readRuntimeArray()
                val index = findRuntimeItemIndex(runtimeArray, wallpaperId)
                if (index < 0) {
                    return@synchronized operationResult(
                        false,
                        error = "wallpaper is not in runtime list"
                    )
                }
                val item = runtimeArray.getJSONObject(index)
                val record = item.toRuntimeRecord() ?: return@synchronized operationResult(
                    false,
                    error = "runtime item is invalid",
                )
                if (!record.imported) {
                    return@synchronized operationResult(
                        false,
                        error = "only REAREye imported wallpapers can be deleted",
                    )
                }

                val nextArray = JSONArray()
                for (i in 0 until runtimeArray.length()) {
                    if (i != index) nextArray.put(runtimeArray.getJSONObject(i))
                }
                writeRuntimeArray(nextArray)
                deleteImportedFiles(record)
                refreshRuntimePanels()
                operationResult(true, wallpaperId = wallpaperId)
            }
        }.getOrElse {
            YLog.error(it)
            operationResult(false, error = it.message ?: "delete failed")
        }
    }

    private fun operationResult(
        success: Boolean,
        error: String? = null,
        wallpaperId: Int? = null,
    ): Bundle {
        return Bundle().apply {
            putBoolean(RearWallpaperApiContract.BundleKeys.SUCCESS, success)
            if (error != null) putString(RearWallpaperApiContract.BundleKeys.ERROR, error)
            if (wallpaperId != null) putInt(
                RearWallpaperApiContract.BundleKeys.WALLPAPER_ID,
                wallpaperId
            )
        }
    }

    private fun copyUriToFileLimited(context: Context, uri: Uri, target: File): Long {
        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read.toLong()
                    if (total > MAX_IMPORT_BYTES) {
                        throw IllegalArgumentException("package is larger than ${MAX_IMPORT_BYTES / 1024 / 1024} MB")
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalArgumentException("failed to open package uri")

        if (target.exists()) target.delete()
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        return total
    }

    private fun copyPreviewImageFromUri(
        context: Context,
        uri: Uri,
        targetDir: File,
        fileNamePrefix: String,
    ): String {
        targetDir.mkdirs()
        val tempFile = File(targetDir, "$fileNamePrefix.source")
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read.toLong()
                    if (total > MAX_PREVIEW_BYTES) {
                        throw IllegalArgumentException("preview image is larger than ${MAX_PREVIEW_BYTES / 1024 / 1024} MB")
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalArgumentException("failed to open preview image uri")

        val previewBytes = loadPreviewBytes(tempFile.absolutePath)
            ?: throw IllegalArgumentException("preview image is invalid")
        val target = File(targetDir, "$fileNamePrefix.jpg")
        writeBytesAtomically(target, previewBytes)
        tempFile.delete()
        ensureReadable(target)
        return target.absolutePath
    }

    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.outputStream().use { output -> output.write(bytes) }
        if (target.exists()) target.delete()
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        ensureReadable(target)
    }

    private fun captureWallpaperPreviewToFile(wallpaperId: Int, targetFile: File): String {
        return runCatching {
            captureWallpaperPreviewOffscreenToFile(wallpaperId, targetFile)
        }.onFailure {
            debugLog("offscreen preview capture failed wallpaperId=$wallpaperId err=${it.message}")
        }.getOrElse {
            captureWallpaperPreviewBySwitchToFile(wallpaperId, targetFile)
        }
    }

    private fun captureWallpaperPreviewOffscreenToFile(wallpaperId: Int, targetFile: File): String {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("preview capture must not run on the main thread")
        }
        val context = hostContext ?: throw IllegalStateException("host context is not ready")
        val panel = mainPanel as? View ?: throw IllegalStateException("main panel is not ready")
        val handler = mainHandler ?: throw IllegalStateException("main handler is not ready")
        val entry = loadWallpaperEntries().firstOrNull { it.wallpaperId == wallpaperId }
            ?: throw IllegalArgumentException("wallpaper is not in current list")
        val bitmapRef = AtomicReference<Bitmap?>()
        val errorRef = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)

        handler.post {
            runCatching {
                // Decompiled source: .tmp-ref/decompiled-jadx/sources/com/xiaomi/subscreencenter/MainPanel.java:410
                // MainPanel.q(int, int, boolean, int, Runnable) clones r.g(spec), assigns r.p,
                // calls r.z(Context), r.y(aod), and r.D() without doing extra parsing.
                // This offscreen path follows that creation chain but never calls MainPanel.d
                // and never writes "user_select", so it does not change the selected wallpaper.
                val size = resolvePreviewRenderSize(panel)
                val renderHost = FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(size.x, size.y)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isClickable = false
                    isFocusable = false
                    translationX = -(size.x * 3f)
                    translationY = 0f
                }
                val parent = (panel.parent as? ViewGroup)
                    ?: (panel as? ViewGroup)
                    ?: throw IllegalStateException("main panel parent is not a ViewGroup")
                val targetWidget = cloneWallpaperWidgetForPreview(entry.widget)
                var cleaned = false

                fun cleanup() {
                    if (cleaned) return
                    cleaned = true
                    runCatching {
                        targetWidget.asResolver().firstMethod {
                            name = "A"
                            parameterCount = 0
                        }.invoke()
                    }.onFailure(YLog::warn)
                    runCatching {
                        (renderHost.parent as? ViewGroup)?.removeView(renderHost)
                    }.onFailure(YLog::warn)
                }

                fun finishWithBitmap(bitmap: Bitmap) {
                    bitmapRef.set(bitmap)
                    cleanup()
                    latch.countDown()
                }

                fun finishWithError(error: Throwable) {
                    errorRef.set(error)
                    cleanup()
                    latch.countDown()
                }

                runCatching {
                    parent.addView(renderHost)
                    renderHost.measure(
                        View.MeasureSpec.makeMeasureSpec(size.x, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(size.y, View.MeasureSpec.EXACTLY),
                    )
                    renderHost.layout(0, 0, size.x, size.y)

                    val targetResolver = targetWidget.asResolver()
                    targetResolver.firstField { name = "p" }.set(renderHost)
                    targetResolver.firstMethod {
                        name = "B"
                        parameterCount = 1
                    }.invoke(false)
                    targetResolver.firstField { name = "m" }.set(true)

                    // Decompiled source: .tmp-ref/decompiled-jadx/sources/t2/r.java:216
                    // t2.r.z(Context) creates the actual View and adds it to r.p.
                    val createdView = targetResolver.firstMethod {
                        name = "z"
                        parameterCount = 1
                    }.invoke<View?>(context)
                    if (createdView == null && renderHost.isEmpty()) {
                        finishWithError(IllegalStateException("offscreen widget view was not created"))
                        return@runCatching
                    }
                    targetResolver.firstMethod {
                        name = "y"
                        parameterCount = 1
                    }.invoke(false)
                    targetResolver.firstMethod {
                        name = "D"
                        parameterCount = 0
                    }.invoke()
                    targetResolver.firstMethod {
                        name = "x"
                        parameterCount = 1
                    }.invoke(true)

                    val startedAt = System.currentTimeMillis()
                    fun tryCapture() {
                        runCatching {
                            renderHost.measure(
                                View.MeasureSpec.makeMeasureSpec(size.x, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(size.y, View.MeasureSpec.EXACTLY),
                            )
                            renderHost.layout(0, 0, size.x, size.y)
                            val bitmap = captureViewBitmap(renderHost)
                            if (bitmap.hasVisiblePixels()) {
                                finishWithBitmap(bitmap)
                                return
                            }
                            bitmap.recycle()
                            if (System.currentTimeMillis() - startedAt >= OFFSCREEN_CAPTURE_TIMEOUT_MS) {
                                throw IllegalStateException("offscreen preview stayed blank")
                            }
                            renderHost.postDelayed(
                                { tryCapture() },
                                OFFSCREEN_CAPTURE_RETRY_INTERVAL_MS
                            )
                        }.onFailure(::finishWithError)
                    }
                    renderHost.postDelayed({ tryCapture() }, OFFSCREEN_CAPTURE_INITIAL_DELAY_MS)
                }.onFailure {
                    finishWithError(it)
                }
            }.onFailure {
                errorRef.set(it)
                latch.countDown()
            }
        }

        if (!latch.await(OFFSCREEN_CAPTURE_TIMEOUT_MS + 1000L, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("offscreen preview capture timed out")
        }
        errorRef.get()?.let { throw it }
        val bitmap =
            bitmapRef.get() ?: throw IllegalStateException("offscreen preview capture failed")
        return try {
            val output = ByteArrayOutputStream()
            output.use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) {
                    throw IllegalStateException("failed to encode offscreen preview")
                }
            }
            writeBytesAtomically(targetFile, output.toByteArray())
            targetFile.absolutePath
        } finally {
            bitmap.recycle()
        }
    }

    private fun cloneWallpaperWidgetForPreview(sourceWidget: Any): Any {
        val spec = sourceWidget.asResolver().firstField { name = "c" }.get()
            ?: throw IllegalStateException("wallpaper spec is missing")
        return createWallpaperWidget(spec)
            ?: throw IllegalStateException("failed to create offscreen wallpaper widget")
    }

    private fun resolvePreviewRenderSize(panel: View): Point {
        val panelWidth = panel.width
        val panelHeight = panel.height
        if (panelWidth > 0 && panelHeight > 0) return Point(panelWidth, panelHeight)
        val devicePoint = runCatching {
            resolveDeviceConfigClass().toClass().resolve().firstField { name = "k" }.get() as? Point
        }.getOrNull()
        val width = devicePoint?.x?.takeIf { it > 0 } ?: panel.measuredWidth
        val height = devicePoint?.y?.takeIf { it > 0 } ?: panel.measuredHeight
        if (width <= 0 || height <= 0) {
            throw IllegalStateException("preview render size is invalid")
        }
        return Point(width, height)
    }

    private fun captureWallpaperPreviewBySwitchToFile(wallpaperId: Int, targetFile: File): String {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("preview capture must not run on the main thread")
        }
        val panel = mainPanel as? View
            ?: throw IllegalStateException("main panel is not ready")
        val handler = mainHandler
            ?: throw IllegalStateException("main handler is not ready")
        val entries = loadWallpaperEntries()
        val targetIndex = entries.indexOfFirst { it.wallpaperId == wallpaperId }
        if (targetIndex < 0) throw IllegalArgumentException("wallpaper is not in current list")
        val currentIndex = readCurrentSelectionIndex(entries.lastIndex)
        val currentWallpaperId = entries.getOrNull(currentIndex)?.wallpaperId
        val widgets = entries.map { it.widget }
        val bitmapRef = AtomicReference<Bitmap?>()
        val errorRef = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)

        handler.post {
            runCatching {
                invokePanelSelection(panel, widgets, targetIndex)
                panel.postDelayed(
                    {
                        runCatching {
                            bitmapRef.set(captureViewBitmap(panel))
                        }.onFailure(errorRef::set)

                        runCatching {
                            if (currentIndex >= 0 && currentIndex != targetIndex) {
                                invokePanelSelection(panel, widgets, currentIndex)
                            }
                            if (currentIndex >= 0) persistSelectionIndex(currentIndex)
                            currentWallpaperId?.let(::persistSelectedWallpaperId)
                        }.onFailure(YLog::warn)
                        latch.countDown()
                    },
                    PREVIEW_CAPTURE_DELAY_MS,
                )
            }.onFailure {
                errorRef.set(it)
                latch.countDown()
            }
        }

        if (!latch.await(PREVIEW_CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("preview capture timed out")
        }
        errorRef.get()?.let { throw it }
        val bitmap = bitmapRef.get() ?: throw IllegalStateException("preview capture failed")
        return try {
            val output = ByteArrayOutputStream()
            output.use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) {
                    throw IllegalStateException("failed to encode preview")
                }
            }
            writeBytesAtomically(targetFile, output.toByteArray())
            targetFile.absolutePath
        } finally {
            bitmap.recycle()
        }
    }

    private fun invokePanelSelection(panel: Any, widgets: List<Any>, index: Int) {
        val selectPoint = resolveMainPanelSelectMethod()
        panel.asResolver().firstMethod {
            name = selectPoint.methodName
            parameterCount = 2
        }.invoke(widgets, index)
    }

    private fun captureViewBitmap(view: View): Bitmap {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            throw IllegalStateException("main panel has invalid size")
        }
        return createBitmap(width, height).also { bitmap ->
            view.draw(Canvas(bitmap))
        }
    }

    private fun Bitmap.hasVisiblePixels(): Boolean {
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if ((this[x, y] ushr 24) != 0) return true
                x += stepX
            }
            y += stepY
        }
        return false
    }

    private fun validateMamlPackage(file: File) {
        if (!file.isFile || file.length() <= 0L) {
            throw IllegalArgumentException("package file is empty")
        }
        ZipFile(file).use { zip ->
            val hasManifest = zip.getEntry("manifest.xml") != null
            val hasConfig = zip.getEntry("config.xml") != null
            if (!hasManifest && !hasConfig) {
                throw IllegalArgumentException("package is missing manifest.xml or config.xml")
            }
        }
    }

    private fun extractPreviewFromPackage(packageFile: File, targetDir: File): String? {
        return runCatching {
            ZipFile(packageFile).use { zip ->
                val entry = findPreviewEntry(zip) ?: return null
                if (entry.size > 20L * 1024L * 1024L) return null
                val extension = entry.name.substringAfterLast('.', "png")
                    .lowercase()
                    .takeIf { it in setOf("png", "jpg", "jpeg", "webp") }
                    ?: "png"
                val target = File(targetDir, "preview.$extension")
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (loadPreviewBytes(target.absolutePath) == null) {
                    target.delete()
                    null
                } else {
                    ensureReadable(target)
                    target.absolutePath
                }
            }
        }.getOrNull()
    }

    private fun findPreviewEntry(zip: ZipFile): ZipEntry? {
        val preferred = listOf(
            "preview.png",
            "preview.jpg",
            "preview.jpeg",
            "snapshot.png",
            "snapshot.jpg",
            "snapshot.jpeg",
            "snapshotPreview.png",
            "thumbnail.png",
            "cover.png",
        )
        preferred.forEach { name ->
            zip.getEntry(name)?.takeIf { !it.isDirectory }?.let { return it }
        }

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val lower = entry.name.lowercase()
            val looksLikeImage = lower.endsWith(".png") ||
                    lower.endsWith(".jpg") ||
                    lower.endsWith(".jpeg") ||
                    lower.endsWith(".webp")
            val looksLikePreview = lower.contains("preview") ||
                    lower.contains("snapshot") ||
                    lower.contains("thumbnail") ||
                    lower.contains("cover")
            if (looksLikeImage && looksLikePreview) return entry
        }
        return null
    }

    private fun readJsonFromUri(context: Context, uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { it.readText() }
        } ?: throw IllegalArgumentException("failed to open metadata uri")
        return JSONObject(text)
    }

    private fun readJsonFile(file: File?): JSONObject? {
        if (file == null || !file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun readDescriptionMetadata(packageFile: File): MetadataValues? {
        return runCatching {
            ZipFile(packageFile).use { zip ->
                val entry = zip.getEntry("description.xml") ?: return null
                zip.getInputStream(entry).use { input ->
                    val factory = DocumentBuilderFactory.newInstance()
                    runCatching {
                        factory.setFeature(
                            "http://apache.org/xml/features/disallow-doctype-decl",
                            true,
                        )
                    }
                    val root = factory.newDocumentBuilder().parse(input).documentElement
                    val titles = readLocaleXmlValues(root, "title")
                    val descriptions = readLocaleXmlValues(root, "description")
                    val authors = readLocaleXmlValues(root, "author")
                    val designers = readLocaleXmlValues(root, "designer")
                    val title = titles["fallback"] ?: titles.values.firstOrNull()
                    ?: packageFile.nameWithoutExtension
                    MetadataValues(
                        titleFallback = title,
                        titleZhCn = titles["zh_CN"] ?: title,
                        descriptionFallback = descriptions["fallback"]
                            ?: descriptions.values.firstOrNull().orEmpty(),
                        descriptionZhCn = descriptions["zh_CN"]
                            ?: descriptions["fallback"]
                            ?: descriptions.values.firstOrNull().orEmpty(),
                        author = authors["fallback"] ?: authors.values.firstOrNull().orEmpty(),
                        designer = designers["fallback"] ?: designers.values.firstOrNull()
                            .orEmpty(),
                        category = readFirstXmlText(root, "widgetCategory")
                            ?: readFirstXmlText(root, "typeTag")
                            ?: IMPORT_RES_TYPE,
                        resSubType = readFirstXmlText(root, "typeTag") ?: DEFAULT_RES_SUB_TYPE,
                        editable = readFirstXmlText(root, "editable") == "true",
                        thirdParties = true,
                        supportAon = false,
                    )
                }
            }
        }.getOrNull()
    }

    private fun readLocaleXmlValues(root: org.w3c.dom.Element, tag: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val nodes = root.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val value = element.textContent?.trim().orEmpty()
            if (value.isBlank()) continue
            val locale = element.getAttribute("locale").takeIf { it.isNotBlank() } ?: "fallback"
            result[locale] = value
        }
        return result
    }

    private fun readFirstXmlText(root: org.w3c.dom.Element, tag: String): String? {
        val nodes = root.getElementsByTagName(tag)
        if (nodes.length <= 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun resolveMetadataValues(
        options: Bundle?,
        source: MetadataValues?,
        displayNameHint: String,
    ): MetadataValues {
        val defaultTitle = displayNameHint
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .ifBlank { "Imported Wallpaper" }
        val titleFallback = options.optionString(
            RearWallpaperApiContract.BundleKeys.META_TITLE_FALLBACK,
        ) ?: source?.titleFallback ?: defaultTitle
        val titleZhCn = options.optionString(
            RearWallpaperApiContract.BundleKeys.META_TITLE_ZH_CN,
        ) ?: source?.titleZhCn ?: titleFallback
        val descriptionFallback = options.optionString(
            RearWallpaperApiContract.BundleKeys.META_DESCRIPTION_FALLBACK,
        ) ?: source?.descriptionFallback ?: "Imported by REAREye"
        val descriptionZhCn = options.optionString(
            RearWallpaperApiContract.BundleKeys.META_DESCRIPTION_ZH_CN,
        ) ?: source?.descriptionZhCn ?: descriptionFallback

        return MetadataValues(
            titleFallback = titleFallback,
            titleZhCn = titleZhCn,
            descriptionFallback = descriptionFallback,
            descriptionZhCn = descriptionZhCn,
            author = options.optionString(RearWallpaperApiContract.BundleKeys.META_AUTHOR)
                ?: source?.author.orEmpty(),
            designer = options.optionString(RearWallpaperApiContract.BundleKeys.META_DESIGNER)
                ?: source?.designer.orEmpty(),
            category = options.optionString(RearWallpaperApiContract.BundleKeys.META_CATEGORY)
                ?: source?.category ?: IMPORT_RES_TYPE,
            resSubType = options.optionString(RearWallpaperApiContract.BundleKeys.META_RES_SUB_TYPE)
                ?: source?.resSubType ?: DEFAULT_RES_SUB_TYPE,
            editable = options?.getBoolean(
                RearWallpaperApiContract.BundleKeys.META_EDITABLE,
                source?.editable ?: false,
            ) ?: source?.editable ?: false,
            thirdParties = options?.getBoolean(
                RearWallpaperApiContract.BundleKeys.META_THIRD_PARTIES,
                source?.thirdParties ?: true,
            ) ?: source?.thirdParties ?: true,
            supportAon = options?.getBoolean(
                RearWallpaperApiContract.BundleKeys.META_SUPPORT_AON,
                source?.supportAon ?: false,
            ) ?: source?.supportAon ?: false,
        )
    }

    private fun buildMetadataJson(
        base: JSONObject?,
        resId: String,
        packageFile: File,
        metadataFile: File,
        previewPath: String?,
        packageSize: Long,
        updatedAt: Long,
        values: MetadataValues,
    ): JSONObject {
        val json = base?.let { JSONObject(it.toString()) } ?: JSONObject()
        json.put("localId", resId)
        json.put("productId", resId)
        json.put("hash", sha256(packageFile))
        json.put("platform", json.optInt("platform", 0))
        json.put("size", packageSize)
        json.put("updatedTime", updatedAt)
        json.put("version", json.optString("version", "1").ifBlank { "1" })
        json.put("authors", localeObject(values.author, values.author))
        json.put("designers", localeObject(values.designer, values.designer))
        json.put("titles", localeObject(values.titleFallback, values.titleZhCn))
        json.put(
            "descriptions",
            localeObject(values.descriptionFallback, values.descriptionZhCn),
        )
        json.put("builtInPreviews", previewMap(previewPath))
        json.put("thumbnails", previewEntries(previewPath))
        json.put("previews", previewEntries(previewPath))
        json.put("extraMeta", json.optJSONObject("extraMeta") ?: JSONObject())
        json.put("metaPath", metadataFile.absolutePath)
        json.put("contentPath", packageFile.absolutePath)
        json.put("rightsPath", json.optString("rightsPath", ""))
        json.put("screenRatio", json.optString("screenRatio", ""))
        json.put("packageName", json.optString("packageName", "hk.uwu.reareye"))
        json.put("subResourceType", values.category)
        json.put("resSubType", values.resSubType)
        json.put("isRearScreenEditable", values.editable)
        json.put("isThirdParties", values.thirdParties)
        json.put("supportAon", values.supportAon)
        json.put("wallpaperStyle", json.optInt("wallpaperStyle", 0))
        json.put("isSingleResource", true)
        json.put("isRearScreenNeedLogin", false)
        return json
    }

    private fun buildRuntimeItem(
        resId: String,
        applyId: String,
        packagePath: String,
        metadataPath: String,
        previewPath: String?,
        position: Int,
        updatedAt: Long,
        values: MetadataValues,
    ): JSONObject {
        return JSONObject().apply {
            put("resType", values.category)
            put("resId", resId)
            put("resSubType", values.resSubType)
            put("resTypeName", localeObject(values.category, values.category).toString())
            put("applyId", applyId)
            put("resName", localeObject(values.titleFallback, values.titleZhCn).toString())
            put(
                "resDescription",
                localeObject(values.descriptionFallback, values.descriptionZhCn).toString(),
            )
            put("resPreviewPath", previewPath ?: "")
            put("resDesigner", localeObject(values.designer, values.designer).toString())
            put("resLocalPath", packagePath)
            put("resSnapshotPath", packagePath)
            put("metaPath", metadataPath)
            put("metaSnapshotPath", metadataPath)
            put("isDownload", false)
            put("downloadUrl", "")
            put("applyTime", updatedAt)
            put("updateTime", updatedAt)
            put("isNFC", false)
            put("snapshotPreviewPath", previewPath ?: "")
            put("position", position)
            put("editable", values.editable)
            put("isThirdParties", values.thirdParties)
            put("supportAon", values.supportAon)
            put("packageName", "hk.uwu.reareye")
            put("isOnlineResource", false)
            put("onlineId", "")
        }
    }

    private fun applyMetadataToRuntimeItem(
        item: JSONObject,
        metadataPath: String,
        previewPath: String?,
        values: MetadataValues,
    ) {
        item.put("resType", values.category)
        item.put("resSubType", values.resSubType)
        item.put("resTypeName", localeObject(values.category, values.category).toString())
        item.put("resName", localeObject(values.titleFallback, values.titleZhCn).toString())
        item.put(
            "resDescription",
            localeObject(values.descriptionFallback, values.descriptionZhCn).toString(),
        )
        item.put("resDesigner", localeObject(values.designer, values.designer).toString())
        item.put("metaPath", metadataPath)
        item.put("metaSnapshotPath", metadataPath)
        item.put("editable", values.editable)
        item.put("isThirdParties", values.thirdParties)
        item.put("supportAon", values.supportAon)
        if (!previewPath.isNullOrBlank()) {
            item.put("resPreviewPath", previewPath)
            item.put("snapshotPreviewPath", previewPath)
        }
        item.put("updateTime", System.currentTimeMillis())
    }

    private fun localeObject(fallback: String, zhCn: String): JSONObject {
        return JSONObject().apply {
            put("fallback", fallback)
            put("zh_CN", zhCn.ifBlank { fallback })
        }
    }

    private fun previewMap(previewPath: String?): JSONObject {
        val map = JSONObject()
        if (!previewPath.isNullOrBlank()) {
            map.put("fallback", JSONArray().put(previewPath))
            map.put("zh_CN", JSONArray().put(previewPath))
        }
        return map
    }

    private fun previewEntries(previewPath: String?): JSONArray {
        val array = JSONArray()
        if (!previewPath.isNullOrBlank()) {
            array.put(
                JSONObject().apply {
                    put("localPath", previewPath)
                    put("onlinePath", "")
                }
            )
        }
        return array
    }

    private fun readRuntimeRecords(): List<RuntimeWallpaperRecord> {
        val array = readRuntimeArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                item.toRuntimeRecord()?.let(::add)
            }
        }
    }

    private fun readRuntimeArray(): JSONArray {
        val file = resolveRuntimeFile()
        if (!file.isFile) return JSONArray()
        val text = runCatching { file.readText() }.getOrDefault("")
        if (text.isBlank()) return JSONArray()
        return runCatching { JSONArray(text) }.getOrElse {
            YLog.warn(it)
            JSONArray()
        }
    }

    private fun writeRuntimeArray(array: JSONArray) {
        val file = resolveRuntimeFile()
        writeTextAtomically(file, array.toString(2))
        ensureReadable(file)
    }

    private fun maxRuntimePosition(array: JSONArray): Int {
        var max = -1
        for (i in 0 until array.length()) {
            max = maxOf(max, array.optJSONObject(i)?.optInt("position", -1) ?: -1)
        }
        return max
    }

    private fun findRuntimeItemIndex(array: JSONArray, wallpaperId: Int): Int {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val resId = item.optNonBlankString("resId") ?: continue
            val applyId = item.optNonBlankString("applyId") ?: continue
            if ((resId + applyId).hashCode() == wallpaperId) return i
        }
        return -1
    }

    private fun JSONObject.toRuntimeRecord(): RuntimeWallpaperRecord? {
        val resId = optNonBlankString("resId") ?: return null
        val applyId = optNonBlankString("applyId") ?: return null
        val resLocalPath = optNonBlankString("resLocalPath")
        val metaPath = optNonBlankString("metaPath")
        val metaSnapshotPath = optNonBlankString("metaSnapshotPath")
        val previewPath = optNonBlankString("snapshotPreviewPath")
            ?: optNonBlankString("resPreviewPath")
        val imported = isReareyeRuntimeItem(resId, resLocalPath, metaPath)
        return RuntimeWallpaperRecord(
            item = this,
            resId = resId,
            applyId = applyId,
            wallpaperId = (resId + applyId).hashCode(),
            resLocalPath = resLocalPath,
            metaPath = metaPath,
            metaSnapshotPath = metaSnapshotPath,
            previewPath = previewPath,
            imported = imported,
            position = optInt("position", -1),
        )
    }

    private fun RuntimeWallpaperRecord.readMetadataValues(): MetadataValues {
        val metaJson = readJsonFile(metaPath?.let(::File))
            ?: readJsonFile(metaSnapshotPath?.let(::File))
        val metaValues = metaJson?.toMetadataValues(item.optNonBlankString("resName") ?: resId)
        val runtimeTitle = item.optJsonLocale("resName")
        val runtimeDescription = item.optJsonLocale("resDescription")
        val runtimeDesigner = item.optJsonLocale("resDesigner")
        val titleFallback = runtimeTitle["fallback"]
            ?: runtimeTitle["zh_CN"]
            ?: metaValues?.titleFallback
            ?: item.optNonBlankString("resName")
            ?: resId
        return MetadataValues(
            titleFallback = titleFallback,
            titleZhCn = runtimeTitle["zh_CN"]
                ?: metaValues?.titleZhCn
                ?: titleFallback,
            descriptionFallback = runtimeDescription["fallback"]
                ?: runtimeDescription["zh_CN"]
                ?: metaValues?.descriptionFallback.orEmpty(),
            descriptionZhCn = runtimeDescription["zh_CN"]
                ?: metaValues?.descriptionZhCn
                ?: runtimeDescription["fallback"].orEmpty(),
            author = metaValues?.author.orEmpty(),
            designer = runtimeDesigner["fallback"]
                ?: runtimeDesigner["zh_CN"]
                ?: metaValues?.designer.orEmpty(),
            category = item.optNonBlankString("resType")
                ?: metaValues?.category
                ?: IMPORT_RES_TYPE,
            resSubType = item.optNonBlankString("resSubType")
                ?: metaValues?.resSubType
                ?: DEFAULT_RES_SUB_TYPE,
            editable = item.optBoolean("editable", metaValues?.editable ?: false),
            thirdParties = item.optBoolean("isThirdParties", metaValues?.thirdParties ?: imported),
            supportAon = item.optBoolean("supportAon", metaValues?.supportAon ?: false),
        )
    }

    private fun JSONObject.toMetadataValues(defaultTitle: String): MetadataValues {
        val titles = optLocaleObject("titles")
        val descriptions = optLocaleObject("descriptions")
        val authors = optLocaleObject("authors")
        val designers = optLocaleObject("designers")
        val titleFallback = titles["fallback"] ?: titles["zh_CN"] ?: defaultTitle
        val descriptionFallback = descriptions["fallback"] ?: descriptions["zh_CN"].orEmpty()
        return MetadataValues(
            titleFallback = titleFallback,
            titleZhCn = titles["zh_CN"] ?: titleFallback,
            descriptionFallback = descriptionFallback,
            descriptionZhCn = descriptions["zh_CN"] ?: descriptionFallback,
            author = authors["fallback"] ?: authors["zh_CN"].orEmpty(),
            designer = designers["fallback"] ?: designers["zh_CN"].orEmpty(),
            category = optNonBlankString("subResourceType")
                ?: optNonBlankString("widgetCategory")
                ?: IMPORT_RES_TYPE,
            resSubType = optNonBlankString("resSubType") ?: DEFAULT_RES_SUB_TYPE,
            editable = optBoolean("isRearScreenEditable", false),
            thirdParties = optBoolean("isThirdParties", true),
            supportAon = optBoolean("supportAon", false),
        )
    }

    private fun isReareyeRuntimeItem(
        resId: String,
        resLocalPath: String?,
        metaPath: String?,
    ): Boolean {
        if (resId.startsWith(IMPORT_RES_PREFIX)) return true
        return listOfNotNull(resLocalPath, metaPath).any { path ->
            path.replace('\\', '/').contains("/$IMPORT_RES_PREFIX")
        }
    }

    private fun deleteImportedFiles(record: RuntimeWallpaperRecord) {
        val root = resolveRuntimeRoot().canonicalFile
        val candidates = listOfNotNull(
            record.resLocalPath?.let(::File)?.parentFile,
            record.metaPath?.let(::File)?.parentFile,
            record.metaSnapshotPath?.let(::File)?.parentFile,
        ).distinctBy { it.absolutePath }

        candidates.forEach { dir ->
            runCatching {
                val canonical = dir.canonicalFile
                val isInsideRoot = canonical.path.startsWith(root.path + File.separator)
                if (isInsideRoot && canonical.name.startsWith(record.resId)) {
                    canonical.deleteRecursively()
                }
            }.onFailure(YLog::warn)
        }
    }

    private fun refreshRuntimePanels() {
        val entries = loadWallpaperEntries()
        if (entries.isEmpty()) return
        val currentIndex = readCurrentSelectionIndex(entries.lastIndex).coerceAtLeast(0)
        mainPanel?.let { panel ->
            dispatchSelection(panel, entries.map { it.widget }, currentIndex)
        }
        refreshSchedule(forceApply = true)
    }

    private fun resolveRuntimeRoot(): File {
        return File("/data/system/theme_magic/users/${currentUserId()}/rearScreen")
    }

    private fun resolveRuntimeFile(): File {
        return File(resolveRuntimeRoot(), "runtime.json")
    }

    private fun currentUserId(): Int {
        return (Process.myUid() / 100000).coerceAtLeast(0)
    }

    private fun writeTextAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.writeText(text)
        if (target.exists()) target.delete()
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        ensureReadable(target)
    }

    private fun sha256(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("${file.length()}_${file.lastModified()}")
    }

    private fun ensureReadableRecursive(file: File) {
        if (file.isDirectory) {
            ensureReadable(file)
            file.listFiles()?.forEach(::ensureReadableRecursive)
            return
        }
        ensureReadable(file)
    }

    @SuppressLint("SetWorldReadable")
    private fun ensureReadable(file: File) {
        file.setReadable(true, false)
        file.parentFile?.setReadable(true, false)
        file.parentFile?.setExecutable(true, false)
    }

    private fun Bundle?.optionString(key: String): String? {
        return this?.getString(key)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNonBlankString(key: String): String? {
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optLocaleObject(key: String): Map<String, String> {
        val json = optJSONObject(key) ?: return emptyMap()
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val locale = keys.next()
                val value = json.optString(locale, "").takeIf { it.isNotBlank() } ?: continue
                put(locale, value)
            }
        }
    }

    private fun JSONObject.optJsonLocale(key: String): Map<String, String> {
        val raw = optNonBlankString(key) ?: return emptyMap()
        return runCatching { JSONObject(raw).optLocaleObjectFromSelf() }
            .getOrDefault(mapOf("fallback" to raw))
    }

    private fun JSONObject.optLocaleObjectFromSelf(): Map<String, String> {
        return buildMap {
            val keys = keys()
            while (keys.hasNext()) {
                val locale = keys.next()
                val value = optString(locale, "").takeIf { it.isNotBlank() } ?: continue
                put(locale, value)
            }
        }
    }

    private fun MetadataValues.preferredTitle(): String {
        return titleZhCn.ifBlank { titleFallback }
    }

    private fun MetadataValues.preferredDescription(): String {
        return descriptionZhCn.ifBlank { descriptionFallback }
    }

    private fun UUID.shortId(): String {
        return toString().replace("-", "").take(12)
    }

    private fun Any.wallpaperSpecId(): Int? {
        return runCatching {
            asResolver().firstField { name = "a" }.get() as? Int
        }.getOrNull()
    }

    private fun debugLog(message: String) {
        if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
            YLog.debug("[$TAG] $message")
        }
    }

    private fun loadPreviewBytes(previewPath: String?): ByteArray? {
        val path = previewPath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.isFile) return null

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, 640)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        var targetWidth = width
        var targetHeight = height
        while (targetWidth > maxSize || targetHeight > maxSize) {
            sample *= 2
            targetWidth /= 2
            targetHeight /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun buildPreviewSignature(previewPath: String?): String {
        val file = previewPath?.let(::File)
        if (file != null && file.isFile) {
            return "${file.absolutePath.hashCode()}_${file.length()}_${file.lastModified()}"
        }
        return "missing"
    }

    private fun readLocalePreviewSuffix(): String? {
        return runCatching {
            resolveDeviceConfigClass().toClass().resolve().firstField { name = "m" }
                .get() as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun Bundle?.resolvePreviewPath(localeSuffix: String?): String? {
        if (this == null) return null
        val localized = localeSuffix?.let { getString("snapshotPath_$it") }
        return localized?.takeIf { it.isNotBlank() }
            ?: getString("snapshotPath")?.takeIf { it.isNotBlank() }
    }

    private fun Int.floorMod(size: Int): Int {
        if (size <= 0) return 0
        val mod = this % size
        return if (mod < 0) mod + size else mod
    }
}
