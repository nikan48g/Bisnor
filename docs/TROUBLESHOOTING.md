# 🐛 راهنمای Troubleshooting بیسنور

## 🤖 Android

اگر Gradle یا Build اجرا نمی‌شود، JDK 17، Android SDK 35 و مقدار `sdk.dir` در `local.properties` را بررسی کنید.

```bash
./gradlew clean
./gradlew assembleDebug
```

اگر GitHub Actions هنگام دانلود Gradle با `Connection refused` شکست خورد، لزوماً خطای کد نیست و می‌تواند مشکل شبکه Runner باشد.

## 🖥️ Windows Desktop

اگر برنامه اجرا نمی‌شود، .NET 10 Runtime/SDK و WebView2 Runtime را بررسی کنید.

اگر Local Manifest غیرفعال است، در ترمینال Administrator:

```powershell
winget settings --enable LocalManifestFiles
```

اگر مسیر manifest پیدا نمی‌شود، از root پروژه اجرا کنید یا مسیر کامل را بدهید.

برای بررسی شناسایی نصب:

```powershell
winget list Bisnor
```

برای بررسی Registry:

```cmd
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall" /s /f "Bisnor"
```

برای بررسی هش Installer:

```powershell
winget hash Bisnor-5.1.1-win-x64.exe
```

هش باید با `InstallerSha256` همان نسخه برابر باشد.

## 📺 Samsung Tizen TV

اگر TV در `sdb devices` دیده نمی‌شود، Developer Mode، IP سیستم توسعه، شبکه و Certificate Profile را بررسی کنید.

```bash
sdb connect TV_IP:26101
sdb devices
```

اگر WGT نصب نمی‌شود، Certificate Profile، target و Developer Mode را دوباره بررسی کنید.

## 🔎 گزارش Bug

نسخه Bisnor، پلتفرم، سیستم‌عامل، مراحل بازتولید، نتیجه مورد انتظار، نتیجه واقعی و Log مرتبط را اضافه کنید.

اطلاعات حساس، token، password، certificate یا کلید خصوصی را در Issue عمومی قرار ندهید.

برای مراحل Build به [BUILD.md](BUILD.md) مراجعه کنید.
