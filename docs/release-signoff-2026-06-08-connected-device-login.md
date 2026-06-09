# Release sign-off: connected logged-in device 2026-06-08

This pass was run after the real Redmi device was connected and already logged
in. It deliberately avoided reinstalling the APK and avoided the full connected
Gradle suite to preserve the Telegram session.

## Device and build

- Device: Redmi `25053RT47C`, serial `3511280d`
- Android API: `36`
- ABI: `arm64-v8a`
- App: `ai.telegram.android` `0.1.0-outside`
- Evidence directory: `build/real-device-qa-20260608-211129/`
- Follow-up media evidence: `build/real-device-media-qa-20260608-211754/`
- Follow-up mobile-data evidence: `build/real-device-mobile-data-qa-20260608-212500/`

## Non-destructive checks

| Area | Result | Evidence |
| --- | --- | --- |
| Logged-in baseline | Pass | `00-summary.txt`, `01-launch.png/xml`; launcher opened the app, header status was `OK`, chat list rendered `61/61`, contacts rendered `416`. |
| Permissions | Partial pass | `05-package-permissions.txt`, `05-appops-post-notification.txt`; `POST_NOTIFICATIONS` and `RECORD_AUDIO` are granted, `CAMERA` remains not granted until a camera flow requests it. |
| Notification channel/settings | Partial pass | `00-summary.txt`; app notification settings exist with default app importance and the `telegram_messages` channel exists with default importance. |
| Chat row navigation | Pass for automation on this session | `02-after-chat-row-tap.png/xml`; ADB tap opened a chat detail surface with composer and history-loading/empty-state text instead of staying stuck on the chat list. |
| Channel detail/history | Pass for one real channel with readable preview | `04-channel-detail.png/xml`; ADB tap opened a channel detail surface with `1` post, date/type filters, gallery summary and composer; `04-channel-detail-logcat.txt` had no app crash marker. |
| Media gallery/player follow-up | Pass | `docs/release-signoff-2026-06-08-connected-device-media.md`; later same-session pass verified a real media channel, gallery, full-screen video viewer and MediaCodec playback without an app crash marker. |
| Mobile-data follow-up | Pass for current cached media fixture | `docs/release-signoff-2026-06-08-connected-device-mobile-data.md`; Wi-Fi was disabled, default network was validated cellular/LTE, channel remained readable, full-screen video playback worked and app media storage did not grow unexpectedly. |
| Artifact hygiene | Pass | Raw notification/connectivity dumps that contained unrelated device data were replaced with `00-summary.txt`; `scripts/redact-sensitive-artifacts.ps1` redacted no remaining Telegram API/phone patterns. |

## Remaining blockers

| Severity | Issue | Notes | Ship impact |
| --- | --- | --- | --- |
| blocker | Inbound notification delivery and tap-to-open-chat remain unverified. | Permission and channel state are verified, but no real incoming Telegram message notification was generated during this pass. | Hold notification sign-off. |
| medium | Fresh uncached media download policy on mobile data remains unverified. | Follow-up mobile-data QA verified cellular playback and no unexpected media storage growth for the current cached fixture, but did not use a fresh uncached media item. | Track before broad beta. |
| medium | Positive subtitle generation remains unverified on the real media fixture. | Follow-up media QA opened the video player and confirmed a clear speech-recognition unavailable message, but generated subtitles did not succeed. | Track before advertising subtitle quality. |
| medium | First chat opened by ADB had `0` messages. | This is no longer an automation blocker because channel detail/history passed, but it does not itself prove chat history paging on private chats. | Needs fixture/manual pass. |

## Final decision

- Decision: hold for any release beyond internal testing.
- Cleared in this pass plus same-session follow-ups: logged-in startup, list-level real-account state, ADB chat-row navigation, one channel detail/history surface, notification permission/channel state, real media fixture, media gallery, full-screen video playback and mobile-data-only playback for the current cached fixture.
- Still blocking: real inbound notification tap-to-open.
