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
| Samsung Tizen TV | 🧪 Experimental | نسخه آزمایشی تلویزیون با پشتیبانی از Remote و AVPlay |

> نسخه‌های Experimental ممکن است ناقص باشند، روی همه دستگاه‌ها کار نکنند یا بدون حفظ سازگاری تغییر کنند.

---

## ✨ قابلیت‌های اصلی

- 🎥 مرور فیلم و سریال
- 🔎 جستجوی محتوا، بازیگر و کارگردان
- ❤️ Watchlist و Playlist
- 📺 پخش آنلاین
- 🎞️ نمایش فصل‌ها و قسمت‌ها
- ▶️ پلیر داخلی مبتنی بر Media3 / ExoPlayer
- 📱 پشتیبانی از پلیرهای خارجی
- ⏯️ ذخیره موقعیت پخش و ادامه تماشا
- ⏭️ Next Episode
- 📥 دانلود محتوا
- 🧠 سیستم Smart Taste و پیشنهاد محتوا
- 👤 حساب کاربری، آواتار و Sync
- 🌙 Light / Dark Theme
- 🇮🇷 رابط مناسب کاربران فارسی‌زبان و فونت Vazirmatn در بخش‌های مختلف پروژه

---

## 📦 دانلود

آخرین نسخه منتشرشده را از بخش Releases دریافت کنید:

