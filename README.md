# Family OTP Relay

On-device Android relay for OTP SMS. Branded **Improm** in the app.

**Status:** build artifacts only — no source in this repo.

## What it does

- Detects new OTP SMS on your phone, using offline English/Hindi rules.
- Forwards the full message to one trusted, registered person.
- Shows every send or skip in the Live tab.

## Privacy

- No internet permission. No cloud models. No uploaded numbers.
- The log stores status and sender, never message text or codes.
- Forwarding is one-way and can be paused at any time.

## Screens

- **Live** — OTP events with send or skip status.
- **People** — your QR, registered contacts, selected recipient.
- **Forwarding settings** — on/off, allowed senders, SIM choice.

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
The archive had compiled output only, so the tracked files are docs:

- `.gitignore` — ignores `.gradle/` and `**/build/`.
- `.opencode/skills/family-otp-relay/SKILL.md` — project brain.
- `scripts/` — mobile-page build and checks.

Build caches and DEX/class files remain local and untracked.

</details>

<details>
<summary><strong>App facts from the artifacts</strong></summary>

- Package `com.familyrelay.local`, version 1.2.1, min SDK 26, target 35.
- Components: `MainActivity`, `ScanActivity`, `SmsReceiver`,
  `SendResultReceiver`, `ControlReceiver`.
- Detection: offline regex, 4–8 digit codes, money messages excluded.
- Colors: accent `#C64309`, background `#FFF8F1`.

See the project brain skill for the full architecture and flows.

</details>

## Checks

```bash
npm run build
npm test
```

The checks keep this page short, wrapped, and readable on small screens.
