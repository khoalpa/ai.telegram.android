# Release sign-off: outsidePlayDebug real-device rerun 2026-06-08

This rerun was started after a real device and Telegram account were reported as connected. It is not approval for a public or broad beta release.

## Device and build

- Device: Redmi `25053RT47C`, serial `3511280d`
- Android API: `36`
- ABI: `arm64-v8a`
- APK: `app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-arm64-v8a-debug.apk`
- Version: `0.1.0-outside` / `1`
- Evidence directory: `build/release-qa-2026-06-08-real-device/`

## Automation result

| Check | Result | Evidence |
| --- | --- | --- |
| `RepositoryRoomTest` via `adb shell am instrument` | Pass | `OK (12 tests)` |
| `AppDatabaseMigrationTest` via `adb shell am instrument` | Pass | `OK (1 test)` |
| Full `:app:connectedOutsidePlayDebugAndroidTest` through Xiaomi watcher | Fail/unsafe | Timed out after 5 minutes; instrumentation remained active and had to be force-stopped. |
| `NavigationUiTest` via direct instrumentation | Fail/timeout | Timed out after 2 minutes; runner had to be force-stopped. |
| ADB chat-row tap | Fail for automation | `02-after-chat-row-tap.xml` and `03-after-chat-row-touchscreen-tap.xml` stayed on the chat list. |

## Important data-loss note

The full connected runner left the device in an unsafe automation state. After cleanup, `pm path ai.telegram.android` no longer returned an installed package, so the app had to be reinstalled manually. The package `firstInstallTime` reset to `2026-06-08 09:10:14`, permissions reset to not granted, and the app header showed `Dang nhap` after launch.

Treat this as a release-process blocker: do not run the full Gradle connected suite on a logged-in QA device unless the account/session can be safely recreated. Use a disposable test install or run targeted `adb shell am instrument` classes only.

## Manual/ADB probes after reinstall

| Area | Result | Evidence |
| --- | --- | --- |
| App launch | Pass for startup only | `01-app-launch.png/xml`; app rendered seed chats and did not crash. |
| Privacy/deeplink | Partial pass | Explicit malformed/non-Telegram VIEW probes produced no crash markers: `04-deeplink-evil-host-*`, `05-deeplink-tg-malformed-*`. |
| Share text | Partial pass | Long text SEND probe produced no crash markers and showed `Da them noi dung share vao composer.` in `06-share-text-long.xml`. |
| Calls feature gate | Pass | `07-open-calls-extra.png/xml`; Calls screen showed the disabled media-engine explanation and voice/video call buttons were disabled. |
| Notification permission/channel | Blocked | App reinstall reset `POST_NOTIFICATIONS` to `granted=false`; no inbound notification path could be verified. |
| TDLib auth/chat/media real account | Blocked | The logged-in app state was lost before these checks could be completed. |
| Mobile-data-only behavior | Blocked | Device had Wi-Fi and cellular available; account/session loss prevented model/media policy verification. |

## Final decision

- Decision: hold for any release beyond internal testing.
- Cleared in this rerun: Room repository instrumentation, database migration instrumentation, external intent no-crash probes, Calls disabled-state evidence.
- Still blocking: stable UI automation on this Redmi/HyperOS device, real-account TDLib auth/chat/media/notification evidence, mobile-data-only policy evidence, emulator API 26/API 35+ coverage.
