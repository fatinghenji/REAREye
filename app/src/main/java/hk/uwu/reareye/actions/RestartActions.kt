package hk.uwu.reareye.actions

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge

object RestartActions {

    const val RESTART_ACTION = "REAREYE_ACTION_RESTART_APPS"

    class RestartHook : YukiBaseHooker() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onHook() {
            loadSystem {
                val clz = "com.android.server.policy.PhoneWindowManager".toClass().resolve()
                clz.firstMethod {
                    name = "init"
                }.hook().after {
                    val mContext =
                        this.instance.asResolver().firstField { name = "mContext" }.get<Context>()
                    val intentFilter = IntentFilter().apply {
                        addAction(RESTART_ACTION)
                    }
                    mContext?.registerReceiver(
                        mRestartReceiver,
                        intentFilter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                }
            }
        }

    }

    private fun forceStopPackage(context: Context, packageName: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.asResolver().firstMethod {
            name = "forceStopPackage"
            parameters(String::class.java)
        }.invoke(packageName)
    }

    private val mRestartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action ?: return
                if (action == RESTART_ACTION) {
                    val pkg = intent.getStringExtra("packageName") ?: return
                    context?.let { forceStopPackage(it, pkg) }
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
        }
    }

    fun broadcastStopPackage(context: Context, packageName: String) {
        val intent = Intent(RESTART_ACTION).apply {
            putExtra("packageName", packageName)
            setPackage("android")
        }
        context.sendBroadcast(intent)
    }
}
