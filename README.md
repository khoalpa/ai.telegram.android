# AI Telegram Android

Telegram client Android huong toi nguoi dung Viet Nam:

- Giao dien mac dinh tieng Viet, co san resource de mo rong ngon ngu.
- Noi dung chat/channel uu tien ban dich theo ngon ngu giao dien; khi chua co ban dich kha dung, UI fallback sang noi dung goc de nguoi dung van doc duoc.
- Blacklist theo noi dung da chuan hoa, ap dung cho moi bai co cung noi dung.
- Cache ban dich theo `content_hash + target_language + provider_version`.
- Download media theo chinh sach tiet kiem du lieu va cache trang thai media.
- Ung dung mien phi 100%, quyen tang tu nguyen va khong mo khoa tinh nang.

## Trang thai hien tai

MVP hien co:

- Android Kotlin + Jetpack Compose + Material 3.
- Min SDK 26, target/compile SDK 37, Kotlin JVM toolchain 21.
- Product flavor `outsidePlay`: tat Google Play Billing, bat VietQR.
- ABI split APK cho `arm64-v8a`, `armeabi-v7a` va `x86_64`.
- Man hinh Chat, Kenh, Danh ba, Nhom/supergroup, Kham pha/Cong cu Telegram, Noi dung da an, Cache, Ung ho va Cai dat.
- Header giu cac tab chinh Chat, Kenh, Danh ba va Cai dat; nut menu tren header mo cac muc Nhom, Kham pha, Da an, Cache, Ung ho va cac be mat Telegram tuy chon.
- Chat list that trong Room, co chon chat, loc message theo tung chat va search tren noi dung dang duoc render.
- UI uu tien ban dich; neu message dang cho dich, dang dich, dich that bai hoac ban dich khong kha dung thi hien noi dung goc kem trang thai dich.
- Search/loc message dung `MessagePrivacyPolicy` de match tren noi dung hien thi: ban dich khi san sang, source text khi dang fallback.
- Kham pha/Cong cu co global search, join channel bang username/link, gui tin nhan text, tao chat rieng, tao basic group va thao tac pin/mute/archive co ban qua TDLib.
- Tin nhan co thao tac co ban giong Telegram: tra loi bang text, sua text message, chuyen tiep theo chat ID, xoa message va sao chep noi dung doc duoc.
- Them thao tac ghim message va quick reactions emoji co ban qua TDLib.
- Composer co gui media/file co ban bang Android picker: app copy file da chon vao cache upload va gui anh/video/document qua TDLib.
- Composer co tuy chon gui im lang va hen gio gui cho text, reply, media caption, poll va contact; draft dang nhap duoc dong bo len TDLib bang server-side draft.
- Danh ba goi TDLib `getContacts`; app chua doc danh ba may truc tiep.
- Sender metadata cache trong Room: TDLib `UpdateUser`, `UpdateBasicGroup`, `UpdateSupergroup` va chat metadata duoc luu de UI hien ten chat/nguoi gui that hon.
- TDLib bridge qua official Java/JNI binding bang reflection, co fallback TDLib JSON bridge tren Android de dang nhap that khi object binding khong duoc bundle.
- App goi TDLib qua interface `TelegramClient`; unit test co fake client cho luong login/chat/message.
- TDLib bridge da nhan `UpdateNewChat`, `UpdateChatLastMessage`, `UpdateNewMessage`, `UpdateFile` va co `loadChatHistory(chatId)`.
- Media message co metadata TDLib `file_id`, local path, MIME type, file name, thumbnail va downloaded-prefix bytes.
- Anh local co the render truc tiep; video/file co block media va nut tai media. Nut tai media se tai full file de nguoi dung co the xem lai video da cache ma khong can tai lai; rieng auto-preview video van co the tai prefix de phat som.
- Settings co tuy chon auto-play video va phu de video toan man hinh.
- Phu de video toan man hinh dung audio tu video, Android `SpeechRecognizer`, ML Kit Translation va cache cue theo file/video version; yeu cau Android 13+ va dich vu nhan dang giong noi ho tro audio source tu file.
- Profile, member/admin va notification da co diem vao ro trong UI; background push, call media engine va cac API Telegram optional van can binding/permission/QA thiet bi that.
- UI Calls va Premium/Stars/Business hien duoc feature-gate: app hien ro ly do chua bat action thay vi de nguoi dung bam vao luong chua duoc QA.
- Room database that cho chats, senders, messages, blacklist, translation cache, translation jobs, media cache va video subtitle cache.
- `BlacklistRepository`, `TranslationCacheRepository`, `MediaCacheRepository` va `VideoSubtitleCacheRepository` khong dung in-memory state.
- `TranslationQueue` duoc gan vao message moi tu TDLib: message vao Room voi trang thai `Pending`, sau do di qua preflight, blacklist, translation cache va ML Kit on-device provider.
- Preflight bo qua noi dung khong can dich nhu URL-only, handle-only, numeric-only, emoji-only, code-like, qua ngan hoac da la tieng Viet.
- Neu thieu model dich, UI gan `MissingModel`, hien loi mo Cache de tai cap model phu hop va van hien source text lam fallback.
- Dich nen bang WorkManager: message duoc ghi vao `translation_jobs`, `TranslationWorker` xu ly batch 25 job, toi da 4 round, retry toi da 3 lan va recover job `Running` bi treo.
- Provider dich on-device that bang ML Kit Translation (`com.google.mlkit:translate:17.0.3`) va Language ID bundled (`com.google.mlkit:language-id:17.0.6`).
- Worker yeu cau mang unmetered/Wi-Fi khi tai model ML Kit de tranh ton du lieu di dong.
- Tab Cache co quan ly model dich ML Kit da tai: lam moi danh sach, tai truoc 5 cap dich sang tieng Viet, tai tung cap va xoa tung model.
- Tab Cache hien trang thai thao tac model: dang lam moi, dang tai, tiep tuc tai model con thieu, dang xoa va loi neu thao tac that bai.
- Man hinh Ung ho hien VietQR tren flavor `outsidePlay`; Google Play Billing khong duoc bat trong build hien tai.
- Khoan quyen tang duoc consume sau khi thanh cong de nguoi dung co the ung ho lai; khong tao entitlement va khong mo khoa tinh nang.
- WorkManager maintenance jobs:
  - `TranslationPrefetchWorker`: moi 6 gio, chi khi mang unmetered, enqueue toi da 100 message gan day can dich.
  - `CacheCleanupWorker`: moi ngay, xoa translation cache cu hon 90 ngay, gioi han 20.000 muc moi nhat, don failed jobs cu hon 14 ngay, don subtitle cache cu hon 90 ngay/toi da 1.000 muc va don media cache cu hon 30 ngay/toi da 5.000 muc/toi da 512 MB.
