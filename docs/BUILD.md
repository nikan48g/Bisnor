# 🛠️ راهنمای Build بیسنور

این سند مراحل Build و آماده‌سازی نسخه‌های Android، Windows Desktop و Samsung Tizen TV را توضیح می‌دهد.

> وضعیت فعلی پلتفرم‌ها: Android پایدار است؛ Windows Desktop و Tizen TV هنوز Experimental هستند.

---

## 🤖 Android

### نیازمندی‌ها

- Android Studio
- JDK 17
- Android SDK 35
- Git

| مورد | مقدار |
|---|---|
| Minimum Android | Android 7.0 |
| Minimum SDK | 24 |
| Target SDK | 35 |
| Compile SDK | 35 |
| Java | 17 |

### Clone

```bash
git clone https://github.com/nikan48g/Bisnor.git
cd Bisnor
```

### تنظیم `local.properties`

فایل نمونه را کپی کنید:

**Linux / macOS**
```bash
cp local.properties.example local.properties
```

**Windows**
```cmd
copy local.properties.example local.properties
```

سپس مقادیر موردنیاز را تنظیم کنید:

```properties
sdk.dir=/path/to/android/sdk

SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key_here
```

> هیچ Service Role Key، Database Password یا secret خصوصی را داخل Repository قرار ندهید.

### Build Debug

**Linux / macOS**
```bash
./gradlew assembleDebug
```

**Windows**
```cmd
gradlew.bat assembleDebug
```

خروجی:
```text
app/build/outputs/apk/debug/
```

### Build Release

```bash
./gradlew assembleRelease
```

خروجی:
```text
app/build/outputs/apk/release/
```

برای انتشار واقعی از keystore خصوصی استفاده کنید و فایل یا رمز signing را commit نکنید.

---

## 🖥️ Windows Desktop — Experimental

مسیر پروژه:

```text
desktop/BisnorDesktop/
```

تکنولوژی اصلی:
- WPF
- Microsoft WebView2
- .NET 10

### نیازمندی‌ها

- Windows 10/11 x64
- .NET 10 SDK
- Microsoft Edge WebView2 Runtime
- Inno Setup 6+
- Git

نصب سریع Inno Setup:

```powershell
winget install JRSoftware.InnoSetup
```

### اجرای Development

```powershell
cd desktop/BisnorDesktop
dotnet restore
dotnet run
```

### Build

```powershell
dotnet build desktop/BisnorDesktop/BisnorDesktop.csproj -c Release
```

### Publish برای Windows x64

```powershell
dotnet publish desktop/BisnorDesktop/BisnorDesktop.csproj `
  -c Release `
  -r win-x64 `
  --self-contained false `
  -p:PublishSingleFile=false `
  -o desktop/publish
```

خروجی در:

```text
desktop/publish/
```

### ساخت Installer با Inno Setup

اسکریپت:

```text
packaging/innosetup/Bisnor.iss
```

نمونه Build:

```powershell
& "C:\Users\<User>\AppData\Local\Programs\Inno Setup 6\ISCC.exe" /DMyAppVersion="5.1.1" packaging/innosetup/Bisnor.iss
```

خروجی:

```text
packaging/innosetup/Output/Bisnor-5.1.1-win-x64.exe
```

Installer به‌صورت per-user طراحی شده و به‌طور پیش‌فرض در `%LOCALAPPDATA%\Programs\Bisnor` نصب می‌شود.

### Silent Install

```powershell
.\packaging\innosetup\Output\Bisnor-5.1.1-win-x64.exe /VERYSILENT /NORESTART /SUPPRESSMSGBOXES
```

### Silent Uninstall

```powershell
& "$env:LOCALAPPDATA\Programs\Bisnor\unins000.exe" /VERYSILENT /NORESTART /SUPPRESSMSGBOXES
```

### WinGet

مانیفست‌ها در این مسیر هستند:

```text
packaging/winget/manifests/h/HNN/Bisnor/5.1.1/
```

اعتبارسنجی:

```powershell
winget validate --manifest packaging/winget/manifests/h/HNN/Bisnor/5.1.1
```

برای تست Local Manifest، ابتدا در ترمینال Administrator:

```powershell
winget settings --enable LocalManifestFiles
```

سپس:

```powershell
winget install --manifest packaging/winget/manifests/h/HNN/Bisnor/5.1.1
```

> مانیفست‌های WinGet تا زمان تأیید پایداری نسخه Desktop نباید بدون بررسی نهایی به `microsoft/winget-pkgs` ارسال شوند.

### GitHub Actions و Code Signing

Workflow نسخه Windows:

```text
.github/workflows/desktop-release.yml
```

این Workflow می‌تواند:
- پروژه .NET را publish کند
- Installer بسازد
- SHA-256 محاسبه کند
- artifact تولید کند
- فایل را به GitHub Release متصل کند

برای Code Signing، secrets زیر در نظر گرفته شده‌اند:

```text
WINDOWS_SIGNING_CERT_BASE64
WINDOWS_SIGNING_PASSWORD
```

هیچ certificate یا signing key خصوصی نباید در Repository ذخیره شود.

---

## 📺 Samsung Tizen TV — Experimental

مسیر پروژه:

```text
tizen-tv/
```

### نیازمندی‌ها

- Samsung Tizen Studio
- TV Extension
- Samsung/Tizen Certificate Profile
- Developer Mode روی TV
- TV و سیستم توسعه روی یک شبکه

### Build

```bash
cd tizen-tv
tizen build-web -- .
```

### Package

```bash
tizen package -t wgt -s YOUR_SIGNING_PROFILE -- .buildResult
```

### اتصال به TV

```bash
sdb connect TV_IP:26101
sdb devices
```

### نصب WGT

```bash
tizen install -n BisnorCinema.wgt -t YOUR_TV_TARGET
```

نام target، مسیر WGT و certificate profile بسته به محیط Tizen Studio شما متفاوت است.

---

## 🧪 تست‌های پایه

Unit Tests:

```bash
./gradlew test
```

Instrumented Tests:

```bash
./gradlew connectedAndroidTest
```

برای تغییرات حساس حداقل Fresh Install، Login/Logout، Search، Player، Watchlist، Downloads، Theme، Deep Links و حالت بدون اینترنت بررسی شوند.

---

## 🔐 نکات امنیتی Build

این موارد هرگز نباید commit شوند:

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

برای خطاهای رایج هنگام Build یا نصب، [راهنمای Troubleshooting](TROUBLESHOOTING.md) را ببینید.
