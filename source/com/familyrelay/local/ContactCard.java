/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import org.json.JSONException;
import org.json.JSONObject;

public final class ContactCard {
    public final String name;
    public final String phone;

    public ContactCard(String name, String phone) {
        String cleanName;
        String string2 = cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 60 || cleanName.matches("(?s).*\\p{Cntrl}.*")) {
            throw new IllegalArgumentException("Enter a name between 1 and 60 characters.");
        }
        this.name = cleanName;
        this.phone = ContactCard.normalizePhone(phone);
    }

    public static String normalizePhone(String phone) {
        String clean;
        String string2 = clean = phone == null ? "" : phone.replaceAll("[ ()-]", "");
        if (!clean.matches("\\+[1-9][0-9]{6,14}")) {
            throw new IllegalArgumentException("Use an international phone number, for example +919876543210.");
        }
        return clean;
    }

    public static boolean samePhone(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String left = a.replaceAll("[ ()-]", "");
        String right = b.replaceAll("[ ()-]", "");
        if (left.startsWith("00")) {
            left = "+" + left.substring(2);
        }
        if (right.startsWith("00")) {
            right = "+" + right.substring(2);
        }
        return left.replaceFirst("^\\+", "").equals(right.replaceFirst("^\\+", ""));
    }

    public String toQr() {
        try {
            return new JSONObject().put("type", (Object)"family-relay-contact").put("version", 1).put("name", (Object)this.name).put("phone", (Object)this.phone).toString();
        }
        catch (JSONException e) {
            throw new IllegalStateException("Could not encode contact QR.", e);
        }
    }

    public static ContactCard fromQr(String content) {
        if (content == null || content.length() > 1000) {
            throw new IllegalArgumentException("This is not an Improm contact QR.");
        }
        try {
            JSONObject object = new JSONObject(content);
            if (!("family-relay-contact".equals(object.getString("type")) && object.getInt("version") == 1 && object.get("name") instanceof String && object.get("phone") instanceof String)) {
                throw new IllegalArgumentException("This is not a supported Improm contact QR.");
            }
            return new ContactCard(object.getString("name"), object.getString("phone"));
        }
        catch (JSONException e) {
            throw new IllegalArgumentException("This is not a valid Improm contact QR.", e);
        }
    }
}

