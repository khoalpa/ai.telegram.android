# Release sign-off: outsidePlayDebug 2026-06-07

This sign-off records the current release gate status for the local `outsidePlayDebug`
candidate. It is suitable as an internal audit snapshot, not as approval for a
public or broad beta release.

## Build evidence

- Version name/code: `0.1.0-outside` / `1`
- Commit or artifact source: local workspace `D:\project\ai.telegram.android`; no `.git` metadata available in this directory
- APK path: `app/build/outputs/apk/outsidePlay/debug/`
- ABI artifacts:
  - `app-outsidePlay-arm64-v8a-debug.apk` - 51,652,170 bytes
  - `app-outsidePlay-armeabi-v7a-debug.apk` - 47,262,980 bytes
  - `app-outsidePlay-x86_64-debug.apk` - 54,471,911 bytes
- `:app:testOutsidePlayDebugUnitTest`: pass
- `:app:lintOutsidePlayDebug`: pass, `No issues found.`
- `:app:assembleOutsidePlayDebug`: pass
- `:app:connectedOutsidePlayDebugAndroidTest`: pass via `scripts/run-connected-android-test-xiaomi.ps1`; 13 tests executed on `25053RT47C`

## Environment coverage

| Environment | Device/API/ABI | Network | Result | Evidence |
| --- | --- | --- | --- | --- |
| Emulator API 26 | Not run | Not verified | Missing | Required min-SDK smoke evidence is absent. |
| Emulator API 35+ | Not run | Not verified | Missing | Required modern permission-flow evidence is absent. |
| Real arm64 device on Wi-Fi | `25053RT47C` / API 36 / `arm64-v8a` connected | Partially observed | Partial | Instrumentation test environment fixed with Xiaomi Install via USB watcher; 13 androidTests pass. Shortcut-intent QA opens Chats/Search/Settings/Cache; full chat detail/media/network QA is still blocked by touch navigation failure. |
| Real arm64 device on mobile data | Not run | Mobile data | Missing | Required data-saver/model-download evidence is absent. |
| Real device without supported speech service | Not run | Not verified | Missing | Required subtitle negative-path evidence is absent. |

## Release gates

| Area | Result | Evidence | Owner |
| --- | --- | --- | --- |
| Build | Pass | Unit test, lint, debug assemble and connected androidTest pass locally. AndroidTest on Xiaomi requires `scripts/run-connected-android-test-xiaomi.ps1` to accept HyperOS install prompts. | Project owner |
| TDLib auth | Partial | Device Settings surface shows a connected Telegram account, 60 chats and 414 contacts. Phone/code/2FA/logout/reset were not exercised on a QA account. | Project owner |
| Chat sync | Fail | Chat list renders 60/60 and survives relaunch, but tapping chat rows does not open history under ADB-driven QA. History paging, read state, edit/delete/pin persistence remain unverified. | Project owner |
| Translation | Partial | Cache/model surface renders, showing 0/5 downloaded language pairs and ML Kit model controls. Message-level pending, missing-model, ready, failed and hidden states remain unverified. | Project owner |
| Media | Not verified | No current sign-off evidence for image/video/document download/open flows on Wi-Fi and mobile data. | Project owner |
| Notifications | Partial | Android notification permission is granted and `POST_NOTIFICATION` app-op is `allow`; no current evidence for inbound notification or tap-to-open-chat. | Project owner |
| Feature gates | Pass for internal MVP | Calls, `Premium/Business` and optional APIs are feature-gated; Calls actions are disabled with `ENABLE_CALL_ACTIONS=false` until call media QA is complete. | Project owner |
| Privacy/security | Not verified | No current malformed deep-link/share-intent evidence. | Project owner |

## Known issues

| Label | Issue | Workaround | Ship decision |
| --- | --- | --- | --- |
| blocker | Required device/environment release evidence is missing or incomplete for TDLib auth, chat sync, translation, media, notifications and privacy/security. | Complete the device QA matrix with a dedicated QA Telegram account and attach redacted screenshots/log excerpts. | Hold beyond internal testing. |
| blocker | Critical-flow device QA on `25053RT47C` could not pass because chat item taps and top-level taps did not change state under ADB-driven testing, even though shortcut intents open key surfaces. | See `docs/device-qa-2026-06-07-critical-flows.md`; repeat with manual physical touch or another device/emulator. | Hold critical-flow sign-off. |
| medium | HyperOS/Xiaomi shows an `Install via USB` confirmation for each Gradle APK install; plain `connectedOutsidePlayDebugAndroidTest` still fails if the dialog is not accepted before countdown. | Use `scripts/run-connected-android-test-xiaomi.ps1`, or run on a clean emulator/non-Xiaomi test device. | Accept for internal device-test runs with wrapper. |
| low | Calls UI remains visible but real create/accept/end actions are intentionally disabled until call media QA is complete. | Keep `ENABLE_CALL_ACTIONS=false`; enable only with call-specific device QA evidence. | Accept for internal MVP. |
| medium | Gradle build relies on legacy AGP flags `android.builtInKotlin=false` and `android.newDsl=false`, which are deprecated for AGP 10. | `verifyAgpLegacyCompatibility` now fails fast on unsafe AGP/Kotlin/KSP drift; follow `docs/gradle-agp-legacy-risk.md` before removing flags or upgrading to AGP 10+. | Accept only for internal MVP. |

## Final decision

- Decision: hold for any release beyond internal testing
- Decision owner: project owner
- Date: 2026-06-07, Asia/Saigon
- Notes: Local build gates are healthy, including connected androidTest when run through the Xiaomi wrapper. Critical-flow device QA was attempted and documented in `docs/device-qa-2026-06-07-critical-flows.md`. Shortcut-intent evidence confirms core surfaces render on the real device, but release remains blocked by UI interaction failure under ADB-driven testing and by incomplete TDLib/chat-detail/media/notification/privacy evidence. Internal development can continue; broader release remains on hold.
