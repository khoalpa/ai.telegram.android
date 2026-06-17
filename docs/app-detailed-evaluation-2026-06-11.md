# App detailed evaluation: 2026-06-11

## Executive summary

Decision: suitable for a staged, limited public beta. It is not yet a production release candidate.

The app is in a materially stronger beta position than the earlier QA notes: local gates pass, release shrink builds, signed outside-Play APK artifacts exist and verify with APK Signature Scheme v2/v3, signed release emulator, Xiaomi launch and Xiaomi logged-in persistence smoke pass, emulator DeviceSafe passes, real-device TDLib auth-cycle proof exists, share/file intake was hardened, manifest permission hygiene now blocks outside-Play Billing/full-screen-call-only permissions, and text artifact redaction checks are clean. A signed media-gallery crash was found and fixed in `build/signed-release-20260611-181219`, physical-tap proof covers full media viewer/playback plus exact child-message notification routing on that artifact, and the channel counter/gallery regression caused by partial message snapshots is fixed in the latest signed artifact `build/signed-release-20260611-184234`. The main remaining risks are notification privacy defaults and a few broad-device QA gaps.

## Verification performed

| Check | Result | Notes |
| --- | --- | --- |
| Local release gates | Pass | `scripts/run-local-release-gates.ps1` completed unit tests, lint, debug APK and androidTest APK successfully. |
| Release build with R8/shrink | Pass | `.\gradlew.bat :app:assembleOutsidePlayRelease` completed successfully. |
| Signed release artifacts | Pass | `scripts/build-signed-release.ps1` created verified split APKs in `build/signed-release-20260611-184234`; all three APKs verify with APK Signature Scheme v2/v3. |
| Signed release emulator smoke | Pass | `build/signed-release-smoke-20260611-172406/summary.md` shows fresh emulator install/launch PASS for the signed x86_64 APK. |
| Signed release Xiaomi launch smoke | Pass | `build/signed-release-smoke-xiaomi-20260611-174810/summary.md` shows clean install of the signed arm64 APK after uninstall, cold `MainActivity` launch, focused/running process, denied dangerous permissions on fresh install and no narrow crash/native markers. |
| Signed release Xiaomi logged-in persistence | Pass | `build/signed-release-logged-in-smoke-xiaomi-20260611-logged-in/summary.md` shows signed-build login state, force-stop/relaunch persistence back to the chat list, focused/running process, malformed external intent tolerance and no narrow crash/native markers. |
| Signed release latest Xiaomi install smoke | Pass | `build/signed-release-smoke-xiaomi-20260611-184234/summary.md` shows install-over of the latest signed arm64 APK, focused/running `MainActivity` and empty narrow crash scan. |
| Signed release Xiaomi media regression | Pass | `build/signed-release-media-proof-xiaomi-20260611-current/summary.md` captured a force-close in the superseded signed artifact; `build/signed-release-media-proof-xiaomi-20260611-181219/summary.md` verifies the new signed artifact no longer crashes in the media/subtitle path. `build/signed-release-physical-media-proof-xiaomi-20260611-182111/summary.md` records physical full viewer/download/playback proof. |
| Signed release Xiaomi notification routing | Pass | `build/signed-release-notification-proof-xiaomi-20260611-current/summary.md` confirms a real message notification and safe app focus after tap. `build/signed-release-physical-notification-proof-xiaomi-20260611-182111/summary.md` records physical exact child-message routing proof, notification consumption and empty crash scans. |
| Channel message count stability | Pass | Small partial message snapshots are now merged into the existing displayed history instead of replacing it, preventing channel counters/gallery counts from collapsing to one item while media or translation updates arrive. |
| Public beta preflight evidence | Pass | `build/public-beta-preflight-20260611-163107/summary.md` shows all steps PASS. |
| Manifest permission hygiene | Pass | `build/public-beta-preflight-20260611-165042/summary.md` shows release APK build PASS and manifest permission hygiene PASS. |
| Auth-cycle proof evidence | Pass | `build/tdlib-auth-cycle-proof-20260611-161549` contains login-ready, force-stop persistence, logout/reset and second-login proof. |
| Redaction check | Pass | `scripts/redact-sensitive-artifacts.ps1 -CheckOnly -Paths docs README.md build app\build` scanned 24 text artifacts and found 0 files requiring redaction. |
| APK metadata check | Pass | Signed release APK is not debuggable and no longer contains Billing/full-screen/modify-audio-settings permissions. Signed artifact passed fresh emulator smoke, fresh Xiaomi launch smoke, logged-in persistence smoke, physical media viewer/playback proof and physical notification routing proof. |

## Strengths

