/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.familyrelay.local.ContactCard;
import java.util.Locale;

final class PhoneProfile {
    private PhoneProfile() {
    }

    /** Prefer the user-set device name; fall back to a readable hardware name. */
    static String deviceName(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String name = "";
        if (Build.VERSION.SDK_INT <= 31) {
            name = readSetting(resolver, "secure", "bluetooth_name");
        }
        if (name.isEmpty()) {
            name = readSetting(resolver, "global", Settings.Global.DEVICE_NAME);
        }
        if (name.isEmpty()) {
            name = readSetting(resolver, "secure", "device_name");
        }
        if (name.isEmpty()) {
            name = readSetting(resolver, "system", "device_name");
        }
        if (!name.isEmpty()) {
            return name;
        }
        String model = clean(Build.MODEL);
        if (!model.isEmpty() && !codeLike(model)) {
            return model;
        }
        String maker = clean(Build.MANUFACTURER);
        return maker.isEmpty() ? "My phone" : Character.toUpperCase(maker.charAt(0)) + maker.substring(1) + " phone";
    }

    private static String readSetting(ContentResolver resolver, String table, String key) {
        try {
            if ("secure".equals(table)) {
                return clean(Settings.Secure.getString(resolver, key));
            }
            if ("system".equals(table)) {
                return clean(Settings.System.getString(resolver, key));
            }
            return clean(Settings.Global.getString(resolver, key));
        }
        catch (RuntimeException e) {
            return "";
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String out = value.replaceAll("\\p{Cntrl}", "").trim();
        if (out.equalsIgnoreCase("android") || out.equalsIgnoreCase("unknown") || out.equalsIgnoreCase("default")) {
            return "";
        }
        return out.substring(0, Math.min(out.length(), 60));
    }

    private static boolean codeLike(String value) {
        return !value.contains(" ") && value.matches(".*\\d.*");
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
            return PhoneProfile.fromSimNumber(context, number, manager.getSimCountryIso());
        }
        catch (SecurityException | UnsupportedOperationException e) {
            throw new IllegalStateException("Android could not provide this SIM's number. You can enter your number once instead.", e);
        }
    }

    static ContactCard fromSimNumber(Context context, String number, String country) {
        if (number == null || number.trim().isEmpty()) {
            throw new IllegalStateException("Your carrier has not made this SIM's number available to Android. Enter your number once to create your QR.");
        }
        String international = PhoneNumberUtils.formatNumberToE164((String)number, (String)(country == null ? "" : country.toUpperCase(Locale.ROOT)));
        try {
            return new ContactCard(PhoneProfile.deviceName(context), international == null ? number : international);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException("Android could not supply a complete international number. Enter your number once, including the country code.", e);
        }
    }
}

