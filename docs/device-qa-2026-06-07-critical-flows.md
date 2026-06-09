# Device QA: critical flows 2026-06-07

## Scope

- Device: Redmi `25053RT47C`
- Android/API: Android 16 / API 36
- ABI: `arm64-v8a`
- APK: `app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-arm64-v8a-debug.apk`
- Evidence directory: `build/qa-device-2026-06-07/`
- Method: ADB install, launch, screenshot, UI hierarchy dump, logcat, force-stop/relaunch, shortcut intents.

## Summary

Result: hold.

The app installs, launches and renders without crashing on the real device. Shortcut intents successfully open Chats, Search, Settings and Cache, which confirms the app can render key authenticated surfaces: 60 chats, 414 contacts, notification permission enabled, and cache/model controls visible.

The critical flow QA still cannot pass. ADB-delivered touch events reach the app process, but taps on chat rows and top-level controls do not produce visible navigation or state changes. This blocks reliable verification of chat detail/history, message-level translation states, media open/download flows, and notification tap-to-open behavior. Treat this as a release blocker until manual physical-touch QA or a second device proves the issue is limited to ADB automation.

## Findings

| Flow | Result | Evidence | Notes |
| --- | --- | --- | --- |
| TDLib auth/session | Partial | `16-shortcut-settings.png/xml`, `21-shortcut-settings.png/xml` | Settings shows Telegram account connected plus 60 chats and 414 contacts. New phone/code/2FA/logout/reset were not tested to avoid disrupting the live account. |
| Chat sync/history | Fail | `21-shortcut-chats.png/xml`, `22-chat-row-tap-after-shortcut.png/xml`, `12-relaunch.png` | Chat list renders 60/60 and unread counts update across launches, but tapping a chat row does not open history. History paging/read/edit/delete/pin persistence is not verified. |
| Translation | Partial | `21-shortcut-cache.png/xml`, `21-shortcut-chats.png/xml` | Cache screen shows translation cache/model controls and 0/5 downloaded language pairs. Message-level pending/missing-model/ready/failed/hidden states could not be opened or exercised. |
| Media | Blocked | `22-chat-row-tap-after-shortcut.png/xml` | No chat/media detail could be opened, so image/video/document download/open behavior was not tested. |
| Notifications | Partial | `21-shortcut-settings.png/xml`, package/app-op dump | Android notification permission is granted and `POST_NOTIFICATION` app-op is `allow`; no inbound message or notification tap-to-open path was exercised. |

## Cross-cutting blocker

The following ADB interactions produced no visible navigation or state change:

- Tap chat item center: `input tap 640 1262`
- Tap hamburger menu: `input tap 115 240`
- Tap settings tab: `input tap 1120 415`
- Tap search field: `input tap 500 870`
- Touchscreen-source tap: `input touchscreen tap 1120 415`
- DPAD/ENTER navigation

The app process remained focused and did not crash. Logcat showed TDLib binlog activity and no `AndroidRuntime` crash for the app during the tap attempts.

Later logcat inspection showed MIUI/InputDispatcher delivering ACTION_DOWN/ACTION_UP to `ai.telegram.android/ai.telegram.android.MainActivity`, so this is no longer just an install or focus problem. It may still be an ADB injection/device-automation issue, but until manual physical touch or another device proves otherwise, it blocks device QA sign-off.

ADB shortcut intents did work:

- `Chats`: renders `Chat`, `60/60`, unread count, search field and chat rows.
- `Search`: renders Telegram search/discovery/join-channel tools.
- `Settings`: renders connected account, notification toggle and translation settings.
- `Cache`: renders cache usage, media cache, language packages and ML Kit model download controls.

## Evidence files

- `01-auth-chat-list.png/xml`: app launch baseline.
- `02-chat-open.png/xml`: initial tap attempt.
- `03-chat-history.png/xml`: chat item tap attempt.
- `04-chat-history-center-tap.png/xml`: exact clickable parent center tap attempt.
- `07-menu-tap.png/xml`: hamburger tap attempt.
- `08-search-tap.png`: search tap attempt.
- `10-touchscreen-tap-settings.png/xml`: touchscreen-source tap attempt.
- `12-relaunch.png`, `12-relaunch-logcat.txt`: force-stop/relaunch evidence.
- `13-after-relaunch-settings-tap.png/xml`: settings tap after relaunch.
- `16-shortcut-settings.png/xml`: Settings opened via shortcut intent.
- `21-shortcut-chats.png/xml`: Chats opened via shortcut intent.
- `21-shortcut-search.png/xml`: Search opened via shortcut intent.
- `21-shortcut-settings.png/xml`: Settings opened via clean shortcut launch.
- `21-shortcut-cache.png/xml`: Cache/model screen opened via clean shortcut launch.
- `22-chat-row-tap-after-shortcut.png/xml`: chat row tap after shortcut launch; no chat detail opened.

Note: evidence is from a live Telegram account and may contain personal names in screenshots/XML. Redact before external sharing.

## Next actions

1. Repeat the same checks with manual physical touch on the device to separate an ADB-injection issue from an app input bug.
2. If manual touch also fails, treat chat-row/top-level click handling as a blocker UI regression before any broader QA.
3. If manual touch passes, run QA again using a clean emulator or non-Xiaomi device to avoid HyperOS input/install prompts.
4. Add a minimal Compose UI/instrumented test for top-level navigation and chat-item click behavior.
5. Use a dedicated QA Telegram account for phone/code/2FA/logout/reset, inbound notification, media, and destructive chat actions.