- Du lieu mau chi dung de seed khi database rong, giup app van mo duoc neu chua bundle TDLib.
- Da co Compose/instrumented UI test toi thieu cho tap chat row, chuyen tab header, mo Search va mo Cache tu nut menu tren header.
- QA rerun ngay 2026-06-08 van ket luan hold beyond internal testing: da co bang chung real-account o muc chat list/settings/cache, nhung con thieu sign-off chat detail/history/media, inbound notification, mobile-data-only, emulator API 26/API 35+ va connected runner an toan.

## TDLib

App da co bridge tai:

```text
app/src/main/java/ai/telegram/android/data/telegram/TdLibReflectionClient.kt
```

De ket noi Telegram that:

1. Lay `api_id` va `api_hash` tu Telegram.
2. Dat vao `local.properties`:

```properties
telegramApiId=123456
telegramApiHash=your_api_hash
```

3. Bundle official TDLib Java/JNI binding vao APK sao cho runtime co cac class:

```text
org.drinkless.tdlib.Client
org.drinkless.tdlib.TdApi
```

4. Bundle native TDLib libraries tuong ung ABI vao `app/src/main/jniLibs/...` theo cach build TDLib cua ban.

Neu chua co object binding/native `libtdjni.so`, app se thu fallback TDLib JSON bridge tu dependency Android:

```text
io.github.xephosbot:tdlib-kmp-android:1.8.62
```

Fallback nay bundle native TDLib JSON libraries vao APK, nen APK debug lon hon dang ke. Do repo dang bat ABI split, khi cai qua USB hay dung APK khop CPU thiet bi, thuong la `arm64-v8a`:

