<div align="center">
<img src="logo.png" alt="Bisnor" width="120">

# Bisnor 💎

### تماشا بدون مرز

کلاینت متن‌باز برای مرور، جست‌وجو و پخش فیلم و سریال؛ با ظاهر شیشه‌ای (Liquid Glass)، فونت فارسی **اجر**، و نسخه‌های اندروید، تلویزیون، ویندوز و وب.

[![Latest release](https://img.shields.io/github/v/release/nikan48g/Bisnor?style=for-the-badge&label=release)](https://github.com/nikan48g/Bisnor/releases/latest)
[![License](https://img.shields.io/github/license/nikan48g/Bisnor?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](app/build.gradle.kts)

</div>

---

## 📸 نمای برنامه

<p align="center">
  <img src="docs/screenshots/home.png" width="210" alt="صفحه اصلی">
  <img src="docs/screenshots/explore.png" width="210" alt="کشف و جست‌وجو">
  <img src="docs/screenshots/episodes.png" width="210" alt="قسمت‌های سریال">
</p>

<p align="center">
  <img src="docs/screenshots/profile.png" width="210" alt="پروفایل">
  <img src="docs/screenshots/favorites.png" width="210" alt="علاقه‌مندی‌ها">
  <img src="docs/screenshots/startup.png" width="210" alt="بررسی اتصال هنگام اجرا">
  <img src="docs/screenshots/tv-home.png" width="420" alt="صفحه اصلی تلویزیون (انگلیسی)">
</p>

---

## ✨ امکانات

- **دو منبع پخش:** نکست‌مووی و ایران‌فلیکس؛ حالت خودکار سالم‌ترین سرور را انتخاب می‌کند یا می‌توانی دستی انتخاب کنی.
- **جست‌وجوی پیشرفته:** بر اساس عنوان، ژانر، کشور، سال و امتیاز IMDb.
- **علاقه‌مندی‌ها و لیست‌ها:** لیست شخصی بساز، مرتب کن و با لینک کوتاه با دوستانت به اشتراک بگذار.
- **همگام‌سازی حساب:** با حساب کاربری، لیست‌ها و پیشرفت تماشا روی همه‌ی دستگاه‌هایت می‌آید.
- **دانلود ۸ تکه‌ای:** هر فایل با ۸ اتصال هم‌زمان دانلود می‌شود؛ پیشرفت هر تکه جداگانه دیده می‌شود و می‌توانی پوشه ذخیره را انتخاب کنی.
- **پخش‌کننده:** ادامه‌ی خودکار قسمت بعد، زیرنویس فارسی، انتخاب کیفیت و سرعت، و امکان باز کردن با VLC یا MX Player.
- **دو ظاهر و دو حالت:** ظاهر شیشه‌ای یا کلاسیک (سبک برای گوشی‌های ضعیف)، روشن یا تاریک.
- **فارسی و انگلیسی:** فونت فارسی **اجر** یا **اجر فانتزی** و در حالت انگلیسی فونت سینمایی Bebas Neue.
- **رایگان و متن‌باز:** بدون اشتراک، بدون تبلیغ.

---

## 📦 دانلود و نصب

| پلتفرم | وضعیت |
| --- | --- |
| اندروید (گوشی و تبلت) | پایدار؛ فایل APK در [Releases](https://github.com/nikan48g/Bisnor/releases/latest) |
| اندروید TV | پایدار؛ همان APK با ناوبری ریموت |
| ویندوز | آزمایشی؛ نصب‌کننده در [Releases](https://github.com/nikan48g/Bisnor/releases/latest) |
| Samsung Tizen TV | آزمایشی |
| وب (هر مرورگری، از جمله آیفون) | آزمایشی: <https://bisnor-pwa.drive-storage-hnn.workers.dev> |

> اگر اندروید یا ویندوز داری، نسخه‌ی اصلی را ترجیح بده؛ نسخه‌ی وب ممکن است در پخش یا ورود ایراد داشته باشد.

---

## 🛠 ساخت از سورس

نیازمندها: Android Studio (JDK 17) و Android SDK 35.

```powershell
git clone https://github.com/nikan48g/Bisnor.git
cd Bisnor
./gradlew assembleDebug
```

APK در `app/build/outputs/apk/debug/` ساخته می‌شود. جزئیات بیشتر در [docs/BUILD.md](docs/BUILD.md) است.

---

## 🧩 ساختار پروژه

| بخش | مسیر |
| --- | --- |
| اندروید | `app/` |
| ویندوز (WebView2) | `desktop/BisnorDesktop/` |
| وب (PWA) | `desktop/BisnorDesktop/wwwroot/` |
| Samsung Tizen TV | `tizen-tv/` |
| Worker (لینک کوتاه، پروکسی) | `cloudflare/worker/` |
| دیتابیس | `supabase/` |

---

## 💎 منابع محتوا

بیسنور برای فهرست و لینک‌های پخش از **IranFlix** و **نکست‌مووی** استفاده می‌کند. این سرویس‌ها، APIها و محتوای رسانه‌ای متعلق به صاحبان آن‌ها هستند و تحت مجوز MIT این مخزن نیستند.

بیسنور فیلم یا سریالی را میزبانی نمی‌کند؛ فقط یک کلاینت است.

---

## 🤝 مشارکت و گزارش مشکل

- ایده یا اصلاح داری؟ Pull Request بفرست.
- مشکلی پیدا کردی؟ [Issue جدید بساز](https://github.com/nikan48g/Bisnor/issues) و این موارد را بنویس: سیستم‌عامل و نسخه‌ی برنامه، مراحل تکرار، و پیام خطا (اگر بود).

---

## 📄 مجوز

[MIT](LICENSE)

اگر بیسنور به کارت آمد، یک ⭐ بده. ❤️

<div align="center">Made by HNN</div>
