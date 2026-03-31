package hk.uwu.reareye.hook.scopes.subscreencenter.modules.lyrics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import hk.uwu.reareye.lyrics.LyricParser
import hk.uwu.reareye.ui.config.ConfigKeys
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ActivePlayerMonitor

class LyriconHook : YukiBaseHooker() {
    private val lyricParser = LyricParser()

    @Volatile
    private var latestLyricLrc: String = ""

    @Volatile
    private var element: Any? = null

    @Volatile
    var monitor: ActivePlayerMonitor? = null

    override fun onHook() {
        loadApp("com.android.systemui") {
            onAppLifecycle {
                onCreate {
                    val context = appContext ?: return@onCreate
                    if (!isLyriconInstalled(context)) {
                        XposedBridge.log("Lyricon is not found, starting bundled central")
                        BridgeCentral.initialize(context)
                        BridgeCentral.sendBootCompleted()
                    } else {
                        XposedBridge.log("Lyricon is found, skip to start central")
                    }
                }
            }
        }

        loadApp("com.xiaomi.subscreencenter") {
            onAppLifecycle {
                onCreate {
                    val context = appContext ?: return@onCreate
                    val listener = createLyricListener()
                    val monitor =
                        LyriconFactory.createActivePlayerMonitor(context, listener = listener)
                    monitor.register()
                    XposedBridge.log("Register player monitor")
                }

                onTerminate {
                    monitor?.also {
                        it.unregister()
                        it.destroy()
                    }
                }
            }

            val clz = "com.miui.maml.elements.MusicControlScreenElement".toClass().resolve()
            clz.constructor().build().hookAll {
                after {
                    element = instance
                    if (latestLyricLrc.isNotEmpty()) {
                        forceUpdateLyric(latestLyricLrc)
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
            private var currentProvider: ProviderInfo? = null

            override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
                currentProvider = providerInfo
            }

            override fun onSongChanged(song: Song?) {
                val lrc = lyricParser.toLrc(
                    song,
                    prefs.getInt(
                        ConfigKeys.LYRIC_DISPLAY_MODE,
                        ConfigKeys.LYRIC_DISPLAY_MODE_DEFAULT,
                    )
                )
                latestLyricLrc = normalizeForMiuiParser(lrc)
                Log.d("REAREye-Lyric", "get song lrc $latestLyricLrc")
                XposedBridge.log("onSongChanged converted LRC length=${latestLyricLrc.length}")
                forceUpdateLyric(latestLyricLrc)
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit

            override fun onPositionChanged(position: Long) = Unit

            override fun onSeekTo(position: Long) = Unit

            override fun onSendText(text: String?) = Unit

            override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

            override fun onDisplayRomaChanged(displayRoma: Boolean) = Unit
        }
    }

    private fun forceUpdateLyric(lrc: String) {
        XposedBridge.log("current instance: $element")
        val ref = element?.asResolver() ?: return
        val mLyric = ref.firstField { name = "mLyric" }
        val parserClz = "com.miui.maml.elements.MusicLyricParser".toClass().resolve()
        val nLyric = parserClz.firstMethod {
            name = "parseLyric"
            parameters(String::class.java)
        }.invoke(lrc)
        XposedBridge.log("parsed $nLyric")
        if (nLyric != null) {
            nLyric.asResolver().firstMethod { name = "decorate" }.invoke()
            mLyric.set(nLyric)
            ref.firstMethod { name = "updateLyric" }.invoke(nLyric)
            XposedBridge.log("Force Update Lyric")
            latestLyricLrc = ""
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
    }
}
