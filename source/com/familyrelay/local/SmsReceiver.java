/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;
import com.familyrelay.local.ContactCard;
import com.familyrelay.local.OtpDetector;
import com.familyrelay.local.RelayNotifications;
import com.familyrelay.local.RelayStore;
import com.familyrelay.local.SendResultReceiver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;

public final class SmsReceiver
extends BroadcastReceiver {
    public static boolean hasSmsPermissions(Context context) {
        return context.checkSelfPermission("android.permission.RECEIVE_SMS") == 0 && context.checkSelfPermission("android.permission.SEND_SMS") == 0;
    }

    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }
        BroadcastReceiver.PendingResult result = this.goAsync();
        new Thread(() -> {
            try {
                SmsReceiver.process(context.getApplicationContext(), intent);
            }
            catch (IllegalStateException | SecurityException e) {
                Log.e((String)"Improm", (String)"SMS relay could not run. Review app permissions and local data.");
                RelayNotifications.error(context, "Forwarding could not run. Open the app to review activity.");
            }
            finally {
                result.finish();
            }
        }, "local-sms-relay").start();
    }

    static void process(Context context, Intent intent) {
        String ownPhone;
        String original;
        SmsMessage[] messages;
        RelayStore store = new RelayStore(context);
        if (context.checkSelfPermission("android.permission.RECEIVE_SMS") != 0) {
            return;
        }
        if (!(!store.enabled() || SmsReceiver.hasSmsPermissions(context) && RelayNotifications.allowed(context))) {
            store.setEnabled(false);
            RelayNotifications.refresh(context);
        }
        if ((messages = Telephony.Sms.Intents.getMessagesFromIntent((Intent)intent)) == null || messages.length == 0) {
            return;
        }
        String sender = messages[0].getOriginatingAddress();
        if (sender == null || sender.trim().isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (SmsMessage part : messages) {
            if (!sender.equals(part.getOriginatingAddress()) || part.getMessageBody() == null) {
                store.newEvent(sender, "", "Skipped: inconsistent SMS parts", 0);
                return;
            }
            text.append(part.getMessageBody());
        }
        String body = text.toString();
        boolean relayed = body.trim().startsWith("[Family Relay]");
        String string2 = original = relayed ? body.trim().substring("[Family Relay]".length()) : body;
        if (!OtpDetector.isOtp(original)) {
            return;
        }
        String fingerprint = SmsReceiver.fingerprint(sender, messages[0].getTimestampMillis(), body);
        if (!store.claimMessage(fingerprint, System.currentTimeMillis())) {
            return;
        }
        ContactCard profile = store.profile();
        String string3 = ownPhone = profile == null ? "" : profile.phone;
        if (relayed) {
            store.newOtpEvent(sender, ownPhone, store.isRegistered(sender) ? "Received forwarded OTP from registered user; skipped forwarding to prevent a loop" : "Skipped: forward from an unregistered sender", 0);
            return;
        }
        if (!store.allowsSender(sender)) {
            store.newOtpEvent(sender, "", "Skipped: OTP sender is not allowed", 0);
            return;
        }
        long consent = store.consentVersion();
        if (!store.enabled()) {
            store.newOtpEvent(sender, "", "Skipped: forwarding is paused", 0);
            return;
        }
        RelayNotifications.refresh(context);
        ContactCard recipient = store.recipient();
        if (recipient == null || profile == null) {
            store.setEnabled(false);
            RelayNotifications.refresh(context);
            store.newOtpEvent(sender, "", "Skipped: your profile or recipient is unavailable", 0);
            return;
        }
        String skip = SmsReceiver.participantSkipReason(sender, profile.phone, recipient.phone);
        if (skip != null) {
            store.newOtpEvent(sender, recipient.phone, skip, 0);
            return;
        }
        int subscription = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1));
        SmsReceiver.send(context, store, sender, body, recipient, subscription, consent);
    }

    static String participantSkipReason(String sender, String ownPhone, String recipientPhone) {
        if (ContactCard.samePhone(sender, ownPhone)) {
            return "Skipped: OTP SMS from your own number";
        }
        if (ContactCard.samePhone(sender, recipientPhone)) {
            return "Skipped: OTP SMS from selected recipient";
        }
        return null;
    }

    private static String fingerprint(String sender, long timestamp, String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((sender + "\n" + timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Local duplicate protection is unavailable.", e);
        }
    }

    private static void send(Context context, RelayStore store, String sender, String body, ContactCard recipient, int incomingSubscription, long consent) {
        int subscription;
        if (body.length() > 1600) {
            store.newOtpEvent(sender, recipient.phone, "Skipped: message exceeds the 1,600-character limit", 0);
            RelayNotifications.error(context, "A long OTP message was not forwarded. See Live activity.");
            return;
        }
        int n = subscription = store.incomingSim() && incomingSubscription >= 0 ? incomingSubscription : SubscriptionManager.getDefaultSmsSubscriptionId();
        if (subscription < 0) {
            store.newOtpEvent(sender, recipient.phone, "Failed: choose a default SMS SIM in Android settings", 0);
            RelayNotifications.error(context, "Set a default SMS SIM in Android settings, then try a new OTP.");
            return;
        }
        SmsManager manager = Build.VERSION.SDK_INT >= 31 ? ((SmsManager)context.getSystemService(SmsManager.class)).createForSubscriptionId(subscription) : SmsManager.getSmsManagerForSubscriptionId((int)subscription);
        String forwarded = "[Family Relay]\nFrom: " + sender + "\n" + body;
        ArrayList parts = manager.divideMessage(forwarded);
        String eventId = store.newOtpEvent(sender, recipient.phone, "Sending: awaiting carrier result", parts.size());
        ArrayList<PendingIntent> sent = new ArrayList<PendingIntent>();
        for (int i = 0; i < parts.size(); ++i) {
            Intent callback = new Intent(context, SendResultReceiver.class).setAction("com.familyrelay.local.SMS_SENT").setData(Uri.parse((String)("familyrelay://sent/" + eventId + "/" + i))).putExtra("event", eventId).putExtra("part", i);
            sent.add(PendingIntent.getBroadcast((Context)context, (int)0, (Intent)callback, (int)0x44000000));
        }
        try {
            boolean submitted = store.submitIfEnabled(recipient.phone, consent, () -> manager.sendMultipartTextMessage(recipient.phone, null, parts, sent, null));
            if (!submitted) {
                store.markSentResult(eventId, 0, false, "forwarding consent changed before sending");
            }
        }
        catch (IllegalArgumentException | SecurityException | UnsupportedOperationException e) {
            Log.e((String)"Improm", (String)"Carrier SMS submission failed. Check SIM and permissions.");
            store.markSentResult(eventId, 0, false, "SMS submission was rejected; check SIM and permissions");
            RelayNotifications.error(context, "SMS submission failed. Check Live activity and SIM settings.");
        }
    }
}

