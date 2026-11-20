/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.familyrelay.local.RelayNotifications;
import com.familyrelay.local.RelayStore;

public final class ControlReceiver
extends BroadcastReceiver {
    public static final String PAUSE = "com.familyrelay.local.PAUSE";

    public void onReceive(Context context, Intent intent) {
        try {
            if (PAUSE.equals(intent.getAction())) {
                new RelayStore(context).setEnabled(false);
            }
            RelayNotifications.refresh(context);
        }
        catch (IllegalStateException e) {
            Log.e((String)"Improm", (String)"Could not restore relay status. Review local data and permissions.");
            RelayNotifications.error(context, "Open the app to review local data and forwarding status.");
        }
    }
}

