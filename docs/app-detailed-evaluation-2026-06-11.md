# App detailed evaluation: 2026-06-11

## Executive summary

Decision: suitable for a staged, limited public beta after release signing/distribution hygiene is closed. It is not yet a production release candidate.

The app is in a materially stronger beta position than the earlier QA notes: local gates pass, release shrink builds, emulator DeviceSafe passes, real-device TDLib auth-cycle proof exists, share/file intake was hardened, and text artifact redaction checks are clean. The main remaining risks are release packaging hygiene, excessive merged permissions from disabled billing/call surfaces, notification privacy defaults, and a few broad-device QA gaps.

## Verification performed

| Check | Result | Notes |
| --- | --- | --- |
| Local release gates | Pass | `scripts/run-local-release-gates.ps1` completed unit tests, lint, debug APK and androidTest APK successfully. |
| Release build with R8/shrink | Pass | `.\gradlew.bat :app:assembleOutsidePlayRelease` completed successfully. |
| Public beta preflight evidence | Pass | `build/public-beta-preflight-20260611-163107/summary.md` shows all steps PASS. |
| Auth-cycle proof evidence | Pass | `build/tdlib-auth-cycle-proof-20260611-161549` contains login-ready, force-stop persistence, logout/reset and second-login proof. |
| Redaction check | Pass | `scripts/redact-sensitive-artifacts.ps1 -CheckOnly -Paths docs README.md build app\build` scanned 23 text artifacts and found 0 files requiring redaction. |
| APK metadata check | Mixed | Release APK is not debuggable, but it is unsigned and still contains unexpected merged permissions. |

## Strengths

| Area | Assessment |
| --- | --- |
| Architecture | Clear separation between Compose UI, repositories, Room, TDLib client abstraction, translation pipeline and notification layer. |
| Data persistence | Room schema is versioned to v14 and migration coverage exists from v1 to v14. |
| Auth/session | TDLib database key is randomly generated and wrapped with Android Keystore AES-GCM. Backup and device-transfer extraction are disabled. |
| Entry intent safety | Telegram links are host/scheme sanitized, shared text is length-limited, shared URI intake now accepts only `content://`, and shared URI count is capped. |
| Translation pipeline | ML Kit translation has preflight skips, negative cache TTLs, WorkManager retry behavior and cache/model management. |
| Media/cache | Media cache accounting now avoids treating declared size as actual local bytes before download. |
| QA automation | Local gates, connected safe runner, auth-cycle capture, artifact redaction and public beta preflight are scripted. |
| Device evidence | API 26/API 35 emulator checks and Xiaomi API 36 auth-cycle evidence exist. |

## Findings

| Priority | Finding | Evidence | Impact | Recommended action |
| --- | --- | --- | --- | --- |
| P0 before public distribution | Public beta evidence is on `outsidePlayDebug`; release APKs are unsigned. | Debug APK reports `application-debuggable`; release APK path is `app-outsidePlay-arm64-v8a-release-unsigned.apk` and `apksigner verify` reports missing signature metadata. | A debug APK should not be distributed publicly; unsigned release cannot be installed/distributed as final beta artifact. | Add release signing config or a documented signing step, produce signed release artifacts, and rerun preflight/install smoke on the signed artifact. |
| P1 | `outsidePlay` still merges `com.android.vending.BILLING` permission even though Play Billing is disabled. | APK badging and merged manifest include `com.android.vending.BILLING`; `BuildConfig.ENABLE_PLAY_BILLING=false`. | Policy/user-trust mismatch for an outside-Play build that claims VietQR-only donations. | Remove Billing dependency from `outsidePlay` or strip the permission with manifest tools for that flavor. Prefer moving Billing code/dependency to a Play-enabled flavor. |
| P1 | Call-related permission surface is broader than enabled functionality. | Manifest includes `USE_FULL_SCREEN_INTENT`, `RECORD_AUDIO`, `CAMERA`, `MODIFY_AUDIO_SETTINGS`; optional calls gate returns false. | Users may see scary permissions for features not available in beta; full-screen intent has policy sensitivity. | For public beta, either remove/gate call permissions from `outsidePlay` or clearly scope them to a calls-enabled flavor. |
| P1 | Notification privacy defaults may expose message content on lock screen/group summary. | Message notification uses title/text and BigText/InboxStyle; call notification/channel uses public visibility. | Chat content can be visible on lock screen depending user/channel settings. | Add a Settings privacy option for notification preview, set a publicVersion/redacted content, and consider `VISIBILITY_PRIVATE` by default for message notifications. |
| P1 | Release artifact has not received the same real-device auth/media/notification smoke as debug. | Signoff and preflight evidence are tied to `outsidePlayDebug`; release build only has assemble/shrink verification. | R8/resource shrink may alter behavior even when build passes. | Install signed release on QA device and rerun auth ready, force-stop persistence, malformed intent probes, media open/download and inbound notification smoke. |
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

Remaining concerns:

- Release package still declares Billing permission in outside-Play flavor.
- Notification preview/lock-screen behavior needs a privacy setting before broad beta.
- Debug distribution must stop once public beta starts; use signed release artifacts only.
- Visual proof bundles can contain private chat names/messages even when text redaction passes.

## Functional readiness

Ready for staged beta:

- Telegram login/code/2FA/logout/reset proof exists on a real Xiaomi API 36 device.
- Chat list/session persistence proof exists.
- Room repositories and migrations are covered by connected DeviceSafe tests.
- Compose smoke coverage covers navigation, chat row tap, search and cache entry.
- Translation and media-cache policy have unit/instrumented coverage.

Needs more beta telemetry/QA:

- Inbound notification delivery on signed release artifact.
- Mobile-data-only media behavior on signed release artifact.
- Video subtitle generation on devices with real SpeechRecognizer support.
- Android OEM matrix beyond Xiaomi/API 36 and AOSP emulators.
- Long-session behavior: large chat history, cache pressure, translation worker backlog and low-storage state.

## Release packaging assessment

Current artifacts:

- Debug split APKs: roughly 46-54 MB and debuggable.
- Release unsigned split APKs: roughly 27-33 MB after R8/resource shrink.
- `versionCode=1`, `versionName=0.1.0-outside`, `minSdk=26`, `targetSdk=37`.

For public beta distribution:

1. Use signed release APK/AAB, not debug.
2. Remove unexpected Billing permission from outside-Play.
3. Decide whether this beta distributes via Play internal/open testing or direct APK. If Play testing is used, add an AAB build/signing path and Play policy notes for Telegram client behavior, donations and permissions.
4. Rerun public beta preflight against the signed artifact, or extend `scripts/run-public-beta-preflight.ps1` to accept an explicit APK path.

## Recommended action order

1. Fix outside-Play manifest/dependency hygiene: remove Billing permission and any unused call/full-screen permission surface that is not enabled for beta.
2. Create signed release distribution path and document artifact naming, checksum and install command.
3. Add notification privacy setting or default redacted lock-screen/public notification content.
4. Rerun signed-release smoke on real device: auth ready, force-stop, malformed intents, inbound notification, mobile-data media, logout/reset if disposable.
5. Run staged beta with 20-50 testers or a very small percentage rollout for 48-72 hours.
6. Expand only if there are no P0/P1 crashes, auth failures, session-loss reports, media regressions or privacy reports.

## Final assessment

The codebase is credible for a controlled public beta, with good automated gates and unusually useful QA tooling already in place. The product should not be promoted using the current debug artifact. The shortest path to a clean public beta is to close packaging/permission hygiene, sign a release artifact, and rerun the existing preflight plus a small real-device smoke on that exact artifact.
