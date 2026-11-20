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

public final class SendResultReceiver
extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String reason;
        boolean success;
        String id = intent.getStringExtra("event");
        if (id == null) {
            Log.e((String)"Improm", (String)"SMS callback did not identify an event.");
            return;
        }
        int code = this.getResultCode();
        boolean bl = success = code == -1;
        if (intent.getBooleanExtra("noDefault", false)) {
            reason = "no default SMS SIM selected";
        } else {
            reason = switch (code) {
                case 2 -> "mobile radio is off";
                case 4 -> "no mobile service";
                case 3 -> "carrier rejected the SMS format";
                case 5 -> "Android SMS sending limit reached";
                case 7, 8 -> "destination is not permitted";
                default -> "carrier rejected SMS (code " + code + ")";
            };
        }
        try {
            new RelayStore(context).markSentResult(id, intent.getIntExtra("part", 0), success, reason);
            if (!success) {
                RelayNotifications.error(context, "An OTP could not be forwarded: " + reason + ".");
            }
        }
        catch (IllegalStateException e) {
            Log.e((String)"Improm", (String)"Could not record SMS result. Review local data and storage.");
            RelayNotifications.error(context, "Could not save an SMS result. Open the app to review activity.");
        }
    }
}

