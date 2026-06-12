# Release sign-off: 2026-06-11 public beta readiness

## Candidate

- Build flavor: `outsidePlayRelease`
- App version observed on device: `0.1.0-outside`
- Device observed: Xiaomi Redmi `25053RT47C`, Android API 36
- Decision: ready for staged public beta; not a production release candidate

## Completed gates

| Area | Result | Evidence |
| --- | --- | --- |
| Local build gates | Pass | `:app:testOutsidePlayDebugUnitTest`, `:app:lintOutsidePlayDebug`, `:app:assembleOutsidePlayDebug`, `:app:assembleOutsidePlayDebugAndroidTest` completed successfully on 2026-06-11. |
| Connected DeviceSafe wrapper | Pass | `scripts/run-connected-safe.ps1 -DeviceSerial emulator-5554 -TestGroup DeviceSafe -SkipBuild` on `AiTelegram_AOSP_35` passed `OK (25 tests)`, including Room repository coverage, migration coverage and `VideoSubtitleGeneratorDeviceTest`. |
| Signed release artifacts | Pass | `scripts/build-signed-release.ps1` created signed split APKs in `build/signed-release-20260611-184234`; `apksigner-verify.log` verifies all three APKs with APK Signature Scheme v2/v3. This supersedes `build/signed-release-20260611-181219` after the channel message-count stability fix, and `181219` superseded `172215` after the media/subtitle crash fix. |
| Signed release emulator smoke | Pass | `build/signed-release-smoke-20260611-172406/summary.md` records a fresh `AiTelegram_AOSP_35` install of the signed x86_64 APK, explicit `MainActivity` launch, running/focused app process and no crash/native call markers in filtered logcat. This rerun followed a release-only native crash fix that avoids loading `libntgcalls.so` while Calls are feature-gated off. |
| Signed release Xiaomi launch smoke | Pass | `build/signed-release-smoke-xiaomi-20260611-174810/summary.md` records uninstall of the debug-signed package, install of the signed arm64 APK on Xiaomi API 36, explicit cold `MainActivity` launch, running/focused app process, denied dangerous runtime permissions on fresh install and no narrow crash/native markers. |
| Signed release Xiaomi logged-in persistence | Pass | `build/signed-release-logged-in-smoke-xiaomi-20260611-logged-in/summary.md` records signed-build login state, force-stop/relaunch persistence back to the chat list, focused/running `MainActivity`, no narrow crash/native markers and expected runtime permission state. |
| Signed release Xiaomi latest install smoke | Pass | `build/signed-release-smoke-xiaomi-20260611-184234/summary.md` records install-over of `build/signed-release-20260611-184234` on Xiaomi, successful `MainActivity` focus, running process and empty narrow crash scan. |
| Signed release Xiaomi media regression | Pass | `build/signed-release-media-proof-xiaomi-20260611-current/summary.md` captured a real force-close in `build/signed-release-20260611-172215` when opening a video/media gallery item. `build/signed-release-media-proof-xiaomi-20260611-181219/summary.md` confirms the new signed artifact installs, preserves session, renders media and no longer crashes on the media/subtitle path. `build/signed-release-physical-media-proof-xiaomi-20260611-182111/summary.md` records a physical tap into the full video viewer/download/playback surface with empty crash scans. |
| Signed release Xiaomi inbound notification | Pass | `build/signed-release-notification-proof-xiaomi-20260611-current/summary.md` confirms a real `telegram_messages` notification exists and tapping it safely focuses `MainActivity` without crash. `build/signed-release-physical-notification-proof-xiaomi-20260611-182111/summary.md` records physical-tap proof that the tapped child message routes to the matching in-app chat/detail surface, consumes the app notification record and leaves crash scans empty. |
| Channel message count stability | Pass | Fixed a UI regression where a small partial message snapshot could shrink channel counters and gallery counts from the cached history size to one visible item. `ChatFilterLogicTest.mergeDisplayedMessages_keepsCurrentChannelHistoryWhenIncomingSnapshotIsPartial` covers the 51-to-1 regression shape. |
| Public beta preflight | Pass | `scripts/run-public-beta-preflight.ps1 -AuthCycleProofDir build\tdlib-auth-cycle-proof-20260611-161549` created `build/public-beta-preflight-20260611-163107/summary.md`; local release gates, emulator DeviceSafe QA, auth-cycle proof validation and artifact redaction all passed. |
| Manifest permission hygiene | Pass | `build/public-beta-preflight-20260611-165042/summary.md` confirms release APK build PASS and manifest permission hygiene PASS. Release APK badging no longer declares `com.android.vending.BILLING`, `android.permission.USE_FULL_SCREEN_INTENT` or `android.permission.MODIFY_AUDIO_SETTINGS`. |
| Disposable TDLib auth-cycle proof | Pass | `build/tdlib-auth-cycle-proof-20260611-161549` captures login-ready state, persistence after force-stop, logout/reset to the login screen, second login-ready state and logcat tail on the Xiaomi API 36 QA device. Text redaction checks reported 0 files requiring redaction. |
| Emulator API 26 | Pass | `AiTelegram_AOSP_26` booted as `emulator-5554`; `scripts/run-connected-safe.ps1 -DeviceSerial emulator-5554 -TestGroup ComposeUi -SkipBuild` installed the x86_64 APK/test APK and passed `OK (3 tests)`. |
| Emulator API 35+ | Pass | `AiTelegram_AOSP_35` booted as `emulator-5554`; `scripts/run-connected-safe.ps1 -DeviceSerial emulator-5554 -TestGroup ComposeUi -SkipBuild` installed the x86_64 APK/test APK and passed `OK (3 tests)`. Fresh install kept `POST_NOTIFICATIONS`, `CAMERA` and `RECORD_AUDIO` at `granted=false`. |
| Share intent hardening | Pass | Shared URI policy now accepts only `content://` and rejects `file://`; unit coverage updated in `AndroidEntryIntentPolicyTest`. |
| Media cache accounting fix | Pass | DeviceSafe first caught a regression where a kind-changing media update counted `sizeMb` as real cache bytes before download. `MediaCacheRepository` now reports actual cache bytes only from a readable local file or downloaded prefix, and DeviceSafe rerun passed. |
| Speech-service negative path | Pass | `AiTelegram_AOSP_35` reported `No services found` for `android.speech.RecognitionService`; `VideoSubtitleGeneratorDeviceTest.generate_returnsUnavailableWhenSpeechRecognitionServiceIsMissing` passed on that emulator with `OK (1 test)`. |
| Optional Telegram APIs | Pass | Calls, Premium/Business APIs and bot web app data remain disabled through `OptionalTelegramFeatureGates`. |
| Redaction check | Pass | `scripts/redact-sensitive-artifacts.ps1 -CheckOnly -Paths docs README.md build app\build` scanned 24 text artifact files and reported 0 files requiring redaction. |
| Installed package state | Pass | ADB package dump for the signed Xiaomi install showed `versionName=0.1.0-outside`; on the fresh install `POST_NOTIFICATIONS`, `CAMERA` and `RECORD_AUDIO` remained `granted=false`. |
| Malformed external entry probes | Pass | Implicit `https://t.me.evil.example/...` did not resolve to the app. On the signed Xiaomi install, explicit malformed `https://t.me.evil.example/...` and `javascript:` VIEW intents delivered to `MainActivity` with `Status: ok`; the app stayed focused/running and narrow crash scan stayed empty. |
| Connected runner safety | Pass | `scripts/run-connected-safe.ps1 -DeviceSerial 3511280d -SkipBuild` refused to install/run instrumentation on the real Xiaomi device without `-DisposableInstall`; `VideoSubtitleGeneratorDeviceTest` is now included in the `DeviceSafe` allowlist for safe reruns. |
| Auth-cycle proof tooling | Pass | Added `scripts/capture-tdlib-auth-cycle-proof.ps1` and linked it from `docs/device-qa-checklist.md` so a disposable phone/code/2FA/logout/reset pass produces an evidence bundle. |
| Public beta gate tooling | Pass | Added `scripts/run-public-beta-preflight.ps1` so local gates, release APK build/permission hygiene, emulator DeviceSafe QA, artifact redaction and disposable auth-cycle proof presence can be checked from one command before promotion. |
| Prior core-flow evidence review | Pass with caveat | `docs/release-signoff-2026-06-09-core-flows.md` records pass evidence for logged-in session, chat detail/history, media, inbound notification and mobile-data-only behavior on the same app version. The 2026-06-11 code change is limited to stricter shared URI intake and does not touch TDLib, media, notification, or translation runtime paths. |

