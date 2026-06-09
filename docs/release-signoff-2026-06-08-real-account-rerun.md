# Release sign-off: outsidePlayDebug real-account rerun 2026-06-08

This rerun was performed after the QA Telegram account was logged in again on the real Redmi device. It intentionally avoids the full Gradle connected suite because that path previously timed out and destroyed the logged-in install state.

This is not approval for a public or broad beta release.

## Device and build

- Device: Redmi `25053RT47C`, serial `3511280d`
- Android API: `36`
- ABI: `arm64-v8a`
- Version: `0.1.0-outside` / `1`
- Package install time: `2026-06-08 09:10:14`
- Evidence directory: `build/release-qa-2026-06-08-real-account-rerun/`

## Safe execution rule

Do not run `:app:connectedOutsidePlayDebugAndroidTest` against this logged-in QA install. Use either physical-touch manual QA or narrow instrumentation classes via:

```text
adb shell am instrument -w -e class <single test class> ai.telegram.android.test/androidx.test.runner.AndroidJUnitRunner
```

The previous full connected suite left instrumentation active, required cleanup, and caused the app package/session to be lost. That risk remains a release-process blocker for this device profile.

## Real-account evidence

| Area | Result | Evidence |
| --- | --- | --- |
| QA account restored | Pass | `01-session-launch.png/xml`; launch did not show login, header status was `OK`, Settings showed `Da ket noi Telegram`, and counts rendered as `61` chats plus `416` contacts. |
| Package/permissions | Pass for current state | `08-package-permission-excerpt.txt`; `POST_NOTIFICATIONS` granted, `CAMERA` and `RECORD_AUDIO` not granted by default. |
| Notification toggle/app-op | Pass for permission state | `04-shortcut-settings.xml`, `08-appops-post-notification.txt`; message notification toggle checked and `POST_NOTIFICATION: allow`. |
| Chat list sync | Pass for list-level real account | `04-shortcut-chats.png/xml`, `13-clean-deeplink-evil-host.xml`; chat list rendered `61/61`, unread count, personal/secret filters, `OK` status, and `416` contacts after process restart. |
| Settings/account surface | Pass | `04-shortcut-settings.png/xml`; account card showed connected Telegram state, diagnostics/logout rows, theme controls and notification setting. |
| Cache surface | Pass for rendering only | `04-shortcut-cache.png/xml`; translation/media cache controls rendered, including clean translation/all-cache actions and media counters. |
| Notification channel dump | Partial | `08-notification-dump-excerpt.txt`; device dump was noisy and did not provide a clean app-specific channel excerpt, but runtime permission/app-op and Settings toggle were verified. |

## Targeted instrumentation

| Test class | Result | Evidence |
| --- | --- | --- |
| `ai.telegram.android.data.RepositoryRoomTest` | Pass | `02-instrument-repository-room.txt`, `OK (12 tests)`. |
| `ai.telegram.android.data.AppDatabaseMigrationTest` | Pass | `03-instrument-migration.txt`, `OK (1 test)`. |

No full connected suite was run in this rerun.

## Local verification gates

| Gate | Result | Notes |
| --- | --- | --- |
| `:app:testOutsidePlayDebugUnitTest` | Pass | Included in the final local Gradle verification run. |
| `:app:lintOutsidePlayDebug` | Pass | No lint failures. |
| `:app:assembleOutsidePlayDebug` | Pass | APK assemble task up-to-date/pass. |
| `:app:assembleOutsidePlayDebugAndroidTest` | Pass | Android test APK assemble task up-to-date/pass. |

## External-entry probes

| Probe | Result | Evidence |
| --- | --- | --- |
| Hostile Telegram-looking URL: `https://t.me.evil.example/channel` | Pass | `13-clean-deeplink-evil-host-*`; no crash marker, app returned to Chat with `61/61` and `OK`. |
| Malformed `tg://resolve?domain=` URL | Pass | `14-clean-deeplink-tg-malformed-*`; no crash marker, app remained in the logged-in Chat surface. |
| Long text share (`5000` chars) | Pass | `17-fast-share-text-long-*`; no crash marker, snackbar showed `Noi dung share da san sang. Chon mot chat de them vao composer.` |
| Calls shortcut extra | Pass as gated feature | `16-clean-open-calls-extra-*`; Calls rendered with media-engine unavailable message and voice/video call buttons disabled. |

## Remaining blockers

| Label | Issue | Current status | Ship impact |
| --- | --- | --- | --- |
| blocker | Reliable chat-detail/history/media manual flow still needs physical touch on the real device or a different automation-capable device. ADB tap injection was unreliable on this Redmi/HyperOS profile in the prior rerun. | Not cleared in this rerun. | Hold public/broad beta. |
| blocker | Inbound notification delivery and tap-to-open-chat were not verified with a real incoming Telegram message. | Permission/toggle state is verified only. | Hold notification sign-off. |
| blocker | Mobile-data-only behavior was not isolated. | Device had Wi-Fi and cellular available; no controlled mobile-data media/model policy pass. | Hold public/broad beta. |
| blocker | Emulator API 26 and API 35+ coverage is still missing locally. | No configured AVDs in this workspace environment. | Hold public/broad beta. |
| high | Full connected suite is unsafe for logged-in Redmi QA install. | Mitigated operationally by using single-class instrumentation only. | Do not use for release sign-off on this device. |

## Final decision

- Decision: hold for any release beyond internal testing.
- Cleared in this rerun: QA account/session restoration, list-level TDLib sync, Settings/Cache surfaces, notification permission state, Room/migration instrumentation, external-entry no-crash behavior, Calls disabled-state gate.
- Still required: physical-touch chat detail/history/media evidence, inbound notification evidence, mobile-data-only evidence, emulator coverage and a safer UI automation target.
