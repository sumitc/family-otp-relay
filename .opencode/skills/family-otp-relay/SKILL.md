---
name: family-otp-relay
description: Persistent project brain for family-otp-relay (Android app branded "Improm") — an on-device OTP SMS relay. Use for any work in /Users/sumitc/projects/family-otp-relay: architecture, UX flows, privacy/design decisions, bytecode-derived facts, mobile-first docs rules, and verification commands. Read before exploring or editing; update after every change.
---

# Family OTP Relay — project brain

**Source of truth caveat:** this repo was created from `FamilyRelay.zip` in
`sumitc/widget-playground-v3`. The archive contains **no source code** — only
`.gradle/` cache and compiled `app/build/` artifacts. Everything below was
recovered from the merged `AndroidManifest.xml`, packaged resources, and
`javap` output of the release `.class` files. If real sources are added later,
they win; update this file.

- Repo: `/Users/sumitc/projects/family-otp-relay`
- GitHub: https://github.com/sumitc/family-otp-relay (public)
- Package: `com.familyrelay.local`
- App label shipped in resources: **Improm** (not "Family Relay")
- Version: **1.2.9 (`versionCode=14`)** — source of truth is the `VERSION`
  file. The archived binary was 1.2.1 / code 6.
- `minSdk 26`, `targetSdk 35`

## Ground rules

