<div align="center">

<img src="logo.png" alt="Bisnor Logo" width="140" />

# Bisnor

### تماشا بدون مرز 💎

A modern, lightweight and open-source Android client for browsing and streaming movies and TV series.

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

**Bisnor** یک اپلیکیشن اندرویدی برای مشاهده، جستجو و پخش فیلم و سریال با رابط کاربری ساده، سریع و مدرن است.

این پروژه به صورت **Open Source** منتشر شده و هدف آن ارائه یک کلاینت سبک و تمیز برای تجربه بهتر تماشای محتوا در اندروید است.

> **بیسنور، تماشا بدون مرز 💎**

---

## ✨ قابلیت‌ها

- 🎥 مشاهده فیلم و سریال
- 🔎 جستجوی محتوا
- 📺 پخش آنلاین
- 🎞️ نمایش فصل‌ها و قسمت‌های سریال
- 🖼️ نمایش پوستر و اطلاعات محتوا
- ▶️ پلیر داخلی مبتنی بر Media3 / ExoPlayer
- ⚡ رابط سریع و سبک
- 📱 طراحی مناسب اندروید
- 🌙 رابط کاربری مدرن
- 🇮🇷 تجربه مناسب کاربران فارسی‌زبان

---

## 💎 Powered by IranFlix

Bisnor یک **client / frontend مستقل** است و برای دریافت اطلاعات و دسترسی به محتوای فیلم و سریال از زیرساخت و سرویس‌های **IranFlix** استفاده می‌کند.

سرویس‌ها، APIها، داده‌ها، زیرساخت، محتوای رسانه‌ای، نام‌ها و سایر منابع شخص ثالث تحت مالکیت و شرایط صاحبان مربوطه هستند و مشمول مجوز MIT این پروژه نمی‌شوند.

---

## 🛠️ تکنولوژی‌ها

| Technology | Usage |
|---|---|
| Android SDK | Core platform |
| Gradle Kotlin DSL | Build system |
| ViewBinding | UI binding |
| Retrofit | API communication |
| OkHttp | HTTP client |
| Gson | JSON parsing |
| Coil | Image loading |
| Media3 / ExoPlayer | Video playback |
| Kotlin Coroutines | Async operations |
| AndroidX Lifecycle | Lifecycle management |
| RecyclerView | Content lists |
| ViewPager2 | Page navigation |
| Material Components | UI components |
| Supabase | Authentication and user data |

---

## 📱 نیازمندی‌ها

| مورد | مقدار |
|---|---|
| Minimum Android | Android 7.0 |
| Minimum SDK | 24 |
| Target SDK | 35 |
| Compile SDK | 35 |
| Java | 17 |

---

## 🚀 Build

ابتدا Repository را Clone کنید:

```bash
git clone https://github.com/nikan48g/Bisnor.git
cd Bisnor
```

### تنظیم Supabase

فایل نمونه تنظیمات در Repository قرار دارد:

```text
local.properties.example
```

یک کپی از آن با نام زیر بسازید:

```text
local.properties
```

و مقادیر پروژه Supabase خودتان را وارد کنید:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key_here
```

> فایل `local.properties` نباید Commit شود. اطلاعات خصوصی، Service Role Key، Database Password یا سایر Secretها را داخل سورس قرار ندهید.

### Build روی Linux / macOS

```bash
./gradlew assembleDebug
```

### Build روی Windows

```cmd
gradlew.bat assembleDebug
```

APK ساخته‌شده معمولاً در مسیر زیر قرار می‌گیرد:

```text
app/build/outputs/apk/debug/
```

---

## 🔐 امنیت و تنظیمات

Bisnor برای جدا نگه داشتن تنظیمات محیطی از سورس، مقادیر Supabase را از `local.properties` دریافت می‌کند.

Repository عمومی نباید شامل موارد حساس زیر باشد:

```text
Service Role Keys
Secret Keys
Database Passwords
Private Tokens
Signing Keys
Private Credentials
```

برای نسخه Fork شده پروژه، از فایل `local.properties.example` به عنوان الگو استفاده کنید و credentialهای مربوط به پروژه خودتان را وارد کنید.

---

## 🤝 مشارکت

Pull Request و گزارش Bug پذیرفته می‌شود.

```bash
git checkout -b feature/my-feature
git commit -m "feat: add my feature"
git push origin feature/my-feature
```

سپس یک Pull Request به Branch اصلی ارسال کنید.

---

## 🐛 گزارش مشکل

اگر مشکلی پیدا کردید، از بخش GitHub Issues استفاده کنید:

[![Open an Issue](https://img.shields.io/badge/Open_an_Issue-GitHub-black?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/issues)

در گزارش خود در صورت امکان نسخه Android، مدل دستگاه، مراحل تکرار مشکل و Log مرتبط را اضافه کنید.

اطلاعات حساس را در Issue عمومی منتشر نکنید.

---

## ⚖️ Disclaimer

Bisnor یک نرم‌افزار **client / frontend** است.

این Repository به خودی خود میزبان یا مالک فیلم‌ها، سریال‌ها یا فایل‌های رسانه‌ای شخص ثالث نیست.

پوسترها، Metadata، فایل‌های رسانه‌ای، APIها، نام‌ها، علائم تجاری و سایر منابع شخص ثالث متعلق به صاحبان مربوطه هستند.

کاربران مسئول رعایت قوانین و شرایط سرویس‌های مرتبط در حوزه قضایی خود هستند.

---

## 📄 License

Bisnor تحت مجوز **MIT License** منتشر شده است.

شما می‌توانید کد تحت پوشش این مجوز را استفاده، تغییر، کپی و توزیع کنید، مشروط به حفظ Copyright Notice و متن مجوز MIT.

[![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

---

## ⭐ حمایت از پروژه

اگر Bisnor برایتان مفید بود، می‌توانید با دادن یک Star از پروژه حمایت کنید.

<div align="center">

[![Star Bisnor](https://img.shields.io/github/stars/nikan48g/Bisnor?style=social)](https://github.com/nikan48g/Bisnor)

<br>

**Bisnor**  
تماشا بدون مرز 💎

Powered by **IranFlix**

Made with ❤️ by **HNN**

</div>
