package hk.uwu.reareye.hook.scopes.subscreencenter.modules.lyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import hk.uwu.reareye.lyrics.LyricParser
import hk.uwu.reareye.ui.config.ConfigKeys
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

class LyriconHook : YukiBaseHooker() {
    private val lyricParser = LyricParser()
    private var hasBoot = false
    private var hasListener = false
    private var receiverRegistered = false
    private var requestReceiverRegistered = false

    @Volatile
    private var latestLyricLrc: String = ""

    @Volatile
    private var element: Any? = null

    override fun onHook() {
        loadApp("com.android.systemui") {
            onAppLifecycle {
                onCreate {
                    val context = appContext ?: return@onCreate
                    registerLyricRequestReceiver(context, "com.android.systemui")
                    initializeLyricBridge(
                        context,
                        "com.android.systemui",
                        forwardToSubscreen = true
                    )
                }
            }
        }

        loadApp("com.xiaomi.subscreencenter") {
            onAppLifecycle {
                onCreate {
                    val context = appContext ?: return@onCreate
                    registerLyricReceiver(context, processName)
                    requestLyricFromSystemUi(context, processName)
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

    private fun initializeLyricBridge(
        context: Context,
        processName: String,
        forwardToSubscreen: Boolean
    ) {
        runCatching {
            BridgeCentral.initialize(context)
            if (!hasListener) {
                ActivePlayerDispatcher.addActivePlayerListener(
                    createLyricListener(processName, forwardToSubscreen)
                )
                hasListener = true
            }
            val hasLyriconApp = isLyriconInstalled(context)
            if (!hasBoot && !hasLyriconApp) {
                BridgeCentral.sendBootCompleted()
                hasBoot = true
            }
            if (hasLyriconApp) {
                XposedBridge.log("Lyricon app detected, skip BridgeCentral boot in $processName")
            }
            XposedBridge.log("Lyricon bridge initialized in $processName")
        }.onFailure {
            XposedBridge.log("Lyricon bridge init failed in $processName: ${it.message}")
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

    private fun createLyricListener(
        processName: String,
        forwardToSubscreen: Boolean,
    ): ActivePlayerListener {
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
                latestLyricLrc = lrc
                Log.d("REAREye-Lyric", "[$processName] get song lrc $lrc")
                XposedBridge.log("[$processName] onSongChanged converted LRC length=${lrc.length}")

                if (!forwardToSubscreen) return
                val context = appContext ?: return
                dispatchLyricToSubscreen(context, lrc)
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit

            override fun onPositionChanged(position: Long) = Unit

            override fun onSeekTo(position: Long) = Unit

            override fun onSendText(text: String?) = Unit

            override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

            override fun onDisplayRomaChanged(displayRoma: Boolean) = Unit
        }
    }

    private fun registerLyricReceiver(context: Context, processName: String) {
        if (receiverRegistered) return

        runCatching {
            val filter = IntentFilter(ACTION_SYNC_LYRIC)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_SYNC_LYRIC) return
                    val lrc =
                        normalizeForMiuiParser(intent.getStringExtra(EXTRA_LYRIC_LRC).orEmpty())
                    latestLyricLrc = lrc
                    XposedBridge.log("[$processName] received SongLRC $lrc")
                    XposedBridge.log("[$processName] receive lyric broadcast length=${lrc.length}")
                    forceUpdateLyric(lrc)
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }.onFailure {
            XposedBridge.log("[$processName] register lyric receiver failed: ${it.message}")
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

    private fun registerLyricRequestReceiver(context: Context, processName: String) {
        if (requestReceiverRegistered) return

        runCatching {
            val filter = IntentFilter(ACTION_REQUEST_LYRIC)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_REQUEST_LYRIC) return
                    val ctx = context ?: return
                    dispatchLyricToSubscreen(ctx, latestLyricLrc)
                    XposedBridge.log("[$processName] received lyric pull request")
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            requestReceiverRegistered = true
        }.onFailure {
            XposedBridge.log("[$processName] register request receiver failed: ${it.message}")
        }
    }

    private fun requestLyricFromSystemUi(context: Context, processName: String) {
        runCatching {
            val intent = Intent(ACTION_REQUEST_LYRIC).apply {
                setPackage(TARGET_SYSTEMUI_PACKAGE)
            }
            context.sendBroadcast(intent)
            XposedBridge.log("[$processName] request lyric from $TARGET_SYSTEMUI_PACKAGE")
        }.onFailure {
            XposedBridge.log("[$processName] request lyric failed: ${it.message}")
        }
    }

    private fun dispatchLyricToSubscreen(context: Context, lyricLrc: String) {
        runCatching {
            val intent = Intent(ACTION_SYNC_LYRIC).apply {
                setPackage(TARGET_SUBSCREEN_PACKAGE)
                putExtra(EXTRA_LYRIC_LRC, lyricLrc)
            }
            context.sendBroadcast(intent)
        }.onFailure {
            XposedBridge.log("dispatch lyric to $TARGET_SUBSCREEN_PACKAGE failed: ${it.message}")
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
        private const val ACTION_SYNC_LYRIC = "hk.uwu.reareye.action.SYNC_LYRIC"
        private const val ACTION_REQUEST_LYRIC = "hk.uwu.reareye.action.REQUEST_LYRIC"
        private const val EXTRA_LYRIC_LRC = "extra_lyric_lrc"
        private const val TARGET_SUBSCREEN_PACKAGE = "com.xiaomi.subscreencenter"
        private const val TARGET_SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val TARGET_LYRICON_PACKAGE = "io.github.proify.lyricon"
    }
}
