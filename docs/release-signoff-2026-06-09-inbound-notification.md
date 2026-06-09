# Release sign-off: real inbound notification tap

Date: 2026-06-09 Asia/Saigon

Device: Redmi `25053RT47C`, Android API `36`, package `ai.telegram.android`
version `0.1.0-outside`.

Evidence bundle: `build/inbound-notification-proof-20260609-142346`

## Result

Pass for real inbound Telegram notification tap-to-open-chat.

## Evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Notification permission and app install state | Pass | `00-package.txt` shows `POST_NOTIFICATIONS` granted for `ai.telegram.android`. |
| Real app message notifications present | Pass | `02-notifications-after-inbound.txt` contains `ai.telegram.android` records in channel `telegram_messages`, including child message notifications without `GROUP_SUMMARY` and with `contentIntent` startActivity records. |
| Physical notification shade evidence | Pass | `02-shade-after-inbound.png/xml` captured the Android notification shade after a real Telegram inbound message appeared. |
| Physical tap opened the app | Pass | `03-activity-after-tap.txt` shows `ai.telegram.android/.MainActivity` as `topResumedActivity`, `ResumedActivity`, `mCurrentFocus` and `mFocusedWindow`. |
| Tap routed to chat detail | Pass | `03-after-tap.png/xml` shows the app on a chat/channel detail surface with message filters, gallery summary and composer visible, not merely the app shell. |
| Redaction guard | Pass | `scripts/redact-sensitive-artifacts.ps1 -Paths build/inbound-notification-proof-20260609-142346 -CheckOnly` reported `Files requiring redaction: 0`. |

## Notes

- This pass used a real inbound Telegram message from another account/device.
- The debug notification QA receiver was not used for this proof.
- Screenshots/XML may still contain private chat names or message/media content;
  review manually before sharing the evidence bundle outside trusted QA.
