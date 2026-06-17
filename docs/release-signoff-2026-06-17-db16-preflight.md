# Release sign-off: 2026-06-17 DB v16 and preflight rerun

## Candidate

- Build flavor: `outsidePlayRelease`
- Signed artifact: `build/signed-release-20260617-164829`
- App version: `0.1.0-outside`
- Decision: local/preflight pass; real-device candidate media and notification proof is blocked until a same-signature install or disposable reinstall is available.

## Completed gates

| Area | Result | Evidence |
| --- | --- | --- |
| Room schema docs | Pass | `README.md` now documents Room database version 16, the `message_translations` table, and migrations v14 -> v15 and v15 -> v16. |
| Migration coverage | Pass | `AppDatabaseMigrationTest.migratesFromVersion1To16AndPreservesMessageAndMainChatList` now verifies v16 migration output, including `translation_jobs.jobKey/contentHash` and `message_translations` indexes. |
| Repository DeviceSafe drift | Pass | The first preflight exposed stale v16 DeviceSafe expectations in translation job identity and older-history exhaustion. `RepositoryRoomTest` was updated to use `TranslationJobIdentity`, and `ChatHistoryStateRepository` now stops older-history reloads once the known oldest page is exhausted. |
| Local gates | Pass | `build/public-beta-preflight-20260617-164954/summary.md` records local release gates PASS. |
| Release build and permission hygiene | Pass | `build/public-beta-preflight-20260617-164954/summary.md` records release APK build PASS and manifest permission hygiene PASS. |
| Emulator DeviceSafe QA | Pass | `build/public-beta-preflight-20260617-164954/02-emulator-device-safe.log` records `OK (25 tests)`. |
| Signed release artifacts | Pass | `build/signed-release-20260617-164829/summary.md` records signed arm64-v8a, armeabi-v7a and x86_64 APKs with APK Signature Scheme verification. |
| Auth-cycle proof redaction | Pass | Existing disposable proof `build/tdlib-auth-cycle-proof-20260611-161549` was present and redaction checked during preflight. |
| Artifact redaction | Pass | Preflight artifact redaction PASS; `build/real-device-candidate-smoke-20260617-165212` also scanned 9 text artifacts with 0 files requiring redaction. |

## Real-device status

Device observed:

- Serial: `3511280d`
- Model: Redmi `25053RT47C`
- Android API: `36`
- ABI: `arm64-v8a`

Earlier install-over of `build/signed-release-20260617-164829/app-outsidePlay-arm64-v8a-release-signed.apk` failed because the already-installed package had a different signature:

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ai.telegram.android signatures do not match newer version
```

Because uninstalling would remove or risk the logged-in Telegram QA state, the signed candidate was not installed on the real device. A non-destructive launch smoke was captured for the already-installed package in `build/real-device-candidate-smoke-20260617-165212`, but that evidence does not prove the 2026-06-17 candidate APK.

After the app was uninstalled from the device, the same signed candidate installed successfully:

| Area | Result | Evidence |
| --- | --- | --- |
| Fresh real-device install | Pass | `build/real-device-candidate-install-20260617-165631/01-install.txt` shows `Success`. |
| Fresh launch smoke | Pass | `build/real-device-candidate-install-20260617-165631/04-activity.txt` shows `ai.telegram.android/.MainActivity` as top/resumed/focused. |
| Package metadata | Pass | `build/real-device-candidate-install-20260617-165631/03-package.txt` shows `versionName=0.1.0-outside`, `versionCode=1`, `apkSigningVersion=3`, and `lastUpdateTime=2026-06-17 16:56:37`. |
| Runtime permissions on fresh install | Pass | `POST_NOTIFICATIONS`, `CAMERA` and `RECORD_AUDIO` are denied on the fresh install. |
| Launch crash scan | Pass | `build/real-device-candidate-install-20260617-165631/08-filtered-logcat.txt` has no `FATAL EXCEPTION`, `AndroidRuntime`, `ANR`, `libntgcalls` or `JNI DETECTED ERROR` crash marker for the app. |
| Fresh-login gate | Expected | `build/real-device-candidate-install-20260617-165631/09-current.xml` shows the app on the `Dang nhap Telegram` / phone-number screen after uninstall. |
| Speech service probe | Informational | `build/real-device-candidate-install-20260617-165631/06-speech-service.txt` reports `No activity found`, so this device can cover subtitle unavailable fallback after a video fixture is available. |
| Evidence redaction | Pass | `scripts/redact-sensitive-artifacts.ps1 -Paths build\real-device-candidate-install-20260617-165631 -CheckOnly` scanned 9 text files and found 0 files requiring redaction. |

## Remaining proof needed

Before promoting this exact candidate beyond emulator/preflight coverage:

- Log in to Telegram on the freshly installed signed candidate.
- Rerun real-device media gallery/fullscreen playback proof.
- Rerun real inbound notification child tap-to-chat proof.
- Rerun mobile-data-only media/translation policy proof with an uncached fixture if possible.
- Open a subtitle-capable video fixture and verify the unavailable fallback on this device, which currently exposes no speech recognition service via the probe above.
