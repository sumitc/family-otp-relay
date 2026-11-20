/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.familyrelay.local.ContactCard;
import java.util.Locale;

final class PhoneProfile {
    private PhoneProfile() {
    }

    static String deviceName() {
        String model = Build.MODEL == null ? "" : Build.MODEL.replaceAll("\\p{Cntrl}", "").trim();
        return model.isEmpty() ? "My phone" : model.substring(0, Math.min(model.length(), 60));
    }

    static ContactCard read(Context context) {
        if (context.checkSelfPermission("android.permission.READ_PHONE_NUMBERS") != 0) {
            throw new IllegalStateException("Phone-number access is off. Allow it to create your QR automatically, or enter your number once.");
        }
        int subscription = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (subscription < 0) {
            throw new IllegalStateException("Choose a default SMS SIM in Android settings, then try again. Or enter that SIM's number once.");
        }
        TelephonyManager manager = (TelephonyManager)context.getSystemService(TelephonyManager.class);
        if (manager == null) {
            throw new IllegalStateException("This device cannot provide a SIM phone number. Enter your number once.");
        }
        manager = manager.createForSubscriptionId(subscription);
        try {
            String number;
            if (Build.VERSION.SDK_INT >= 33) {
                SubscriptionManager subscriptions = (SubscriptionManager)context.getSystemService(SubscriptionManager.class);
                if (subscriptions == null) {
                    throw new IllegalStateException("SIM details are unavailable. Enter your number once.");
                }
                number = subscriptions.getPhoneNumber(subscription);
            } else {
                number = manager.getLine1Number();
            }
            return PhoneProfile.fromSimNumber(number, manager.getSimCountryIso());
        }
        catch (SecurityException | UnsupportedOperationException e) {
            throw new IllegalStateException("Android could not provide this SIM's number. You can enter your number once instead.", e);
        }
    }

    static ContactCard fromSimNumber(String number, String country) {
        if (number == null || number.trim().isEmpty()) {
            throw new IllegalStateException("Your carrier has not made this SIM's number available to Android. Enter your number once to create your QR.");
        }
        String international = PhoneNumberUtils.formatNumberToE164((String)number, (String)(country == null ? "" : country.toUpperCase(Locale.ROOT)));
        try {
            return new ContactCard(PhoneProfile.deviceName(), international == null ? number : international);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException("Android could not supply a complete international number. Enter your number once, including the country code.", e);
        }
    }
}

