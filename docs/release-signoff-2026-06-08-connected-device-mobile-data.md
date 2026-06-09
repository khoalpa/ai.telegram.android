# Release sign-off: connected logged-in mobile data 2026-06-08

This pass was run on the already logged-in Redmi device after Wi-Fi was turned
off and mobile data was enabled. It deliberately avoided reinstalling the APK
and avoided the full connected Gradle suite to preserve the Telegram session.

## Device and build

- Device: Redmi `25053RT47C`, serial `3511280d`
- Android API: `36`
- ABI: `arm64-v8a`
- App: `ai.telegram.android` `0.1.0-outside`
- Evidence directory: `build/real-device-mobile-data-qa-20260608-212500/`

## Non-destructive checks

| Area | Result | Evidence |
| --- | --- | --- |
| Mobile-data-only network state | Pass | `00-network-summary.txt`; Wi-Fi was disabled and the default network was cellular/LTE with `INTERNET` and `VALIDATED` capabilities. Raw connectivity dumps were not saved because they include SSIDs, IP addresses and unrelated package/device data. |
| Channel readability on cellular | Pass | `02-mobile-channel.png/xml`; the logged-in channel stayed readable on mobile data with header `OK`, `Media 1`, `Gallery: 1 media / 0 tep / 0 link`, composer and inline media preview. |
| Full-screen video playback on cellular | Pass | `04-mobile-fullscreen-player.png/xml`; the inline media opened to the full-screen video viewer with ExoPlayer controls, seek bar, metadata and close control. |
| No app crash marker during cellular media flow | Pass | `04-mobile-fullscreen-player-logcat.txt`; no `FATAL EXCEPTION`, app process crash or ANR marker for `ai.telegram.android` was observed. |
| Unexpected large media download guard | Pass for this fixture | `01-app-storage-before.txt`, `04-app-storage-after.txt`; `app_tdlib-files` stayed at `543175 KB`, total app storage moved from `554508 KB` to `554544 KB` and only `app_tdlib-db` grew by about `36 KB`. |
| Subtitle negative path on cellular | Pass for fallback, generated subtitles not verified | Full-screen viewer showed the same clear speech-recognition unavailable message instead of spinning indefinitely. |

## Notes

- The media file was already present in TDLib storage before this pass, so this
  verifies cellular playback and no unexpected large auto-download for the
  current fixture, not a fresh mobile-data media download.
- The gallery row remained visible on mobile data, but the ADB tap used during
  this pass did not re-open the gallery modal. The same gallery modal was
  verified earlier in the same logged-in session before the network switch.

## Remaining issues

| Severity | Issue | Notes | Ship impact |
| --- | --- | --- | --- |
| medium | Fresh uncached media download policy on mobile data remains unverified. | The available video was already cached before Wi-Fi was disabled. | Track with an uncached media fixture before broad beta. |
| medium | Positive subtitle generation remains unverified on this real device. | The fallback is readable, but generated original/Vietnamese subtitles did not succeed. | Track before advertising subtitle quality. |
| blocker | Inbound notification delivery and tap-to-open-chat remain unverified. | This mobile-data pass did not generate an incoming Telegram notification. | Hold notification sign-off. |

## Final decision

- Decision: hold for any release beyond internal testing.
- Cleared in this pass: mobile-data-only network state, channel readability,
  full-screen video playback on cellular and no unexpected large media storage
  growth for the current fixture.
- Still blocking: real inbound notification tap-to-open.
