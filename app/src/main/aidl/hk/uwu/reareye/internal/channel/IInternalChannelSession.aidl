package hk.uwu.reareye.internal.channel;

import android.os.Bundle;

interface IInternalChannelSession {
    void onMessage(String channel, String subchannel, in Bundle payload);

    void onChannelClosed(String channel, String reason);
}
