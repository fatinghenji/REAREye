package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import hk.uwu.reareye.hook.hostbridge.HookHostBridgeClient
import hk.uwu.reareye.internal.notification.INotificationRouteBridgeService
import java.util.ArrayDeque

internal class NotificationRouteBridgeClient :
    HookHostBridgeClient<INotificationRouteBridgeService>(
        hostPackage = NotificationRouteBridgeContract.HOOK_HOST_PACKAGE,
    ) {
    private data class PendingDispatch(
        val subchannel: String,
        val payload: Bundle,
        val createdAt: Long,
    )

    companion object {
        private const val MAX_PENDING_DISPATCHES = 64
        private const val PENDING_DISPATCH_TTL_MS = 15_000L
    }

    override val requestAction: String =
        NotificationRouteBridgeContract.Action.REQUEST_BINDER

    override val serviceLabel: String = "Notification route bridge"

    private val pendingDispatches = ArrayDeque<PendingDispatch>()
    private val queueLock = Any()

    override fun asRemoteInterface(binder: IBinder?): INotificationRouteBridgeService? {
        return INotificationRouteBridgeService.Stub.asInterface(binder)
    }

    override fun onRemoteConnected(remote: INotificationRouteBridgeService) {
        drainPendingDispatches()
    }

    override fun onUnbound() {
        synchronized(queueLock) {
            pendingDispatches.clear()
        }
    }

    fun bind(
        context: Context,
        onConnected: (() -> Unit)? = null,
        onClosed: ((String) -> Unit)? = null,
        timeoutMs: Long = 900L,
    ): Boolean {
        return bindToHost(
            context = context,
            onConnected = onConnected,
            onClosed = onClosed,
            timeoutMs = timeoutMs,
        )
    }

    fun dispatch(subchannel: String, payload: Bundle = Bundle()): Boolean {
        val normalizedSubchannel = subchannel.trim()
        if (normalizedSubchannel.isBlank()) return false

        val payloadCopy = Bundle(payload)
        callRemote { remote ->
            remote.dispatch(normalizedSubchannel, payloadCopy)
        }?.let { return it }

        enqueuePendingDispatch(
            subchannel = normalizedSubchannel,
            payload = payloadCopy,
        )
        currentContext()?.let { bind(it, timeoutMs = 0L) }
        requestRebind()
        return true
    }

    private fun enqueuePendingDispatch(subchannel: String, payload: Bundle) {
        synchronized(queueLock) {
            pruneExpiredDispatchesLocked()
            pendingDispatches.addLast(
                PendingDispatch(
                    subchannel = subchannel,
                    payload = payload,
                    createdAt = System.currentTimeMillis(),
                )
            )
            while (pendingDispatches.size > MAX_PENDING_DISPATCHES) {
                pendingDispatches.removeFirst()
            }
        }
    }

    private fun drainPendingDispatches() {
        while (true) {
            val next = synchronized(queueLock) {
                pruneExpiredDispatchesLocked()
                pendingDispatches.firstOrNull()
            } ?: return

            val delivered = callRemote { remote ->
                remote.dispatch(next.subchannel, Bundle(next.payload))
            } ?: return
            if (!delivered) return

            synchronized(queueLock) {
                if (pendingDispatches.firstOrNull() === next) {
                    pendingDispatches.removeFirst()
                } else {
                    pendingDispatches.remove(next)
                }
            }
        }
    }

    private fun pruneExpiredDispatchesLocked() {
        val now = System.currentTimeMillis()
        while (pendingDispatches.isNotEmpty()) {
            val pending = pendingDispatches.firstOrNull() ?: return
            if (now - pending.createdAt <= PENDING_DISPATCH_TTL_MS) {
                return
            }
            pendingDispatches.removeFirst()
        }
    }
}
