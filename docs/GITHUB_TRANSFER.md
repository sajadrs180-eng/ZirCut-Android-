# انتقال ZirCut به مخزن GitHub با نام ZirCut-Android (از موبایل)

فایل `ZirCut_GitHub_Ready.zip` مستقیماً محتویات ریشهٔ مخزن است (پوشهٔ اضافه ندارد).
ساختار داخل ZIP: `app/`، `.github/workflows/build.yml`، `build.gradle.kts`، `settings.gradle.kts`، `gradle.properties`، `docs/`، `legacy/`، `README.md`، `.gitignore` و ...

## هشدارهای مهم (قبل از شروع)
1. **خود فایل ZIP را داخل مخزن آپلود نکن.** GitHub ZIP را باز نمی‌کند و workflow هم عمداً در صورت وجود هر `.zip` در ریشه، Build را رد می‌کند.
2. پوشهٔ `.github` و فایل `.gitignore` با نقطه شروع می‌شوند و در بعضی فایل‌منیجرها **مخفی‌اند**؛ نمایش فایل‌های مخفی را روشن کن. بدون `.github/workflows/build.yml` اصلاً Build اجرا نمی‌شود.
3. ساختار پوشه‌ها باید دقیقاً حفظ شود (مثلاً `app/src/main/java/app/zircut/...`). آپلود دستی فایل‌ها به‌صورت تخت، پروژه را خراب می‌کند.

## روش پیشنهادی: Termux (ساختار پوشه‌ها حفظ می‌شود)
Termux را از F-Droid نصب کن (نسخهٔ Google Play قدیمی است).

1. در GitHub (مرورگر موبایل) مخزن خالی بساز: نام `ZirCut-Android`، بدون README و بدون .gitignore (Private یا Public).
2. توکن بساز: GitHub ← Settings ← Developer settings ← Personal access tokens ← Fine-grained ← فقط همین مخزن، دسترسی Contents = Read and write، و Workflows = Read and write (برای فایل workflow لازم است). توکن را جایی امن نگه دار.
3. در Termux:
```
pkg update && pkg install -y git unzip
termux-setup-storage
mkdir -p ~/zircut && cd ~/zircut
unzip ~/storage/downloads/ZirCut_GitHub_Ready.zip
ls -A        # باید app و .github و build.gradle.kts را ببینی
git init -b main
git config user.name "YOUR_NAME"
git config user.email "YOUR_EMAIL"
git add -A
git commit -m "ZirCut 0.1.238 - single flat source tree"
git remote add origin https://github.com/YOUR_USERNAME/ZirCut-Android.git
git push -u origin main
```
هنگام push، نام کاربری = نام GitHub و رمز = همان توکن.

## روش جایگزین بدون Termux (مرورگر موبایل)
آپلود پوشه در مرورگر موبایل معمولاً ممکن نیست؛ فقط اگر فایل‌منیجرت اجازه دهد:
1. ZIP را با یک فایل‌منیجر باز کن و محتویات را در یک پوشه استخراج کن.
2. مرورگر را روی «Desktop site» بگذار، مخزن ← Add file ← Upload files.
3. اگر انتخاب پوشه پشتیبانی نشد، هر پوشه را جدا آپلود کن؛ در هر آپلود حداکثر ۱۰۰ فایل. برای حفظ مسیر، از «Create new file» و نوشتن مسیر کامل (مثلاً `app/src/...`) استفاده کن. این روش برای ۹۱ فایل کند و خطاپذیر است، پس Termux بهتر است.

## بعد از آپلود
1. تب **Actions** ← workflow «Build APK» ← اجرا شدن خودکار با push (یا Run workflow).
2. مرحلهٔ «Source check» باید سبز باشد و `versionCode = 238` را چاپ کند.
3. خروجی در بخش Artifacts با نام `ZirCut-APK-<شمارهٔ اجرا>-<commit>` است.
4. اگر Build شکست خورد، لاگ مرحلهٔ «Error summary» را بفرست.

## یادآوری
- Build در GitHub اینترنت دارد؛ مدل `selfie_segmenter.tflite` فعلاً موقع Build دانلود و داخل APK گذاشته می‌شود.
- این بسته هنوز Build نشده است و روی گوشی تست نشده است.
- امضای Release فعلاً با کلید debug است و برای انتشار در فروشگاه‌ها باید با کلید واقعی عوض شود.
