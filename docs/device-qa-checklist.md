# Device QA Checklist

Run these checks on a real device or emulator with Telegram credentials.

Use `docs/release-readiness-matrix.md` to decide which device/API/data-network combinations must pass before a wider release.

Do not run the full Gradle connected test suite on the same installed app that
holds a logged-in Telegram QA session. On the Redmi/HyperOS rerun from
2026-06-08, the connected runner timed out, left active instrumentation behind,
and the app package had to be reinstalled, which reset permissions and removed
the logged-in app state. Run full connected tests before login, on a disposable
install, or use targeted `adb shell am instrument -e class ...` commands.

Connected instrumentation is separated from real Telegram sessions by default:

- Emulator connected runs are allowed.
- Real-device connected runs must use a disposable install and pass
  `-DisposableInstall` to `scripts\run-connected-safe.ps1`.
- `scripts\run-release-qa.ps1` skips real-device connected runs unless
  `-AllowDisposableRealDeviceConnectedQa` is passed for a disposable QA device.
- Logged-in real-account devices are for manual/physical-touch QA only. Do not
  install test APKs or run raw Gradle connected tasks on them.

Before any device QA pass, run the local release gates:

```powershell
.\scripts\run-local-release-gates.ps1
```

Record the output in `docs/release-signoff-template.md` or in the release issue.

Before attaching logs or QA bundles to an issue, PR, chat thread or shared
archive, scrub generated artifacts:

```powershell
.\scripts\redact-sensitive-artifacts.ps1
.\scripts\redact-sensitive-artifacts.ps1 -CheckOnly
```

The scrubber redacts Telegram API IDs/hashes and phone numbers from generated
text artifacts while leaving `local.properties` untouched by default. Use
`-IncludeLocalProperties` only when preparing a shareable copy that must not be
used for real Telegram login afterwards. `-CheckOnly` exits non-zero if any
scanned artifact still needs redaction.

For a full local QA artifact bundle, prefer:

```powershell
.\scripts\run-release-qa.ps1
```

`run-release-qa.ps1` automatically scrubs its generated `build/release-qa-*`
text artifacts and records an `Artifact redaction` step in `summary.md`.

To include a disposable real device in the connected portion:

```powershell
.\scripts\run-release-qa.ps1 -DeviceSerial <serial> -AllowDisposableRealDeviceConnectedQa
```

Attach `build/release-qa-*/summary.md` to the sign-off.

For connected instrumentation, use the safe wrapper instead of the raw Gradle
connected task:

```powershell
.\scripts\run-connected-safe.ps1 -DeviceSerial <serial> -DisposableInstall
```

The wrapper keeps Redmi/HyperOS on the Room/migration allowlist and sends
Compose UI tests to AOSP emulators or non-Xiaomi devices.

## TDLib Login

- Build the outside-Play APK with valid `telegramApiId` and `telegramApiHash`.
- Install the ABI-matched APK for the device.
- Open Settings and confirm TDLib reaches the phone-number state.
- Enter phone number, code and any two-step verification password, then confirm the app reaches the ready state.
- Sign out, then sign in again to verify TDLib local reset/key handling.
- Trigger a TDLib action that should fail, such as joining an invalid public username, and confirm the operation notice shows the concrete TDLib error instead of staying on a generic requested message.

## Settings Persistence

- Toggle translated-only reading, adult-content visibility, notifications, auto-play video and full-screen video subtitles.
- Change the subtitle color.
- Force-stop and reopen the app.
- Confirm those settings keep the selected values.
- Confirm Interface language and Content translation language are fixed informational rows, not actionable language pickers.

## Chat Privacy

- Open a chat with untranslated messages.
- Confirm pending/translating/failed messages show the translation state plus the source text fallback.
- Search inside the chat using a word that exists only in the visible source fallback; it should match.
- Search using a word from the Vietnamese translation; it should match once translation is ready.
- Hide a message and confirm duplicate normalized content is hidden without rendering source text.

## Media And Video

- Open an image message and confirm thumbnail/full media download.
- Open a video message and confirm prefix download state, full download, inline playback and full-screen playback.
- Toggle Settings > full-screen video subtitles on.
- Open a video with spoken audio in full screen.
- Grant speech/audio recognition permission when prompted.
- Confirm the app extracts the video's audio, runs speech recognition, translates the recognized text to Vietnamese and shows it as timed overlay cues.
- On Android 14/API 34 or newer, confirm cues follow speech timing when the recognizer returns word timings.
- On devices without word timing support, confirm the app falls back to sentence cues distributed across the video duration.
- Confirm videos without speech, unsupported speech services, missing translation models, unsupported Android versions or denied permission show a clear unavailable subtitle message instead of staying in the loading state.
- Confirm subtitle generation times out with a visible unavailable message rather than hanging indefinitely.
- Toggle subtitles off and confirm the overlay disappears.

## Stories

- Open the header menu, then open Stories.
- Choose a chat and load active stories.
- Use the story media picker to select an image or video instead of typing a raw local path.
- Confirm the local path field is filled and the image/video chip matches the selected media.
- Post the story with a test caption and confirm either TDLib accepts it or the operation notice shows the concrete TDLib error.
- Download story media and confirm the story card updates when TDLib returns the file local path.

## Feature Gates

- Open the header menu, then open Calls and confirm start/accept/end call actions are disabled with a visible explanation about the missing call media engine.
- Open the header menu, then open Premium and confirm Premium, Stars and Business actions are disabled with a visible optional-API explanation.
- Confirm gated actions do not show misleading success/request messages.

## Inbound Notification Proof

Use this only on a logged-in real Telegram QA account. The proof must use a real
incoming Telegram message from another account/device, not the debug notification
receiver.

```powershell
.\scripts\capture-inbound-notification-proof.ps1 -DeviceSerial <serial>
```

Minimum passing evidence:

- `02-notifications-after-inbound.txt` contains a non-summary `ai.telegram.android`
  message notification in channel `telegram_messages`.
- `02-shade-after-inbound.png/xml` shows the real Android notification shade with
  the AI Telegram child message notification visible.
- `03-after-tap.png/xml` shows `MainActivity` opened to the matching chat/detail
  route after the physical tap.
- `03-activity-after-tap.txt` shows `ai.telegram.android/.MainActivity` in focus.
- The script redaction check passes, and screenshots/XML/text are manually
  reviewed for private chat names or message text before sharing the bundle.

## ML Kit Model Download

- Open Cache and refresh downloaded models.
- Download one Vietnamese translation pair.
- Force app restart and confirm downloaded model state is retained.
- Try translating a message for that pair on Wi-Fi.
- Try with no Wi-Fi and confirm the app stays pending/failed while showing source text fallback.
