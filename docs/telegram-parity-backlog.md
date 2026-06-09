# Telegram parity backlog

This app is currently a Vietnamese-first Telegram reader/client MVP. The list below tracks the main gaps to close before it behaves like the official Telegram Android app.

## Shipped in this repo

- TDLib sign-in, chat list, chat history, contact sync, public search and basic channel join.
- Text sending, private chat creation, basic group creation, pin, mute, archive and media download.
- Rich composer shell: photo/video/file pickers, multi-file album queue, camera photo capture, pending-media preview/removal, captions, polls, contacts, silent send and scheduled send.
- Outgoing media files are copied into an app upload cache and sent as photo, video or document via TDLib.
- Translation-first reading with source-text fallback when no usable translation is available, plus blacklist, translation cache, media cache, video playback and generated Vietnamese subtitles.
- Basic message actions: reply with text, edit text message, forward message by target chat ID, delete message, pin message and send quick emoji reactions.
- Message actions can also be opened from a Telegram-like long-press context menu with copy, reply, forward, pin, unpin, edit, delete, select and quick reactions.
- Message sync state: TDLib read inbox/outbox updates, outgoing sending/failed/sent/read flags, edited content updates, deleted-message updates, pinned-message updates and chat action/typing status are persisted to Room and surfaced in the UI.
- Chat search has Telegram-like content filters for all messages, media, files and links, date chips for 24h/7d/30d, sender chips when a chat has messages from multiple senders and compact result navigation.
- Shared media gallery is available per chat with media grid, file list, link list, media download actions and full-screen media viewer reuse.
- Group/channel tabs now expose creation and management flows: create channel, basic group and forum group; rename/description, invite link create/revoke, member add/remove/ban/unban, admin promote/demote, permissions, slow mode, auto-delete, pin/mute/archive/read/leave actions.
- Secret Chats are enabled in TDLib parameters, parsed as a first-class chat type, shown in a Secret Chats directory, filterable from the chat list and creatable for Telegram users through TDLib.
- Calls have a TDLib call update parser and an active Calls screen in the Menu, but real create/accept/end actions are feature-gated until the call media engine is integrated and device-QA'd.
- Stories have a TDLib command/update layer for active-story loading, story viewing, photo/video story posting, deletion, media download, a media picker for posting, and an in-memory Stories screen in the Menu.
- Bot platform has a TDLib command/result layer for bot discovery, `/start`, bot commands, bot menu buttons, inline query results, sending inline results, callback button payloads, mini app URL requests and web app data, plus a Bots screen in the Menu.
- Account/profile/privacy has TDLib actions for profile name/bio/username/photo updates, common privacy presets, active session loading and session termination, plus an Account screen in the Menu.
- Premium/Business/monetization has a Premium screen and TDLib command scaffolding for Premium features/limits, Stars, and Business links; those optional actions are disabled by default and must stay feature-gated until binding support and device QA are confirmed.
- TDLib command errors from the JSON bridge are surfaced in the app operation notice instead of being silently ignored.
- User-facing reading/media settings persist across app process restarts.
- Android polish includes Material You dynamic color, static launcher shortcuts, Telegram deep-link intent filters, Android Sharesheet text/media intake with unit-covered sanitization limits, and bottom navigation accessibility labels.
- MainActivity handles orientation/screen-size configuration changes so rotating the device does not tear down the TDLib session.

## Next highest impact

1. Rich composer follow-up: real media editor, grouped TDLib albums, HD photo option, voice/video messages and reply markup for bots.
2. Message actions: reaction count/rendering, report and richer resend failed outgoing messages.
3. Shared media and search follow-up: server-side full-history search, public post search and richer media grouping by month/day.
4. Group and channel management follow-up: join-request review, topic list/composer, channel posting roles, comments, statistics and channel direct messages.
5. Notifications follow-up: background push registration, per-chat exceptions, grouped notifications and reply/mark-read actions.

## Larger platform work

