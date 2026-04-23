package hk.uwu.reareye.internal.hostbridge;

import android.os.IBinder;

interface IHookHostBridgeBootstrap {
    void onBinderReady(IBinder binder);
}
