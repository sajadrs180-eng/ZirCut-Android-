# زیرکات (ZirCut)

ادیتور خودکار ویدیو برای اندروید: واردکردن زیرنویس از SRT، حذف سکوت با تحلیل صدا روی خود گوشی، زوم ریتمی، خروجی عمودی ۹:۱۶.

## ساخت APK فقط با گوشی (بدون کامپیوتر)

1. توی github.com یه ریپوی جدید بساز (Public یا Private).
2. تو ریپو: Add file ← Create new file. اسم فایل رو دقیقاً `.github/workflows/build.yml` بنویس و محتوای فایل `build.yml` رو توش بچسبون. Commit کن.
3. Add file ← Upload files و فایل `zircut_project.zip` رو آپلود کن (همون zip، بازش نکن). Commit کن.
4. تب Actions ← Build APK. اگه خودکار شروع نشد، Run workflow بزن. حدود ۸ تا ۱۵ دقیقه طول می‌کشه.
5. وقتی سبز شد، همون صفحه پایین بخش Artifacts، فایل `ZirCut-apk` رو دانلود کن (zip می‌شه، بازش کن) و `ZirCut.apk` رو نصب کن.

اگه ساخت قرمز شد، متن خطای آخر لاگ رو برای Claude بفرست تا درست کنه.

نکته: هر بار ساخت، امضای APK فرق داره. قبل از نصب نسخه جدید، نسخه قبلی رو پاک کن.

## ساختار

- (fix235) کد C++ و whisper.cpp حذف شد؛ هیچ مدل گفتار، سرویس ابری یا دسترسی اینترنت وجود ندارد
- `core/AudioDecoder` — خواندن صدای ویدیو و تبدیل به ۱۶kHz
- `core/Planner` — حذف سکوت و ساخت عبارت‌های زیرنویس
- `core/CaptionRenderer` + `core/Exporter` — زیرنویس متحرک و رندر نهایی با Media3
- `ui/` — رابط Compose با تم‌های پس‌زمینه


## fix189
UI updates: nine caption-position presets, layer size presets, 90-degree rotation, layer reset, four audio fade presets, and trim audio to playhead.

## fix198
Freeze frame (clip tab): turns the frame under the playhead into a photo clip of 1-5 s.

## fix199
Output aspect ratio: 9:16, 16:9, 1:1, 4:5, original.

## fix200
Rotate 90, zoom and pan of the video picture (clip tab).

## fix201
Clip speed 0.25x-4x and speed ramps.

## fix202
Mirror (flip) horizontal/vertical for video picture.

## fix203
Audio: noise reduction (3 strengths) for music/voice clips.

## fix204
Audio: 3-band equaliser for music/voice clips.

## fix205
Timeline: multi-select clips (copy / delete / speed).

## fix206
Transform: free-angle rotation (-45..45 deg, auto-zoom).

## fix207
Output frame: free aspect ratio (0.4-2.5).

## fix208
Subtitles: split into short parts, exact timing nudge, undo for subtitle edits.

## fix209
Audio: extract the video's sound into an audio clip (then noise reduction / EQ apply to it).

## fix210
Layers: colour key (green/blue/white/black) for picture layers.

## fix211
Audio: silence removal for audio clips.

## fix212
Privacy: consent before sending speech online; unit tests; docs/TESTING.md, docs/RELEASE.md.

## fix213
Templates: 5 ready-made video templates.

## fix214
Templates: save and reuse your own templates.

## fix215
Undo/Redo now also covers frame ratio/options, subtitle style, font and position.

## fix216
Audio: auto-duck music under voice-over.

## fix217
Timeline: move several selected clips together.

## fix218
Auto-duck: music also turns down under the video's own speech (audio tab switch).

## fix219
Ripple editing switch (clip tab): layers and audio after a clip change move with it.

## fix220
Keyframes (zoom + pan) for the main video picture (clip tab).

## fix221
A transition per cut (clip tab): each clip can have its own entrance transition kind and length.

## fix222
Colour LUT (.cube) with strength, in the Filter tab; exact in the export, approximate in the preview.

## fix228
Chroma key (green screen) for video clips: eyedropper, tolerance, edge softness, spill suppression, colour/picture background; live preview on Android 13+, export on all.

## fix229
Layer keyframes: rotation is now animated too; per-key motion type (smooth / linear / hold); move a key in time (±100 ms).

## fix230
Video picture keyframes now also animate the free-angle rotation (preview and export).

## fix231
Video picture keyframes also animate opacity (fade to black), preview and export.

## fix232
Video picture keyframes: motion type (smooth/linear/hold), nudge in time, follow ripple edits, diamonds on the timeline ruler (tap = jump).

## fix233
Audio: five-band equaliser (added 350 Hz and 3.5 kHz bells), new presets; flat setting is refused.

## fix234
App language: first-run picker + Settings chips; packs fa, en, ar, tr (assets/i18n). See docs/PROGRESS.md.

## fix235
سیاست آفلاین: تشخیص گفتار/زیرنویس خودکار، سرویس ابری، دانلود مدل و مجوز INTERNET حذف شد. «حذف سکوت‌ها» حالا از روی تحلیل صدا (بدون مدل) کار می‌کند. زیرنویس فقط با ورود SRT.

## Repository rule (fix238)
Keep ONE flat source tree in the repo root. Do not commit `fix*.zip` / `ZirCut-fix*.zip` files: the workflow fails if any .zip is in the root.
