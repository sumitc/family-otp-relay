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
- Version: 1.2.1 (`versionCode=6`), `minSdk 26`, `targetSdk 35`

## Ground rules

- Do not claim the app can be built here — there is no `build.gradle`, no
  `settings.gradle`, no `gradlew`, and no `src/`. Only build outputs exist.
- The included `.gitignore` ignores `.gradle/` and `**/build/`, so the git
  history contains only `.gitignore` plus this skill/docs. Build artifacts
  stay local; never force-add them to the public repo.
- Anything stated about behavior must be traceable to the manifest, resources,
  or bytecode. Mark inference as inference.

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
- The archive is version 1.2.1 but has no VCS metadata; `versionCode=6`.
- `.gitignore` hides all artifacts, so `git status` looks empty even though
  ~16 MB of build output sits on disk. That is intentional.
- No APK/AAB is present in the archive — only intermediate DEX/class files.
