/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.familyrelay.local.ContactCard;
import com.familyrelay.local.ControlReceiver;
import com.familyrelay.local.MainActivity;
import com.familyrelay.local.R;
import com.familyrelay.local.RelayStore;
import com.familyrelay.local.SmsReceiver;

public final class RelayNotifications {
    private static final String CHANNEL = "relay_status";
    private static final int RUNNING = 1;
    private static final int ERROR = 2;

    private RelayNotifications() {
    }

    public static boolean allowed(Context context) {
        NotificationManager manager = (NotificationManager)context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel == null || channel.getImportance() != 0);
    }

    private static Notification.Builder builder(Context context) {
        NotificationManager manager = (NotificationManager)context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, (CharSequence)"SMS forwarding status", 2));
        PendingIntent open = PendingIntent.getActivity((Context)context, (int)0, (Intent)new Intent(context, MainActivity.class), (int)0xC000000);
        return new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_relay).setContentIntent(open).setVisibility(0).setShowWhen(false);
    }

    public static void refresh(Context context) {
        RelayStore store = new RelayStore(context);
        NotificationManager manager = (NotificationManager)context.getSystemService(NotificationManager.class);
        if (!store.enabled()) {
            manager.cancel(1);
            return;
        }
        ContactCard recipient = store.recipient();
        if (recipient == null || !RelayNotifications.allowed(context) || !SmsReceiver.hasSmsPermissions(context)) {
            store.setEnabled(false);
            manager.cancel(1);
            return;
        }
        PendingIntent pause = PendingIntent.getBroadcast((Context)context, (int)0, (Intent)new Intent(context, ControlReceiver.class).setAction("com.familyrelay.local.PAUSE"), (int)0xC000000);
        manager.notify(1, RelayNotifications.builder(context).setContentTitle((CharSequence)"OTP forwarding is on").setContentText((CharSequence)("To " + recipient.name + " \u2022 Tap to manage")).setOngoing(true).addAction(new Notification.Action.Builder(null, (CharSequence)"Pause forwarding", pause).build()).build());
    }

    public static void error(Context context, String message) {
        if (RelayNotifications.allowed(context)) {
            ((NotificationManager)context.getSystemService(NotificationManager.class)).notify(2, RelayNotifications.builder(context).setContentTitle((CharSequence)"Improm needs attention").setContentText((CharSequence)message).setStyle((Notification.Style)new Notification.BigTextStyle().bigText((CharSequence)message)).setAutoCancel(true).build());
        }
    }
}

