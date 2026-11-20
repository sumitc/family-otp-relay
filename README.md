# Family OTP Relay

On-device Android relay for OTP SMS. Branded **Improm** in the app.

**Status:** 1.3.5 (code 20). One pause point; Live links to People.

## What it does

- Detects new OTP SMS on your phone, using offline English/Hindi rules.
- Forwards the full message to one trusted, registered person.
- Shows every send or skip in the Live tab.

## Privacy

- No internet permission. No cloud models. No uploaded numbers.
- The log stores status and sender, never message text or codes.
- Forwarding is one-way and can be paused at any time.
- QR labels use your friendly device name; edit it anytime.

## Screens

- **Live** — short ON/PAUSED header with a link to People.
- Setup buttons stay until a contact is registered.
- **People** — title, subtitle, then QR or camera in each card.
- Each contact is one compact row with glyph actions.
- The offline detector sits below a divider.

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

- Version 1.3.5 (code 20), from `VERSION`.
- Local build: `dist/Improm-1.3.5.apk`.
- Rebuild: `npm run apk` (needs Android SDK + JDK 17).
- Bump the version on every change; Play Store needs a new code each upload.
- Same signing key, so this installs over earlier 1.x builds.

## Checks

```bash
npm test        # builds docs, loads page, checks both app pages and dialogs
npm run apk     # runs all checks, builds, verifies APK structure
```

The checks keep docs short and both app pages consistent.