```powershell
adb install --no-streaming -r -d app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-arm64-v8a-debug.apk
```

Man hinh Cai dat se khoa form dang nhap khi chua co `telegramApiId`/`telegramApiHash` hoac khi ca object binding lan JSON bridge deu khong kha dung, de tranh thao tac khong co tac dung. Neu TDLib gap database key cu khong khop, nut dang xuat/reset se xoa du lieu TDLib cuc bo va tao key moi.

## Room

Database:

```text
ai_telegram_android.db
```

Bang hien co:

- `chats`
- `senders`
- `messages`
- `hidden_content`
- `translation_cache`
- `translation_jobs`
- `media_cache`
- `video_subtitle_cache`
- `chat_history_state`

Database hien o version 13:

- v1 -> v2: them chat list, `chatId` va primary key message theo `chatId:messageId`.
- v2 -> v3: them `translationStatus` cho message.
- v3 -> v4: them bang `senders` va `senderId` cho message.
- v4 -> v5: them bang `translation_jobs` cho WorkManager.
- v5 -> v6: them metadata media TDLib (`mediaFileId`, local path, MIME type, file name).
- v6 -> v7: them thumbnail metadata va `mediaDownloadedPrefixBytes` cho download/preview video.
- v7 -> v8: them failure metadata cho `translation_cache`, `expiresAtMillis`, bang `media_cache` va `video_subtitle_cache`.
- v8 -> v9: them `translationFailureReason`, `translationTargetLanguage` cho message va `actualSizeBytes` cho media cache.
- v9 -> v10: them chat active action/read/pinned metadata va message sync/read/edited/pinned flags.
- v10 -> v11: them index cho chat/message window, translation jobs va media cache de giam scan khi lich su Telegram lon.
- v11 -> v12: them `chat_history_state` de tranh tai lai lich su chat da dong bo.
- v12 -> v13: them `sourceLanguageCode` cho `video_subtitle_cache`.

Trang thai dich:

- `Pending`
- `Translating`
- `Ready`
- `Hidden`
- `Failed`

Ly do loi dich:

- `None`
- `MissingModel`
- `UnsupportedLanguage`
- `UndetectedLanguage`
- `NetworkRequired`
- `TransientError`

Trang thai media cache:

- `Requested`
- `ThumbnailReady`
- `PrefixReady`
- `FullDownloaded`
- `Corrupt`

Pipeline message:

```text
TDLib message
  -> MessageRepository(Room Pending)
  -> TranslationQueue(preflight + network policy)
  -> TranslationJobRepository(Room job)
  -> WorkManager
  -> TranslationWorker(batch)
  -> BlacklistRepository
  -> TranslationCacheRepository(positive/negative cache)
  -> MlKitOnDeviceTranslationProvider
  -> MessageRepository(Room Ready/Hidden/Failed)
```

## Build notes

Project dang dung AGP 9.2.1 voi Kotlin/KSP 2.2.21 de build Room schema/compiler. Hien tai van phai giu:

```properties
android.builtInKotlin=false
android.newDsl=false
```

Ly do: AGP built-in Kotlin chua tuong thich voi KSP trong cau hinh nay, va `org.jetbrains.kotlin.android` + KSP hien van can legacy Android extension path. Neu bo `android.builtInKotlin=false`, KSP dung build vi chua ho tro AGP built-in Kotlin. Neu bo rieng `android.newDsl=false`, Kotlin Android plugin dung build do khong con nhan duoc legacy `BaseExtension`. Khi KSP/Room ho tro AGP built-in Kotlin day du, co the thu lai viec bo hai flag tren va go `org.jetbrains.kotlin.android` khoi `app/build.gradle.kts`.

Rui ro hien tai cua cac flag legacy nam tai `docs/gradle-agp-legacy-risk.md`. Ke hoach go flag truoc khi nang toolchain nam rieng tai `docs/agp-toolchain-migration-plan.md`; checklist tong hop nam tai `docs/build-modernization-checklist.md`. Ma tran QA truoc khi phat hanh noi bo/rong hon nam tai `docs/release-readiness-matrix.md`.