| Area | Assessment |
| --- | --- |
| Architecture | Clear separation between Compose UI, repositories, Room, TDLib client abstraction, translation pipeline and notification layer. |
| Data persistence | Room schema is versioned to v16 in the current repo, and migration coverage exists from v1 to v16. |
| Auth/session | TDLib database key is randomly generated and wrapped with Android Keystore AES-GCM. Backup and device-transfer extraction are disabled. |
| Entry intent safety | Telegram links are host/scheme sanitized, shared text is length-limited, shared URI intake now accepts only `content://`, and shared URI count is capped. |
| Translation pipeline | ML Kit translation has preflight skips, negative cache TTLs, WorkManager retry behavior and cache/model management. |
| Media/cache | Media cache accounting now avoids treating declared size as actual local bytes before download. |
| QA automation | Local gates, connected safe runner, auth-cycle capture, artifact redaction and public beta preflight are scripted. |
| Device evidence | API 26/API 35 emulator checks, Xiaomi API 36 auth-cycle evidence and Xiaomi signed-release launch smoke exist. |

## Findings

| Priority | Finding | Evidence | Impact | Recommended action |
| --- | --- | --- | --- | --- |
| Resolved | Public beta evidence was on `outsidePlayDebug`, and release APKs were unsigned. | `scripts/build-signed-release.ps1` now generates signed split APKs under `build/signed-release-*`; `build/signed-release-20260611-172215/apksigner-verify.log` verifies v2/v3 signatures. | Resolved for artifact creation. | Use the signed APKs for tester distribution, not debug APKs. |
| Resolved | `outsidePlay` was merging `com.android.vending.BILLING` permission even though Play Billing is disabled. | `app/src/outsidePlay/AndroidManifest.xml` strips the permission, and `build/public-beta-preflight-20260611-165042/summary.md` confirms manifest permission hygiene PASS. | Resolved for the outside-Play beta artifact. | Longer term, move Billing dependency/code to a Play-enabled flavor to reduce APK size and dependency surface. |
| Resolved | Call-only permissions were broader than enabled functionality. | `MODIFY_AUDIO_SETTINGS` and `USE_FULL_SCREEN_INTENT` were removed from the outside-Play APK. APK badging now omits both permissions. | Resolved for current beta. `RECORD_AUDIO` remains for video subtitles, and `CAMERA` remains for composer camera capture. | If calls are re-enabled later, reintroduce call permissions only in a calls-enabled flavor and rerun policy QA. |
| Resolved | Signed release crashed on first launch because `libntgcalls.so` loaded while Calls were feature-gated off. | `build/signed-release-smoke-debug-20260611-171845/07-filtered-logcat.txt` showed `JNI DETECTED ERROR` from `libntgcalls.so`; `build/signed-release-smoke-20260611-172406/summary.md` passes after lazy/no-op call media engine initialization. | Resolved for current beta. | Keep call media native initialization behind the Calls feature gate until call QA is ready. |
| P1 | Notification privacy defaults may expose message content on lock screen/group summary. | Message notification uses title/text and BigText/InboxStyle; call notification/channel uses public visibility. | Chat content can be visible on lock screen depending user/channel settings. | Add a Settings privacy option for notification preview, set a publicVersion/redacted content, and consider `VISIBILITY_PRIVATE` by default for message notifications. |
| Resolved | Superseded signed release crashed when opening a real video/media gallery item. | `build/signed-release-media-proof-xiaomi-20260611-current/summary.md` captured `FATAL EXCEPTION` from `VideoSubtitleGenerator` / ML Kit after media gallery item tap. `build/signed-release-media-proof-xiaomi-20260611-181219/summary.md` passes after the generator now returns `Unavailable` for unexpected subtitle failures. | Resolved in `build/signed-release-20260611-181219`. | Keep `build/signed-release-20260611-172215` out of distribution. |
| Resolved | Signed release artifact needed exact media viewer/download and notification child-chat routing proof. | `build/signed-release-physical-media-proof-xiaomi-20260611-182111/summary.md` and `build/signed-release-physical-notification-proof-xiaomi-20260611-182111/summary.md` cover physical media viewer/playback and exact notification child-message routing on `build/signed-release-20260611-181219`. | Resolved for public beta readiness on the media/notification runtime paths. | Keep both visual proof bundles internal; rerun exact taps on `184234` before wider rollout if strict artifact-by-artifact signoff is required. |
| Resolved | Channel counters and gallery counts could collapse from the full cached history to one item after a small partial message update. | Screenshots showed all channel/content/date/gallery counts moving from the full history size to one visible item while the same chat recovered after leaving and reopening. | Resolved in UI snapshot handling. | Keep `ChatFilterLogicTest.mergeDisplayedMessages_keepsCurrentChannelHistoryWhenIncomingSnapshotIsPartial` in the beta regression suite. |
| P2 | Runtime debug logs remain in chat/call paths. | `Log.d` for chat tap and call signaling/call state remains in main sources. | Lower severity in release because logs are not usually readable cross-app, but noisy and may include chat titles/call IDs in local logs or shared QA artifacts. | Wrap debug logs behind `BuildConfig.DEBUG` or remove before wider beta. |
| P2 | Toolchain depends on AGP legacy flags. | `android.builtInKotlin=false`, `android.newDsl=false`; release build prints AGP 10 removal warnings. | Not a beta blocker, but upgrades can break suddenly. | Keep current guard task; schedule migration off legacy flags before AGP 10 work. |
| P2 | Full inbound notification/mobile-data proof was not regenerated after latest code changes. | Signoff relies partly on 2026-06-09 evidence for unchanged paths. | Acceptable for small staged beta, but weak for broad rollout. | Rerun on final signed artifact before expanding beta. |
| P3 | Root workspace contains many manual screenshots/logs. | Numerous `*.png`, `*.xml`, `logcat*.txt` files exist at repo root. | Git currently does not show them as new, but they increase chance of accidental sharing or stale evidence confusion. | Move/share only curated artifacts; add cleanup/archive convention for manual QA captures. |

