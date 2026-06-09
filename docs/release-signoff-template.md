# Release sign-off template

Copy this template into the release issue or PR before promoting an APK beyond internal testing.

## Build evidence

- Version name/code:
- Commit or artifact source:
- APK path or CI artifact link:
- ABI(s) tested:
- Local gate command: `.\scripts\run-local-release-gates.ps1`
- Release QA bundle command: `.\scripts\run-release-qa.ps1`
- Release QA summary path:
- Artifact redaction log:
- Artifact redaction check-only result:
- `:app:testOutsidePlayDebugUnitTest`: pass/fail, link:
- `:app:lintOutsidePlayDebug`: pass/fail, link:
- `:app:assembleOutsidePlayDebug`: pass/fail, link:
- `:app:assembleOutsidePlayDebugAndroidTest`: pass/fail, link:
- Safe connected command: `.\scripts\run-connected-safe.ps1`

## Safe test execution

- Full connected suite was run only on a disposable install or before Telegram login: yes/no, evidence:
- Logged-in QA install was protected from `:app:connectedOutsidePlayDebugAndroidTest`: yes/no
- Real-device connected instrumentation used only disposable installs: yes/no/not applicable, evidence:
- `scripts\run-connected-safe.ps1 -DisposableInstall` or `scripts\run-release-qa.ps1 -AllowDisposableRealDeviceConnectedQa` was used for any real device instrumentation: yes/no/not applicable
- Redmi/HyperOS used only the Room/migration allowlist: yes/no/not applicable
- Compose UI tests ran on emulator/non-Xiaomi: yes/no/not applicable
- Any direct instrumentation command used:
- Cleanup needed after instrumentation: yes/no, notes:

## Environment coverage

| Environment | Device/API/ABI | Network | Result | Evidence |
| --- | --- | --- | --- | --- |
| Emulator API 26 |  |  |  |  |
| Emulator API 35+ |  |  |  |  |
| Real arm64 device on Wi-Fi |  | Wi-Fi |  |  |
| Real arm64 device on mobile data |  | Mobile data |  |  |
| Real device without supported speech service |  |  |  |  |

## Release gates

| Area | Result | Evidence | Owner |
| --- | --- | --- | --- |
| TDLib auth |  |  |  |
| Chat sync and chat detail/history |  |  |  |
| Translation |  |  |  |
| Media |  |  |  |
| Notifications and tap-to-open-chat |  |  |  |
| Feature gates |  |  |  |
| Privacy/security and artifact redaction |  |  |  |

## Known issues

| Label | Issue | Workaround | Ship decision |
| --- | --- | --- | --- |
| blocker/high/medium/low |  |  |  |

## Final decision

- Decision: hold / internal testing only / closed beta / public/broad beta / production candidate
- Release criteria used: `docs/release-readiness-matrix.md`
- Decision owner:
- Date:
- Notes:
