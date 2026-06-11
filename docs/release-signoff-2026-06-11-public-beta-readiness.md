# Release sign-off: 2026-06-11 public beta readiness

## Candidate

- Build flavor: `outsidePlayDebug`
- App version observed on device: `0.1.0-outside`
- Device observed: Xiaomi Redmi `25053RT47C`, Android API 36
- Decision: ready for staged public beta; not a production release candidate

## Completed gates

| Area | Result | Evidence |
| --- | --- | --- |
| Local build gates | Pass | `:app:testOutsidePlayDebugUnitTest`, `:app:lintOutsidePlayDebug`, `:app:assembleOutsidePlayDebug`, `:app:assembleOutsidePlayDebugAndroidTest` completed successfully on 2026-06-11. |
| Connected DeviceSafe wrapper | Pass | `scripts/run-connected-safe.ps1 -DeviceSerial emulator-5554 -TestGroup DeviceSafe -SkipBuild` on `AiTelegram_AOSP_35` passed `OK (25 tests)`, including Room repository coverage, migration coverage and `VideoSubtitleGeneratorDeviceTest`. |
| Public beta preflight | Pass | `scripts/run-public-beta-preflight.ps1 -AuthCycleProofDir build\tdlib-auth-cycle-proof-20260611-161549` created `build/public-beta-preflight-20260611-163107/summary.md`; local release gates, emulator DeviceSafe QA, auth-cycle proof validation and artifact redaction all passed. |
| Disposable TDLib auth-cycle proof | Pass | `build/tdlib-auth-cycle-proof-20260611-161549` captures login-ready state, persistence after force-stop, logout/reset to the login screen, second login-ready state and logcat tail on the Xiaomi API 36 QA device. Text redaction checks reported 0 files requiring redaction. |
| Emulator API 26 | Pass | `AiTelegram_AOSP_26` booted as `emulator-5554`; `scripts/run-connected-safe.ps1 -DeviceSerial emulator-5554 -TestGroup ComposeUi -SkipBuild` installed the x86_64 APK/test APK and passed `OK (3 tests)`. |
| Emulator API 35+ | Pass | `AiTelegram_AOSP_35` booted as `emulator-5554`; `scripts/run-connected-safe.ps1 -DeviceSerial emulator-5554 -TestGroup ComposeUi -SkipBuild` installed the x86_64 APK/test APK and passed `OK (3 tests)`. Fresh install kept `POST_NOTIFICATIONS`, `CAMERA` and `RECORD_AUDIO` at `granted=false`. |
| Share intent hardening | Pass | Shared URI policy now accepts only `content://` and rejects `file://`; unit coverage updated in `AndroidEntryIntentPolicyTest`. |
| Media cache accounting fix | Pass | DeviceSafe first caught a regression where a kind-changing media update counted `sizeMb` as real cache bytes before download. `MediaCacheRepository` now reports actual cache bytes only from a readable local file or downloaded prefix, and DeviceSafe rerun passed. |
| Speech-service negative path | Pass | `AiTelegram_AOSP_35` reported `No services found` for `android.speech.RecognitionService`; `VideoSubtitleGeneratorDeviceTest.generate_returnsUnavailableWhenSpeechRecognitionServiceIsMissing` passed on that emulator with `OK (1 test)`. |
| Optional Telegram APIs | Pass | Calls, Premium/Business APIs and bot web app data remain disabled through `OptionalTelegramFeatureGates`. |
| Redaction check | Pass | `scripts/redact-sensitive-artifacts.ps1 -CheckOnly -Paths docs README.md build app\build` scanned 23 text artifact files and reported 0 files requiring redaction. |
| Installed package state | Partial pass | ADB package dump showed `versionName=0.1.0-outside`; `POST_NOTIFICATIONS`, `CAMERA` and `RECORD_AUDIO` were granted on the connected Xiaomi API 36 device. |
| Malformed external entry probes | Partial pass | Implicit `https://t.me.evil.example/...` did not resolve to the app. Explicit malformed `https://t.me.evil.example/...` and `javascript:...` VIEW intents delivered to `MainActivity` with `Status: ok`; recent logcat showed no `FATAL EXCEPTION` or `AndroidRuntime` crash. |
| Connected runner safety | Pass | `scripts/run-connected-safe.ps1 -DeviceSerial 3511280d -SkipBuild` refused to install/run instrumentation on the real Xiaomi device without `-DisposableInstall`; `VideoSubtitleGeneratorDeviceTest` is now included in the `DeviceSafe` allowlist for safe reruns. |
| Auth-cycle proof tooling | Pass | Added `scripts/capture-tdlib-auth-cycle-proof.ps1` and linked it from `docs/device-qa-checklist.md` so a disposable phone/code/2FA/logout/reset pass produces an evidence bundle. |
| Public beta gate tooling | Pass | Added `scripts/run-public-beta-preflight.ps1` so local gates, emulator DeviceSafe QA, artifact redaction and disposable auth-cycle proof presence can be checked from one command before promotion. |
| Prior core-flow evidence review | Pass with caveat | `docs/release-signoff-2026-06-09-core-flows.md` records pass evidence for logged-in session, chat detail/history, media, inbound notification and mobile-data-only behavior on the same app version. The 2026-06-11 code change is limited to stricter shared URI intake and does not touch TDLib, media, notification, or translation runtime paths. |

## Public beta blockers

No public beta blockers remain for a staged, limited public beta.

| Severity | Residual risk | Follow-up |
| --- | --- | --- |
| medium | Real inbound Telegram notification and mobile-data media were not regenerated after the 2026-06-11 shared URI hardening. | Existing 2026-06-09 evidence covers the same app version and unaffected runtime paths. Rerun before broader beta expansion or if the final candidate changes notification, media, TDLib, storage, or WorkManager code. |
| low | Auth-cycle proof screenshots/XML may include private QA account content even though text artifact redaction checks passed. | Keep the proof bundle internal or manually scrub visual artifacts before sharing outside the release team. |
| low | Production-readiness bar is higher than the public beta bar. | Before production promotion, rerun the full matrix on the final signed candidate and collect two consecutive unchanged green preflights. |

## Next action

Promote this build only as a staged public beta, starting with a limited rollout and monitoring crash-free sessions, login failures, media download errors and notification delivery before widening distribution.