## On-device translation

App dung standalone ML Kit SDK, khong dung Firebase ML Kit cu:

- Translation: `com.google.mlkit:translate:17.0.3`
- Language ID bundled: `com.google.mlkit:language-id:17.0.6`

Provider hien tai:

```text
app/src/main/java/ai/telegram/android/data/translation/MlKitOnDeviceTranslationProvider.kt
```

Quan ly model:

```text
app/src/main/java/ai/telegram/android/data/translation/MlKitModelManager.kt
```

Preflight va skip classifier:

```text
app/src/main/java/ai/telegram/android/data/translation/TranslationPreflight.kt
```

ML Kit se nhan dien ngon ngu nguon, kiem tra cap model, tai model neu can bang `downloadModelIfNeeded`, va dich tren thiet bi bang `translate`. WorkManager duoc schedule voi `NetworkType.CONNECTED`; preflight chi enqueue tren mobile data khi cap model da san sang, nen bai dang xem co the dich nhanh hon ma van tranh tai model qua roaming.

Tab Cache co nut `Tai 5 cap sang Viet` cho cac cap nguon pho bien: Anh, Trung, Nga, Nhat, Han sang tieng Viet. Model download co resume planner de bo qua model da co va tiep tuc model con thieu.

Negative cache duoc luu cho cac loi dich co TTL rieng:

- `MissingModel`, `NetworkRequired`: 6 gio.
- `UndetectedLanguage`: 1 ngay.
- `UnsupportedLanguage`: 30 ngay.
- `TransientError`: 1 gio.

## Media va phu de video

Media download:

- Wi-Fi: auto-download anh nho/toi 8 MB.
- Mobile data: chi auto-download anh rat nho/toi 1 MB.
- Roaming: khong auto-download.
- Video co the tai prefix truoc de biet tien do, sau do tai day du de play.

Media cache:

```text
app/src/main/java/ai/telegram/android/data/Repositories.kt
```

Phu de video:

```text
app/src/main/java/ai/telegram/android/data/translation/VideoSubtitleGenerator.kt
```

Luong phu de video:

```text
Full-screen video
  -> extract PCM audio bang MediaExtractor/MediaCodec
  -> Android SpeechRecognizer
  -> tao cue theo word timing neu co, hoac chia theo cau
  -> dich cue sang tieng Viet bang ML Kit
  -> cache trong video_subtitle_cache theo fileId + targetLanguage + providerVersion
```

Phu de video can QA tren thiet bi that vi phu thuoc dich vu nhan dang giong noi, quyen audio/speech, ho tro `RecognizerIntent.EXTRA_AUDIO_SOURCE` va ho tro word timing cua tung Android version. Neu dich vu STT tra loi, app se hien ly do loi cu the thay vi treo o trang thai dang tao.

## Donations

Nguyen tac:

- quyen tang hoan toan tu nguyen;
- khong co premium/paywall;
- khong mo khoa tinh nang;
- VietQR chi hien trong build ngoai Play Store.

Google Play Billing code hien khong duoc bat vi du an chi giu flavor `outsidePlay`.

VietQR resource:

```text
app/src/main/res/drawable-nodpi/vietqr_donation.jpg
```

Thong tin hien thi:

- MB Bank
- LE PHAM ANH KHOA
- 0914030780
- Quyen tang tu nguyen, khong mo khoa them tinh nang.

Schema duoc export tai:

```text
app/schemas/
```

## Build

Gradle da bat configuration cache trong `gradle.properties`; neu gap plugin/task khong tuong thich, chay lai voi `--no-configuration-cache` de chan doan.

Build ban ngoai Play Store, co VietQR va khong goi Google Play Billing:

```powershell
.\gradlew.bat :app:assembleOutsidePlayDebug
```

APK debug split theo ABI nam tai:

```text
app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-arm64-v8a-debug.apk
app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-armeabi-v7a-debug.apk
app/build/outputs/apk/outsidePlay/debug/app-outsidePlay-x86_64-debug.apk
```

Build release unsigned:

```powershell
.\gradlew.bat :app:assembleOutsidePlayRelease
```

## Tests

Unit tests:

