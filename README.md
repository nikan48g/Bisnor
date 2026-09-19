<div align="center">

<img src="logo.png" alt="Bisnor Logo" width="140" />

# Bisnor

### تماشا بدون مرز 💎

یک کلاینت متن‌باز برای مرور، جستجو و پخش فیلم و سریال با تمرکز روی تجربه فارسی، رابط سبک و پخش انعطاف‌پذیر.

<br>

[![GitHub stars](https://img.shields.io/github/stars/nikan48g/Bisnor?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/nikan48g/Bisnor?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/forks)
[![GitHub issues](https://img.shields.io/github/issues/nikan48g/Bisnor?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/issues)
[![License](https://img.shields.io/github/license/nikan48g/Bisnor?style=for-the-badge)](LICENSE)

<br>

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue?style=flat-square&logo=android)
![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Open Source](https://img.shields.io/badge/Open%20Source-MIT-red?style=flat-square)

</div>

---

## 🎬 درباره Bisnor

**Bisnor** یک پروژه چندپلتفرمی برای تجربه تماشای فیلم و سریال است. نسخه اصلی و پایدار پروژه روی **Android** توسعه داده می‌شود و نسخه‌های **Windows Desktop** و **Samsung Tizen TV** در حال حاضر آزمایشی هستند.

Bisnor یک **client / frontend مستقل** است و برای دریافت اطلاعات و دسترسی به محتوای رسانه‌ای از زیرساخت و سرویس‌های **IranFlix** استفاده می‌کند.

> **بیسنور، تماشا بدون مرز 💎**

---

## 🧭 وضعیت پلتفرم‌ها

| پلتفرم | وضعیت | توضیح |
|---|---|---|
| Android | ✅ Stable / Primary | نسخه اصلی و توصیه‌شده برای استفاده روزمره |
| Windows Desktop | 🧪 Experimental | نسخه آزمایشی مبتنی بر WPF + WebView2 |
| Samsung Tizen TV | 🧪 Experimental | نسخه آزمایشی تلویزیون با Remote و AVPlay |

> نسخه‌های Experimental ممکن است ناقص باشند، روی همه دستگاه‌ها کار نکنند یا بدون حفظ سازگاری تغییر کنند.

---

## ✨ قابلیت‌های اصلی

- 🎥 مرور فیلم و سریال
- 🔎 جستجوی محتوا، بازیگر و کارگردان
- ❤️ Watchlist و Playlist
- 📺 پخش آنلاین
- 🎞️ فصل‌ها و قسمت‌ها
- ▶️ پلیر داخلی مبتنی بر Media3 / ExoPlayer
- 📱 پشتیبانی از پلیرهای خارجی
- ⏯️ ادامه پخش و ذخیره موقعیت
- ⏭️ Next Episode
- 📥 دانلود محتوا
- 🧠 Smart Taste و پیشنهاد محتوا
- 👤 حساب کاربری، آواتار و Sync
- 🌙 Light / Dark Theme
- 🇮🇷 رابط مناسب کاربران فارسی‌زبان و فونت Vazirmatn

---

## 📦 دانلود

آخرین نسخه منتشرشده را از بخش Releases دریافت کنید:

[![Download Latest Release](https://img.shields.io/badge/Download-Latest%20Release-2ea44f?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/releases/latest)

> برای Android از APK موجود در Release استفاده کنید. نسخه‌های Desktop و Tizen فعلاً Experimental هستند.

---

## 📚 مستندات

README عمداً خلاصه نگه داشته شده تا صفحه اصلی پروژه تبدیل به دفترچه تعمیرات هواپیما نشود 😅

- 🛠️ [راهنمای کامل Build برای Android، Windows و Tizen](docs/BUILD.md)
- 🐛 [راهنمای Troubleshooting و خطاهای رایج](docs/TROUBLESHOOTING.md)

---

## 💎 Powered by IranFlix

Bisnor برای دریافت اطلاعات و دسترسی به محتوای فیلم و سریال از زیرساخت و سرویس‌های **IranFlix** استفاده می‌کند.

سرویس‌ها، APIها، داده‌ها، زیرساخت و محتوای رسانه‌ای شخص ثالث تحت مالکیت و شرایط صاحبان مربوطه هستند و مشمول مجوز MIT این Repository نمی‌شوند.

---

## 🧱 ساختار Repository

```text
Bisnor/
├─ app/                    # Android app
├─ desktop/
│  └─ BisnorDesktop/       # Windows experimental app
├─ tizen-tv/               # Samsung Tizen TV experimental app
├─ packaging/              # Inno Setup + WinGet manifests
├─ docs/                   # Build & Troubleshooting docs
├─ .github/workflows/      # CI / Release workflows
├─ gradle/
├─ local.properties.example
├─ logo.png
└─ README.md
```

---

## 🛠️ تکنولوژی‌ها

| Technology | Usage |
|---|---|
| Android SDK | Android core platform |
| Gradle Kotlin DSL | Android build system |
| ViewBinding | Android UI binding |
| Retrofit / OkHttp | Networking |
| Gson | JSON parsing |
| Coil | Image loading |
| Media3 / ExoPlayer | Android playback |
| Kotlin Coroutines | Async operations |
| Supabase | Account / sync data |
| WPF | Windows desktop shell |
| Microsoft WebView2 | Desktop UI runtime |
| .NET 10 | Windows runtime |
| HTML / CSS / JavaScript | Desktop and Tizen UI |
| Tizen Web API | Samsung TV integration |
| Tizen AVPlay | TV playback |

---

## 🤖 Android

نسخه Android پلتفرم اصلی پروژه است.

نیازمندی اصلی:
- Android 7.0+
- SDK 35
- JDK 17

Build، تنظیم `local.properties`، Release build و تست‌ها در سند زیر توضیح داده شده‌اند:

👉 **[docs/BUILD.md](docs/BUILD.md#-android)**

---

## 🖥️ Windows Desktop — Experimental

نسخه Windows در مسیر زیر قرار دارد:

```text
desktop/BisnorDesktop/
```

این نسخه از **WPF + Microsoft WebView2 + .NET 10** استفاده می‌کند و مسیر انتشار آن شامل **Inno Setup، GitHub Actions و WinGet** است.

جزئیات Build، Publish، Installer، Silent Install، WinGet و Code Signing:

👉 **[docs/BUILD.md](docs/BUILD.md#️-windows-desktop--experimental)**

---

## 📺 Samsung Tizen TV — Experimental

نسخه آزمایشی Tizen در مسیر:

```text
tizen-tv/
```

قرار دارد و شامل پشتیبانی از Remote navigation و Tizen AVPlay است.

مراحل Build، Package، Certificate و نصب روی TV:

👉 **[docs/BUILD.md](docs/BUILD.md#-samsung-tizen-tv--experimental)**

---

## 🔐 امنیت و تنظیمات

فایل‌های محیطی و credentialهای خصوصی نباید وارد Git شوند.

موارد زیر را **هرگز** داخل Repository عمومی قرار ندهید:

```text
Service Role Keys
Secret API Keys
Database Passwords
Private Tokens
Signing Keys
Keystore files
PFX / certificates
Tizen certificates
Device credentials
Private connection information
```

کلیدهایی که برای اجرای client عمومی طراحی شده‌اند جایگزین کنترل دسترسی سمت سرور نیستند. برای داده‌های خصوصی، Policy و RLS مناسب را در Backend تنظیم کنید.

---

## 🧪 تست

پروژه شامل Unit Test و Android Instrumented Test است.

```bash
./gradlew test
./gradlew connectedAndroidTest
```

هنگام تغییر بخش‌های حساس حداقل Fresh Install، Login/Logout، Search، Player، Watchlist، Downloads، Theme، Deep Links و حالت بدون اینترنت بررسی شوند.

برای خطاهای Build، CI، WinGet یا Tizen:

👉 **[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)**

---

## 🤝 مشارکت

Pull Request و Bug Report پذیرفته می‌شود.

```bash
git checkout -b feature/my-feature
git commit -m "feat: add my feature"
git push origin feature/my-feature
```

در PR بهتر است توضیح دهید:
- چه چیزی تغییر کرده
- چرا تغییر لازم بوده
- چطور تست شده
- روی کدام پلتفرم اثر دارد

---

## 🐞 گزارش مشکل

اگر مشکلی پیدا کردید، از GitHub Issues استفاده کنید:

[![Open an Issue](https://img.shields.io/badge/Open_an_Issue-GitHub-black?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/issues)

لطفاً نسخه Bisnor، پلتفرم، نسخه سیستم‌عامل، مراحل بازتولید، نتیجه مورد انتظار و Log مرتبط را اضافه کنید.

اطلاعات حساس را داخل Issue عمومی منتشر نکنید.

---

## ⚖️ Disclaimer

Bisnor یک نرم‌افزار **client / frontend** است و این Repository به خودی خود میزبان یا مالک فیلم‌ها، سریال‌ها یا فایل‌های رسانه‌ای شخص ثالث نیست.

مسئولیت رعایت قوانین، مجوزها و شرایط استفاده از سرویس‌های شخص ثالث بر عهده استفاده‌کننده است.

---

## 📄 License

Bisnor تحت مجوز **MIT License** منتشر شده است.

[![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

---

## ⭐ حمایت از پروژه

اگر Bisnor برایتان مفید بود، با دادن یک Star از پروژه حمایت کنید.

<div align="center">

[![Star Bisnor](https://img.shields.io/github/stars/nikan48g/Bisnor?style=social)](https://github.com/nikan48g/Bisnor)

<br>

**Bisnor**  
تماشا بدون مرز 💎

Powered by **IranFlix**

Made with ❤️ by **HNN**

</div>
