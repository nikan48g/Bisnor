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

| پلتفرم | وضعیت |
|---|---|
| Android | ✅ Stable / Primary |
| Windows Desktop | 🧪 Experimental |
| Samsung Tizen TV | 🧪 Experimental |

---

## ✨ قابلیت‌های اصلی

- 🎥 مرور فیلم و سریال
- 🔎 جستجوی محتوا، بازیگر و کارگردان
- ❤️ Watchlist و Playlist
- 📺 پخش آنلاین
- 🎞️ نمایش فصل‌ها و قسمت‌ها
- ▶️ پلیر داخلی Media3 / ExoPlayer
- 📱 پشتیبانی از پلیرهای خارجی
- ⏯️ ادامه پخش و ذخیره موقعیت
- ⏭️ Next Episode
- 📥 دانلود محتوا
- 🧠 Smart Taste و پیشنهاد محتوا
- 👤 حساب کاربری، آواتار و Sync
- 🌙 Light / Dark Theme
- 🇮🇷 رابط فارسی و فونت Vazirmatn

---

## 📦 دانلود

[![Download Latest Release](https://img.shields.io/badge/Download-Latest%20Release-2ea44f?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/releases/latest)

> Android پلتفرم اصلی پروژه است. نسخه‌های Desktop و Tizen فعلاً Experimental هستند.

---

## 📚 مستندات

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
├─ app/                    # Android
├─ desktop/                # Windows Desktop
├─ tizen-tv/               # Samsung Tizen TV
├─ packaging/              # Windows packaging / WinGet
├─ docs/                   # Documentation
└─ .github/workflows/      # CI / Release workflows
```

---

## 🛠️ تکنولوژی‌ها

| Technology | Usage |
|---|---|
| Android SDK / Kotlin | Android app |
| Media3 / ExoPlayer | Video playback |
| Retrofit / OkHttp | Networking |
| Supabase | Account / sync |
| WPF / .NET 10 / WebView2 | Windows Desktop |
| HTML / CSS / JavaScript | Desktop and Tizen UI |
| Tizen Web API / AVPlay | Samsung TV |

---

## 🔐 امنیت

اطلاعات حساس مانند Service Role Keys، Database Passwords، Private Tokens، Signing Keys، Keystoreها و Certificateها نباید داخل Repository عمومی قرار بگیرند.

---

## 🤝 مشارکت

Pull Request و Bug Report پذیرفته می‌شود. در PR توضیح دهید چه چیزی تغییر کرده، چرا لازم بوده و روی کدام پلتفرم اثر دارد.

---

## 🐞 گزارش مشکل

[![Open an Issue](https://img.shields.io/badge/Open_an_Issue-GitHub-black?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/issues)

در گزارش، نسخه Bisnor، پلتفرم، نسخه سیستم‌عامل، مراحل بازتولید و Log مرتبط را اضافه کنید. اطلاعات حساس را داخل Issue عمومی منتشر نکنید.

---

## ⚖️ Disclaimer

Bisnor یک نرم‌افزار **client / frontend** است و این Repository به خودی خود میزبان یا مالک فیلم‌ها، سریال‌ها یا فایل‌های رسانه‌ای شخص ثالث نیست.

---

## 📄 License

Bisnor تحت مجوز **MIT License** منتشر شده است.

[![MIT License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

---

<div align="center">

[![Star Bisnor](https://img.shields.io/github/stars/nikan48g/Bisnor?style=social)](https://github.com/nikan48g/Bisnor)

<br>

**Bisnor**  
تماشا بدون مرز 💎

Powered by **IranFlix**

Made with ❤️ by **HNN**

</div>
