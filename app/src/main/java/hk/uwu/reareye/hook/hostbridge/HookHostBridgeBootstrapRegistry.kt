package hk.uwu.reareye.hook.hostbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat
import hk.uwu.reareye.internal.hostbridge.IHookHostBridgeBootstrap
import java.util.concurrent.atomic.AtomicBoolean

class HookHostBridgeBootstrapRegistry(
    private val action: String,
    private val binderProvider: () -> IBinder?,
    private val onRequest: (Intent) -> Unit = {},
    private val logger: ((String) -> Unit)? = null,
) {
    private val registered = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != action) return

            onRequest(intent)

            val callbackBinder = intent
                .getBundleExtra(HookHostBridgeContract.Extras.BUNDLE)
                ?.getBinder(HookHostBridgeContract.Extras.BINDER)
            val callback = IHookHostBridgeBootstrap.Stub.asInterface(callbackBinder)

            runCatching {
                callback?.onBinderReady(binderProvider())
            }.onFailure {
                logger?.invoke(
                    "host bridge bootstrap reply failed action=$action err=${it.message}"
                )
            }
        }
    }

    fun register(
        context: Context,
        requiredPermission: String? = null,
    ): Boolean {
        if (!registered.compareAndSet(false, true)) return true

        val ok = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                requiredPermission,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
            true
        }.onFailure {
            registered.set(false)
            logger?.invoke(
                "host bridge bootstrap register failed action=$action err=${it.message}"
            )
        }.getOrDefault(false)

        return ok
    }
}
