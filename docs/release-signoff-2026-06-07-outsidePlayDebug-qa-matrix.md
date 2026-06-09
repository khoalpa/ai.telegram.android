# Release sign-off: outsidePlayDebug QA matrix 2026-06-07

This sign-off records the QA matrix attempt for the local `outsidePlayDebug`
candidate after adding minimal Compose/instrumented navigation coverage.

## Build evidence

- Version name/code: `0.1.0-outside` / `1`
- Artifact source: local workspace `D:\project\ai.telegram.android`; no `.git` metadata available in this directory
- APK path: `app/build/outputs/apk/outsidePlay/debug/`
- ABI artifacts:
  - `app-outsidePlay-arm64-v8a-debug.apk` - 51,665,941 bytes
  - `app-outsidePlay-armeabi-v7a-debug.apk` - 47,276,751 bytes
  - `app-outsidePlay-x86_64-debug.apk` - 54,485,682 bytes
- `:app:testOutsidePlayDebugUnitTest`: pass, 54 tests / 0 failures / 0 skipped
- `:app:lintOutsidePlayDebug`: pass
- `:app:assembleOutsidePlayDebug`: pass
- `:app:compileOutsidePlayDebugAndroidTestKotlin`: pass
- Connected Compose/instrumented execution on the Redmi remains unreliable: direct `am instrument` and Gradle connected runs timed out at the runner/device layer.

## Evidence

- Evidence directory: `build/qa-matrix-2026-06-07/`
- Related chat-tap debug evidence: `build/chat-tap-debug-2026-06-07/`
- Device: Redmi `25053RT47C`
- Android/API: Android 16 / API 36
- ABI: `arm64-v8a`
- Network observed: validated Wi-Fi, SSID redacted in external sharing; mobile network also present but app QA was not switched to mobile-data-only.

## Environment coverage

| Environment | Device/API/ABI | Network | Result | Evidence |
| --- | --- | --- | --- | --- |
| Emulator API 26 | Not available | Not verified | Missing | No AVD/system image/cmdline tools available locally. |
| Emulator API 35+ | Not available | Not verified | Missing | No AVD/system image/cmdline tools available locally. |
| Real arm64 device on Wi-Fi | Redmi `25053RT47C` / API 36 / `arm64-v8a` | Wi-Fi validated | Partial | `01-shortcut-chats`, `02-shortcut-search`, `03-shortcut-settings`, `04-shortcut-cache`; authenticated surfaces render. |
| Real arm64 device on mobile data | Same device | Mobile data not isolated | Missing | Connectivity shows cellular networks, but the app was not forced off Wi-Fi for data-saver/model-download QA. |
| Real device without supported speech service | Not available | Not verified | Missing | Subtitle negative-path environment not available. |

## Release gates

| Area | Result | Evidence | Owner |
| --- | --- | --- | --- |
| TDLib auth | Blocked | Settings surface renders an existing connected session, but this is not a dedicated QA Telegram account. Phone/code/2FA/logout/reset were not exercised to avoid disrupting a live account. | Project owner |
| Chat sync | Fail | Chat list renders 60/60. Previous latest-app debug run showed ADB taps on chat rows do not reach `AiTelegramChatTap`, so chat detail/history cannot be reliably opened by automation. | Project owner |
| Translation | Partial | Cache screen renders translation/model controls. Message-level pending/missing-model/ready/failed/hidden states were not exercised because chat detail remains blocked and QA account fixtures are absent. | Project owner |
| Media | Blocked | No image/video/document chat fixture could be opened. Malformed share URI to a missing file starts the app and does not crash, but real media download/open QA is not complete. | Project owner |
| Notifications | Partial | `POST_NOTIFICATION` app-op is `allow` and package permission is granted. No inbound Telegram message or notification tap-to-open-chat was exercised. | Project owner |
| Feature gates | Partial/pass for internal MVP | Search, Settings, Cache shortcuts render; feature-gated surfaces were not fully re-run in this matrix pass. | Project owner |
| Privacy/security | Partial pass | Explicit malformed Telegram links and malformed share inputs did not crash the app. Evidence: `05-deeplink-tg-malformed`, `06-deeplink-http-malformed`, `07-deeplink-nontelegram-explicit`, `08-share-text-explicit-fixed`, `10-share-image-missing-file-uri`. | Project owner |

## Tested inputs

| Input | Result | Evidence |
| --- | --- | --- |
| Shortcut: Chats | Renders chat list with connected data | `01-shortcut-chats.png/xml` |
| Shortcut: Search | Renders Telegram search/discovery tools | `02-shortcut-search.png/xml` |
| Shortcut: Settings | Renders account/settings surface | `03-shortcut-settings.png/xml` |
| Shortcut: Cache | Renders cache/model controls | `04-shortcut-cache.png/xml` |
| `tg://resolve?domain=%zz` explicit deep link | No app crash; app renders readable fallback surface | `05-deeplink-tg-malformed*` |
| `https://t.me/%zz` explicit deep link | No app crash; app renders readable fallback surface | `06-deeplink-http-malformed*` |
| `https://example.com/not-telegram` explicit robustness probe | No app crash; sanitizer avoids treating it as a Telegram link | `07-deeplink-nontelegram-explicit*` |
| `ACTION_SEND text/plain` with malformed URL text | Starts app, no crash | `08-share-text-explicit-fixed*` |
| `ACTION_SEND image/png` with missing `file://` URI | Starts app, no crash | `10-share-image-missing-file-uri*` |

Invalid command attempts such as SEND_MULTIPLE without streams are preserved in the evidence directory but are not counted as app pass/fail evidence.

## Known issues

| Label | Issue | Workaround | Ship decision |
| --- | --- | --- | --- |
| blocker | QA matrix cannot be fully completed without a dedicated Telegram QA account and phone/code/2FA access. | Provision a disposable QA Telegram account with media/message fixtures and repeat auth, destructive, notification and media checks. | Hold beyond internal testing. |
| blocker | Chat row ADB taps still do not reach `ChatScreen` row callback on Redmi/HyperOS, while channel rows and some top-level controls do. | Run on emulator/non-Xiaomi device and add an app-level test host that can exercise `TelegramClientApp` without TDLib. | Hold chat/history/media sign-off. |
| blocker | Chat history, translation states, media download/open and notification tap-to-open remain unverified end-to-end. | Complete device QA with the QA account after chat-detail navigation is testable. | Hold. |
| medium | Mobile-data data-saver/model-download behavior was not isolated because Wi-Fi remained active. | Repeat with Wi-Fi disabled or a controlled network profile. | Required before public beta. |
| medium | Connected Compose/instrumented runner times out on the current Redmi/HyperOS device. | Use emulator/non-Xiaomi device for connected UI test sign-off; keep Xiaomi wrapper only for install-prompt handling. | Required before automation sign-off. |

## Final decision

- Decision: hold for any release beyond internal testing
- Decision owner: project owner
- Date: 2026-06-07, Asia/Saigon
- Notes: The QA matrix has been executed as far as possible on the available device without a dedicated Telegram QA account. Local build gates and non-destructive robustness checks pass. End-to-end auth, chat history, media, translation, notification and mobile-data requirements remain incomplete and must not be marked as release-passing yet.
