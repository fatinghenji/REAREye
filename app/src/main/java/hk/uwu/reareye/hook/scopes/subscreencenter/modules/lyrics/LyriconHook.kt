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
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ActivePlayerMonitor
import java.util.concurrent.CopyOnWriteArrayList


class LyriconHook : YukiBaseHooker() {
    private val lyricParser = LyricParser()

    @Volatile
    private var latestLyricLrc: String = ""

    @Volatile
    private var currentProvider: ProviderInfo? = null
    private val elements: CopyOnWriteArrayList<Any> = CopyOnWriteArrayList<Any>()

    @Volatile
    var monitor: ActivePlayerMonitor? = null

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
                        if (!isLyriconInstalled(context)) {
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
                                LyriconFactory.createActivePlayerMonitor(
                                    context,
                                    listener = listener
                                )
                            monitor.register()
                            YLog.info("Registered lyricon player monitor")
                        }

                        LyricProvider.SUPER_LYRIC -> {
                            superLyricStub = object : ISuperLyric.Stub() {
                                override fun onStop(data: SuperLyricData) {
                                }

                                override fun onSuperLyric(data: SuperLyricData) {
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
                            forceUpdateLyric(it, latestLyricLrc)
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
                    invokeOriginal()
                }
            }

            val seClz = "com.miui.maml.elements.ScreenElement".toClass().resolve()
            seClz.firstMethod {
                name = "show"
                parameters(Boolean::class.java)
            }.hook().after {
                if (instanceClass == clz && !args(0).boolean()) {
                    YLog.debug("Release music control instance: $instance")
                    elements.remove(instance)
                }
            }

            val musicControlListenerClz =
                "com.miui.maml.elements.MusicControlScreenElement$1".toClass().resolve()
            musicControlListenerClz.firstMethod {
                name = "onClientMetadataUpdate"
                returnType = Void.TYPE
                parameters(MediaMetadata::class.java)
            }.hook {
                before {
                    if (prefs.getBoolean(ConfigKeys.HOOK_REMOVE_NATIVE_LYRIC_SUPPORT, false)) {
                        val metadataArg = args(0)
                        val metadata = args(0).cast<MediaMetadata>()
                        val builder = MediaMetadata.Builder(metadata)
                        builder.putString(XIAOMI_LYRIC_METADATA, null)
                        metadataArg.set(builder.build())
                        if (prefs.getBoolean(ConfigKeys.MORE_DEBUG, false)) {
                            YLog.debug("Force removed native lyric data")
                        }
                    }
                }

                after {
                    val i = instance.asResolver().firstField {
                        name = "this$0"
                    }.get() ?: return@after
                    val iRef = i.asResolver()
                    val mLyric = iRef.firstField { name = "mLyric" }.get()
                    val lrc = XposedHelpers.getAdditionalInstanceField(i, "TEMP_LRC") as? String
                    if (mLyric == null) {
                        if (lrc != null || latestLyricLrc.isNotEmpty()) {
                            YLog.debug("onUpdateLrc $mLyric ${lrc == null} ${latestLyricLrc.isEmpty()}")
                            forceUpdateLyric(i, lrc ?: latestLyricLrc)
                            return@after
                        }
                        val line =
                            XposedHelpers.getAdditionalInstanceField(
                                i,
                                "TEMP_LYRIC_LINE"
                            ) as? String
                        val mLyricCurrentVar =
                            iRef.firstField { name = "mLyricCurrentVar" }.get() ?: return@after
                        val currentLyric =
                            mLyricCurrentVar.asResolver().firstMethod { name = "get" }.invoke()
                        if (line != null && currentLyric == null) {
                            YLog.debug("onUpdateLine $line")
                            updateFallbackLine(i, line)
                        }
                    }
                }
            }
        }
    }

    private fun isLyriconInstalled(context: Context): Boolean {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(TARGET_LYRICON_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(TARGET_LYRICON_PACKAGE, 0)
            }
        }.isSuccess
    }

    private fun createLyricListener(): ActivePlayerListener {
        return object : ActivePlayerListener {

            override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
                currentProvider = providerInfo
                YLog.debug("onProviderChanged $currentProvider")
            }

            override fun onSongChanged(song: Song?) {
                runCatching {
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
                    YLog.debug("REAREye getSongLRC $latestLyricLrc")
                    YLog.debug("onSongChanged converted LRC length=${latestLyricLrc.length}")
                    YLog.debug("current instance size ${elements.size}")
                    elements.forEach {
                        forceUpdateLyric(it, latestLyricLrc)
                    }
                    if (elements.isNotEmpty()) {
                        latestLyricLrc = ""
                    }
                }.onFailure {
                    YLog.error(it)
                }
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit

            override fun onPositionChanged(position: Long) = Unit

            override fun onSeekTo(position: Long) = Unit

            override fun onSendText(text: String?) {
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

            override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

            override fun onDisplayRomaChanged(displayRoma: Boolean) = Unit
        }
    }

    private fun updateFallbackLyric(text: String) {
        elements.forEach { element ->
            XposedHelpers.setAdditionalInstanceField(element, "TEMP_LYRIC_LINE", text)
            updateFallbackLine(element, text)
        }
    }

    private fun updateFallbackLine(element: Any, text: String) {
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

    private fun forceUpdateLyric(element: Any, lrc: String) {
        YLog.debug("handle instance: $element")
        val ref = element.asResolver()
        val mLyric = ref.firstField { name = "mLyric" }
        val parserClz = "com.miui.maml.elements.MusicLyricParser".toClass().resolve()
        val nLyric = parserClz.firstMethod {
            name = "parseLyric"
            parameters(String::class.java)
        }.invoke(lrc)
        YLog.debug("parsed $nLyric")
        if (nLyric != null) {
            nLyric.asResolver().firstMethod { name = "decorate" }.invoke()
            mLyric.set(nLyric)
            ref.firstMethod { name = "updateLyric" }.invoke(nLyric)
            YLog.debug("Force Update Lyric")
            ref.firstField { name = "mMetadata" }.get<MediaMetadata>()?.also {
                XposedHelpers.setAdditionalInstanceField(
                    element,
                    "OLD_MEDIA_ID",
                    it.description.mediaId
                )
            }
            XposedHelpers.setAdditionalInstanceField(element, "TEMP_LRC", lrc)
        }
    }

    private fun normalizeForMiuiParser(rawLrc: String): String {
        if (rawLrc.isEmpty()) return rawLrc
        return rawLrc
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", "\r\n")
    }

    private companion object {
        private const val TARGET_LYRICON_PACKAGE = "io.github.proify.lyricon"
        private const val XIAOMI_LYRIC_METADATA = "android.media.metadata.LYRIC"
    }
}
