# Release sign-off: connected logged-in media channel 2026-06-08

This pass was run on the already logged-in Redmi device after a real Telegram
channel with media was opened manually. It deliberately avoided reinstalling the
APK and avoided the full connected Gradle suite to preserve the Telegram
session.

## Device and build

- Device: Redmi `25053RT47C`, serial `3511280d`
- Android API: `36`
- ABI: `arm64-v8a`
- App: `ai.telegram.android` `0.1.0-outside`
- Evidence directory: `build/real-device-media-qa-20260608-211754/`

## Non-destructive checks

| Area | Result | Evidence |
| --- | --- | --- |
| Media fixture availability | Pass | `02-after-open-media.png/xml`; real channel detail exposed `Media 1`, `Gallery: 1 media / 0 tep / 0 link`, and an inline ExoPlayer surface. |
| Media gallery | Pass | `03-after-open-gallery.png/xml`; gallery modal opened with `Media 1`, `Tep 0`, `Link 0`, and one video thumbnail with duration. |
| Full-screen video viewer | Pass | `04-after-gallery-thumbnail-tap.png/xml`; tapping the gallery thumbnail opened the full-screen video viewer with ExoPlayer controls, seek bar, metadata and close control. |
| Video decode/playback | Pass | `04-after-gallery-thumbnail-tap-logcat.txt`; logcat showed MediaCodec video decode/render stats for `ai.telegram.android` and no app crash marker. |
| Subtitle negative path | Pass for fallback, generated subtitles not verified | Full-screen viewer showed a clear unavailable message for speech recognition error code `12` instead of spinning indefinitely. |

## Remaining issues

| Severity | Issue | Notes | Ship impact |
| --- | --- | --- | --- |
| medium | Positive subtitle generation remains unverified on this real device. | The player fallback is readable, but this fixture did not produce generated original/Vietnamese subtitles. | Track before advertising subtitle quality; not a media playback blocker. |
| blocker | Inbound notification delivery and tap-to-open-chat remain unverified. | This media pass did not generate an incoming Telegram notification. | Hold notification sign-off. |
| medium | Fresh uncached media download policy on mobile data remains unverified. | Same-session follow-up verified cellular playback and no unexpected media storage growth for the current cached fixture, but did not use fresh uncached media. | Track before broad beta. |

## Final decision

- Decision: hold for any release beyond internal testing.
- Cleared in this pass: real media fixture, media gallery, full-screen video
  viewer, video decode/playback and subtitle unavailable fallback.
- Still blocking: real inbound notification tap-to-open.
