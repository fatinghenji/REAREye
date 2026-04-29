package hk.uwu.reareye.hook.hostbridge

object HookHostBridgeContract {
    object Extras {
        const val BUNDLE = "bundle"
        const val BINDER = "binder"
        const val FORCE_SYNC = "forceSync"
    }

    object Reason {
        const val REMOTE_DIED = "remote_died"
        const val REMOTE_CLOSED = "remote_closed"
    }
}
