package hk.uwu.reareye.hook.hostbridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import hk.uwu.reareye.internal.hostbridge.IHookHostBridgeBootstrap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class HookHostBridgeClient<Remote : IInterface>(
    private val hostPackage: String,
) {
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var remote: Remote? = null

    @Volatile
    private var remoteBinder: IBinder? = null

    @Volatile
    private var remoteDeathRecipient: IBinder.DeathRecipient? = null

    @Volatile
    private var connectLatch: CountDownLatch? = null

    @Volatile
    private var closedListener: ((String) -> Unit)? = null

    protected abstract val requestAction: String
    protected abstract val serviceLabel: String

    protected abstract fun asRemoteInterface(binder: IBinder?): Remote?

    protected open fun onBeforeRequest(forceSync: Boolean) {
    }

    protected open fun onRemoteConnected(remote: Remote) {
    }

    protected open fun onRemoteDisconnected(reason: String) {
    }

    fun isConnected(): Boolean = remote != null

    fun unbind() {
        clearRemote(notifyClosed = false)
    }

    protected fun bindToHost(
        context: Context,
        onConnected: (() -> Unit)? = null,
        onClosed: ((String) -> Unit)? = null,
        timeoutMs: Long = 1200L,
        retryTimeoutMs: Long = timeoutMs,
        retryWithForceSync: Boolean = false,
    ): Boolean {
        appContext = context.applicationContext
        if (onClosed != null) {
            closedListener = onClosed
        }

        remote?.let {
            onConnected?.invoke()
            return true
        }

        val connected = requestBridge(forceSync = false, timeoutMs = timeoutMs) ||
                (retryWithForceSync && requestBridge(forceSync = true, timeoutMs = retryTimeoutMs))
        if (connected) {
            onConnected?.invoke()
        }
        return connected
    }

    protected fun requireRemote(): Remote {
        return remote ?: error("$serviceLabel is not connected")
    }

    protected fun callRemote(block: (Remote) -> Boolean): Boolean? {
        val service = remote ?: return null
        return runCatching {
            block(service)
        }.onFailure {
            clearRemote(
                notifyClosed = true,
                reason = HookHostBridgeContract.Reason.REMOTE_DIED,
            )
        }.getOrNull()
    }

    protected fun requestRebind(forceSync: Boolean = false): Boolean {
        return requestBridge(forceSync = forceSync, timeoutMs = 0L)
    }

    protected fun currentContext(): Context? = appContext

    private fun requestBridge(forceSync: Boolean, timeoutMs: Long): Boolean {
        remote?.let { return true }

        val latch = synchronized(lock) {
            remote?.let { return true }
            connectLatch?.let { return@synchronized it }

            CountDownLatch(1).also { pending ->
                connectLatch = pending
                val context = appContext
                val ok = if (context == null) {
                    false
                } else {
                    runCatching {
                        onBeforeRequest(forceSync)
                        val callback = object : IHookHostBridgeBootstrap.Stub() {
                            override fun onBinderReady(binder: IBinder?) {
                                installRemote(asRemoteInterface(binder))
                            }
                        }
                        val bundle = Bundle().apply {
                            putBinder(
                                HookHostBridgeContract.Extras.BINDER,
                                callback.asBinder(),
                            )
                        }
                        val intent = Intent(requestAction)
                            .setPackage(hostPackage)
                            .putExtra(HookHostBridgeContract.Extras.BUNDLE, bundle)
                            .putExtra(HookHostBridgeContract.Extras.FORCE_SYNC, forceSync)
                        context.sendBroadcast(intent)
                        true
                    }.getOrDefault(false)
                }

                if (!ok) {
                    connectLatch = null
                    pending.countDown()
                }
            }
        }

        if (timeoutMs <= 0L) {
            return remote != null
        }

        val ok = runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
            .getOrDefault(false) && remote != null
        synchronized(lock) {
            if (connectLatch === latch) {
                connectLatch = null
            }
        }
        return ok
    }

    private fun installRemote(candidate: Remote?) {
        if (candidate == null) {
            synchronized(lock) {
                connectLatch?.countDown()
                connectLatch = null
            }
            return
        }

        val binder = candidate.asBinder()
        val deathRecipient = IBinder.DeathRecipient {
            clearRemote(
                notifyClosed = true,
                reason = HookHostBridgeContract.Reason.REMOTE_DIED,
            )
        }

        val installed = synchronized(lock) {
            releaseRemoteLocked()
            val linked = runCatching {
                binder.linkToDeath(deathRecipient, 0)
                true
            }.getOrDefault(false)
            if (!linked) {
                connectLatch?.countDown()
                connectLatch = null
                false
            } else {
                remote = candidate
                remoteBinder = binder
                remoteDeathRecipient = deathRecipient
                connectLatch?.countDown()
                connectLatch = null
                true
            }
        }

        if (installed) {
            onRemoteConnected(candidate)
        }
    }

    private fun clearRemote(
        notifyClosed: Boolean,
        reason: String = HookHostBridgeContract.Reason.REMOTE_CLOSED,
    ) {
        val hadRemote = synchronized(lock) {
            val existed = remote != null
            releaseRemoteLocked()
            connectLatch?.countDown()
            connectLatch = null
            existed
        }

        if (hadRemote) {
            onRemoteDisconnected(reason)
            if (notifyClosed) {
                closedListener?.invoke(reason)
            }
        }
    }

    private fun releaseRemoteLocked() {
        val binder = remoteBinder
        val deathRecipient = remoteDeathRecipient
        if (binder != null && deathRecipient != null) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
        remote = null
        remoteBinder = null
        remoteDeathRecipient = null
    }
}
