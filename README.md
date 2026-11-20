# Family OTP Relay

On-device Android relay for OTP SMS. Branded **Improm** in the app.

**Status:** 1.2.6 (code 11). Diagnoses and self-heals enable-permission checks.

## What it does

- Detects new OTP SMS on your phone, using offline English/Hindi rules.
- Forwards the full message to one trusted, registered person.
- Shows every send or skip in the Live tab.

## Privacy

- No internet permission. No cloud models. No uploaded numbers.
- The log stores status and sender, never message text or codes.
- Forwarding is one-way and can be paused at any time.

## Screens

- **Live** — one next step, then pause / turn on. No toggles to decode.
- **People** — QR, contacts, recipient pick, and forwarding settings.
- **Forwarding settings** — rules, SIM, offline test.

Explanations sit behind a small **ⓘ** on the header's line, 5–7 words each.
Spacing is tightened for small screens.

## Before you enable

<details>
<summary><strong>Read the consent note</strong></summary>

Forwarding sends full OTP SMS, including sign-in and payment codes.
Anyone receiving them may access your accounts.

Carrier charges apply. Forwarding stays on after restart until paused.

Scanning a QR does not enable forwarding. Confirm the number in person;
a QR is not proof of phone ownership.

</details>

## Details

<details>
<summary><strong>What is in this repo</strong></summary>

This repo came from `FamilyRelay.zip` in `widget-playground-v3`.
The archive had compiled output only. Sources were recovered by decompiling
the release classes and are now tracked:

- `source/` — decompiled Java, with the crisp UI changes.
- `scripts/build-apk.sh` — compiles, dexes, patches version, signs.
- `.opencode/skills/family-otp-relay/SKILL.md` — project brain.

Build caches and the signed APK remain local and untracked.

</details>

<details>
<summary><strong>App facts from the artifacts</strong></summary>

- Package `com.familyrelay.local`, archive was 1.2.1, min SDK 26, target 35.
- Components: `MainActivity`, `ScanActivity`, `SmsReceiver`,
  `SendResultReceiver`, `ControlReceiver`.
- Detection: offline regex, 4–8 digit codes, money messages excluded.
- Colors: accent `#C64309`, background `#FFF8F1`.

See the project brain skill for the full architecture and flows.

</details>

## APK

- Version 1.2.6 (code 11), from `VERSION`.
- Local build: `dist/Improm-1.2.6.apk`.
- Rebuild: `npm run apk` (needs Android SDK + JDK 17).
- Bump the version on every change; Play Store needs a new code each upload.
- Signed with a new local key, so uninstall an existing install first.

## Checks

```bash
npm run build
npm test
```

The checks keep this page short, wrapped, and readable on small screens.
