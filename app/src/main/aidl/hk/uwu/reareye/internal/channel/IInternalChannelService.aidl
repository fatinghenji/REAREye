package hk.uwu.reareye.internal.channel;

import android.os.Bundle;
import hk.uwu.reareye.internal.channel.IInternalChannelSession;

interface IInternalChannelService {
    void registerReceiver(String channel, IInternalChannelSession session);

    void unregisterReceiver(String channel);

    boolean post(String channel, String subchannel, in Bundle payload);
}
