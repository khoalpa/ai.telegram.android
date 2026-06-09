# Release signoff: close remaining QA blockers

Date: 2026-06-09 Asia/Saigon

Build: `outsidePlayDebug`, package `ai.telegram.android`, installed on physical arm64 Redmi device after final local gates passed.

Evidence bundle: `build/close-blockers-20260609-003221`

## Decision

Pass for the two remaining QA blockers:

| Blocker | Result | Evidence |
| --- | --- | --- |
| Fresh uncached media on mobile data | Pass | Wi-Fi disabled, app on cellular data, uncached video request advanced from 0 B to downloaded/playable. `19-db-after-install-before-retry.txt`, `20-mobile-downloaded-viewer.png/xml`, `21-storage-after-media-pass.txt`. |
| Physical notification shade tap | Pass | Debug-only app-owned system notification posted through Android NotificationManager; tap from notification shade moved focus from launcher/SystemUI to `ai.telegram.android/.MainActivity` with the chat UI open. `25-final-notification-before.png/xml`, `26-final-notification-after.png/xml`. |

## Media blocker notes

Root cause was a stale `media_cache` row whose `fileId` matched the target video but whose `kind` was `Image`. Because the row also pointed to a readable local file, full video download was skipped.

Fix:

- `MediaCacheRepository.shouldRequestFullDownload(fileId, kind)` now treats a kind mismatch as needing a fresh request.
- `markRequested` and `markFullDownloadRequested` only reuse local path, byte counts and request metadata when the existing row matches the requested `MessageKind`; kind changes reset stale cache metadata.
- Added connected Room regression coverage for wrong-kind cache rows and same-kind requeue metadata.

Observed device evidence after the fix: the target video cache moved to `kind=Video`, `state=FullDownloaded`, with downloaded prefix bytes recorded, and the app rendered the video player without a crash on cellular data.

## Notification blocker notes

The existing live notification available on the device was only the production group summary notification, so it was not a valid child chat tap target. To exercise the real Android notification shade route without waiting for an inbound message, a debug-only receiver posts a high-importance app-owned notification whose `contentIntent` starts `MainActivity` with `TelegramNotificationManager.EXTRA_OPEN_CHAT_ID`.

This validates the physical tap plumbing through SystemUI, PendingIntent delivery, `MainActivity`, and open-chat handling on the device. Background push and real inbound Telegram child-notification generation remain outside this specific tap verification.

## Verification

- `.\scripts\run-local-release-gates.ps1 -PlainConsole` passed after the final code changes.
- Final APK installed with `adb install --no-streaming -r -d app\build\outputs\apk\outsidePlay\debug\app-outsidePlay-arm64-v8a-debug.apk`.
- Artifact redaction check passed for known secret patterns: `.\scripts\redact-sensitive-artifacts.ps1 -Paths build\close-blockers-20260609-003221 -CheckOnly`.
