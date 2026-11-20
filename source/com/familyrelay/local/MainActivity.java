/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.familyrelay.local.ContactCard;
import com.familyrelay.local.OtpDetector;
import com.familyrelay.local.PhoneProfile;
import com.familyrelay.local.RelayNotifications;
import com.familyrelay.local.RelayStore;
import com.familyrelay.local.ScanActivity;
import com.familyrelay.local.SmsReceiver;
import com.familyrelay.local.UiProtection;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity
extends Activity {
    private static final int ORANGE = Color.rgb((int)198, (int)67, (int)9);
    private static final int SOFT_ORANGE = Color.rgb((int)255, (int)235, (int)219);
    private static final int INK = Color.rgb((int)43, (int)33, (int)27);
    private static final int MUTED = Color.rgb((int)114, (int)99, (int)88);
    private static final int BACKGROUND = Color.rgb((int)255, (int)248, (int)241);
    private static final int SMS_PERMISSION_REQUEST = 10;
    private static final int SCAN_REQUEST = 20;
    private static final int DETECTION_PERMISSION_REQUEST = 30;
    private static final int PHONE_PERMISSION_REQUEST = 40;
    private RelayStore store;
    private LinearLayout content;
    private String tab = "Live";
    private ScrollView pageScroll;
    private boolean automaticProfileAttempted;
    private String profileStatus = "QR from your SMS SIM.";
    private String cachedQrText;
    private Bitmap cachedQr;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshActivity = () -> {
        if ("Live".equals(this.tab) && !this.isFinishing()) {
            this.render(true);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener activityListener = (prefs, key) -> {
        if ("events".equals(key) || "enabled".equals(key) || "selected".equals(key)) {
            this.uiHandler.removeCallbacks(this.refreshActivity);
            this.uiHandler.postDelayed(this.refreshActivity, 200L);
        }
    };

    public void onCreate(Bundle state) {
        super.onCreate(state);
        this.getWindow().getDecorView().setSystemUiVisibility(8208);
        this.store = new RelayStore((Context)this);
        if (state != null) {
            this.tab = "People".equals(state.getString("tab")) ? "People" : "Live";
        }
        this.render();
    }

    protected void onResume() {
        super.onResume();
        this.store.registerListener(this.activityListener);
        try {
            RelayNotifications.refresh((Context)this);
            if (this.store.profile() == null && !this.automaticProfileAttempted && !this.store.enabled() && this.checkSelfPermission("android.permission.READ_PHONE_NUMBERS") == 0) {
                this.detectProfile();
            }
            this.render();
        }
        catch (IllegalStateException e) {
            this.showError(e.getMessage());
        }
    }

    protected void onPause() {
        this.store.unregisterListener(this.activityListener);
        this.uiHandler.removeCallbacks(this.refreshActivity);
        super.onPause();
    }

    protected void onSaveInstanceState(Bundle state) {
        state.putString("tab", this.tab);
        super.onSaveInstanceState(state);
    }

    private int dp(int value) {
        return Math.round((float)value * this.getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView((Context)this);
        view.setText((CharSequence)value);
        view.setTextSize((float)size);
        view.setTextColor(color);
        view.setLineSpacing((float)this.dp(3), 1.0f);
        if (bold) {
            view.setTypeface(Typeface.create((String)"sans-serif", (int)1));
        }
        view.setPadding(0, this.dp(4), 0, this.dp(6));
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout((Context)this);
        layout.setOrientation(1);
        return layout;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius((float)this.dp(radius));
        return shape;
    }

    private LinearLayout card() {
        LinearLayout card = this.column();
        card.setPadding(this.dp(20), this.dp(16), this.dp(20), this.dp(16));
        card.setBackground((Drawable)this.background(-1, 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, this.dp(12), 0, 0);
        this.content.addView((View)card, (ViewGroup.LayoutParams)params);
        return card;
    }

    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button((Context)this);
        button.setText((CharSequence)label);
        button.setAllCaps(false);
        button.setTextSize(15.0f);
        button.setTextColor(primary ? -1 : ORANGE);
        button.setBackground((Drawable)this.background(primary ? ORANGE : SOFT_ORANGE, 16));
        button.setPadding(this.dp(14), this.dp(10), this.dp(14), this.dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, this.dp(52));
        params.topMargin = this.dp(12);
        button.setLayoutParams((ViewGroup.LayoutParams)params);
        button.setOnClickListener(v -> {
            try {
                action.run();
            }
            catch (IllegalArgumentException | IllegalStateException e) {
                this.showError(e.getMessage());
            }
        });
        return button;
    }

    /** Compact info trigger: shows a short explanation on tap. */
    private TextView info(String explanation) {
        TextView view = this.text("(i)", 12, MUTED, true);
        view.setPadding(this.dp(6), this.dp(2), this.dp(6), this.dp(2));
        view.setOnClickListener(v -> UiProtection.show(new AlertDialog.Builder((Context)this)
            .setTitle((CharSequence)"Info")
            .setMessage((CharSequence)explanation)
            .setPositiveButton((CharSequence)"Got it", null)
            .create()));
        return view;
    }

    private void render() {
        this.render(false);
    }

    private void render(boolean preserveScroll) {
        ScrollView scroll;
        int scrollY = preserveScroll && this.pageScroll != null ? this.pageScroll.getScrollY() : 0;
        LinearLayout root = this.column();
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        this.pageScroll = scroll = new ScrollView((Context)this);
        scroll.setFillViewport(true);
        this.content = this.column();
        this.content.setPadding(this.dp(22), this.dp(18), this.dp(22), this.dp(28));
        scroll.addView((View)this.content);
        root.addView((View)scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        TextView brand = this.text("IMPROM   /   ALWAYS LOCAL", 11, ORANGE, true);
        this.content.addView((View)brand);
        this.content.addView((View)this.text("People".equals(this.tab) ? "Your trusted circle." : "Live activity", 30, INK, true));
        if ("People".equals(this.tab)) {
            this.people();
        } else {
            this.live();
        }
        LinearLayout navigation = new LinearLayout((Context)this);
        navigation.setBackgroundColor(-1);
        navigation.setPadding(this.dp(12), this.dp(8), this.dp(12), this.dp(8));
        for (String name : new String[]{"Live", "People"}) {
            TextView item = this.text("Live".equals(name) ? "Live" : "People", 14, this.tab.equals(name) ? ORANGE : MUTED, this.tab.equals(name));
            item.setGravity(17);
            item.setPadding(this.dp(8), this.dp(12), this.dp(8), this.dp(12));
            item.setBackground((Drawable)this.background(this.tab.equals(name) ? SOFT_ORANGE : -1, 16));
            item.setOnClickListener(v -> {
                this.tab = name;
                this.render();
            });
            navigation.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        }
        root.addView((View)navigation);
        this.setContentView((View)root);
        UiProtection.protect(this.getWindow());
        root.requestApplyInsets();
        if (preserveScroll) {
            scroll.post(() -> scroll.scrollTo(0, scrollY));
        }
    }

    private void live() {
        ContactCard recipient = this.store.recipient();
        LinearLayout status = this.card();
        status.setBackground((Drawable)this.background(SOFT_ORANGE, 24));
        status.addView((View)this.text(this.store.enabled() ? "FORWARDING ON" : "PAUSED", 12, ORANGE, true));
        status.addView((View)this.text(recipient == null ? "Choose your person" : recipient.name, 25, INK, true));
        if (recipient != null) status.addView((View)this.text(recipient.phone, 14, MUTED, false));
        Switch toggle = new Switch((Context)this);
        toggle.setText((CharSequence)"Auto-forward OTPs");
        toggle.setTextSize(15.0f);
        toggle.setTextColor(INK);
        toggle.setPadding(0, this.dp(18), 0, this.dp(8));
        toggle.setChecked(this.store.enabled());
        toggle.setOnCheckedChangeListener((view, checked) -> {
            if (checked) {
                view.setChecked(false);
                this.requestEnable();
            } else if (this.store.enabled()) {
                this.store.setEnabled(false);
                RelayNotifications.refresh((Context)this);
                this.render();
            }
        });
        status.addView((View)toggle);
        status.addView((View)this.info("New OTPs only. One person. Pause anytime."));
        if (this.checkSelfPermission("android.permission.RECEIVE_SMS") != 0) {
            status.addView((View)this.button("Enable SMS detection", false, () -> UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Enable SMS detection?").setMessage((CharSequence)"Check new SMS for OTPs locally.").setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Allow detection", (dialog, which) -> this.requestPermissions(new String[]{"android.permission.RECEIVE_SMS"}, 30)).create())));
        } else {
            status.addView((View)this.text("SMS detection ready", 12, ORANGE, true));
        }
        if (this.store.profile() == null || recipient == null) {
            status.addView((View)this.button("Set up People & QR", true, () -> {
                this.tab = "People";
                this.render();
            }));
        }
        status.addView((View)this.button("Forwarding settings", false, this::forwardingSettings));
        this.activity();
    }

    private void forwardingSettings() {
        LinearLayout rules = this.dialogLayout();
        rules.addView((View)this.text("SMS RULES", 12, ORANGE, true));
        rules.addView((View)this.text(this.store.senderFilter().isEmpty() ? "Senders: all" : "Senders: " + this.store.senderFilter(), 14, MUTED, false));
        rules.addView((View)this.button("Choose allowed senders", false, this::editSenders));
        Switch sim = new Switch((Context)this);
        sim.setText((CharSequence)"Use receiving SIM");
        sim.setTextColor(INK);
        sim.setTextSize(14.0f);
        sim.setPadding(0, this.dp(16), 0, this.dp(6));
        sim.setChecked(this.store.incomingSim());
        sim.setOnCheckedChangeListener((view, checked) -> this.store.setIncomingSim(checked));
        rules.addView((View)sim);
        rules.addView((View)this.info("Falls back to default SMS SIM."));
        rules.addView((View)this.info("No internet. Detection runs on-device."));
        rules.addView((View)this.info("Codes never saved. Carrier charges apply."));
        rules.addView((View)this.info("One-way only. Recipient needs own setup."));
        rules.addView((View)this.button("Try offline detector", false, this::testDetector));
        ScrollView scroll = new ScrollView((Context)this);
        scroll.addView((View)rules);
        UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Forwarding settings").setView((View)scroll).setPositiveButton((CharSequence)"Done", null).create());
    }

    private void people() {
        ContactCard me = this.store.profile();
        LinearLayout profile = this.card();
        profile.addView((View)this.text("YOUR CONTACT QR", 12, ORANGE, true));
        profile.addView((View)this.text(me == null ? "Not set up" : me.name, 23, INK, true));
        if (me == null) {
            profile.addView((View)this.text(this.profileStatus, 14, MUTED, false));
        profile.addView((View)this.button("Generate my QR", true, this::requestAutomaticProfile));
        profile.addView((View)this.button("Enter number", false, this::editProfile));
        } else {
            profile.addView((View)this.text(me.phone, 16, INK, true));
            profile.addView((View)this.qrImage(me), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(216)));
            profile.addView((View)this.info("Confirm the number in person."));
            LinearLayout links = new LinearLayout((Context)this);
            Button enlarge = this.button("Enlarge QR", false, this::showQr);
            Button edit = this.button("Edit details", false, this::editProfile);
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, this.dp(48), 1.0f);
            left.setMargins(0, this.dp(8), this.dp(6), 0);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, this.dp(48), 1.0f);
            right.setMargins(this.dp(6), this.dp(8), 0, 0);
            links.addView((View)enlarge, (ViewGroup.LayoutParams)left);
            links.addView((View)edit, (ViewGroup.LayoutParams)right);
            profile.addView((View)links);
        }
        LinearLayout actions = this.card();
        actions.addView((View)this.text("ADD SOMEONE", 19, INK, true));
        actions.addView((View)this.info("Scan, then authorize to register."));
        actions.addView((View)this.button("Scan contact QR", true, () -> {
            if (this.store.profile() == null) {
                this.showError("Create your own contact QR first.");
            } else {
                this.startActivityForResult(new Intent((Context)this, ScanActivity.class), 20);
            }
        }));
        actions.addView((View)this.button("Paste QR text", false, this::pasteQr));
        this.registeredPeople();
    }

    private void registeredPeople() {
        TextView heading = this.text("Registered people  /  " + this.store.contacts().size(), 18, INK, true);
        heading.setPadding(0, this.dp(24), 0, this.dp(4));
        this.content.addView((View)heading);
        if (this.store.contacts().isEmpty()) {
            this.content.addView((View)this.text("No contacts yet.", 14, MUTED, false));
        }
        for (ContactCard contact : this.store.contacts()) {
            LinearLayout person = this.card();
            boolean selected = contact.phone.equals(this.store.selectedPhone());
            if (selected) {
                person.addView((View)this.text("YOUR FORWARDING RECIPIENT", 11, ORANGE, true));
            }
            person.addView((View)this.text(contact.name, 20, INK, true));
            person.addView((View)this.text(contact.phone, 14, MUTED, false));
            if (!selected) {
                person.addView((View)this.button("Use for forwarding", true, () -> {
                    this.store.select(contact.phone);
                    this.render();
                }));
            }
            person.addView((View)this.button("Remove", false, () -> UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)("Remove " + contact.name + "?")).setMessage((CharSequence)(selected ? "Pauses forwarding too." : "Removes local access.")).setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Remove", (dialog, which) -> {
                this.store.removeContact(contact.phone);
                RelayNotifications.refresh((Context)this);
                this.render();
            }).create())));
        }
    }

    private void activity() {
        TextView heading = this.text("OTP activity", 21, INK, true);
        heading.setPadding(0, this.dp(24), 0, this.dp(4));
        this.content.addView((View)heading);
        this.content.addView((View)this.info("OTPs only. Codes never saved."));
        JSONArray events = this.store.otpEvents();
        if (events.length() == 0) {
            LinearLayout empty = this.card();
            empty.addView((View)this.text("All quiet for now.", 22, INK, true));
            empty.addView((View)this.info("New OTPs will appear here."));
        }
        try {
            for (int i = 0; i < events.length(); ++i) {
                JSONObject event = events.getJSONObject(i);
                LinearLayout item = this.card();
                long timestamp = event.getLong("time");
                String status = event.getString("status");
                if (status.startsWith("Sending:") && System.currentTimeMillis() - timestamp > 120000L) {
                    status = "Carrier result unknown";
                }
                item.addView((View)this.text(event.getString("source") + "  /  " + DateFormat.getDateTimeInstance(2, 3).format(new Date(timestamp)), 11, MUTED, true));
                item.addView((View)this.text(status, 16, status.startsWith("Failed") ? Color.rgb((int)167, (int)51, (int)37) : ORANGE, true));
                item.addView((View)this.text("From " + event.getString("sender"), 14, INK, false));
                String recipient = event.getString("recipient");
                if (recipient.isEmpty()) continue;
                item.addView((View)this.text("To " + recipient, 14, MUTED, false));
            }
        }
        catch (JSONException e) {
            throw new IllegalStateException("Could not read the local activity log.", e);
        }
        if (events.length() > 0) {
            this.content.addView((View)this.button("Clear activity log", false, () -> UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Clear activity?").setMessage((CharSequence)"Deletes log only. Contacts stay.").setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Clear", (dialog, which) -> {
                this.store.clearEvents();
                this.render();
            }).create())));
        }
    }

    private LinearLayout dialogLayout() {
        LinearLayout layout = this.column();
        layout.setPadding(this.dp(24), this.dp(8), this.dp(24), this.dp(8));
        return layout;
    }

    private EditText input(String hint, int inputType) {
        EditText field = new EditText((Context)this);
        field.setHint((CharSequence)hint);
        field.setTextSize(16.0f);
        field.setInputType(inputType);
        field.setPadding(this.dp(4), this.dp(12), this.dp(4), this.dp(12));
        return field;
    }

    private void editProfile() {
        if (this.store.enabled()) {
            this.showError("Pause forwarding first.");
            return;
        }
        LinearLayout layout = this.dialogLayout();
        EditText name = this.input("Device label", 8193);
        EditText phone = this.input("Mobile number", 3);
        ContactCard existing = this.store.profile();
        name.setText((CharSequence)PhoneProfile.deviceName());
        if (existing != null) {
            name.setText((CharSequence)existing.name);
            phone.setText((CharSequence)existing.phone);
        }
        layout.addView((View)name);
        layout.addView((View)phone);
        layout.addView((View)this.info("Your number is never uploaded."));
        AlertDialog dialog = new AlertDialog.Builder((Context)this).setTitle((CharSequence)"QR details").setView((View)layout).setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Save QR", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                String label = name.getText().toString().trim();
                this.store.saveProfile(new ContactCard(label.isEmpty() ? PhoneProfile.deviceName() : label, phone.getText().toString()));
                dialog.dismiss();
                this.render();
            }
            catch (IllegalArgumentException | IllegalStateException e) {
                phone.setError((CharSequence)e.getMessage());
            }
        }));
        UiProtection.show(dialog);
    }

    private ImageView qrImage(ContactCard me) {
        try {
            String encoded = me.toQr();
            if (!encoded.equals(this.cachedQrText)) {
                HashMap<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 2);
                BitMatrix matrix = new QRCodeWriter().encode(encoded, BarcodeFormat.QR_CODE, 600, 600, hints);
                int[] pixels = new int[360000];
                for (int y = 0; y < 600; ++y) {
                    for (int x = 0; x < 600; ++x) {
                        pixels[y * 600 + x] = matrix.get(x, y) ? -16777216 : -1;
                    }
                }
                this.cachedQr = Bitmap.createBitmap((int[])pixels, (int)600, (int)600, (Bitmap.Config)Bitmap.Config.ARGB_8888);
                this.cachedQrText = encoded;
            }
            ImageView image = new ImageView((Context)this);
            image.setImageBitmap(this.cachedQr);
            image.setContentDescription((CharSequence)"Your contact registration QR code");
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(true);
            return image;
        }
        catch (WriterException e) {
            throw new IllegalStateException("Could not generate the contact QR. Check your profile details.", e);
        }
    }

    private void showQr() {
        ContactCard me = this.store.profile();
        if (me != null) {
            LinearLayout layout = this.dialogLayout();
            layout.addView((View)this.text(me.name + "\n" + me.phone, 16, INK, true));
            layout.addView((View)this.qrImage(me), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(280)));
            layout.addView((View)this.info("Share only with people you trust."));
            UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"My contact QR").setView((View)layout).setPositiveButton((CharSequence)"Done", null).create());
        }
    }

    private void requestAutomaticProfile() {
        if (this.store.enabled()) {
            this.showError("Pause forwarding first.");
            return;
        }
        if (this.checkSelfPermission("android.permission.READ_PHONE_NUMBERS") != 0) {
            UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Create my QR?").setMessage((CharSequence)"Uses SMS SIM. Nothing uploaded.").setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Continue", (dialog, which) -> this.requestPermissions(new String[]{"android.permission.READ_PHONE_NUMBERS"}, 40)).create());
        } else {
            this.detectProfile();
            this.render();
        }
    }

    private void detectProfile() {
        this.automaticProfileAttempted = true;
        if (this.store.profile() != null || this.store.enabled()) {
            return;
        }
        try {
            this.store.saveProfile(PhoneProfile.read((Context)this));
        }
        catch (IllegalArgumentException | IllegalStateException e) {
            this.profileStatus = e.getMessage();
        }
    }

    private void pasteQr() {
        if (this.store.profile() == null) {
            this.showError("Create your own contact QR first.");
            return;
        }
        LinearLayout layout = this.dialogLayout();
        EditText value = this.input("Paste Improm contact QR text", 131073);
        layout.addView((View)value);
        AlertDialog dialog = new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Register from QR text").setView((View)layout).setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Review contact", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                ContactCard contact = ContactCard.fromQr(value.getText().toString());
                dialog.dismiss();
                this.confirmContact(contact);
            }
            catch (IllegalArgumentException e) {
                value.setError((CharSequence)e.getMessage());
            }
        }));
        UiProtection.show(dialog);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 20 && resultCode == -1 && data != null) {
            try {
                this.confirmContact(ContactCard.fromQr(data.getStringExtra("qr")));
            }
            catch (IllegalArgumentException e) {
                this.showError(e.getMessage());
            }
        }
    }

    private void confirmContact(ContactCard contact) {
        UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)("Authorize " + contact.name + "?")).setMessage((CharSequence)("Phone: " + contact.phone + "\n\nConfirm in person. Full OTPs when selected.")).setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Authorize contact", (dialog, which) -> {
            try {
                this.store.addContact(contact);
                this.tab = "People";
                this.render();
                Toast.makeText((Context)this, (CharSequence)"Contact registered", (int)0).show();
            }
            catch (IllegalArgumentException | IllegalStateException e) {
                this.showError(e.getMessage());
            }
        }).create());
    }

    private void editSenders() {
        if (this.store.enabled()) {
            this.showError("Pause forwarding first.");
            return;
        }
        LinearLayout layout = this.dialogLayout();
        layout.addView((View)this.info("Leave empty for all senders."));
        layout.addView((View)this.info("Sender IDs can be spoofed."));
        EditText field = this.input("Allowed sender IDs", 131073);
        field.setText((CharSequence)this.store.senderFilter());
        layout.addView((View)field);
        UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Allowed senders").setView((View)layout).setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Save", (dialog, which) -> {
            this.store.setSenderFilter(field.getText().toString());
            this.render();
        }).create());
    }

    private void testDetector() {
        LinearLayout layout = this.dialogLayout();
        layout.addView((View)this.info("On-device only. Nothing sent."));
        EditText sample = this.input("Sample SMS", 131073);
        sample.setMinLines(3);
        sample.setText((CharSequence)"Your verification code is 482915. Do not share it.");
        layout.addView((View)sample);
        TextView result = this.text("", 15, ORANGE, true);
        layout.addView((View)result);
        AlertDialog dialog = new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Offline OTP detector").setView((View)layout).setNegativeButton((CharSequence)"Close", null).setPositiveButton((CharSequence)"Check locally", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> result.setText((CharSequence)(OtpDetector.isOtp(sample.getText().toString()) ? "OTP pattern found. Nothing sent." : "No OTP pattern. Nothing sent."))));
        UiProtection.show(dialog);
        dialog.getWindow().addFlags(8192);
    }

    private void requestEnable() {
        ContactCard recipient = this.store.recipient();
        if (this.store.profile() == null || recipient == null) {
            this.showError("Set up People first: profile, QR, recipient.");
            return;
        }
        if (!this.getPackageManager().hasSystemFeature("android.hardware.telephony")) {
            this.showError("No carrier SMS support.");
            return;
        }
        ArrayList<String> missing = new ArrayList<String>();
        for (String permission2 : new String[]{"android.permission.RECEIVE_SMS", "android.permission.SEND_SMS"}) {
            if (this.checkSelfPermission(permission2) == 0) continue;
            missing.add(permission2);
        }
        if (Build.VERSION.SDK_INT >= 33 && this.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            missing.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!missing.isEmpty()) {
            UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Allow SMS forwarding").setMessage((CharSequence)"Receive: detect OTPs locally.\nSend: forward to your contact.\nNotifications: status and pause.\nNo internet. Old SMS unread.").setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Continue", (dialog, which) -> this.requestPermissions(missing.toArray(new String[0]), 10)).create());
            return;
        }
        if (!RelayNotifications.allowed((Context)this)) {
            UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Enable notifications").setMessage((CharSequence)"Needed for status and pause.").setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Open settings", (dialog, which) -> this.startActivity(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", this.getPackageName()))).create());
            return;
        }
        UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Turn on forwarding?").setMessage((CharSequence)("Full OTPs to:\n" + recipient.name + "\n" + recipient.phone + "\n\nIncludes bank and sign-in codes. Others with these codes can access accounts. Carrier charges apply.\n\nWorks when closed. Pause anytime.")).setNegativeButton((CharSequence)"Cancel", null).setPositiveButton((CharSequence)"Turn on forwarding", (dialog, which) -> {
            this.store.setEnabled(true);
            RelayNotifications.refresh((Context)this);
            this.render();
        }).create());
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10) {
            if (SmsReceiver.hasSmsPermissions((Context)this) && RelayNotifications.allowed((Context)this)) {
                this.requestEnable();
            } else {
                this.showError("Paused. Allow SMS and notifications in App info.");
            }
        } else if (requestCode == 30) {
            this.render();
            if (this.checkSelfPermission("android.permission.RECEIVE_SMS") != 0) {
                this.showError("SMS detection off. Allow it in App info.");
            }
        } else if (requestCode == 40) {
            this.detectProfile();
            this.render();
        }
    }

    private void showError(String message) {
        UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Improm").setMessage((CharSequence)message).setPositiveButton((CharSequence)"OK", null).create());
    }
}

