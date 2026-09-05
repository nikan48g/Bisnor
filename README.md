<div align="center">

<img src="logo.png" alt="Bisnor Logo" width="140"/>

# Bisnor

### تماشا بدون مرز 💎

A modern and lightweight Android client for browsing and streaming movies and TV series.

<br>

[![GitHub stars](https://img.shields.io/github/stars/nikan48g/Bisnor?style=for-the-badge\&logo=github)](https://github.com/nikan48g/Bisnor/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/nikan48g/Bisnor?style=for-the-badge\&logo=github)](https://github.com/nikan48g/Bisnor/forks)
[![GitHub issues](https://img.shields.io/github/issues/nikan48g/Bisnor?style=for-the-badge\&logo=github)](https://github.com/nikan48g/Bisnor/issues)
[![License](https://img.shields.io/github/license/nikan48g/Bisnor?style=for-the-badge)](LICENSE)

<br>

![Android](https://img.shields.io/badge/Android-24%2B-3DDC84?style=flat-square\&logo=android\&logoColor=white)
![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue?style=flat-square\&logo=android)
![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square\&logo=openjdk)
![Open Source](https://img.shields.io/badge/Open%20Source-❤-red?style=flat-square)

</div>

---

## 🎬 درباره بیسنور

**Bisnor** یک اپلیکیشن اندرویدی برای مشاهده، جستجو و پخش فیلم و سریال با رابط کاربری ساده، سریع و مدرن است.

هدف بیسنور ارائه‌ی یک تجربه‌ی روان و تمیز برای دسترسی به محتوای فیلم و سریال است، بدون شلوغی و پیچیدگی‌های غیرضروری.

> **بیسنور، تماشا بدون مرز 💎**

---

## ✨ قابلیت‌ها

* 🎥 مشاهده فیلم و سریال
* 🔎 جستجوی محتوا
* 📺 پخش آنلاین
* 🎞️ نمایش فصل‌ها و قسمت‌های سریال
* 🖼️ نمایش پوستر و اطلاعات محتوا
* ▶️ پلیر داخلی مبتنی بر Media3 / ExoPlayer
* ⚡ رابط سریع و سبک
* 📱 طراحی مناسب اندروید
* 🔄 دریافت اطلاعات از سرویس آنلاین
* 🧩 ساختار ماژولار و قابل توسعه
* 🌙 رابط مناسب استفاده طولانی‌مدت
* 🇮🇷 تجربه مناسب کاربران فارسی‌زبان

---

## 💎 Powered by IranFlix

<div align="center">

### داده‌ها و زیرساخت محتوایی با همکاری IranFlix

</div>

Bisnor یک **client / frontend مستقل** است و برای دریافت اطلاعات و دسترسی به محتوای فیلم و سریال از زیرساخت و سرویس‌های **IranFlix** استفاده می‌کند.

تمامی حقوق مربوط به سرویس‌ها، APIها، داده‌ها، زیرساخت، محتوای رسانه‌ای و سایر منابع متعلق به IranFlix یا صاحبان اصلی آن‌ها محفوظ است.

مجوز MIT این مخزن تنها شامل بخش‌هایی از سورس‌کد Bisnor است که متعلق به این پروژه هستند و شامل سرویس‌ها یا محتوای شخص ثالث نمی‌شود.

---

## 🛠️ تکنولوژی‌ها

Bisnor با ابزارها و کتابخانه‌های مدرن اکوسیستم Android ساخته شده است.

| Technology          | Usage                   |
| ------------------- | ----------------------- |
| Android SDK         | Core platform           |
| Gradle Kotlin DSL   | Build system            |
| ViewBinding         | UI binding              |
| Retrofit            | API communication       |
| OkHttp              | HTTP client             |
| Gson                | JSON parsing            |
| Coil                | Image loading           |
| Media3 / ExoPlayer  | Video playback          |
| Kotlin Coroutines   | Asynchronous operations |
| AndroidX Lifecycle  | Lifecycle management    |
| RecyclerView        | Content lists           |
| ViewPager2          | Page navigation         |
| Material Components | UI components           |

---

## 📱 نیازمندی‌ها

| مورد            | مقدار       |
| --------------- | ----------- |
| Minimum Android | Android 7.0 |
| Minimum SDK     | 24          |
| Target SDK      | 35          |
| Compile SDK     | 35          |
| Java            | 17          |

---

## 🚀 Build

ابتدا مخزن را Clone کنید:

```bash
git clone https://github.com/nikan48g/Bisnor.git
```

سپس وارد پوشه پروژه شوید:

```bash
cd Bisnor
```

### Linux / macOS

```bash
./gradlew assembleDebug
```

### Windows

```cmd
gradlew.bat assembleDebug
```

فایل APK ساخته‌شده در مسیر زیر قرار می‌گیرد:

```text
app/build/outputs/apk/debug/
```

همچنین می‌توانید پروژه را مستقیماً در **Android Studio** باز کرده و اجرا کنید.

---

## 📂 ساختار پروژه

```text
Bisnor/
├── app/
│   ├── src/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── LICENSE
├── logo.png
└── README.md
```

---

## 🤝 مشارکت

مشارکت در توسعه Bisnor آزاد است.

برای اعمال تغییرات:

1. Repository را Fork کنید.
2. یک Branch جدید ایجاد کنید.
3. تغییرات خود را اعمال کنید.
4. Commit مناسب ایجاد کنید.
5. Pull Request ارسال کنید.

مثال:

```bash
git checkout -b feature/my-feature
git commit -m "feat: add my feature"
git push origin feature/my-feature
```

Pull Requestها بهتر است تا حد ممکن روی یک تغییر مشخص تمرکز داشته باشند.

---

## 🐛 گزارش مشکل

اگر مشکلی در برنامه پیدا کردید، از بخش **Issues** استفاده کنید:

[![Open an Issue](https://img.shields.io/badge/Open_an_Issue-GitHub-black?style=for-the-badge\&logo=github)](https://github.com/nikan48g/Bisnor/issues)

در گزارش خود در صورت امکان این اطلاعات را وارد کنید:

* نسخه Android
* مدل دستگاه
* توضیح مشکل
* مراحل تکرار مشکل
* Screenshot یا Log مرتبط

اطلاعات خصوصی، Tokenها، API Keyها یا سایر اطلاعات حساس را در Issue عمومی قرار ندهید.

---

## 🔐 امنیت

اطلاعات حساس نباید در Repository عمومی Commit شوند.

از انتشار موارد زیر خودداری کنید:

```text
API Keys
Access Tokens
Passwords
Private Credentials
Signing Keys
Private Endpoints
```

---

## ⚖️ Disclaimer

Bisnor یک نرم‌افزار **client / frontend** است.

این Repository به خودی خود میزبان یا مالک فیلم‌ها، سریال‌ها یا سایر فایل‌های رسانه‌ای شخص ثالث نیست.

نام‌ها، تصاویر، پوسترها، Metadata، فایل‌های رسانه‌ای، علائم تجاری، APIها و سرویس‌های شخص ثالث متعلق به صاحبان مربوطه هستند.

Bisnor مالکیت یا مجوزی را نسبت به منابع شخص ثالث صرفاً به دلیل نمایش یا استفاده از آن‌ها ادعا نمی‌کند.

کاربران موظف‌اند هنگام استفاده از نرم‌افزار، قوانین و مقررات محل زندگی خود و شرایط سرویس‌های مرتبط را رعایت کنند.

---

## 📄 License

این پروژه تحت مجوز **MIT License** منتشر شده است.

شما می‌توانید کد تحت پوشش این مجوز را:

* استفاده کنید
* تغییر دهید
* کپی کنید
* توزیع کنید
* در پروژه‌های تجاری استفاده کنید

به شرط آنکه Copyright Notice و متن License مطابق شرایط MIT حفظ شوند.

برای جزئیات کامل:

[![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

### توجه



## ⭐ حمایت از پروژه

اگر Bisnor برایتان مفید بود، ساده‌ترین راه حمایت از پروژه دادن یک **Star** است.

<div align="center">

[![Star Bisnor](https://img.shields.io/github/stars/nikan48g/Bisnor?style=social)](https://github.com/nikan48g/Bisnor)

**Bisnor**

تماشا بدون مرز 💎

Powered by **IranFlix**

<br>

Made with ❤️ by **HNN**

</div>
