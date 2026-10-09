<div align="center">

<img src="https://raw.githubusercontent.com/nikan48g/Bisnor/main/logo.png" width="120" alt="Bisnor Logo">

# Bisnor v5.1.5 💎

### Android Install Hotfix & Security Hardening

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Windows](https://img.shields.io/badge/Windows-10%2F11-0078D4?style=for-the-badge&logo=windows&logoColor=white)
![Version](https://img.shields.io/badge/Version-5.1.5-blue?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

</div>

---

نسخه **5.1.5 بیسنور** یک انتشار اصلاحی فوری برای نصب مطمئن‌تر APK و سخت‌سازی امنیت پروژه است. این نسخه با `versionCode 32` منتشر می‌شود تا دستگاه‌هایی که بیلد قبلی را ناسازگار تشخیص داده‌اند بتوانند بدون حذف اطلاعات کاربر ارتقا پیدا کنند.

## 📱 Android Install Hotfix

- افزایش نسخه به `5.1.5` با `versionCode 32`
- ساخت دوباره APK از خروجی Release
- حفظ شناسه بسته `com.hnn.bisnor` و گواهی نسخه‌های قبلی
- بررسی صحت امضای APK و Zip Alignment
- تست نصب واقعی به‌صورت ارتقا از 5.1.3 به 5.1.4 و سپس 5.1.5

## 🔐 Security & CI

- محدودسازی پراکسی کاتالوگ Tizen به مسیرهای مجاز API برای جلوگیری از SSRF
- افزودن تست خودکار برای رد URLهای خارجی و مسیرهای فرار
- تعیین حداقل دسترسی `contents: read` برای GitHub Actions
- حذف تنظیم پراکسی محلی از Gradle تا CI و CodeQL روی GitHub اجرا شوند
- هماهنگ‌سازی CodeQL با Default setup مخزن

## 🤖 Telegram Release Publisher

- انتشار پیام معرفی پیش از فایل‌ها
- نمایش SHA256 کنار فایل APK و Windows
- نمایش دکمه دانلود از تلگرام فقط پس از آپلود موفق APK
- اتصال دکمه تلگرام به همان پیام فایل APK
- حفظ لینک‌ها و متن‌های Bold در قالب پیام

## 🖥️ Windows & WinGet

- ساخت خودکار Installer ویندوز برای نسخه 5.1.5
- تولید SHA256 در جریان Release
- آماده‌سازی مانیفست WinGet پس از تولید Installer نهایی

---

<div align="center">

### 💎 Bisnor

**تماشا بدون مرز**

Powered by **IranFlix**

Made with ❤️ by **HNN**

</div>