- No Gradle project exists. The app is rebuilt from `source/` (Java recovered
  by decompiling the archive's release classes) with `javac` + `d8`.
- The included `.gitignore` keeps `.gradle/`, `**/build/`, `dist/` and
  `.android-tools/` local. Never force-add build artifacts or the APK.
- Anything stated about behavior must be traceable to the manifest, resources,
  or bytecode. Mark inference as inference.
- **Every update bumps the version.** No commit changes shipped behavior,
  resources, docs or APK without a `VERSION` bump first. See below.

## Versioning (mandatory — Play Store)

Never skip this. The Play Store rejects an upload whose `versionCode` was
already used, even if `versionName` changed.

- `VERSION` is the source of truth: `versionName=1.2.9`, `versionCode=14`.
- On **every** change: increment `versionCode` by at least 1, update
  `versionName` to match the release, then rebuild the APK and re-run checks.
- `versionCode` is monotonic and never reused or decreased. `versionName` is
  the human-facing label. Keep both in sync across `VERSION`, this skill,
  `README.md` and the generated APK filename.
- `scripts/patch-manifest.py` enforces a strictly increasing code and rejects
  a `versionName` whose UTF-16 byte length changed (the binary string pool
  cannot be resized safely). For a different-length name, regenerate the
  manifest from real sources with aapt2/Gradle.
- `scripts/build-apk.sh` compiles `source/`, dexes it, patches the binary
  manifest from `VERSION`, and signs. Repeated builds never double-increment.
- Output is `dist/Improm-<versionName>.apk` — the **app brand is Improm**, even
  though the repo is `family-otp-relay`. Keep the APK name branded Improm.
- If real Gradle sources are restored, put the bump in `build.gradle` and keep
  `VERSION` identical. Mention the new version in the commit message.

## Architecture map

Components declared in the merged manifest:

| Component | Role |
| --- | --- |
| `MainActivity` | Launcher UI. Programmatic Android views (no XML layouts). Three tabs: **Live**, **People**, **Forwarding settings**. |
| `ScanActivity` | Camera QR scanner (legacy `android.hardware.Camera`, `SurfaceView`, background `ExecutorService`). Falls back to pasting QR text. |
| `SmsReceiver` | Listens to `SMS_RECEIVED`; detects OTPs and forwards. Uses `goAsync()`. |
| `SendResultReceiver` | Receives SMS send callbacks (`familyrelay://sent/`), records carrier result. |
| `ControlReceiver` | Handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, and `com.familyrelay.local.PAUSE`. |

Classes under `com.familyrelay.local`:

- `RelayStore` — single `SharedPreferences` store (prefs namespace `family_relay`).
  Holds profile, contacts, selected recipient, `enabled`, `consent_version`,
  `incoming_sim`, `sender_filter`, and event lists (`events` / `otpEvents`).
  Has a `migrate()` step and a change listener used to refresh Live.
- `ContactCard` — immutable `(name, phone)`; normalizes phone numbers,
  serializes to/from a `family-relay-contact` QR payload.
- `PhoneProfile` — reads default-SMS-SIM number via Android, labels it with
  `deviceName()` (device model, not the user's personal name).
- `OtpDetector` — offline regex detector. Context keywords in English and
  Hindi (OTP / one-time password / verification code / ओटीपी / सत्यापन कोड),
  4–8 digit codes, with money/amount patterns explicitly excluded.
- `RelayNotifications` — channel `relay_status`; persistent running/error
  notification with a pause action.
- `UiProtection` — flags windows secure and filters touch events to block
  screenshots/overlays ("Close overlapping apps to use Improm").
- Tests recovered as class names only: `MainActivityTest`, `RelayStoreTest`,
  `SmsReceiverTest`, `ContactCardTest`, `OtpDetectorTest`. Sources absent.

Permissions: `RECEIVE_SMS`, `SEND_SMS`, `POST_NOTIFICATIONS`, `CAMERA` (optional),
`RECEIVE_BOOT_COMPLETED`, `READ_PHONE_NUMBERS`, `HIDE_OVERLAY_WINDOWS`.
Telephony is a required feature. **There is no `INTERNET` permission.**

## UX flows

1. **First run / consent.** User grants SMS detection. Copy explains that only
   new OTP-classified SMS are checked, no contents are saved, and detection
   alone does not enable forwarding. Explicit "Allow local SMS forwarding"
   gate plus `consent_version` tracks the accepted warning.
2. **Profile.** Number comes from the default SMS SIM (or entered once if
   Android cannot supply it). Device model becomes the label. Generates
   "YOUR CONTACT QR". Warning: confirm the number in person; QR is not proof
   of ownership.
3. **People.** Scan another phone's QR (camera or paste), review, then
   "Authorize contact" to register locally. Cannot register own number;
   max 50 contacts. Select exactly one recipient ("Use for forwarding").
4. **Forwarding settings.** Master ON/PAUSED switch; optional allowed sender
   IDs/numbers; SIM choice — "Use the SIM that received the OTP" or
   "Use for forwarding" (Android default SMS SIM).
5. **Live.** New OTP SMS appear with send/skip status. Ordinary SMS never
   appear. Skips are explicit: paused, unregistered sender, own number,
   recipient loop, sender not allowed, inconsistent SMS parts, >1,600 chars,
   duplicate fingerprint, missing profile/recipient.
6. **Offline detector test.** "Try the offline detector" with a sample SMS;
   purely on-device, sends nothing.
7. **Pause anytime** from the app or notification; revoke registration and
   clear the activity log independently.

Forwarded messages are prefixed `[Family Relay]` and sent in full to one
trusted person (including sign-in/payment codes). Forwarding is **one-way**.

## Design decisions (locked unless changed)

- **D1 Local only.** No internet permission, no cloud models; QR scan and OTP
  detection run on-device.
- **D2 Metadata-only logging.** Event log stores kind/sender/status/time/
  fingerprint — never message bodies or OTP codes.
- **D3 Explicit, versioned consent** before forwarding; warning states bank,
  payment and sign-in codes are included and carrier charges apply.
- **D4 One-way relay to a single registered recipient**; loop prevention skips
  OTPs from the selected recipient.
- **D5 Regex heuristic detection**, English + Hindi, 4–8 digits, money-like
  messages excluded. Known limitation: can miss OTPs or flag ordinary codes.
- **D6 Duplicate protection** via SHA-256 fingerprint of `(message, time,
  sender)`; failure of local duplicate check pauses forwarding.
- **D7 Persistent status notification + pause control** required while
  forwarding; boot/package-replace receiver restores state.
- **D8 Screen privacy:** secure flag + overlay/touch protection.
- **D9 Design tokens:** accent `#C64309`, background/status bars `#FFF8F1`,
  sans font, light status bar, RTL supported, no backup, cleartext blocked.
- **D10 Crisp UI (1.2.3+):** Live and People are short — bold headers and
  buttons only. Explanations sit behind a small inline `ⓘ` icon **on the same
  line as its header**, never on its own row. Each explanation is 5–7 words.
  Card/button/text padding is tightened for small screens. Critical consent
  dialogs stay explicit but condensed.
- **D11 Guided setup (1.2.5+):** Live shows exactly one next action — create
  QR → add contact (auto-selected as recipient) → turn on forwarding. There
  is no standalone toggle or separate SMS-detection button: the single
  contextual button advances the flow, and `requestEnable()` requests receive,
  send and notification permissions together. Forwarding settings lives in
  the People tab, not as a third Live button.
- **D12 People-first onboarding (1.2.8+):** Cold start opens **People** until
  at least one contact is registered, then Live becomes the default from the
  next load. The old big page titles are gone; cards carry their own headers.
  People's top row is two directional cards: **SHOW QR / to receive OTPs**
  (others scan yours and forward to you) and **SCAN QR / to forward OTPs**
  (you add them and send them your codes). The tall inline QR preview is
  removed; "Edit details" moved into the QR dialog. Each registered person
  row owns its forwarding action: "Forward here" switches recipient in one
  tap, while the selected row shows "Turn on forwarding" / "Pause
  forwarding". First enable still runs permissions + versioned consent.
- **D13 Friendly QR labels (1.2.9+):** `PhoneProfile.deviceName(Context)`
  prefers the user-set device name: `Settings.Secure["bluetooth_name"]`
  only on API ≤ 31 (it throws `SecurityException` on targetSdk 32+), then
  `Settings.Global.DEVICE_NAME`, then secure/system `"device_name"`.
  All reads are wrapped in `RuntimeException` catch. No new permission:
  `BluetoothAdapter.getName()` needs `BLUETOOTH_CONNECT`, which would
  re-trigger restricted settings on sideloads. Fallback: `Build.MODEL`
  unless it is code-like (no spaces + digits), else `Manufacturer phone`.
  "QR details" prefills this name, hints "Name on your QR", and explains
  "Others see this name on your QR." Users can always override it.

## Distribution constraints (checked 2026-11-20)

**Restricted settings are a sideload-only artifact.** Android 13+ marks apps
from a non-trusted installer (sideload, ADB, "Install unknown apps") as
restricted. Sensitive permissions — SMS, notifications, accessibility, VPN,
device admin — need App info → ⋮ → **Allow restricted settings** first.
Play Store (`com.android.vending`) is a trusted installer, so this gate does
not apply there. Play still does not auto-grant: users tap Allow for SMS and
`POST_NOTIFICATIONS` (33+) and can block a channel.

**Play's SMS policy is the real blocker.** `RECEIVE_SMS` / `SEND_SMS` are
restricted. Current policy requires an **actively registered default SMS,
Phone or Assistant handler**; apps without that capability may not declare
the permissions in the manifest at all, even as placeholders. Improm is a
relay, not a default SMS app, so it does not qualify on a strict reading.

- Exception path: Permissions Declaration Form + **video demo** + extended
  review (weeks). Rejection or removal is a real possibility.
- Also required: privacy policy, Play description documenting the core SMS
  feature, Data Safety declaration for "messages".
- SMS Retriever / SMS User Consent cover **self-verification**, not relaying
  another person's OTP — no drop-in replacement for our use case.
- Policy tightens further on **2027-01-27**.

**Recommendation:** direct APK distribution (or a private F-Droid-style repo)
fits this permission model better for family use. If Play is mandatory,
either rework Improm as a genuine default SMS handler or reduce permissions
before investing in a listing. Do not assume Play upload will "just work".

## Mobile-first page rules (learned — apply to docs in this repo)

Keep the GitHub README short enough to scan on a 360px phone:

- One idea per paragraph; **max 2 sentences / ~240 characters**.
- **Max ~88 characters per line** so wrapping stays predictable.
- A heading every 3–6 lines; no section longer than ~12 lines.
- Bullets over prose; at most one nesting level.
- **No Markdown tables** (they scroll horizontally on mobile) — use short
  labeled bullets, e.g. `- **Live** — status feed`.
- Move detail into `<details>` blocks so the initial page height stays small.
- No code blocks longer than ~6 lines; prefer inline `code`.
- Lead with title + one-line value proposition + current status; repeat no
  call to action and no consent paragraph.
- Enforce with `npm test` (see `scripts/test.mjs`); `npm run build` renders a
  compact mobile HTML preview into `dist/`.

## Verification

```bash
npm run build   # renders dist/index.html from README.md
npm test        # enforces the mobile-first rules above
```

### APK build (no Gradle project)

`npm run apk` rebuilds the app from source. Steps in `scripts/build-apk.sh`:

- `source/` holds Java recovered by decompiling the archive's release classes
  with CFR. `MainActivity.java` carries the crisp-UI rewrite.
- Compile with `javac --release 17` against `android.jar` + ZXing core 3.5.3,
  then produce `classes.dex` with `d8 --min-api 26`. Tooling comes from the
  local Android SDK (`~/Android/Sdk`) or a cached download in `.android-tools/`.
- The archive's `linked-resources-binary-format-release.ap_` supplies the
  binary `AndroidManifest.xml`, `resources.arsc` and `res/`.
- `scripts/patch-manifest.py` rewrites the binary manifest's version fields
  from `VERSION` before packaging.
- Resources + DEX are zipped (stored), then aligned and signed with
  uber-apk-signer 1.3.0 (APK Signature Scheme v2 + v3) and verified. Output:
  `dist/Improm-<versionName>.apk`.
- Signer download is checksum-pinned to
  `e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835`.
- The keystore lives at `signing/family-relay.jks` (gitignored); password comes
  from `KEYSTORE_PASS` or defaults to `FamilyRelay2026`.
- This is a **new local signing key**, so it cannot update an existing install
  of the original release. Uninstall first, then sideload.
- APK files stay in `dist/`, which is gitignored — do not commit binaries.

To re-derive architecture facts from the local artifacts:

```bash
CP=app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes
cat app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
javap -classpath "$CP" -p -constants com.familyrelay.local.RelayStore
javap -classpath "$CP" -p -c -constants com.familyrelay.local.SmsReceiver
```

## Known quirks

- Repo name says "family OTP relay" but the app's own UI says **Improm** and
  its relay prefix is `[Family Relay]`. Do not silently rename one to match.
  The signed APK is deliberately named `Improm-<version>.apk`, not
  `family-otp-relay-*.apk`.
- The archive is version 1.2.1 but has no VCS metadata; `versionCode=6`.
- `.gitignore` hides all artifacts, so `git status` looks empty even though
  ~16 MB of build output sits on disk. That is intentional.
- No APK/AAB is present in the archive — only intermediate DEX/class files.
- Enable failures are usually **notifications**, not SMS: `RelayNotifications.allowed()`
  also requires the `relay_status` channel to have non-zero importance. Since
  1.2.6 a blocked channel is re-created at DEFAULT importance, the permission
  result names the exact blocker, and Forwarding settings shows live
  `SMS: ready/missing • Alerts: ready/blocked`. Channel ID: `relay_status`.