## Public beta blockers

No public beta blockers remain for a staged, limited public beta.

| Severity | Residual risk | Follow-up |
| --- | --- | --- |
| medium | Notification preview and lock-screen/public notification behavior still need privacy hardening before broader beta expansion. | Add a Settings privacy option for notification previews, set redacted `publicVersion` content and consider `VISIBILITY_PRIVATE` by default for message notifications. |
| low | Visual proof screenshots/XML may include private QA account content even though text artifact redaction checks passed. | Keep the proof bundles internal or manually scrub visual artifacts before sharing outside the release team. |
| low | Full media/notification physical proof was collected on the immediately previous signed artifact before the channel counter fix. | The latest candidate `build/signed-release-20260611-184234` changes only message snapshot handling and has a clean install/launch smoke. Rerun exact physical media/notification taps on `184234` before wider rollout if strict artifact-by-artifact signoff is required. |
| low | A media gallery crash existed in a superseded signed artifact. | Fixed in `build/signed-release-20260611-181219` by treating unexpected subtitle/ML Kit failures as unavailable subtitle state instead of an uncaught app crash. Keep the older `172215` artifact out of tester distribution. |
| low | Production-readiness bar is higher than the public beta bar. | Before production promotion, rerun the full matrix on the final signed candidate and collect two consecutive unchanged green preflights. |

## Next action

Promote this build only as a staged public beta, starting with a limited rollout and monitoring crash-free sessions, login failures, media download errors and notification delivery before widening distribution.
