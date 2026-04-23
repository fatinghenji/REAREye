package hk.uwu.reareye.internal.notification;

import android.os.Bundle;

interface INotificationRouteBridgeService {
    boolean dispatch(String subchannel, in Bundle payload);
}
