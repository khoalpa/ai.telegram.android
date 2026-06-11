# Release sign-off: core Telegram flows 2026-06-09

Date: 2026-06-09 Asia/Saigon

This is a consolidated sign-off for the requested core flows: real login/session,
chat detail/history, media download/open, inbound notification, mobile-data-only
behavior, and emulator API 26/API 35+ coverage.

## Build and devices

- Commit: `7066b28`
- App: `ai.telegram.android` `0.1.0-outside` / versionCode `1`
- Real device: Xiaomi/Redmi `25053RT47C`, serial `3511280d`, Android API `36`,
  ABI `arm64-v8a`
- Emulators:
  - `AiTelegram_AOSP_26`, Android API `26`, ABI `x86_64`
  - `AiTelegram_AOSP_35`, Android API `35`, ABI `x86_64`
- APKs:
  - `app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-arm64-v8a-debug.apk`
  - `app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-x86_64-debug.apk`
- New release QA bundles:
  - `build/release-qa-20260609-144027`
  - `build/release-qa-20260609-144135`

## Build gates

| Gate | Result | Evidence |
| --- | --- | --- |
| `.\scripts\run-local-release-gates.ps1` | Pass | Local run on 2026-06-09 completed `BUILD SUCCESSFUL`; 88 tasks, 10 executed/78 up-to-date. |
| `:app:testOutsidePlayDebugUnitTest` | Pass | Included in local release gates and both release QA bundles. |
| `:app:lintOutsidePlayDebug` | Pass | Included in local release gates and both release QA bundles. |
| `:app:assembleOutsidePlayDebug` | Pass | Included in local release gates and both release QA bundles. |
| `:app:assembleOutsidePlayDebugAndroidTest` | Pass | Included in local release gates and both release QA bundles. |

## Requested flow sign-off

| Flow | Result | Evidence | Notes |
| --- | --- | --- | --- |
| Real Telegram login/session | Pass for logged-in real-account session | `docs/release-signoff-2026-06-08-real-account-rerun.md`; `docs/release-signoff-2026-06-08-connected-device-login.md` | The real QA account was restored, app launched without showing login, header status was `OK`, Settings showed connected Telegram state, and chat/contact counts rendered. Full phone/code/2FA/logout/reset was not rerun on 2026-06-09 to avoid destroying the active QA session. |
| Chat detail/history | Pass for core detail/history surface | `docs/release-signoff-2026-06-08-connected-device-login.md` | ADB tap opened a chat detail surface, and a real channel detail rendered a readable history surface with a post, filters, gallery summary and composer. |
| Media download/open | Pass | `docs/release-signoff-2026-06-08-connected-device-media.md`; `docs/release-signoff-2026-06-09-close-qa-blockers.md` | Real media channel, gallery, full-screen video viewer and MediaCodec playback passed. The later blocker-close pass verified a fresh uncached video request advanced from 0 B to downloaded/playable on cellular. |
| Inbound notification | Pass | `docs/release-signoff-2026-06-09-inbound-notification.md`; `build/inbound-notification-proof-20260609-142346` | Real inbound Telegram message produced app notifications in channel `telegram_messages`; physical shade tap opened `MainActivity` and routed to chat/detail UI. Debug notification receiver was not used for this proof. |
| Mobile-data-only | Pass | `docs/release-signoff-2026-06-08-connected-device-mobile-data.md`; `docs/release-signoff-2026-06-09-close-qa-blockers.md`; `docs/release-signoff-2026-06-09-qa-coverage-refactor.md` | Wi-Fi-off cellular state was captured. Channel remained readable, video playback worked, no crash/ANR marker was observed, and the fresh uncached media blocker was closed on cellular. |
| Emulator API 26 | Pass | `build/release-qa-20260609-144027/summary.md`; `build/release-qa-20260609-144027/03-connected-compose-ui.log` | AVD API 26 installed app/test APK and ran Compose UI instrumentation: `OK (3 tests)`. |
| Emulator API 35+ | Pass | `build/release-qa-20260609-144135/summary.md`; `build/release-qa-20260609-144135/03-connected-compose-ui.log` | AVD API 35 installed app/test APK and ran Compose UI instrumentation: `OK (3 tests)`. |

## Safe execution

| Check | Result | Evidence |
| --- | --- | --- |
| Logged-in Redmi protected from full connected instrumentation | Pass | Both new release QA bundles skipped real-device DeviceSafe instrumentation because the real device was not marked disposable. |
| Emulator connected UI used for Compose coverage | Pass | API 26 and API 35 release QA bundles ran `run-connected-safe.ps1 -TestGroup ComposeUi` on emulator devices. |
| Real-device destructive/auth flows avoided | Pass with note | Existing docs record prior Redmi/HyperOS connected-run risk. This pass did not reinstall or run the full connected suite on the logged-in QA install. |

## Artifact hygiene

| Check | Result | Evidence |
| --- | --- | --- |
| New QA bundle redaction | Pass | `build/release-qa-20260609-144027/99-artifact-redaction.log`; `build/release-qa-20260609-144135/99-artifact-redaction.log` |
| Referenced blocker/notification bundle redaction | Pass | `.\scripts\redact-sensitive-artifacts.ps1 -Paths build\release-qa-20260609-144027,build\release-qa-20260609-144135,build\inbound-notification-proof-20260609-142346,build\close-blockers-20260609-003221 -CheckOnly` scanned 53 text artifacts and reported `Files requiring redaction: 0`. |

## Residual risks

| Severity | Issue | Impact |
| --- | --- | --- |
| medium | Full phone/code/2FA/logout/reset auth cycle was not rerun in this final pass. | Current real-account session is verified, but a destructive auth-cycle sign-off still needs a disposable QA account/session window. |
| medium | Positive generated video subtitles remain unverified on the real media fixture. | Media playback is signed off; do not market subtitle generation quality as fully device-QA'd yet. |
| medium | Xiaomi/HyperOS remains unsafe for full connected instrumentation on a logged-in install. | Continue using emulator/non-Xiaomi devices for UI automation and protect real-account devices from full connected suites. |

## Final decision

- Core requested runtime flows: pass, with the auth-cycle caveat above.
- Recommended release audience: closed beta candidate / trusted QA only.
- Not yet a production candidate because the full destructive TDLib auth cycle
  should still be rerun on a disposable QA account before broad promotion.
