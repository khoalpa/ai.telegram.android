# Release sign-off follow-up: QA coverage and refactor 2026-06-09

This pass follows up the remaining review items from the prior assessment:
notification routing, mobile-data media behavior, emulator API coverage and the
first small split from a large UI file.

It is not approval for a public or broad beta release.

## Device and build

- Real device: Redmi `25053RT47C`, Android API `36`, ABI `arm64-v8a`
- Emulators:
  - `AiTelegram_AOSP_26`, Android API `26`, ABI `x86_64`
  - `AiTelegram_AOSP_35`, Android API `35`, ABI `x86_64`
- App: `ai.telegram.android` `0.1.0-outside`
- Evidence directories:
  - `build/real-device-notification-qa-20260609/`
  - `build/real-device-fresh-media-mobile-qa-20260609/`
  - `build/emulator-api26-qa-20260609/`
  - `build/emulator-api35-qa-20260609/`

## Local gates

| Gate | Result |
| --- | --- |
| `:app:testOutsidePlayDebugUnitTest` | Pass |
| `:app:lintOutsidePlayDebug` | Pass |
| `:app:assembleOutsidePlayDebug` | Pass |
| `:app:assembleOutsidePlayDebugAndroidTest` | Pass |

## Notification follow-up

| Check | Result | Evidence |
| --- | --- | --- |
| Message notification exists | Pass | `dumpsys notification` listed two `ai.telegram.android` notifications in channel `telegram_messages`; one message notification had a `contentIntent` and app package `ai.telegram.android`. |
| Runtime notification permission/channel | Pass | Device package dump showed `POST_NOTIFICATIONS` granted; notification channel `telegram_messages` was active at default importance. |
| Tap route equivalent | Pass | Notification id was mapped to the matching Room chat id via the debug DB, then `MainActivity` was started with the same `ai.telegram.android.extra.OPEN_CHAT_ID` extra used by the notification PendingIntent. `02-after-open-chat-extra.xml/png` showed the expected channel detail with history, gallery summary and composer. |

Note: the crowded Redmi notification shade made a coordinate tap unsafe because
other apps' notifications were above the AI Telegram notification. This pass
therefore verifies the real notification's PendingIntent shape plus the exact
activity extra route, but does not claim a physical shade-tap on this device.

## Mobile-data media follow-up

| Check | Result | Evidence |
| --- | --- | --- |
| Cellular-only state | Pass | `00-network-summary.txt`; Wi-Fi was disabled and the default validated internet network was cellular/LTE. |
| Channel readability on cellular | Pass | `02-gallery-mobile.xml` and `03-gallery-mobile-after-second-tap.xml` showed the channel detail remained readable with media counts, composer and `OK` status. |
| Unexpected large download guard | Pass for exercised flow | `01-app-storage-before.txt` and `04-app-storage-after.txt`; total app storage stayed `298139 KB`, `app_tdlib-files` stayed `43854 KB`, and media subdirectories did not grow. |
| Crash/ANR marker | Pass | `04-logcat-excerpt.txt` contained no app crash or ANR marker for the exercised flow. |
| Fresh uncached media download | Partial | The available safe fixture did not expose a clearly uncached media item through ADB without deleting TDLib cache or disturbing account state. Gallery/open attempts kept the app stable, but this pass does not prove a fresh uncached download on cellular. |

## Emulator coverage

| Environment | Result | Evidence |
| --- | --- | --- |
| API 26 AOSP emulator | Pass | APK/test APK installed; Compose UI instrumentation passed `OK (3 tests)` in `build/emulator-api26-qa-20260609/03-compose-ui-instrument.log`; app launched and evidence was captured in `04-launch.*`. |
| API 35 AOSP emulator | Pass | APK/test APK installed; Compose UI instrumentation passed `OK (3 tests)` in `build/emulator-api35-qa-20260609/03-compose-ui-instrument.log`; app launched and evidence was captured in `04-launch.*`; permissions were captured in `05-permissions.txt`. |

## Refactor follow-up

| Change | Result |
| --- | --- |
| Extracted chat filter/link/sender helper logic from `ChatScreens.kt` to `ChatFilterLogic.kt` | Pass |
| Added `ChatFilterLogicTest` for folder classification, visible-link filtering and old-message date filtering | Pass |
| Local build/lint/unit gates after refactor | Pass |

## Remaining release impact

- Notification route is materially improved: the real notification and exact
  `OPEN_CHAT_ID` route now have evidence. A physical shade-tap remains useful on
  a less crowded/non-Redmi device.
- Fresh uncached media download on cellular remains the main unclosed media
  policy gap. Avoid clearing TDLib cache on the logged-in Redmi session just to
  manufacture this condition.
- Emulator API 26 and API 35+ coverage is no longer missing for Compose UI smoke
  and launch.

## Decision

- Decision: still hold beyond internal testing.
- Cleared in this pass: API 26/API 35 emulator smoke, notification PendingIntent
  route, mobile-data no-crash/no-growth guard for the exercised flow, and one
  low-risk UI-file split with unit coverage.
- Still required before broader release: a fresh uncached media fixture on
  cellular, a physical notification shade-tap on a cleaner device profile, and
  continued extraction of large UI/TDLib files in small tested slices.
