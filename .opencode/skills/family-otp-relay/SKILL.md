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
- Version: **1.3.6 (`versionCode=21`)** — source of truth is the `VERSION`
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

- `VERSION` is the source of truth: `versionName=1.3.6`, `versionCode=21`.
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
| `MainActivity` | Launcher UI. Programmatic Android views (no XML layouts). Two tabs: **Live**, **People**. |
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
4. **Forwarding controls.** Master ON/PAUSED action lives inline on Live and
   on the selected People row. Rules, sender filter and SIM choice were
   removed in 1.3.1 as confusing; there is no settings popup.
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
- **D14 Dense controls, visible affordances (1.3.0+):** Each contact is one
  compact row with small actions (38dp targets + content descriptions).
  There is **no** forwarding-rules section: sender filter, SIM switch and
  privacy lines were removed entirely as confusing; "Forwarding settings"
  and "Allowed senders" popups are gone. A divider precedes
  "Try offline detector". SHOW QR renders the QR inline; SCAN QR shows a
  camera glyph. Tapping the QR enlarges it.
- **D15 Single-glyph actions, fixed card order (1.3.2+):** Every action uses
  the same compact `actionButton` with a **single icon glyph, never a word**:
  `⏸` stops running forwarding, `▶` starts/resumes forwarding **and** is the
  "Forward here" glyph, `🗑` removes a contact (ASCII has no trash character;
  the wastebasket glyph is the text equivalent). No `>`, `->`, `||`, `→`,
  `Fwd` or `Bin` labels — the user rejected those. Theme colours only: orange
  accent `#C64309`, soft-orange secondary, 38dp targets + content descriptions.
  Every card's children are strictly **title → subtitle → content**: SHOW QR
  is "SHOW QR / to receive OTPs / QR", SCAN QR is "SCAN QR / to forward OTPs /
  camera". The camera glyph must never render above the title.
- **D16 One pause point, short Live header (1.3.5+):** Once a recipient is
  registered, Live shows only `FORWARDING ON` / `FORWARDING PAUSED`, the
  recipient name and an underlined blue **change setting** link **in the same
  header row**. Tapping it switches to People. There is no pause/play control
  on Live at all: all forwarding changes happen on the People row. Setup
  buttons (Create my QR, Scan contact QR, Paste QR text) stay only while no
  recipient exists. This keeps the Live header short and removes duplicate
  pause points.
- **D17 Registration completes setup (1.3.6+):** Authorizing a scanned or
  pasted QR is the consent moment. The **Authorize and forward** dialog states
  that forwarding turns on and names the risks. Its positive action adds the
  contact, selects them as recipient (pausing an existing recipient first if
  needed), and calls `autoEnable()`. Missing SMS/notification permissions are
  requested in the same flow (`pendingAutoEnable`), then `finishAutoEnable()`
  sets `enabled=true` and refreshes the notification. The user never has to
  come back and tap the row's `▶` button. D3 consent is preserved: no silent
  enable; the warning is in the confirmation the user already accepts.

## UI principles (learned from user — apply to all future designs)

These are essential, not preferences. Apply without being asked again:

- **Show the thing, don't hide it behind a tap.** If a feature is a QR, show
  the QR. If it opens the camera, show a camera icon. A label-only tile adds
  drop-off.
- **One row per item.** Lists use single compact glyphs (`⏸`, `▶`, `🗑`),
  never words or stacked full-width buttons.
- **Controls belong where the object is.** Put primary controls inline with
  the list row or section. Do not hide them in a popup or a distant tab.
- **Popups are for confirmation and detail, not primary actions.** Keep the
  remove confirmation and the enlarged QR dialog; remove settings popups.
- **Separate secondary/test tools with a divider** so they don't compete
  with primary content.
- **Small actions need 38–48dp touch targets and content descriptions.**
  Use single icon glyphs, never words. `▶` means start/resume/forward; `⏸`
  means stop; `🗑` means remove. No `>` or `->` substitutes. Theme colours only.
- **One control point.** Do not duplicate pause/play on Live and People. Keep
  the action on the People row; Live only reports state and links there.
- **Finish the flow in the confirmation.** If the user authorizes a contact,
  select them and turn forwarding on in that same action. Never send them back
  to the list to tap another button. Permissions/risks belong in the dialog.
- **Keep status headers short and inline.** A secondary action such as
  "change setting" belongs in the header row as an underlined blue link,
  never on its own line below the state text.
- **Card child order is fixed:** title, subtitle, then content/affordance.
  Never put an icon before the title. Do not vertically centre a card.
- Explanations stay behind an inline `ⓘ` on the header line, 5–7 words
  (D10). Never add a standalone explanation row.
- **Never confirm a build without load checks.** `npm test` serves
  `dist/index.html` over loopback HTTP and asserts it loads. `npm run apk`
  runs those tests first, then validates the APK archive, manifest version,
  launcher declaration and core DEX classes.

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
npm test        # docs build + mobile rules + HTTP load + UX suite + browser
npm run apk     # all of the above, then build and verify the APK
npm run check:ux       # 16 named cases: Live, People, dialogs, registration
npm run check:mobile   # 360px and 390px browser checks
```

Never report a build as done unless `npm test` printed:
`PASS — ... mobile rules OK`, `PASS — docs page loads over HTTP`,
`UX CHECK PASS — 16 cases`, `MOBILE CHECK PASS — 2 viewports`; and
`npm run apk` printed `APK verified: ...` before `APK ready: ...`.

`scripts/ux-check.py` locks the regressions that already happened: card child
order (title → subtitle → content), ASCII-only actions, identical compact
controls on both pages, no rules section, and dialog action buttons surviving
typing (keyboard resize, validation listeners, no hiding/text watchers).
`scripts/mobile-check.py` uses Playwright Chromium at phone widths to assert
no horizontal scroll, readable text, details that toggle, and no JS errors.
Run the whole suite after **every** change, not just before release.

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
  result names the exact blocker. The old live
  `SMS: ready/missing • Alerts: ready/blocked` readout was removed with the
  rules section in 1.3.1. Channel ID: `relay_status`.
