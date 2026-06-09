# Release readiness matrix

Use this matrix before promoting an APK beyond internal testing. Unit tests and lint only prove the local code path is healthy; TDLib, media, speech recognition, notifications and billing-adjacent flows still need device evidence.

Record each candidate build in `docs/release-signoff-template.md` so build output, device coverage, known issues and the final ship/hold decision stay together.

## Release decision policy

Use the most restrictive matching decision for each candidate build.

| Decision | Allowed audience | Required criteria |
| --- | --- | --- |
| `hold` | No new testers. Keep the APK local or discard it. | Any blocker is open, any required build gate fails, generated QA artifacts cannot be redacted, or TDLib login/chat reading is broken. |
| `internal testing only` | Maintainers and trusted QA accounts only. | Build gate passes, no launch/auth blocker is known, primary chat list renders, Settings/Cache are usable, feature-gated surfaces show disabled states, and all known high/blocker issues are documented in the sign-off. |
| `closed beta` | Small invited tester group. | All release gates pass on at least the required emulator coverage plus one real arm64 Wi-Fi device; no blocker is open; every high issue has either a fix or a documented feature gate; notification tap-to-open and chat detail/history/media have real-device evidence. |
| `public/broad beta` | Wider external testing. | Closed-beta criteria pass, mobile-data-only policy is verified on a real device, inbound notification delivery is verified with a real Telegram message, privacy/security probes pass, and artifact redaction is recorded for the final QA bundle. |
| `production candidate` | General availability candidate. | Public/broad-beta criteria pass for two consecutive candidate builds or one candidate plus an unchanged rerun; no open blocker/high issues; rollback/install notes and versioned artifacts are attached. |

Hard no-go conditions:

- Crash on launch, broken TDLib auth, unreadable chat list/detail, local data loss, or destructive Telegram action without confirmation.
- Any real secret, phone number, chat identifier, or Telegram API hash appears in a shared artifact after redaction.
- Full connected instrumentation was run against a logged-in QA install and left app/session state uncertain.
- Calls, Premium/Business, or other optional Telegram APIs are enabled without matching device QA evidence.

## Required environments

| Environment | Purpose | Minimum pass criteria |
| --- | --- | --- |
| Emulator API 26 | Min-SDK smoke test | App launches, seed data renders, settings persist, no startup crash. |
| Emulator API 35+ | Modern Android permissions | Notification/audio/camera permission prompts appear only when needed. |
| Real arm64 device on Wi-Fi | Main TDLib/device pass | Login, chat history, media download, ML Kit model download and translation succeed. |
| Real arm64 device on mobile data | Data-saver behavior | Model downloads are gated, source fallback remains readable, no unexpected large auto-downloads. |
| Real device without supported speech service | Subtitle negative path | Video subtitle UI shows an unavailable state instead of spinning forever. |

## Release gates

| Area | Gate | Evidence |
| --- | --- | --- |
| Build | `:app:testOutsidePlayDebugUnitTest`, `:app:lintOutsidePlayDebug`, `:app:assembleOutsidePlayDebug` and `:app:assembleOutsidePlayDebugAndroidTest` pass | Console output or CI artifact link. |
| TDLib auth | Phone, code, 2FA and logout/reset work with configured `telegramApiId`/`telegramApiHash` | Screen recording or QA notes with device/API/ABI. |
| Chat sync | Chat list, history paging, read state, edit/delete/pin updates persist after force-stop | QA notes plus logcat excerpt for failures. |
| Translation | Pending, missing-model, ready, failed and hidden states all render readable fallback text | Screenshots for each state. |
| Media | Image, video and document download/open flows work on Wi-Fi and respect mobile-data policy | Screenshots plus cache size before/after. |
| Notifications | Android 13+ permission, local notification channel and tap-to-open-chat work | Screenshot of permission/channel and tapped destination. |
| Feature gates | Calls, Premium/Business and optional APIs either work or show a clear disabled state; optional Premium/Business actions must remain disabled unless binding/device QA is attached | Screenshots of disabled state or QA evidence for each enabled action group. |
| Privacy/security | Share/deep-link intents accept only expected input, do not crash on malformed links, and generated QA artifacts are redacted before sharing | Unit test output plus device malformed-link/share probes, observed result and `scripts/redact-sensitive-artifacts.ps1` plus `-CheckOnly` output. |

## Triage labels

| Label | Meaning | Ship impact |
| --- | --- | --- |
| `blocker` | Data loss, auth broken, crash on launch, unreadable primary chat flow | Do not ship. |
| `high` | Major Telegram/translation/media path broken for a common device | Fix before public beta. |
| `medium` | Feature-gated or secondary path broken with clear fallback | Can ship internally with known issue. |
| `low` | Visual polish, copy, rare-device issue with workaround | Track in backlog. |

## Automation safety

- Use `scripts/run-connected-safe.ps1` for connected instrumentation. It fails
  the process when Android instrumentation reports test failures, even when
  `adb shell am instrument` exits with code `0`.
- Do not run the full connected Gradle suite on a logged-in Redmi/HyperOS QA
  install. Use a disposable install, a non-Xiaomi device, or emulator-specific
  Compose UI runs.
