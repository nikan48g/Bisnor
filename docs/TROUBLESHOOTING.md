# 🐛 راهنمای Troubleshooting بیسنور

این سند برای خطاهای رایج Build، نصب و اجرای Bisnor روی Android، Windows و Tizen است.

---

## 🤖 Android

### Gradle یا Build اجرا نمی‌شود

ابتدا نسخه Java را بررسی کنید:

```bash
java -version
```

پروژه به JDK 17 نیاز دارد.

سپس بررسی کنید Android SDK 35 نصب باشد و `sdk.dir` در `local.properties` درست تنظیم شده باشد.

پاک‌سازی و Build مجدد:

```bash
./gradlew clean
./gradlew assembleDebug
```

روی Windows:

```cmd
gradlew.bat clean
gradlew.bat assembleDebug
```

### GitHub Actions هنگام دانلود Gradle خطا می‌دهد

اگر لاگ مشابه زیر بود:

```text
Downloading https://services.gradle.org/distributions/...
java.net.ConnectException: Connection refused
```

این خطا لزوماً از کد پروژه نیست و می‌تواند خطای شبکه Runner هنگام دریافت Gradle Wrapper باشد.

اقدامات پیشنهادی:
- Workflow را دوباره اجرا کنید.
- نسخه Wrapper را بدون دلیل تغییر ندهید.
- از cache و retry مناسب در CI استفاده کنید.
- قبل از تغییر کد، بررسی کنید failure واقعاً وارد مرحله compile/test شده باشد.

---

## 🖥️ Windows Desktop

### برنامه اجرا نمی‌شود

بررسی کنید:
- .NET 10 Runtime یا SDK نصب باشد.
- Microsoft Edge WebView2 Runtime موجود باشد.
- Build از مسیر صحیح انجام شده باشد.

برای اجرا در Development:

```powershell
cd desktop/BisnorDesktop
dotnet restore
dotnet run
```

### Installer نصب می‌شود ولی برنامه پیدا نمی‌شود

مسیر پیش‌فرض نصب per-user:

```text
%LOCALAPPDATA%\Programs\Bisnor
```

بررسی:

```powershell
Test-Path "$env:LOCALAPPDATA\Programs\Bisnor"
```

### WinGet Local Manifest اجازه اجرا نمی‌دهد

اگر پیام زیر را دیدید:

```text
This feature needs to be enabled by administrators.
```

PowerShell یا CMD را با Administrator باز کنید و اجرا کنید:

```powershell
winget settings --enable LocalManifestFiles
```

### مسیر Manifest پیدا نمی‌شود

اگر:

```text
Path does not exist
```

نمایش داده شد، ابتدا وارد root پروژه شوید:

```cmd
cd /d D:\Projects\Bisnor
```

یا مسیر کامل manifest را به WinGet بدهید.

### WinGet برنامه را با `HNN.Bisnor` پیدا نمی‌کند

ابتدا بررسی کنید WinGet چه ARP entryای می‌بیند:

```powershell
winget list Bisnor
```

و Registry:

```cmd
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall" /s /f "Bisnor"
```

اگر برنامه فقط با یک شناسه `ARP\User\...` دیده می‌شود، مقادیر `ProductCode` و `AppsAndFeaturesEntries` در manifest باید دقیقاً با اطلاعات ثبت‌شده توسط Inno Setup هماهنگ باشند.

### بررسی SHA-256 Installer

```powershell
winget hash Bisnor-5.1.1-win-x64.exe
```

مقدار حاصل باید با `InstallerSha256` همان نسخه در manifest برابر باشد.

---

## 📺 Samsung Tizen TV

### TV در `sdb devices` دیده نمی‌شود

بررسی کنید:
- Developer Mode روی TV فعال باشد.
- IP سیستم توسعه در Developer Mode درست وارد شده باشد.
- TV و سیستم روی یک شبکه باشند.
- اتصال روی پورت موردنیاز برقرار باشد.

سپس:

```bash
sdb connect TV_IP:26101
sdb devices
```

### WGT نصب نمی‌شود

رایج‌ترین علت‌ها:
- Certificate Profile نامعتبر
- certificate مربوط به دستگاه نیست
- target اشتباه انتخاب شده
- WGT با profile صحیح TV ساخته نشده
- Developer Mode غیرفعال است

Build و package را دوباره انجام دهید:

```bash
tizen build-web -- .
tizen package -t wgt -s YOUR_SIGNING_PROFILE -- .buildResult
```

---

## 🔎 هنگام گزارش Bug

برای اینکه Issue قابل بررسی باشد، این اطلاعات را اضافه کنید:

- نسخه Bisnor
- پلتفرم
- نسخه سیستم‌عامل
- مدل دستگاه در صورت مرتبط بودن
- مراحل دقیق بازتولید
- نتیجه مورد انتظار
- نتیجه واقعی
- Log مرتبط

اطلاعات حساس، token، password، certificate یا کلید خصوصی را داخل Issue عمومی قرار ندهید.

برای مراحل Build به [BUILD.md](BUILD.md) مراجعه کنید.