- Secret Chats follow-up: self-destruct timer controls, key verification UI, screenshot alerts where TDLib/platform exposes them and stricter feature gating for unsupported cloud actions.
- Calls follow-up: real audio/video media engine integration, one-to-one device QA, group calls, screen sharing and picture-in-picture.
- Stories follow-up: full-screen story viewer, visual editor, stickers/areas, advanced privacy controls, reposting, archive/albums and richer reactions/analytics.
- Advanced media/storage: Telegram-like download manager, streaming audio player, robust background upload/download, media quality controls, large-file workflow and complete cache controls.
- Bots and Mini Apps follow-up: attachment menu, full in-app WebView runtime, bot-owned admin surfaces, payments and Telegram Stars.
- Account/profile follow-up: change phone, passkeys, two-step verification, account deletion/self-destruct, public photo management, collectible usernames and richer blocked-user management.
- Android UI parity follow-up: current Telegram Android bottom/tab layout, advanced chat folders, tablet/split layouts, widgets, notification shortcuts, per-chat themes and deeper accessibility audits.
- Premium/Business follow-up: boosts, gifts, paid media composer, subscriptions, payout analytics, business greeting/away messages, quick replies and chatbot management.

## Additional Telegram parity gaps

- Stickers, GIFs and emoji: sticker/GIF pickers, custom emoji, animated emoji effects, emoji status, sticker and emoji pack management, sticker/GIF editing and AI-powered sticker search.
- Rich text and message entities: bold/italic/code/link formatting, spoilers, block quotes, expandable quotes, hashtags, mentions, bot commands and tap targets for message entities.
- Advanced poll/checklist support: quiz polls, media polls, voting timestamps, revoting, custom voter limits, poll duration, poll statistics and collaborative checklists.
- Contacts and discovery: phone contact import, invite friends, add/edit contact notes, nearby/shared contact flows, username management, collectible usernames and profile ratings.
- Privacy and safety: last-seen/online/phone-number/photo privacy, forward restrictions, protected content, disable sharing, passcode/app lock, blocked users management and content reporting flows.
- Account lifecycle: edit profile, profile photos/videos/music, change phone number, delete account, active sessions, connected devices, passkeys, two-step verification and account self-destruct settings.
- Chat folders and organization: custom folders, folder invite links, archived chats behavior, unread/mention filters, saved messages improvements and per-chat unread/marking controls.
- Message sync fidelity follow-up: per-message reaction counters, delivery receipts beyond TDLib read ranges, failed-message resend UI, multi-device conflict handling and richer draft/media upload progress.
- Media experience: full media gallery, music/audio player queue, voice-message trimming, video-message recording, timestamp links, playback speeds, Chromecast/route-to-device and document scanner parity where platform-supported.
- Channel creator tools: channel statistics, boosts, reactions/comments controls, suggested posts, paid posts, giveaways, subscriptions, ads/sponsored messages and channel auto-translation parity.
- Stars, gifts and TON surfaces: Telegram Stars balance/payments, paid media, gift marketplace, collectible gifts, gift upgrades/crafting, auctions and Fragment/TON-linked flows.
- Multi-device and data export: session management, QR login, export/download personal data where supported, cloud sync controls and migration/reset flows.
- Moderation and admin tools follow-up: anti-spam controls, member tags, join-request review, admin logs, granular admin rights and ownership transfer UI.
- Platform polish: Android notification shortcuts, share sheet integration, deep links, widgets, adaptive/tablet layouts, accessibility labels, dynamic color/theming and localization parity.

## QA notes

- Each TDLib command should be tested against both the JSON bridge and official Java/JNI binding where possible.
- Local message notifications now have an Android notification channel, Android 13 permission flow, Settings toggle, and notification tap-to-open-chat. Full Telegram-style background push still needs FCM/device-token registration and real-device QA with Telegram credentials.
- Features that touch account state, secret chats, background push, media upload, billing or calls need real-device QA with Telegram credentials.