```powershell
.\gradlew.bat :app:testOutsidePlayDebugUnitTest
```

Unit test hien co bao ve:

- chuan hoa va hash noi dung cho blacklist/dedupe theo cung noi dung;
- chinh sach auto-download tren Wi-Fi, du lieu di dong va roaming;
- fake `TelegramClient` cho login/chat/message/send-message flow;
- parser TDLib object/JSON cho message, file update va command mapper;
- preflight skip classifier cho URL-only, tieng Viet, code-like va noi dung can dich;
- danh sach 5 cap model dich pho bien sang tieng Viet;
- planner tiep tuc tai model con thieu;
- privacy policy render/match source text khi ban dich chua kha dung, nhung van uu tien ban dich khi da san sang;
- retry policy cua `TranslationWorker`.

Chay local release gates truoc moi lan device QA hoac release sign-off:

```powershell
.\scripts\run-local-release-gates.ps1
```

Script nay chay unit test, lint, assemble debug APK va assemble Android test APK. Neu chi can build Android instrumentation test APK:

```powershell
.\gradlew.bat :app:assembleOutsidePlayDebugAndroidTest
```

Instrumentation test hien co bao ve:

- migration Room tu version 1 len version 13;
- blacklist ap dung cho noi dung tuong duong sau khi chuan hoa;
- go blacklist lam hien lai noi dung cung hash;
- translation cache tai su dung ban dich, tang hit count va tach theo ngon ngu/provider;
- failed translation khong luu source text vao `translatedText`, nhung chat preview fallback sang source text.
- Compose chat row: `ChatScreenTapTest` tap `chat_row_<id>` va xac nhan `onSelectChat` duoc goi;
- Compose navigation: `NavigationUiTest` tap header tab `Channels`, `Contacts`, `Settings`, mo Search va mo Cache tu nut menu tren header.

Chay instrumentation test that can thiet bi/emulator co `adb` kha dung. Khong chay len install dang giu Telegram QA session:

```powershell
.\scripts\run-connected-safe.ps1
```

Script nay tu nhan dien Redmi/HyperOS/Xiaomi va chi chay allowlist `RepositoryRoomTest` + `AppDatabaseMigrationTest`. Tren emulator AOSP hoac thiet bi non-Xiaomi, che do `Auto` se chay nhom Compose UI duoc gan `@ComposeUiConnectedTest`.

Luu y: tren Redmi/HyperOS da quan sat thay runner/device layer timeout, instrumentation bi ket lai va install dang dang nhap bi mat session sau khi cleanup/reinstall. Chi chay full connected suite tren disposable install, truoc khi dang nhap Telegram, hoac tren emulator/thiet bi QA co the reset:

```powershell
.\scripts\run-connected-safe.ps1 -TestGroup All -AllowAllOnRestrictedDevice
```

De gom local gates, connected DeviceSafe va Compose UI neu co emulator/non-Xiaomi thanh artifact release QA:

```powershell
.\scripts\run-release-qa.ps1
```

Script se ghi summary vao `build/release-qa-*/summary.md`. Neu khong co emulator/non-Xiaomi, buoc Compose UI duoc danh dau `SKIPPED` de release owner thay ro phan con thieu.

QA thiet bi that:

```text
docs/device-qa-checklist.md
docs/device-qa-2026-06-07-critical-flows.md
docs/release-readiness-matrix.md
docs/release-signoff-2026-06-07-outsidePlayDebug-qa-matrix.md
```

TDLib login, chat history, media/video, translation, notification va malformed deep links/share intents van can QA tren thiet bi/emulator that voi account Telegram QA rieng.

## Huong phat trien tiep theo

1. Tao/provision account Telegram QA rieng va chay lai auth phone/code/2FA, logout/reset key, sync chat history va inbound notification.
2. Debug chat row tap tren emulator hoac thiet bi non-Xiaomi; xac nhan callback `onSelectChat`/log `AiTelegramChatTap` khi mo chat detail.
3. Hoan tat QA matrix cho media download/open, translation state, malformed deep links va share intents.
4. QA full-screen video, speech permission, subtitle generation va cache cue tren Android version khac nhau.
5. Bo sung instrumentation test cho media cache va video subtitle cache.