[![Download Latest Release](https://img.shields.io/badge/Download-Latest%20Release-2ea44f?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/releases/latest)

> برای Android از APK موجود در Release استفاده کنید. نسخه‌های Desktop و Tizen فعلاً آزمایشی هستند و ممکن است نیاز به Build دستی داشته باشند.

---

## 💎 Powered by IranFlix

Bisnor برای دریافت اطلاعات و دسترسی به محتوای فیلم و سریال از زیرساخت و سرویس‌های **IranFlix** استفاده می‌کند.

سرویس‌ها، APIها، داده‌ها، زیرساخت، محتوای رسانه‌ای، نام‌ها و سایر منابع شخص ثالث تحت مالکیت و شرایط صاحبان مربوطه هستند و مشمول مجوز MIT این Repository نمی‌شوند.

---

## 🧱 ساختار Repository

```text
Bisnor/
├─ app/                    # Android app
├─ desktop/
│  └─ BisnorDesktop/       # Windows experimental app
├─ tizen-tv/               # Samsung Tizen TV experimental app
├─ gradle/                 # Gradle version catalog / wrapper files
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
| Retrofit | API communication |
| OkHttp | HTTP client |
| Gson | JSON parsing |
| Coil | Image loading |
| Media3 / ExoPlayer | Android video playback |
| Kotlin Coroutines | Async operations |
| AndroidX Lifecycle | Lifecycle management |
| Supabase | Account / sync data |
| WPF | Windows desktop shell |
| Microsoft WebView2 | Desktop UI runtime |
| HTML / CSS / JavaScript | Desktop and Tizen UI |
| Tizen Web API | Samsung TV integration |
| Tizen AVPlay | TV media playback |

---

# 🤖 Android

## نیازمندی‌ها

| مورد | مقدار |
|---|---|
| Minimum Android | Android 7.0 |
| Minimum SDK | 24 |
| Target SDK | 35 |
| Compile SDK | 35 |
| Java | 17 |

### ابزارهای پیشنهادی

- Android Studio
- JDK 17
- Android SDK 35
- Git

## Build نسخه Android

ابتدا Repository را Clone کنید:

```bash
git clone https://github.com/nikan48g/Bisnor.git
cd Bisnor
```

### 1. تنظیم local.properties

فایل نمونه را کپی کنید:

**Linux / macOS**

```bash
cp local.properties.example local.properties
```

**Windows CMD**

```cmd
copy local.properties.example local.properties
```

سپس مسیر Android SDK و تنظیمات Supabase خود را وارد کنید:

```properties
sdk.dir=/path/to/android/sdk

SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key_here
```

> مقادیر Supabase برای قابلیت‌های حساب و Sync استفاده می‌شوند. اطلاعات خصوصی مانند Service Role Key یا Database Password را داخل پروژه قرار ندهید.

### 2. Build نسخه Debug

**Linux / macOS**

```bash
./gradlew assembleDebug
```

**Windows**

```cmd
gradlew.bat assembleDebug
```

خروجی معمولاً در این مسیر قرار می‌گیرد:

```text
app/build/outputs/apk/debug/
```

### 3. Build نسخه Release

```bash
./gradlew assembleRelease
```

خروجی:

```text
app/build/outputs/apk/release/
```

> برای انتشار واقعی، از keystore خصوصی و signing configuration مخصوص خودتان استفاده کنید و هیچ فایل signing یا رمز آن را Commit نکنید.

---

# 🖥️ Windows Desktop — Experimental

نسخه Windows در مسیر زیر قرار دارد:

```text
desktop/BisnorDesktop/
```

این نسخه از **WPF + Microsoft WebView2** استفاده می‌کند.

## نیازمندی‌ها

- Windows 10 یا Windows 11
- .NET 10 SDK
- Microsoft Edge WebView2 Runtime
- Git

## اجرا در حالت Development

```powershell
cd desktop/BisnorDesktop
dotnet restore
dotnet run
```

## Build

```powershell
dotnet build -c Release
```

## Publish برای Windows x64

```powershell
dotnet publish -c Release -r win-x64 --self-contained false
```

خروجی Publish داخل پوشه `bin/Release` پروژه ساخته می‌شود.

> نسخه Desktop هنوز Experimental است. قبل از توزیع عمومی روی چند سیستم، پخش، WebView2، Login و مسیرهای شبکه را جداگانه تست کنید.

---

# 📺 Samsung Tizen TV — Experimental

نسخه آزمایشی Tizen در مسیر زیر قرار دارد:

```text
tizen-tv/
```

این نسخه برای محیط TV طراحی شده و شامل پشتیبانی از:

- Remote navigation
- کلیدهای تلویزیون
- Tizen AVPlay
- حالت Landscape
- رابط مخصوص فاصله مشاهده تلویزیون

است.

## نیازمندی‌ها

- Samsung Tizen Studio
- TV Extension
- Samsung Certificate / Tizen Certificate Profile
- Developer Mode فعال روی تلویزیون
- کامپیوتر و TV روی یک شبکه

## روش پیشنهادی با Tizen Studio

1. Tizen Studio را نصب کنید.
2. از Package Manager، افزونه‌های مربوط به **TV** را نصب کنید.
3. یک Certificate Profile معتبر برای TV بسازید.
4. Developer Mode را روی تلویزیون فعال کنید.
5. IP کامپیوتر را در Developer Mode تلویزیون ثبت کنید.
6. پوشه `tizen-tv` را به عنوان Web Project باز کنید.
7. پروژه را Build و سپس روی TV یا Emulator اجرا کنید.

## Build با CLI

بعد از نصب Tizen CLI:

```bash
cd tizen-tv
tizen build-web -- .
```

برای ساخت WGT امضاشده:

```bash
tizen package -t wgt -s YOUR_SIGNING_PROFILE -- .buildResult
```

نام Certificate Profile خود را جایگزین `YOUR_SIGNING_PROFILE` کنید.

## اتصال به تلویزیون

Developer Mode باید فعال باشد. سپس:

```bash
sdb connect TV_IP:26101
sdb devices
```

مثال:

```bash
sdb connect 192.168.1.50:26101
```

## نصب WGT

پس از Build و Package، فایل WGT تولیدشده را با Tizen Studio یا CLI روی Target نصب کنید.

نمونه:

```bash
tizen install -n BisnorCinema.wgt -t YOUR_TV_TARGET
```

> نام Target و مسیر WGT بسته به تنظیمات Tizen Studio شما متفاوت است.

### نکات Tizen

- فایل `config.xml` شامل تنظیمات اپ، permissionها و TV profile است.
- اطلاعات دستگاه، Certificateها و فایل‌های signing را هرگز Commit نکنید.
- فایل‌های `.wgt` و خروجی Debug در `.gitignore` قرار دارند.
- نسخه Tizen فعلاً Experimental است و سازگاری آن با همه مدل‌های Samsung TV تضمین نمی‌شود.

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
Tizen certificates
Device credentials
Private connection information
```

کلیدهایی که برای اجرای client عمومی طراحی شده‌اند، جایگزین کنترل دسترسی سمت سرور نیستند. برای داده‌های خصوصی، Policy و RLS مناسب را در Backend تنظیم کنید.

---

## 🧪 تست

پروژه شامل ساختار پایه Unit Test و Android Instrumented Test است.

```bash
./gradlew test
```

برای تست‌های Instrumented روی Emulator یا دستگاه متصل:

```bash
./gradlew connectedAndroidTest
```

هنگام تغییر بخش‌های حساس، حداقل این سناریوها را دستی بررسی کنید:

- Fresh install
- Login / Logout
- Search
- Detail page
- Internal player
- External player
- Watchlist
- Downloads
- Light / Dark Theme
- No-network state
- Deep links

---

## 🐛 Troubleshooting

### Android build خطا می‌دهد

- JDK را روی نسخه 17 قرار دهید.
- Android SDK 35 را نصب کنید.
- مقدار `sdk.dir` را در `local.properties` بررسی کنید.
- سپس:

```bash
./gradlew clean
./gradlew assembleDebug
```

### Desktop باز نمی‌شود

- نصب بودن .NET 10 Runtime / SDK را بررسی کنید.
- WebView2 Runtime باید نصب باشد.
- پروژه را از مسیر `desktop/BisnorDesktop` اجرا کنید.

### Tizen روی TV نصب نمی‌شود

معمولاً یکی از این موارد علت است:

- Developer Mode فعال نیست.
- Certificate Profile معتبر انتخاب نشده.
- TV و کامپیوتر روی یک شبکه نیستند.
- Target در Device Manager متصل نیست.
- Certificate اجازه نصب روی Device موردنظر را ندارد.

---

## 🤝 مشارکت

Pull Request و Bug Report پذیرفته می‌شود.

```bash
git checkout -b feature/my-feature
git commit -m "feat: add my feature"
git push origin feature/my-feature
```

سپس یک Pull Request به Branch اصلی ارسال کنید.

در PR بهتر است توضیح دهید:

- چه چیزی تغییر کرده
- چرا تغییر لازم بوده
- چطور تست شده
- آیا روی Android / Desktop / Tizen اثر دارد

---

## 🐞 گزارش مشکل

اگر مشکلی پیدا کردید، از GitHub Issues استفاده کنید:

[![Open an Issue](https://img.shields.io/badge/Open_an_Issue-GitHub-black?style=for-the-badge&logo=github)](https://github.com/nikan48g/Bisnor/issues)

در گزارش، در صورت امکان این موارد را اضافه کنید:

- نسخه Bisnor
- پلتفرم
- نسخه سیستم‌عامل
- مدل دستگاه
- مراحل تکرار مشکل
- نتیجه مورد انتظار
- نتیجه واقعی
- Log مرتبط

اطلاعات حساس را داخل Issue عمومی منتشر نکنید.

---

## ⚖️ Disclaimer

Bisnor یک نرم‌افزار **client / frontend** است.

این Repository به خودی خود میزبان یا مالک فیلم‌ها، سریال‌ها یا فایل‌های رسانه‌ای شخص ثالث نیست.

مسئولیت رعایت قوانین، مجوزها و شرایط استفاده از سرویس‌های شخص ثالث بر عهده استفاده‌کننده است.

---

## 📄 License

Bisnor تحت مجوز **MIT License** منتشر شده است.

شما می‌توانید کد تحت پوشش این مجوز را استفاده، تغییر، کپی و توزیع کنید، مشروط به حفظ Copyright Notice و متن مجوز MIT.

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
