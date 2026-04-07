package hk.uwu.reareye.hook.scopes.subscreencenter.modules.lyrics

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.os.Build
import com.hchen.superlyricapi.ISuperLyric
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricTool
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XposedHelpers
import hk.uwu.reareye.lyrics.LyricParser
import hk.uwu.reareye.ui.config.ConfigKeys
import hk.uwu.reareye.ui.config.LyricProvider
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class LyriconHook : YukiBaseHooker() {
    private val lyricParser = LyricParser()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope by lazy(LazyThreadSafetyMode.NONE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @Volatile
    private var latestLyricLrc: String = ""

    @Volatile
    private var currentProvider: ProviderInfo? = null
    private val elements: CopyOnWriteArrayList<Any> = CopyOnWriteArrayList<Any>()

    @Volatile
    var monitor: LyriconSubscriber? = null

    @Volatile
    var superLyricStub: ISuperLyric.Stub? = null

    override fun onHook() {
        loadApp("com.android.systemui") {
            onAppLifecycle {
                onCreate {
                    val context = appContext ?: return@onCreate
                    if (LyricProvider.fromValue(
                            prefs.getInt(
                                ConfigKeys.LYRIC_PROVIDER,
                                ConfigKeys.LYRIC_PROVIDER_DEFAULT
                            )
                        ) == LyricProvider.LYRICON
                    ) {
                        if (!(isPackageInstalled(
                                context,
                                TARGET_LYRICON_PACKAGE
                            ) || isPackageInstalled(context, LYRICON_CORE_PACKAGE))
                        ) {
                            YLog.info("Lyricon is not found, starting bundled central")
                            BridgeCentral.initialize(context)
                            BridgeCentral.sendBootCompleted()
                        } else {
                            YLog.info("Lyricon is found, skip to start central")
                        }
                    }
                }
            }
        }

        loadApp("com.xiaomi.subscreencenter") {
            onAppLifecycle {
                onCreate {
                    val context = appContext ?: return@onCreate
                    when (LyricProvider.fromValue(
                        prefs.getInt(
                            ConfigKeys.LYRIC_PROVIDER,
                            ConfigKeys.LYRIC_PROVIDER_DEFAULT
                        )
                    )) {
                        LyricProvider.LYRICON -> {
                            val listener = createLyricListener()
                            val monitor =
                                io.github.proify.lyricon.subscriber.LyriconFactory.createSubscriber(
                                    context
                                )
                            monitor.subscribeActivePlayer(listener)
                            monitor.register()
                            YLog.info("Registered lyricon player monitor")
                        }

                        LyricProvider.SUPER_LYRIC -> {
                            superLyricStub = object : ISuperLyric.Stub() {
                                override fun onStop(data: SuperLyricData) {
                                }

                                override fun onSuperLyric(data: SuperLyricData) {
                                    scope.launch {
                                        runCatching {
                                            if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                                                YLog.debug("onSuperLyric ${data.lyric} ${data.translation}")
                                            }
                                            val mode = prefs.getInt(
                                                ConfigKeys.SUPER_LYRIC_DISPLAY_MODE,
                                                ConfigKeys.SUPER_LYRIC_DISPLAY_MODE_DEFAULT
                                            )
                                            val originalLines = data.lyric.split("\n")
                                            val lyric = when {
                                                LyricParser.DisplayMode.shouldShowTranslation(mode) -> {
                                                    val translation = data.translation
                                                    if (!translation.isNullOrEmpty()) {
                                                        translation
                                                    } else if (originalLines.size > 1) {
                                                        originalLines[1]
                                                    } else {
                                                        data.lyric
                                                    }
                                                }

                                                else -> {
                                                    originalLines[0]
                                                }
                                            }
                                            if (lyric.isNotEmpty()) {
                                                updateFallbackLyric(lyric)
                                            }
                                        }.onFailure {
                                            YLog.error(it)
                                        }
                                    }
                                }
                            }
                            SuperLyricTool.registerSuperLyric(context, superLyricStub!!)
                            YLog.info("Registered super-lyric listener")
                        }
                    }
                }

                onTerminate {
                    monitor?.also {
                        it.unregister()
                        it.destroy()
                    }
                    val context = appContext ?: return@onTerminate
                    superLyricStub?.also {
                        SuperLyricTool.unregisterSuperLyric(context, it)
                    }
                    YLog.debug("Terminated music lyric services")
                }
            }

            val clz = "com.miui.maml.elements.MusicControlScreenElement".toClass()
            val ref = clz.resolve()
            ref.constructor().build().hookAll {
                after {
                    elements.addIfAbsent(instance)
                    if (latestLyricLrc.isNotEmpty()) {
                        elements.forEach {
                            updateLyric(it, latestLyricLrc)
                        }
                    }
                }
            }

            ref.firstMethod {
                name = "resetLyric"
            }.hook().replaceUnit {
                val iRef = instance.asResolver()
                val mMetadata = iRef.firstField { name = "mMetadata" }.get<MediaMetadata>()
                if (mMetadata != null && XposedHelpers.getAdditionalInstanceField(
                        instance,
                        "OLD_MEDIA_ID"
                    ) == mMetadata.description.mediaId
                ) {
                    YLog.debug("Reject reset lyric while media id is not changed")
                    return@replaceUnit
                } else {
                    clearManagedLyricState(instance)
                    invokeOriginal()
                }
            }

            ref.firstMethod {
                name = "updateLyricVar"
                parameters(Long::class.java)
            }.hook().replaceUnit {
                if (!isManagedFullLyric(instance)) {
                    invokeOriginal()
                    return@replaceUnit
                }
                updateLyricVarsDiff(instance, args(0).cast<Long>() ?: 0L)
            }

            ref.firstMethod {
                name = "startProgressUpdate"
                parameters(Boolean::class.java, Long::class.java)
            }.hook().replaceUnit {
                if (!isManagedFullLyric(instance)) {
                    invokeOriginal(*args)
                    return@replaceUnit
                }
                scheduleManagedProgressTick(
                    element = instance,
                    isPlaying = args(0).boolean(),
                    delayMs = args(1).cast<Long>() ?: 0L
                )
            }

            val seClz = "com.miui.maml.elements.ScreenElement".toClass().resolve()
            seClz.firstMethod {
                name = "show"
                parameters(Boolean::class.java)
            }.hook().after {
                if (instanceClass == clz && !args(0).boolean()) {
                    YLog.debug("Release music control instance: $instance")
                    clearManagedLyricState(instance)
                    elements.remove(instance)
                }
            }

            "com.miui.maml.elements.MusicControlScreenElement$4".toClass().resolve().firstMethod {
                name = "run"
            }.hook().replaceUnit {
                val element = XposedHelpers.getObjectField(instance, "this$0") ?: run {
                    invokeOriginal()
                    return@replaceUnit
                }
                if (!isManagedFullLyric(element)) {
                    invokeOriginal()
                    return@replaceUnit
                }
                runManagedProgressTick(element)
            }

            val musicControlListenerClz =
                "com.miui.maml.elements.MusicControlScreenElement$1".toClass().resolve()
            musicControlListenerClz.firstMethod {
                name = "onClientMetadataUpdate"
                returnType = Void.TYPE
                parameters(MediaMetadata::class.java)
            }.hook {
                replaceUnit {
                    val metadata = args(0).cast<MediaMetadata>()
                    val i = instance.asResolver().firstField {
                        name = "this$0"
                    }.get()
                    if (i == null) {
                        invokeOriginal(metadata)
                        return@replaceUnit
                    }
                    elements.addIfAbsent(i)
                    if (prefs.getBoolean(ConfigKeys.HOOK_REMOVE_NATIVE_LYRIC_SUPPORT, false)) {
                        val moreDebug = prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)
                        val builder = MediaMetadata.Builder(metadata)
                        if (moreDebug) {
                            YLog.debug("Native lyric: ${metadata?.getString(XIAOMI_LYRIC_METADATA)}")
                        }
                        builder.putString(XIAOMI_LYRIC_METADATA, null)
                        val newMeta = builder.build()
                        if (moreDebug) {
                            YLog.debug("Force removed native lyric data")
                        }
                        invokeOriginal(newMeta)
                    } else {
                        invokeOriginal(metadata)
                    }
                }

                after {
                    val metadata = args(0).cast<MediaMetadata>()
                    val i = instance.asResolver().firstField {
                        name = "this$0"
                    }.get() ?: return@after
                    scope.launch {
                        checkLyricState(metadata, i)
                    }
                }
            }
        }
    }

    private fun checkLyricState(metadata: MediaMetadata?, instance: Any) {
        val clz = "com.miui.maml.elements.MusicControlScreenElement".toClass()
        val moreDebug = prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)
        val iRef = instance.asResolver()
        val mLyric = iRef.firstField { name = "mLyric" }.get()
        val lrc = XposedHelpers.getAdditionalInstanceField(instance, "TEMP_LRC") as? String
        if (mLyric == null) {
            if (lrc != null || latestLyricLrc.isNotEmpty()) {
                if (moreDebug) {
                    YLog.debug("onUpdateLrc $mLyric ${lrc == null} ${latestLyricLrc.isEmpty()}")
                }
                updateLyric(instance, lrc ?: latestLyricLrc)
                return
            }
            val currentId = metadata?.description?.mediaId
            if (currentId != null && currentId == XposedHelpers.getAdditionalStaticField(
                    clz,
                    "LAST_LYRIC_ID"
                )
            ) {
                val lastLrc = XposedHelpers.getAdditionalStaticField(
                    clz,
                    "LAST_LYRIC_LRC"
                ) as String
                if (moreDebug) {
                    YLog.debug("onUseLastLrc $lastLrc")
                }
                updateLyric(instance, lastLrc)
            }
            val line =
                XposedHelpers.getAdditionalInstanceField(
                    instance,
                    "TEMP_LYRIC_LINE"
                ) as? String
            val mLyricCurrentVar =
                iRef.firstField { name = "mLyricCurrentVar" }.get() ?: return
            val currentLyric =
                mLyricCurrentVar.asResolver().firstMethod { name = "get" }.invoke()
            if (line != null && currentLyric == null) {
                if (moreDebug) {
                    YLog.debug("onUpdateLine $line")
                }
                updateFallbackLine(instance, line)
            }
        }
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        }.isSuccess
    }

    private fun createLyricListener(): ActivePlayerListener {
        val clz = "com.miui.maml.elements.MusicControlScreenElement".toClass()

        return object : ActivePlayerListener {
            override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
                currentProvider = providerInfo
                if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                    YLog.debug("onProviderChanged $currentProvider")
                }
            }

            override fun onSongChanged(song: Song?) {
                if (song == null) return
                scope.launch {
                    runCatching {
                        song.id
                        val lrc = lyricParser.toLrc(
                            song = song,
                            displayMode = prefs.getInt(
                                ConfigKeys.LYRIC_DISPLAY_MODE,
                                ConfigKeys.LYRIC_DISPLAY_MODE_DEFAULT,
                            ),
                            showArtistBeforeFirstLine = prefs.getBoolean(
                                ConfigKeys.LYRIC_SHOW_ARTIST_BEFORE_FIRST_LINE,
                                false,
                            ),
                        )
                        latestLyricLrc = normalizeForMiuiParser(lrc)
                        if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                            YLog.debug("REAREye getSongLRC $latestLyricLrc")
                            YLog.debug("onSongChanged converted LRC length=${latestLyricLrc.length}")
                            YLog.debug("current instance size ${elements.size}")
                        }
                        XposedHelpers.setAdditionalStaticField(clz, "LAST_LYRIC_ID", song.id)
                        XposedHelpers.setAdditionalStaticField(
                            clz,
                            "LAST_LYRIC_LRC",
                            latestLyricLrc
                        )
                        if (elements.isNotEmpty()) {
                            elements.forEach {
                                updateLyric(it, latestLyricLrc)
                            }
                            latestLyricLrc = ""
                        }
                        delay(2.seconds)
                        elements.forEach {
                            updateLyric(it, latestLyricLrc, force = false, checkId = true)
                        }
                    }.onFailure {
                        YLog.error(it)
                    }
                }
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit

            override fun onPositionChanged(position: Long) = Unit

            override fun onSeekTo(position: Long) = Unit

            override fun onReceiveText(text: String?) {
                scope.launch {
                    runCatching {
                        if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                            YLog.debug("onSendText $text")
                        }
                        val mode = prefs.getInt(
                            ConfigKeys.SUPER_LYRIC_DISPLAY_MODE,
                            ConfigKeys.SUPER_LYRIC_DISPLAY_MODE_DEFAULT
                        )
                        if (text != null) {
                            val originalLines = text.split("\n")
                            val lyric = when {
                                LyricParser.DisplayMode.shouldShowTranslation(mode) -> {
                                    if (originalLines.size > 1) {
                                        originalLines[1]
                                    } else {
                                        text
                                    }
                                }

                                else -> {
                                    originalLines[0]
                                }
                            }
                            if (lyric.isNotEmpty()) {
                                updateFallbackLyric(lyric)
                            }
                        }
                    }.onFailure {
                        YLog.error(it)
                    }
                }
            }

            override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

            override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
        }
    }

    private fun updateFallbackLyric(text: String) {
        elements.forEach { element ->
            XposedHelpers.setAdditionalInstanceField(element, "TEMP_LYRIC_LINE", text)
            updateFallbackLine(element, text)
        }
    }

    private fun updateFallbackLine(element: Any, text: String) {
        clearManagedLyricState(element)
        val ref = element.asResolver()
        val mLyric = ref.firstField { name = "mLyric" }.get()
        if (mLyric != null) return
        val mLyricCurrentVar =
            ref.firstField { name = "mLyricCurrentVar" }.get() ?: return
        mLyricCurrentVar.asResolver().firstMethod {
            name = "set"
            parameters(Any::class.java)
        }.invoke(text)
    }

    private fun updateLyric(
        element: Any,
        lrc: String,
        force: Boolean = true,
        checkId: Boolean = false
    ) {
        val moreDebug = prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)
        val clz = "com.miui.maml.elements.MusicControlScreenElement".toClass()
        if (moreDebug) {
            YLog.debug("handle instance: $element")
        }
        val ref = element.asResolver()
        val mLyric = ref.firstField { name = "mLyric" }
        val metadata = ref.firstField { name = "mMetadata" }.get<MediaMetadata>()
        if (!force && mLyric.get() != null) {
            if (moreDebug) {
                YLog.debug("Skip to update, in non-force mode")
            }
            return
        }
        var vLrc = lrc
        if (checkId) {
            if (metadata == null) return
            if (metadata.description.mediaId != XposedHelpers.getAdditionalStaticField(
                    clz,
                    "LAST_LYRIC_ID"
                )
            ) {
                return
            }
            vLrc = XposedHelpers.getAdditionalStaticField(clz, "LAST_LYRIC_LRC") as String
        }
        val parserClz = "com.miui.maml.elements.MusicLyricParser".toClass().resolve()
        val nLyric = parserClz.firstMethod {
            name = "parseLyric"
            parameters(String::class.java)
        }.invoke(vLrc)
        if (moreDebug) YLog.debug("parsed $nLyric")
        if (nLyric != null) {
            clearManagedLyricState(element)
            nLyric.asResolver().firstMethod { name = "decorate" }.invoke()
            mLyric.set(nLyric)
            ref.firstMethod { name = "updateLyric" }.invoke(nLyric)
            if (moreDebug) {
                YLog.debug("Force Update Lyric")
            }
            metadata?.also {
                XposedHelpers.setAdditionalInstanceField(
                    element,
                    "OLD_MEDIA_ID",
                    it.description.mediaId
                )
            }
            XposedHelpers.setAdditionalInstanceField(element, "TEMP_LRC", vLrc)
            XposedHelpers.setAdditionalInstanceField(element, MANAGED_FULL_LRC, true)
        } else {
            clearManagedLyricState(element)
        }
    }

    private fun runManagedProgressTick(element: Any) {
        val metadata =
            XposedHelpers.getObjectField(element, "mMetadata") as? MediaMetadata ?: return
        if (!XposedHelpers.getBooleanField(element, "mPlaying")) return
        val duration = metadata.getLong(DURATION_METADATA)
        val musicController = XposedHelpers.getObjectField(element, "mMusicController") ?: return
        val position = (XposedHelpers.callMethod(musicController, "getPosition") as? Long) ?: return
        if (duration <= 0 || position < 0) return

        setIndexedVariable(
            XposedHelpers.getObjectField(element, "mDurationVar"),
            duration.toDouble()
        )
        setIndexedVariable(
            XposedHelpers.getObjectField(element, "mPositionVar"),
            position.toDouble()
        )

        val lyricChanged = updateLyricVarsDiff(element, position)
        if (lyricChanged) {
            XposedHelpers.callMethod(element, "requestUpdate")
        }

        val interval = XposedHelpers.getIntField(element, "mUpdateProgressInterval").toLong()
            .coerceAtLeast(MIN_PROGRESS_INTERVAL_MS)
        val lyric = XposedHelpers.getObjectField(element, "mLyric")
        val cache = lyric?.let { getOrBuildLyricCache(element, it) }
        val delay = cache?.let { computeNextTickDelay(it.times, position, interval) } ?: interval
        scheduleManagedProgressTick(element, true, delay)
    }

    private fun scheduleManagedProgressTick(element: Any, isPlaying: Boolean, delayMs: Long) {
        cancelManagedProgressJob(element)
        if (!isPlaying) return
        val safeDelay = delayMs.coerceAtLeast(0L)
        val job = mainScope.launch {
            if (safeDelay > 0L) {
                delay(safeDelay)
            }
            runManagedProgressTick(element)
        }
        XposedHelpers.setAdditionalInstanceField(element, MANAGED_PROGRESS_JOB, job)
    }

    private fun updateLyricVarsDiff(element: Any, position: Long): Boolean {
        val lyric = XposedHelpers.getObjectField(element, "mLyric") ?: return false
        val cache = getOrBuildLyricCache(element, lyric) ?: return false
        val currentIndex = findLineIndex(cache.times, position)
        val previousIndex =
            XposedHelpers.getAdditionalInstanceField(element, LAST_LINE_INDEX) as? Int
                ?: Int.MIN_VALUE

        setIndexedVariable(
            XposedHelpers.getObjectField(element, "mLyricCurrentLineProgressVar"),
            computeLineProgress(cache.times, currentIndex, position)
        )

        if (currentIndex == previousIndex) {
            return false
        }

        XposedHelpers.setAdditionalInstanceField(element, LAST_LINE_INDEX, currentIndex)
        applyLyricSnapshot(element, lyric, cache, currentIndex, position)
        return true
    }

    private fun applyLyricSnapshot(
        element: Any,
        lyric: Any,
        cache: LyricCache,
        currentIndex: Int,
        position: Long
    ) {
        val currentText = cache.lines.getOrNull(currentIndex)?.toString()
        setIndexedVariable(XposedHelpers.getObjectField(element, "mLyricCurrentVar"), currentText)
        setIndexedVariable(
            XposedHelpers.getObjectField(element, "mLyricBeforeVar"),
            XposedHelpers.callMethod(lyric, "getBeforeLines", position)
        )
        setIndexedVariable(
            XposedHelpers.getObjectField(element, "mLyricAfterVar"),
            XposedHelpers.callMethod(lyric, "getAfterLines", position)
        )
        val lastLine = XposedHelpers.callMethod(lyric, "getLastLine", position)
        setIndexedVariable(XposedHelpers.getObjectField(element, "mLyricLastVar"), lastLine)
        setIndexedVariable(XposedHelpers.getObjectField(element, "mLyricPrevVar"), lastLine)
        setIndexedVariable(
            XposedHelpers.getObjectField(element, "mLyricNextVar"),
            XposedHelpers.callMethod(lyric, "getNextLine", position)
        )
    }

    private fun getOrBuildLyricCache(element: Any, lyric: Any): LyricCache? {
        val cachedLyric = XposedHelpers.getAdditionalInstanceField(element, CACHED_LYRIC)
        if (cachedLyric === lyric) {
            val times = XposedHelpers.getAdditionalInstanceField(element, CACHED_TIMES) as? IntArray

            @Suppress("UNCHECKED_CAST")
            val lines = XposedHelpers.getAdditionalInstanceField(
                element,
                CACHED_LINES
            ) as? List<CharSequence>
            if (times != null && lines != null) {
                return LyricCache(times, lines)
            }
        }

        val times = (XposedHelpers.callMethod(lyric, "getTimeArr") as? IntArray) ?: return null

        @Suppress("UNCHECKED_CAST")
        val lines =
            (XposedHelpers.callMethod(lyric, "getStringArr") as? List<CharSequence>) ?: return null
        XposedHelpers.setAdditionalInstanceField(element, CACHED_LYRIC, lyric)
        XposedHelpers.setAdditionalInstanceField(element, CACHED_TIMES, times)
        XposedHelpers.setAdditionalInstanceField(element, CACHED_LINES, lines)
        XposedHelpers.setAdditionalInstanceField(element, LAST_LINE_INDEX, Int.MIN_VALUE)
        return LyricCache(times, lines)
    }

    private fun isManagedFullLyric(element: Any): Boolean {
        return XposedHelpers.getAdditionalInstanceField(
            element,
            MANAGED_FULL_LRC
        ) as? Boolean == true
    }

    private fun clearManagedLyricState(element: Any) {
        cancelManagedProgressJob(element)
        XposedHelpers.setAdditionalInstanceField(element, MANAGED_FULL_LRC, false)
        XposedHelpers.setAdditionalInstanceField(element, CACHED_LYRIC, null)
        XposedHelpers.setAdditionalInstanceField(element, CACHED_TIMES, null)
        XposedHelpers.setAdditionalInstanceField(element, CACHED_LINES, null)
        XposedHelpers.setAdditionalInstanceField(element, LAST_LINE_INDEX, Int.MIN_VALUE)
    }

    private fun cancelManagedProgressJob(element: Any) {
        (XposedHelpers.getAdditionalInstanceField(element, MANAGED_PROGRESS_JOB) as? Job)?.cancel()
        XposedHelpers.setAdditionalInstanceField(element, MANAGED_PROGRESS_JOB, null)
    }

    private fun setIndexedVariable(target: Any?, value: Any?) {
        if (target == null) return
        XposedHelpers.callMethod(target, "set", value)
    }

    private fun computeNextTickDelay(
        times: IntArray,
        position: Long,
        fallbackInterval: Long
    ): Long {
        if (times.isEmpty()) return fallbackInterval
        val currentIndex = findLineIndex(times, position)
        val nextIndex = if (currentIndex < 0) 0 else currentIndex + 1
        if (nextIndex !in times.indices) return fallbackInterval
        val nextLineDelay =
            (times[nextIndex].toLong() - position).coerceAtLeast(MIN_PROGRESS_INTERVAL_MS)
        return minOf(fallbackInterval, nextLineDelay)
    }

    private fun computeLineProgress(times: IntArray, currentIndex: Int, position: Long): Double {
        if (times.isEmpty() || currentIndex < 0) return 0.0
        if (currentIndex >= times.lastIndex) {
            return ((position - times.last().toLong()) / LAST_LINE_DURATION_MS.toDouble())
                .coerceIn(0.0, 1.0)
        }
        val lineStart = times[currentIndex].toLong()
        val lineEnd = times[currentIndex + 1].toLong()
        if (lineEnd <= lineStart) return 0.0
        return ((position - lineStart).toDouble() / (lineEnd - lineStart).toDouble())
            .coerceIn(0.0, 1.0)
    }

    private fun findLineIndex(times: IntArray, position: Long): Int {
        if (times.isEmpty() || position < times.first().toLong()) return -1
        var left = 0
        var right = times.lastIndex
        while (left <= right) {
            val middle = (left + right) ushr 1
            if (times[middle].toLong() <= position) {
                left = middle + 1
            } else {
                right = middle - 1
            }
        }
        return right
    }

    private fun normalizeForMiuiParser(rawLrc: String): String {
        if (rawLrc.isEmpty()) return rawLrc
        return rawLrc
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", "\r\n")
    }

    private data class LyricCache(
        val times: IntArray,
        val lines: List<CharSequence>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LyricCache

            if (!times.contentEquals(other.times)) return false
            if (lines != other.lines) return false

            return true
        }

        override fun hashCode(): Int {
            var result = times.contentHashCode()
            result = 31 * result + lines.hashCode()
            return result
        }
    }

    private companion object {
        private const val TARGET_LYRICON_PACKAGE = "io.github.proify.lyricon"
        private const val LYRICON_CORE_PACKAGE = "io.github.proify.lyricon.core"
        private const val XIAOMI_LYRIC_METADATA = "android.media.metadata.LYRIC"
        private const val DURATION_METADATA = "android.media.metadata.DURATION"
        private const val MANAGED_FULL_LRC = "REAREYE_MANAGED_FULL_LRC"
        private const val CACHED_LYRIC = "REAREYE_CACHED_LYRIC"
        private const val CACHED_TIMES = "REAREYE_CACHED_TIMES"
        private const val CACHED_LINES = "REAREYE_CACHED_LINES"
        private const val LAST_LINE_INDEX = "REAREYE_LAST_LINE_INDEX"
        private const val MANAGED_PROGRESS_JOB = "REAREYE_MANAGED_PROGRESS_JOB"
        private const val MIN_PROGRESS_INTERVAL_MS = 100L
        private const val LAST_LINE_DURATION_MS = 8000L
    }
}
