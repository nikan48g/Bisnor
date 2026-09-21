# 🛠️ راهنمای Build بیسنور

این سند مراحل Build و آماده‌سازی نسخه‌های Android، Windows Desktop و Samsung Tizen TV را توضیح می‌دهد.

## 🤖 Android

نیازمندی‌ها: Android Studio، JDK 17، Android SDK 35 و Git.

```bash
git clone https://github.com/nikan48g/Bisnor.git
cd Bisnor
```

فایل `local.properties.example` را به `local.properties` کپی و مسیر SDK و تنظیمات Supabase را وارد کنید.

Build Debug:

```bash
./gradlew assembleDebug
```

روی Windows:

```cmd
gradlew.bat assembleDebug
```

Build Release:

```bash
./gradlew assembleRelease
```

خروجی‌ها در `app/build/outputs/apk/` قرار می‌گیرند.

## 🖥️ Windows Desktop — Experimental

مسیر پروژه:

```text
desktop/BisnorDesktop/
```

نیازمندی‌ها: Windows 10/11 x64، .NET 10 SDK، WebView2 Runtime و Inno Setup 6+.

Development:

```powershell
cd desktop/BisnorDesktop
dotnet restore
dotnet run
```

پیش از Build، کلاینت کاتالوگ زنده را از منبع مشترک تایزن همگام کنید. این اسکریپت
هر snapshot آفلاین قدیمی را نیز حذف می‌کند:

```powershell
.\desktop\sync-live-web-assets.ps1
```

Build:

```powershell
dotnet build desktop/BisnorDesktop/BisnorDesktop.csproj -c Release
```

Publish:

```powershell
dotnet publish desktop/BisnorDesktop/BisnorDesktop.csproj `
  -c Release `
  -r win-x64 `
  --self-contained false `
  -p:PublishSingleFile=false `
  -o desktop/publish
```

ساخت Installer:

```powershell
& "C:\Users\<User>\AppData\Local\Programs\Inno Setup 6\ISCC.exe" /DMyAppVersion="5.1.3" packaging/innosetup/Bisnor.iss
```

WinGet manifest:

```text
packaging/winget/manifests/h/HNN/Bisnor/5.1.1/
```

اعتبارسنجی:

```powershell
winget validate --manifest packaging/winget/manifests/h/HNN/Bisnor/5.1.1
```

تست Local Manifest:

```powershell
winget settings --enable LocalManifestFiles
winget install --manifest packaging/winget/manifests/h/HNN/Bisnor/5.1.1
```

## 📺 Samsung Tizen TV — Experimental

نیازمندی‌ها: Tizen Studio، TV Extension، Certificate Profile معتبر و Developer Mode.

Build:

```bash
cd tizen-tv
tizen build-web -- .
```

Package:

```bash
tizen package -t wgt -s YOUR_SIGNING_PROFILE -- .buildResult
```

اتصال و نصب:

```bash
sdb connect TV_IP:26101
sdb devices
tizen install -n BisnorCinema.wgt -t YOUR_TV_TARGET
```

## 🧪 تست

```bash
./gradlew test
./gradlew connectedAndroidTest
```

هیچ Service Role Key، Database Password، signing key، keystore، PFX یا certificate خصوصی را commit نکنید.

برای خطاهای رایج به [TROUBLESHOOTING.md](TROUBLESHOOTING.md) مراجعه کنید.