## Security and privacy posture

Current positives:

- App backup and device transfer are disabled in manifest and data extraction rules.
- TDLib database key is generated locally and encrypted with Android Keystore.
- Exported receiver for notification QA is debug-only and protected by `android.permission.DUMP`.
- FileProvider is not exported and exposes only specific cache subdirectories.
- Malformed Telegram link and unsafe shared URI handling are hardened and covered by tests.
- Redaction script exists and passed on text artifacts.

Resolved in this pass:

- Release package no longer declares Billing, full-screen intent or modify-audio-settings permissions in outside-Play.
- Signed outside-Play split APK artifacts are generated and verified.
- Signed outside-Play x86_64 APK passed fresh emulator launch smoke after the call-native-load fix.
- Signed outside-Play arm64 APK passed fresh Xiaomi launch smoke after uninstalling the debug-signed install.
- Signed outside-Play arm64 APK passed logged-in persistence smoke after force-stop/relaunch.
- Signed media-gallery crash in `172215` was fixed in `181219`.
- Signed artifact `184234` supersedes `181219` for beta distribution because it includes the channel message-count stability fix.

Remaining concerns:

- Notification preview/lock-screen behavior needs a privacy setting before broad beta.
- Visual proof bundles can contain private chat names/messages even when text redaction passes.

## Functional readiness

Ready for staged beta:

- Telegram login/code/2FA/logout/reset proof exists on a real Xiaomi API 36 device.
- Signed release login state and force-stop persistence are confirmed on the Xiaomi API 36 device.
- Chat list/session persistence proof exists.
- Room repositories and migrations are covered by connected DeviceSafe tests.
- Compose smoke coverage covers navigation, chat row tap, search and cache entry.
- Translation and media-cache policy have unit/instrumented coverage.

Needs more beta telemetry/QA:

- Notification privacy defaults for message previews and lock-screen/public notification content.
- Mobile-data-only media behavior on signed release artifact.
- Video subtitle generation on devices with real SpeechRecognizer support.
- Android OEM matrix beyond Xiaomi/API 36 and AOSP emulators.
- Long-session behavior: large chat history, cache pressure, translation worker backlog and low-storage state.

## Release packaging assessment

Current artifacts:

- Debug split APKs: roughly 46-54 MB and debuggable.
- Signed release split APKs: roughly 27-33 MB after R8/resource shrink and signing.
- `versionCode=1`, `versionName=0.1.0-outside`, `minSdk=26`, `targetSdk=37`.
- Release APK permissions no longer include `com.android.vending.BILLING`, `android.permission.USE_FULL_SCREEN_INTENT` or `android.permission.MODIFY_AUDIO_SETTINGS`.
- Latest signed artifact directory: `build/signed-release-20260611-184234`.
- Latest signed emulator smoke: `build/signed-release-smoke-20260611-172406`.
- Latest signed Xiaomi launch smoke: `build/signed-release-smoke-xiaomi-20260611-174810`.
- Latest signed Xiaomi logged-in smoke: `build/signed-release-logged-in-smoke-xiaomi-20260611-logged-in`.
- Latest signed Xiaomi install-over smoke: `build/signed-release-smoke-xiaomi-20260611-184234`.
- Latest signed Xiaomi media regression proof: `build/signed-release-media-proof-xiaomi-20260611-181219`.
- Latest signed Xiaomi physical media proof: `build/signed-release-physical-media-proof-xiaomi-20260611-182111`.
- Latest signed Xiaomi physical notification proof: `build/signed-release-physical-notification-proof-xiaomi-20260611-182111`.

For public beta distribution:

1. Use signed release APK/AAB, not debug.
2. Decide whether this beta distributes via Play internal/open testing or direct APK. If Play testing is used, add an AAB build/signing path and Play policy notes for Telegram client behavior, donations and permissions.
3. Keep visual proof bundles internal or scrubbed before sharing outside the release team.

## Recommended action order

1. Add notification privacy setting or default redacted lock-screen/public notification content.
2. Run staged beta with 20-50 testers or a very small percentage rollout for 48-72 hours.
3. Expand only if there are no P0/P1 crashes, auth failures, session-loss reports, media regressions or privacy reports.

## Final assessment

The codebase is credible for a controlled public beta, with good automated gates and unusually useful QA tooling already in place. The product should not be promoted using a debug artifact or the superseded `172215`/`181219` signed artifacts. The shortest path to a cleaner beta is to distribute `build/signed-release-20260611-184234` to a limited first cohort, keep the visual proof bundles internal, and prioritize notification privacy hardening before widening rollout.
