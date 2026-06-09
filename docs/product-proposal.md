# De xuat ung dung AI Telegram Android

## Muc tieu

Ung dung la Telegram client Android mien phi 100%, uu tien nguoi dung Viet Nam, hien thi giao dien bang tieng Viet va uu tien noi dung da dich theo ngon ngu giao dien. Khi ban dich chua kha dung, UI fallback sang noi dung goc de nguoi dung van doc duoc.

## Nguyen tac san pham

- Mien phi 100%, khong yeu cau Telegram Premium, khong paywall, khong tinh nang tra phi.
- Quyen tang la tu nguyen va khong mo khoa quyen loi rieng.
- Mac dinh tieng Viet, mo rong ngon ngu bang Android resources.
- Blacklist theo noi dung, ap dung toan bo bai co cung noi dung da chuan hoa.
- Cache va tai xuong theo chinh sach tiet kiem chi phi, pin, bo nho va du lieu di dong.

## MVP

1. Dang nhap va dong bo Telegram qua TDLib.
2. Danh sach chat va man hinh doc bai/tin.
3. Pipeline dich: normalize -> blacklist -> cache lookup -> on-device translation -> optional sponsored backend fallback.
4. UI uu tien ban dich, fallback source text khi chua co ban dich kha dung.
5. Blacklist theo content hash, co man hinh quan ly va go bo.
6. Cache ban dich theo content hash, ngon ngu dich va phien ban provider.
7. Media download policy: Wi-Fi tai thumbnail/anh nho, mobile data chi thumbnail, roaming tat auto-download.
8. Man hinh quyen tang tu nguyen.

## Kien truc

```text
TDLib
  -> MessageRepository
  -> ContentNormalizer
  -> BlacklistRepository
  -> TranslationCacheRepository
  -> TranslationQueue
  -> MediaDownloadPolicy
  -> Compose UI
```

## Giai doan tiep theo

- Them UI dieu khien lich prefetch/don cache neu nguoi dung muon tiet kiem pin hon.
- Da cau hinh VietQR that cho kenh phan phoi ngoai Play Store: MB Bank, LE PHAM ANH KHOA, 0914030780.
- Da cau hinh man hinh Ung ho cho kenh phan phoi ngoai Play Store bang VietQR tren flavor `outsidePlay`.
- Kiem thu luong quyen tang VietQR va thong tin hien thi tren thiet bi that.
- Tiep tuc them Room migration cho moi thay doi schema sau version 2.
- Them test cho hashing, blacklist, cache invalidation va download policy.
