# Release sign-off: outsidePlayDebug QA rerun 2026-06-08

This rerun records the local state after fixing the Compose locale lint issue and
updating Room migration documentation/test coverage to database version 13. It is
not approval for a public or broad beta release.

## Build evidence

- Version name/code: `0.1.0-outside` / `1`
- Artifact source: local workspace `D:\project\ai.telegram.android`; no `.git` metadata available in this directory
- APK path: `app/build/outputs/apk/outsidePlay/debug/`
- ABI artifacts:
  - `app-outsidePlay-arm64-v8a-debug.apk` - 51,751,854 bytes
  - `app-outsidePlay-armeabi-v7a-debug.apk` - 47,362,664 bytes
  - `app-outsidePlay-x86_64-debug.apk` - 54,571,595 bytes
- Android test APK: `app/build/outputs/apk/androidTest/outsidePlay/debug/app-outsidePlay-debug-androidTest.apk` - 1,741,473 bytes
- `:app:testOutsidePlayDebugUnitTest`: pass
- `:app:lintOutsidePlayDebug`: pass
- `:app:assembleOutsidePlayDebug`: pass
- `:app:assembleOutsidePlayDebugAndroidTest`: pass
- Manual instrumentation on Redmi `25053RT47C`:
  - `ai.telegram.android.data.AppDatabaseMigrationTest`: pass, `migratesFromVersion1To13AndPreservesMessage`
  - `ai.telegram.android.data.RepositoryRoomTest`: pass, 12 tests
- Gradle connected suite on Redmi `25053RT47C`: fail. `ChatScreenTapTest.chatRowTap_callsOnSelectChat` reported process crash under Gradle connected runner.
- Manual `ChatScreenTapTest` instrumentation on Redmi `25053RT47C`: timed out after 2 minutes and was force-stopped.

## Environment coverage

| Environment | Device/API/ABI | Network | Result | Evidence |
| --- | --- | --- | --- | --- |
| Emulator API 26 | Not available | Not verified | Missing | No AVD configured locally; SDK has no command-line tools available to create one from this shell. |
| Emulator API 35+ | Not available | Not verified | Missing | Emulator binary exists, but no AVDs are configured. Installed platforms include Android 35/36/37 only. |
| Real arm64 device on Wi-Fi | Redmi `25053RT47C` / API 36 / `arm64-v8a` | Wi-Fi-connected device observed | Partial | APK/test APK installed manually after accepting HyperOS Install via USB prompt; Room/migration instrumentation pass. |
| Real arm64 device on mobile data | Same device | Not isolated | Missing | Wi-Fi was not disabled and no mobile-data-only model/media policy pass was run. |
| Real device without supported speech service | Not available | Not verified | Missing | No separate speech-negative-path device available. |

## Release gates

| Area | Result | Evidence | Owner |
| --- | --- | --- | --- |
| Build | Pass | Unit test, lint, assemble and androidTest APK assemble pass after the locale/migration updates. | Project owner |
| TDLib auth | Blocked | No dedicated QA Telegram account credentials/phone-code flow were available in this rerun. | Project owner |
| Chat sync | Fail for sign-off | Connected UI runner still fails or hangs on the current Redmi/HyperOS device, so chat-detail navigation/history cannot be signed off. | Project owner |
| Translation | Partial | Unit tests pass; real message-level pending/missing-model/ready/failed/hidden states were not exercised through a QA chat fixture. | Project owner |
| Media | Not verified | No image/video/document fixture was opened on Wi-Fi or mobile data. | Project owner |
| Notifications | Not verified | No inbound notification or tap-to-open-chat rerun was performed. | Project owner |
| Feature gates | Mitigated in code, not device-rerun | Premium/Business optional TDLib actions now default to disabled until binding/device QA is confirmed; Calls and Web App data actions remain disabled. Device screenshots are still required. | Project owner |
| Privacy/security | Unit-covered, not device-rerun | External Telegram link/share sanitization now has focused unit coverage for allowed hosts/schemes, malformed links, text length caps and unsafe share schemes. Device malformed-intent probes are still required. | Project owner |
| Room/migration | Pass | Version `1 -> 13` migration test and repository Room test pass manually on the real device. | Project owner |

## Known issues

| Label | Issue | Workaround | Ship decision |
| --- | --- | --- | --- |
| blocker | QA matrix still lacks a dedicated Telegram QA account for phone/code/2FA/logout/reset, chat fixtures, media fixtures and notification checks. | Provision a disposable QA Telegram account and fixtures, then rerun destructive and end-to-end flows. | Hold beyond internal testing. |
| blocker | Connected UI test automation is unreliable on Redmi/HyperOS: Gradle connected run crashes at `ChatScreenTapTest`; manual UI instrumentation hangs. | Repeat UI automation on a non-Xiaomi device or configured emulator. Keep the Xiaomi install-prompt watcher only as a device-specific workaround. | Hold chat/history/media sign-off. |
| blocker | Required emulator coverage is unavailable locally: no API 26 or API 35+ AVD is configured, and SDK command-line tools are missing. | Install Android SDK command-line tools and system images, create API 26 and API 35+ AVDs, then rerun min-SDK and modern-permission smoke tests. | Hold broader release. |
| medium | Mobile-data behavior was not isolated. | Disable Wi-Fi or use a controlled network profile before rerunning data-saver/model-download checks. | Required before public beta. |

## Post-assessment mitigations

- Premium/Business optional Telegram API actions are disabled by default, including the screen-level refresh action, so unverified Premium, Stars and Business commands are visible as gated UI rather than executable actions.
- Android external-entry sanitization now has local unit tests covering Telegram deeplink host allowlisting, malformed-link rejection, shared-text capping and safe shared URI schemes.
- These mitigations reduce accidental exposure, but they do not replace the missing emulator/device QA gates above.

## Final decision

- Decision: hold for any release beyond internal testing
- Decision owner: project owner
- Date: 2026-06-08, Asia/Saigon
- Notes: The code-level fixes are verified by local build gates, lint and targeted device instrumentation for Room/migration. The broader QA matrix remains incomplete because emulator coverage, mobile-data isolation, a dedicated Telegram QA account and reliable UI automation are not available in this environment.
