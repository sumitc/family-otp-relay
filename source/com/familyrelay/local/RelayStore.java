/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.familyrelay.local.ContactCard;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RelayStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;

    public RelayStore(Context context) {
        this.prefs = context.getSharedPreferences("family_relay", 0);
        this.migrate();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void migrate() {
        Object object = LOCK;
        synchronized (object) {
            if (this.prefs.getInt("activity_schema", 0) >= 2) {
                return;
            }
            JSONArray events = this.readArray("events");
            try {
                for (int i = 0; i < events.length(); ++i) {
                    JSONObject event = events.getJSONObject(i);
                    String status = event.getString("status");
                    boolean otp = status.startsWith("OTP detected") || status.startsWith("Ignored: OTP sender") || status.startsWith("Skipped: OTP SMS") || status.startsWith("Received forwarded OTP") || status.startsWith("Sending:") || status.startsWith("Sent to carrier") || status.startsWith("Failed:") || status.startsWith("Carrier result") || status.startsWith("Skipped: message exceeds") || status.startsWith("Skipped: forward from");
                    event.put("kind", (Object)(otp ? "otp" : "system")).put("source", (Object)"SMS");
                    if (!status.startsWith("Skipped: OTP SMS from selected recipient;")) continue;
                    event.put("status", (Object)"Skipped: OTP SMS from selected recipient");
                }
            }
            catch (JSONException e) {
                this.persist(this.prefs.edit().putBoolean("enabled", false));
                throw new IllegalStateException("Could not upgrade local activity. Forwarding has been paused.", e);
            }
            this.persist(this.prefs.edit().putString("events", events.toString()).putInt("activity_schema", 2).remove("recipient_test_mode"));
        }
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        this.prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        this.prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void persist(SharedPreferences.Editor editor) {
        if (!editor.commit()) {
            Log.e((String)"Improm", (String)"Could not persist local relay state.");
            throw new IllegalStateException("Could not save local data. Check your phone's available storage.");
        }
    }

    public ContactCard profile() {
        String name = this.prefs.getString("profile_name", "");
        String phone = this.prefs.getString("profile_phone", "");
        if (name.isEmpty() || phone.isEmpty()) {
            return null;
        }
        try {
            return new ContactCard(name, phone);
        }
        catch (IllegalArgumentException e) {
            this.persist(this.prefs.edit().putBoolean("enabled", false));
            throw new IllegalStateException("Your saved profile is invalid. Forwarding has been paused.", e);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void saveProfile(ContactCard profile) {
        Object object = LOCK;
        synchronized (object) {
            if (this.enabled()) {
                throw new IllegalStateException("Pause forwarding before changing your profile.");
            }
            if (this.contacts().stream().anyMatch(contact -> contact.phone.equals(profile.phone))) {
                throw new IllegalArgumentException("Your own number cannot be a registered recipient.");
            }
            this.persist(this.prefs.edit().putString("profile_name", profile.name).putString("profile_phone", profile.phone));
        }
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(this.prefs.getString(key, "[]"));
        }
        catch (JSONException e) {
            Log.e((String)"Improm", (String)("Invalid local " + key + "; forwarding stopped."));
            this.persist(this.prefs.edit().putBoolean("enabled", false));
            throw new IllegalStateException("Local data could not be read. Forwarding has been paused.", e);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<ContactCard> contacts() {
        Object object = LOCK;
        synchronized (object) {
            ArrayList<ContactCard> result = new ArrayList<ContactCard>();
            JSONArray array = this.readArray("contacts");
            try {
                for (int i = 0; i < array.length(); ++i) {
                    JSONObject item = array.getJSONObject(i);
                    result.add(new ContactCard(item.getString("name"), item.getString("phone")));
                }
            }
            catch (IllegalArgumentException | JSONException e) {
                this.persist(this.prefs.edit().putBoolean("enabled", false));
                throw new IllegalStateException("A saved contact is invalid. Forwarding has been paused.", e);
            }
            return result;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void addContact(ContactCard contact) {
        Object object = LOCK;
        synchronized (object) {
            ContactCard me = this.profile();
            if (me == null) {
                throw new IllegalArgumentException("Create your own contact QR first.");
            }
            if (ContactCard.samePhone(me.phone, contact.phone)) {
                throw new IllegalArgumentException("You cannot register your own number as a recipient.");
            }
            List<ContactCard> contacts = this.contacts();
            for (ContactCard existing : contacts) {
                if (!existing.phone.equals(contact.phone)) continue;
                throw new IllegalArgumentException("That phone number is already registered.");
            }
            if (contacts.size() >= 50) {
                throw new IllegalArgumentException("Remove a contact before adding more than 50 people.");
            }
            contacts.add(contact);
            this.writeContacts(contacts);
            if (this.selectedPhone().isEmpty()) {
                this.persist(this.prefs.edit().putString("selected", contact.phone));
            }
            this.newEvent(contact.phone, me.phone, "Contact registered", 0);
        }
    }

    private void writeContacts(List<ContactCard> contacts) {
        JSONArray array = new JSONArray();
        try {
            for (ContactCard contact : contacts) {
                array.put((Object)new JSONObject().put("name", (Object)contact.name).put("phone", (Object)contact.phone));
            }
        }
        catch (JSONException e) {
            throw new IllegalStateException("Could not save contacts.", e);
        }
        this.persist(this.prefs.edit().putString("contacts", array.toString()));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removeContact(String phone) {
        Object object = LOCK;
        synchronized (object) {
            List<ContactCard> contacts = this.contacts();
            contacts.removeIf(contact -> contact.phone.equals(phone));
            this.writeContacts(contacts);
            if (this.selectedPhone().equals(phone)) {
                this.persist(this.prefs.edit().putString("selected", "").putBoolean("enabled", false).putLong("consent_version", this.consentVersion() + 1L));
            }
            this.newEvent(phone, "", "Contact removed", 0);
        }
    }

    public String selectedPhone() {
        return this.prefs.getString("selected", "");
    }

    public ContactCard recipient() {
        for (ContactCard contact : this.contacts()) {
            if (!contact.phone.equals(this.selectedPhone())) continue;
            return contact;
        }
        return null;
    }

    public boolean isRegistered(String phone) {
        return this.contacts().stream().anyMatch(contact -> ContactCard.samePhone(contact.phone, phone));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void select(String phone) {
        Object object = LOCK;
        synchronized (object) {
            if (this.enabled()) {
                throw new IllegalStateException("Pause forwarding before changing the recipient.");
            }
            boolean registered = this.contacts().stream().anyMatch(c -> c.phone.equals(phone));
            if (!registered) {
                throw new IllegalArgumentException("Register this contact before selecting them.");
            }
            this.persist(this.prefs.edit().putString("selected", phone));
        }
    }

    public boolean enabled() {
        return this.prefs.getBoolean("enabled", false);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void setEnabled(boolean enabled) {
        Object object = LOCK;
        synchronized (object) {
            if (enabled && (this.profile() == null || this.recipient() == null)) {
                throw new IllegalStateException("Create your profile and select a registered recipient first.");
            }
            SharedPreferences.Editor editor = this.prefs.edit().putBoolean("enabled", enabled);
            if (enabled != this.enabled()) {
                editor.putLong("consent_version", this.consentVersion() + 1L);
            }
            this.persist(editor);
        }
    }

    long consentVersion() {
        return this.prefs.getLong("consent_version", 0L);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    boolean submitIfEnabled(String expectedRecipient, long expectedConsent, Runnable submission) {
        Object object = LOCK;
        synchronized (object) {
            ContactCard current = this.recipient();
            if (!this.enabled() || this.consentVersion() != expectedConsent || this.profile() == null || current == null || !expectedRecipient.equals(current.phone)) {
                return false;
            }
            submission.run();
            return true;
        }
    }

    public boolean incomingSim() {
        return this.prefs.getBoolean("incoming_sim", true);
    }

    public void setIncomingSim(boolean value) {
        this.persist(this.prefs.edit().putBoolean("incoming_sim", value));
    }

    public String senderFilter() {
        return this.prefs.getString("sender_filter", "");
    }

    public void setSenderFilter(String filter) {
        this.persist(this.prefs.edit().putString("sender_filter", filter.trim()));
    }

    public boolean allowsSender(String sender) {
        String filter = this.senderFilter();
        if (filter.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        for (String allowed : filter.split("[,\\n]")) {
            if (allowed.trim().isEmpty() || !allowed.trim().equalsIgnoreCase(sender.trim())) continue;
            return true;
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean claimMessage(String fingerprint, long now) {
        Object object = LOCK;
        synchronized (object) {
            JSONArray saved = this.readArray("recent");
            JSONArray retained = new JSONArray();
            try {
                for (int i = 0; i < saved.length(); ++i) {
                    JSONObject item = saved.getJSONObject(i);
                    if (now - item.getLong("time") >= 600000L) continue;
                    if (fingerprint.equals(item.getString("fingerprint"))) {
                        return false;
                    }
                    if (i < saved.length() - 127) continue;
                    retained.put((Object)item);
                }
                retained.put((Object)new JSONObject().put("fingerprint", (Object)fingerprint).put("time", now));
            }
            catch (JSONException e) {
                throw new IllegalStateException("Could not check duplicate SMS.", e);
            }
            this.persist(this.prefs.edit().putString("recent", retained.toString()));
            return true;
        }
    }

    public String newEvent(String sender, String recipient, String status, int partCount) {
        return this.addEvent(sender, recipient, status, partCount, "system");
    }

    public String newOtpEvent(String sender, String recipient, String status, int partCount) {
        return this.addEvent(sender, recipient, status, partCount, "otp");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private String addEvent(String sender, String recipient, String status, int partCount, String kind) {
        Object object = LOCK;
        synchronized (object) {
            String id = UUID.randomUUID().toString();
            JSONArray events = this.readArray("events");
            JSONArray updated = new JSONArray();
            try {
                updated.put((Object)new JSONObject().put("id", (Object)id).put("time", System.currentTimeMillis()).put("sender", (Object)sender).put("recipient", (Object)recipient).put("status", (Object)status).put("kind", (Object)kind).put("source", (Object)"SMS").put("parts", partCount).put("sent", (Object)new JSONArray()).put("failed", false));
                for (int i = 0; i < Math.min(events.length(), 99); ++i) {
                    updated.put((Object)events.getJSONObject(i));
                }
            }
            catch (JSONException e) {
                throw new IllegalStateException("Could not save relay activity.", e);
            }
            this.persist(this.prefs.edit().putString("events", updated.toString()));
            return id;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void markSentResult(String id, int part, boolean success, String error) {
        Object object = LOCK;
        synchronized (object) {
            JSONArray events = this.readArray("events");
            try {
                for (int i = 0; i < events.length(); ++i) {
                    JSONObject event = events.getJSONObject(i);
                    if (!id.equals(event.getString("id"))) continue;
                    if (!success) {
                        event.put("failed", true).put("status", (Object)("Failed: " + error));
                    } else if (!event.getBoolean("failed")) {
                        JSONArray completed = event.getJSONArray("sent");
                        boolean duplicate = false;
                        for (int j = 0; j < completed.length(); ++j) {
                            duplicate |= completed.getInt(j) == part;
                        }
                        if (!duplicate) {
                            completed.put(part);
                        }
                        int count = event.getInt("parts");
                        if (event.getJSONArray("sent").length() == count) {
                            event.put("status", (Object)"Sent to carrier; delivery unconfirmed");
                        }
                    }
                    this.persist(this.prefs.edit().putString("events", events.toString()));
                    return;
                }
            }
            catch (JSONException e) {
                throw new IllegalStateException("Could not update SMS status.", e);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public JSONArray events() {
        Object object = LOCK;
        synchronized (object) {
            return this.readArray("events");
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public JSONArray otpEvents() {
        Object object = LOCK;
        synchronized (object) {
            JSONArray result = new JSONArray();
            JSONArray events = this.readArray("events");
            try {
                for (int i = 0; i < events.length(); ++i) {
                    JSONObject event = events.getJSONObject(i);
                    if (!"otp".equals(event.getString("kind"))) continue;
                    result.put((Object)event);
                }
            }
            catch (JSONException e) {
                throw new IllegalStateException("Could not read OTP activity.", e);
            }
            return result;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clearEvents() {
        Object object = LOCK;
        synchronized (object) {
            this.persist(this.prefs.edit().putString("events", "[]"));
        }
    }
}

