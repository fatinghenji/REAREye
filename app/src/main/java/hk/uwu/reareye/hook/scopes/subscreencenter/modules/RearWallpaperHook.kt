@file:Suppress("UNCHECKED_CAST")

package hk.uwu.reareye.hook.scopes.subscreencenter.modules

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
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.widgetapi.IRearWallpaperApiConnection
import hk.uwu.reareye.widgetapi.IRearWallpaperApiService
import hk.uwu.reareye.widgetapi.RearWallpaperApiContract
import hk.uwu.reareye.widgetapi.RearWallpaperScheduleCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RearWallpaperHook : YukiBaseHooker() {

    companion object {
        private const val TAG = "REAREye-RearWallpaper"
        private const val RETRY_SWITCH_DELAY_MS = 350L

        @Volatile
        private var cachedNextSwitchAtMillis: Long = Long.MIN_VALUE

        @Volatile
        private var cachedScheduleConfig: ScheduleConfig? = null
    }

    private data class WallpaperEntry(
        val wallpaperId: Int,
        val title: String,
        val name: String,
        val previewPath: String?,
        val previewSignature: String,
        val widget: Any,
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
    private var smartPanel: Any? = null
    private var mainHandler: Handler? = null
    private var schedulerTask: Runnable? = null

    override fun onHook() {
        loadApp("com.xiaomi.subscreencenter") {
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
                smartPanel = null
                mainHandler = null
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
        smartPanel = runCatching {
            resolver.firstField { name = "z" }.get()
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
        debugLog("switchToResolved wallpaperId=${item.wallpaperId} runtimeIndex=${item.runtimeIndex} targetIndex=$targetIndex")

        val widgets = entries.map { it.widget }
        var applied = false
        mainPanel?.let { panel ->
            applied = dispatchSelection(panel, widgets, targetIndex) || false
        }
        smartPanel?.let { panel ->
            applied = dispatchSelection(panel, widgets, targetIndex) || applied
        }
        debugLog("switchToResolved result wallpaperId=${item.wallpaperId} applied=$applied main=${mainPanel != null} smart=${smartPanel != null}")
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
                panel.asResolver().firstMethod {
                    name = "d"
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

    private fun loadWallpaperEntries(): List<WallpaperEntry> {
        val specList = loadWallpaperSpecs()
        if (specList.isEmpty()) return emptyList()

        val widgetRef = "t2.r".toClass().resolve()
        val localeSuffix = readLocalePreviewSuffix()
        return buildList {
            specList.forEach { spec ->
                val widget = runCatching {
                    widgetRef.firstMethod {
                        name = "g"
                        parameterCount = 1
                    }.invoke(spec)
                }.getOrNull() ?: return@forEach

                val resolver = spec.asResolver()
                val extras = runCatching {
                    resolver.firstField { name = "d" }.get() as? Bundle
                }.getOrNull()
                val previewPath = extras.resolvePreviewPath(localeSuffix)
                add(
                    WallpaperEntry(
                        wallpaperId = runCatching {
                            resolver.firstField { name = "a" }.get() as Int
                        }.getOrDefault(0),
                        title = extras?.getString("title").orEmpty().ifBlank { "Wallpaper" },
                        name = extras?.getString("resName").orEmpty().ifBlank { "unknown" },
                        previewPath = previewPath,
                        previewSignature = buildPreviewSignature(previewPath),
                        widget = widget,
                    )
                )
            }
        }
    }

    private fun loadWallpaperSpecs(): List<Any> {
        val prefStore = runCatching {
            "Z1.S".toClass().resolve().firstField { name = "a" }.get()
        }.getOrNull()

        val persisted = runCatching {
            prefStore?.asResolver()?.firstMethod {
                name = "e"
                parameterCount = 1
            }?.invoke(false) as? List<Any>
        }.getOrNull().orEmpty()
        if (persisted.isNotEmpty()) return persisted

        return runCatching {
            "com.bumptech.glide.d".toClass().resolve().firstMethod {
                name = "G"
                parameterCount = 1
            }.invoke(true) as? List<Any>
        }.getOrNull().orEmpty()
    }

    private fun readCurrentSelectionIndex(maxIndex: Int): Int {
        val index = runCatching {
            val store =
                "Z1.S".toClass().resolve().firstField { name = "a" }.get() ?: return@runCatching 0
            store.asResolver().firstMethod {
                name = "c"
                parameterCount = 3
            }.invoke(Int::class.javaPrimitiveType!!, 0, "user_select") as? Int ?: 0
        }.getOrDefault(0)
        if (maxIndex < 0) return -1
        return index.coerceIn(0, maxIndex)
    }

    private fun persistSelectionIndex(index: Int) {
        runCatching {
            val store = "Z1.S".toClass().resolve().firstField { name = "a" }.get() ?: return
            store.asResolver().firstMethod {
                name = "j"
                parameterCount = 2
            }.invoke(index, "user_select")
        }.onFailure(YLog::error)
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
            "r2.e".toClass().resolve().firstField { name = "m" }.get() as? String
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
