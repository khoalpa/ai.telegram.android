# Release sign-off: priority rerun 2026-06-08

This rerun executes the highest-priority cleanup and emulator QA items from the
application review. It is not approval for a public or broad beta release.

## Artifact hygiene

| Check | Result | Evidence |
| --- | --- | --- |
| Generated log redaction | Pass | `scripts/redact-sensitive-artifacts.ps1` scanned generated text artifacts and redacted two existing log files that contained Telegram runtime identifiers. |
| Future artifact guard | Pass | Added `scripts/redact-sensitive-artifacts.ps1`; `.gitignore` now ignores root-level QA screenshots/XML/log captures. |
| Local Telegram config | Preserved | `local.properties` remains ignored and was intentionally not rewritten so local TDLib login/build configuration still works. Rotate the Telegram API hash externally if any unredacted artifact was already shared. |

## Local gates

| Check | Result | Evidence |
| --- | --- | --- |
| `.\scripts\run-local-release-gates.ps1 -PlainConsole` | Pass | Unit tests, lint, debug APK assemble and androidTest APK assemble all passed after the cleanup and test fixes. |

## Emulator coverage

| Environment | Result | Evidence |
| --- | --- | --- |
| Emulator API 26, `AiTelegram_AOSP_26` | Pass | Created the API 26 AVD after installing `platforms;android-26` and `system-images;android-26;default;x86_64`; `build/release-qa-20260608-210240/summary.md` records local gates plus Compose UI instrumentation `OK (3 tests)`. |
| Emulator API 35, `AiTelegram_AOSP_35` | Pass | `build/release-qa-20260608-210330/summary.md` records local gates plus Compose UI instrumentation `OK (3 tests)`. |

## Fixes made during rerun

| Area | Change | Reason |
| --- | --- | --- |
| Menu UI test stability | Added `menu_list` test tag and changed `NavigationUiTest` to scroll the lazy list to `menu_item_Cache` before clicking. | API 26 viewport did not compose the Cache row eagerly, so direct node lookup failed. |
| Connected wrapper correctness | `scripts/run-connected-safe.ps1` now parses instrumentation output for `FAILURES!!!`, non-zero failure counts, crash markers and negative instrumentation code. | `adb shell am instrument` can report test failures while the process exits with code `0`; release QA summaries must not mark that as pass. |

## Remaining blockers

| Severity | Issue | Ship impact |
| --- | --- | --- |
| blocker | Real-account chat detail/history/media flow still needs physical-touch QA or a reliable non-Xiaomi automation device. | Hold public/broad beta. |
| blocker | Inbound notification delivery and tap-to-open-chat remain unverified with a real incoming Telegram message. | Hold notification sign-off. |
| blocker | Mobile-data-only model/media policy remains unverified on a controlled real-device network setup. | Hold public/broad beta. |
| high | Full connected suite remains unsafe for a logged-in Redmi/HyperOS install. | Continue using disposable installs, emulator Compose runs and targeted DeviceSafe instrumentation only. |

## Final decision

- Decision: hold for any release beyond internal testing.
- Cleared in this rerun: artifact redaction, local gates, API 26 emulator Compose UI, API 35 emulator Compose UI and connected wrapper failure detection.
