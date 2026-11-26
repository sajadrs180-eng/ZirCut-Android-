# ZirCut — progress and hand-off notes

## 0.1.251 — multiple independent video overlay tracks (implementation prepared; build/device checks pending)
- Raises independent video overlays from one to a maximum of three per project.
- Preview uses a dedicated `PreviewPlayer` for each overlay and synchronizes all active layers against the edited-timeline clock.
- Exporter creates one Media3 sequence per visible overlay; compositor input IDs map to each overlay, preserving the optional transparent top sequence for text/photo/FX overlays.
- Adds capacity validation tests and releases players when overlay layers are deleted.
- Audio on overlay videos remains muted. Masks, per-overlay keyframes, and drag positioning are not included.
- Must verify GitHub Actions build, simultaneous preview, and exported composition order/performance on a real phone.

## 0.1.248 — direct timeline clip selection
- Tapping a video/photo block selects it, opens the Clip panel, and seeks to the tapped time.
- The selected clip has an amber outline independent of the playhead's current-clip highlight.
- Timeline selection and Clip panel multi-selection now share state; a timeline tap intentionally replaces the multi-selection with one clip.
- Version is 248 / 0.1.248; GitHub Actions triggers on `feature/0.1.248-reviewed`.
- Android build and device gesture checks must be confirmed by GitHub Actions and on-device testing.

## 0.1.247 — timeline regression hardening
- Added tests for snap-target priority and exclusion of only the currently dragged audio clip. No timeline runtime logic or project format changed.
- GitHub Actions triggers on `feature/0.1.247-reviewed`; version was 247 / 0.1.247.
- GitHub Actions Build APK completed successfully; device gesture checks remain unverified.

Android video editor (Kotlin, Jetpack Compose, Media3 Transformer). Built only through GitHub Actions
(workflow unpacks every `fix*.zip` in the repo root in version order, then builds). Persian (RTL) first.

Target: a CapCut-class editor, delivered in 8 parts. Every part is a full project snapshot zip (fix110, fix120, ...).

## 0.1.246 — precision timeline (NOT yet built on a device)
- New: `timeline/TimelineSnap.kt` (`TimelineSnap` = magnet logic, `TrimOps` = exact audio/layer trimming limits), `TimelineSnapTest` (21 tests, run with kotlinc 2.0.21 + JUnit 4.13.2).
- `Editor.kt`: the timeline drag gesture now snaps, previews the range and shows the readout; magnet button; `MainViewModel`: `snap` app setting, `newStep` for one-undo-per-drag.
- Details: docs/CHANGELOG-0.1.246.md. Test list: docs/TESTING.md section 12.
- Known: Editor.kt/MainViewModel.kt changes are only syntax-checked (no Android SDK here); the first Gradle build may need small fixes.

## Parts

| # | Part | Status |
|---|------|--------|
| 1 | Projects + library (save, thumbnails, autosave, continue editing) | DONE (needs build verification) |
| 2 | Clip editing on the timeline of the main video (split, trim, delete, duplicate, reorder, undo/redo) | DONE (needs build verification) |
| 3 | Extra videos/photos as clips + multi-source preview and export | DONE (needs build verification) |
| 4 | Image and text layers (position, scale, rotation, opacity, time) | DONE (needs build verification) |
| 5 | Audio (music/voice tracks, waveform, volume, fades, recording) | IMPLEMENTED in fix150 (build and phone test pending) |
| 6 | Effects (GPU), transitions, color grading page | step A fix160 + step B fix180 (build and phone test pending) |
| 7 | Advanced export (resolution, fps, bitrate, codec, device capability check) | fix170 done |
| 8 | Scene detect, SRT import/export, cleanup, project recovery | fix180 (build and phone test pending) |

Out of scope for these 8 parts (heavy models / separate engine): background removal, subject tracking,
advanced masks, full keyframe system. Do not fake them in the UI.

## Part 1 — what exists now

Done and wired to real logic:
- `project/Project.kt`: `ProjectStore` (filesDir/projects/<id>/ media.mp4, thumb.jpg, meta.json, project.json),
  create / save / load / list / rename / delete / duplicate. JSON via org.json. Original video is moved into the
  project folder (never modified).
- `MainViewModel`: every picked video becomes a project (`pickVideo`); `openProject`, `renameProject`,
  `duplicateProject`, `deleteProject`; autosave 500 ms after any edit (`autosave` / `persist`);
  `UiState` has `projectId`, `projectName`, `projects`, `saveStatus`.
- Home = library: new project, continue editing, 2-column project grid with thumbnails, ratio, age, search,
  per-project menu (rename / duplicate / delete), list of exported videos.
- New-project sheet: language + auto-edit options, then video picker.
- Editor top bar shows project name (tap to rename) and save status.
- Timeline ruler label no longer hidden under the playhead.

Not done in part 1 (deliberately later): templates, media browser, project settings screen, project recovery UI,
undo/redo (part 2), multiple media per project (part 2).

## Part 2 — what exists now

- `timeline/Edl.kt`: `EdlMap` (clips laid end to end; out-time <-> source-time), `EdlOps` (split, trimStart, trimEnd,
  delete, duplicate, moveEarlier, moveLater -> `EdlResult(segs, playhead)`), `outPhrasesOf` (subtitles on the edited timeline).
- `UiState.segments: List<Range>?` = manual clip list (null = automatic silence cut from `Planner.ranges`).
  First edit bakes the current automatic cut into manual clips. Saved in project.json ("segments").
  `MainViewModel.setSegments / undo / redo` (60 steps; history is only for clip edits, not subtitle text).
  `export()` uses the manual clips; the Exporter already accepts any ordered list of source ranges.
- Editor: playhead (`posMs`) is now on the EDITED timeline. Timeline draws clips end to end (current clip highlighted),
  subtitle blocks at their edited position. New dock tab "کلیپ" with the clip tools (work on the clip under the playhead).
  Preview playback walks the clip list (jumps between clips, supports duplicates and reordering).
  Undo/redo buttons in the top bar. App settings moved to the "ادیت" tab.
- Subtitle sync: `CaptionTimeline(phrases, ranges)` is used for preview exactly like export.

Not done in part 2: drag-to-trim handles and drag-and-drop reordering (use the tool buttons), ripple behaviours,
more than one source video (part 3), multiple tracks (parts 3-5), undo for subtitle edits.
A phrase that is played twice (duplicated clip) is drawn once on the timeline (first play).

## Part 3 — what exists now

- Extra media: "افزودن ویدیو یا عکس در پلی‌هد" in the clip tab. The picked file is copied into the project folder
  (`extra_<id>.mp4/jpg/png/webp`), stored in project.json ("extras") and inserted as a clip at the playhead
  (`EdlOps.insertAt`: before/after the clip under the playhead, or the clip is split). Photos get 5 s by default
  (can be shortened with the clip tools, cannot be lengthened yet).
- Source tagging: a clip's Range encodes its source (`timeline/Edl.kt` object `Src`: source 0 = main video,
  k >= 1 = extra k, time = k * 10^10 + ms). All clip tools/undo/redo/persistence work unchanged on mixed clips.
- Subtitles exist only for the main video. `captionRanges()` gives other clips impossible source times so no subtitle
  is shown over them.
- Preview: `ui/PreviewPlayer.kt` (one MediaPlayer that switches files; ~0.2 s pause when switching between files),
  photos are shown as an image and timed by wall clock. The preview frame keeps the output shape; each clip is
  cropped to fill it, like the exporter does.
- Export: `Exporter.Params` has `sources` (per-clip uri + isImage) and `captionRanges`. When media are mixed every
  clip is scaled/cropped to the output size (`Presentation.createForWidthAndHeight`). Photo clips are
  `EditedMediaItem` with duration + 30 fps; audio gaps are filled by calling `experimentalSetForceAudioTrack`
  through reflection (if that method is missing on the Media3 version, photos stay silent and audio may be dropped
  for the whole export — check first on a real device).
- Timeline: photo clips are grey, video clips blue, labelled "عکس N" / "ویدیو N".

Not done in part 3: transcribing extra videos (no subtitles on them), photo EXIF rotation, lengthening photos,
removing unused extra files from the project folder, per-clip volume/speed, multiple video tracks/overlays
(parts 4-6).

## Part 4 — what exists now

- `timeline/Layers.kt`: `Layer` (text or picture: start/end on the EDITED timeline, centre x/y as fractions of the frame,
  scale, rotation in degrees clockwise, opacity, text/colour/background/stroke/font, or picture file) and `LayerOps`
  (timeline rows, split, move up/down, unique ids). `UiState.layers` + `selectedLayer` (selection is not saved).
  Saved in project.json ("layers"). Picture files are copied into the project folder (`layer_<time>.png|jpg`, max 2000 px,
  EXIF-rotated; photos become JPEG, pictures with transparency stay PNG).
- `core/LayerRenderer.kt`: ONE renderer for preview and export (sizes are fractions of the frame's short side).
  A layer is drawn at min(scale, 2)x and shown at scale / that, so big text stays sharp. `LayerBitmapCache` for the preview.
- Editor: new dock tab "لایه" (the dock now scrolls sideways). Panel: add text / add picture, layer chips, text field, text colour,
  background box, dark outline, font, size, rotation, opacity, X, Y, start time, duration, "start here", "end here", split,
  copy, move up/down (drawing order), go to layer, delete. On the preview: one finger moves the selected layer, two fingers
  resize and turn it. Timeline: layers under the subtitle track (up to 3 rows; more overlapping layers share the last row),
  tap to select.
- History: undo/redo now covers clip edits AND layer edits (`Snap(segs, layers)`). Slider drags, typing and finger gestures
  on the same layer within 0.9 s are one undo step (`MainViewModel.editLayer`).
- Export: `Exporter.Params.layers/projectDir/layerFont`. Each layer is a Media3 `BitmapOverlay` (`LayerOverlay`), rotation baked
  into the bitmap, position/scale/opacity through `OverlaySettings`; only clips that overlap the layer's time get the overlay.
  Layers are drawn under the subtitles. Same time handling as the caption overlay (relative or absolute frame times).

Not done in part 4: drag-to-move/trim layers on the timeline (use the sliders and buttons), keyframes/animation, fade in/out,
text styles beyond colour/background/outline, bold/italic, per-letter effects, layer lock/hide, unused picture files are
not removed from the project folder, layer times do not follow clip edits (a layer keeps its place on the timeline even if
clips before it are cut), no layers over the timeline end (they simply are not shown).
Risk (still open, fix140 phone test not confirmed): `OverlaySettings` / multiple overlays in one `OverlayEffect` were written from memory of Media3 1.4.1 (same calls the
caption overlay already uses); first thing to test on the phone is export with a text layer and a picture layer.

## Part 5 — what exists now (fix150, NOT yet built or tested)

Written without a compiler (no Gradle/Kotlin in the authoring environment); the first GitHub Actions run decides whether it builds.
Media3 1.4.1 sources were checked for: `Composition.Builder(seq, vararg seq)`, `EditedMediaItem.Builder.setRemoveAudio`,
`Composition.Builder.experimentalSetForceAudioTrack` (EXISTS in 1.4.1, so the old reflection call is valid). Media3 1.4.1 has NO audio-gap
API, so the export builds the whole sound track itself.

New files
- `timeline/Audio.kt`: `AudioClip` (kind music/voice, file in project folder, start on edited timeline, in-point, length, file length,
  volume 0..1, fade in/out, mute) and `AudioOps` (`gainAt` = ONE volume/fade function used by preview and export, rows, fit, split).
- `core/PcmDecoder.kt`: any audio / video sound -> 44.1 kHz stereo 16-bit, streamed in blocks (MediaCodec, linear resampler;
  plain 16-bit WAV is read directly). `durationMs`, `hasAudio`.
- `core/AudioMixer.kt`: every video clip's sound (laid end to end on the edited timeline) + all music/voice clips -> ONE stereo WAV
  (sources decoded to temp files, mixed block by block, soft clip above 0.95). Photos/clips without audio = silence.
- `core/Waveform.kt`: real peaks (one byte per 20 ms) from the decoded file, stored as `<audiofile>.pk` in the project folder.
- `core/VoiceRecorder.kt`: AudioRecord mono 44.1 kHz -> WAV, always releases the microphone (`stop` / `cancel`).
- `ui/AudioPreview.kt`: one MediaPlayer per audible clip (created ~1.5 s before, released after), drift correction, per-moment volume.

Changed files
- `project/Project.kt`: `ProjectData.audio / mainVolume / mainMuted`, saved in project.json ("audio", "mainVol", "mainMute").
- `MainViewModel.kt`: `UiState.audio / selectedAudio / mainVolume / mainMuted / waves`; `importAudio`, `addRecording`, `editAudio`,
  `deleteAudio`, `duplicateAudio`, `splitAudio`, `setMainVolume`, `setMainMuted`, `showMessage`; undo/redo snapshot now also holds audio
  and main volume; `export()` mixes the sound first ("در حال میکس صدا…") when there are audio clips or the main volume is not 100 %.
  With no audio edits the OLD export path is used unchanged (clips keep their own sound).
- `core/Exporter.kt`: `Params.mixAudio`; with it, video clips get `setRemoveAudio(true)` and the WAV is a second audio-only sequence.
- `ui/PreviewPlayer.kt`: `setVolume`. `ui/Editor.kt`: dock tab "صدا" (`AudioPanel`), audio rows with waveform + fade corners under the
  layer rows on the timeline, recording (permission request, video plays while recording, pause/end stops the take).
- `ui/ZirCutApp.kt` (audio file picker), `ui/Theme.kt` (icons Music, Mic), `AndroidManifest.xml` (RECORD_AUDIO).

Known limits of part 5 (do not report as done)
- Volume is capped at 100 % (MediaPlayer cannot boost; the export matches the preview).
- No drag-to-move/trim of audio on the timeline (sliders and buttons only). Audio keeps its time when video clips are cut/moved.
- No waveform for the video's own sound. Only fades are linear. No ducking, no noise reduction, no per-clip speed/pitch.
- Preview starts a clip a fraction of a second late when playback starts inside it (player must prepare first).
- Recording in the background (app minimised) is not handled; headphones advised because the video plays while recording.
- Unused audio/recording/.pk files are not deleted (part 8). Mixing needs temp disk of about 10 MB per minute per source.
- Export of two sequences (silent video + WAV) is new in this build: must be tested on the phone, also with photo clips and zoom.

To test on the phone: import a song, check waveform and playback; change volume/fades; record a voice-over with headphones;
undo/redo each; export and listen (volumes, fades, position, original sound muted/lowered); export without any audio edit (old path);
reopen the project (clips and waveforms come back).

## Part 6 — step A (fix160, NOT yet built or tested)

fix150/fix151: build succeeded (after two small fixes); phone test of audio still pending.

Done in this step (only things that look the same in preview and export, and need no new GPU code):
- `core/ColorGrade.kt`: new grade controls Exposure (stops), Whites, Blacks, Hue rotation. All are affine, so they go through the
  same matrix as before (preview ColorMatrix = export RgbMatrix). Saved in project.json (old projects load with defaults).
- `core/Fx.kt` + `Exporter.kt` + `Editor.kt`: whole-video effects Vignette, Film grain (4 moving frames), Cinema bars, Light leak.
  They are pictures drawn over the video (Media3 BitmapOverlay in the export, Canvas in the preview) from the SAME bitmaps
  (`FxRender.build`). Media3 sizes an overlay by its pixels, so the export scales them to the output size (verified in
  OverlayMatrixProvider 1.4.1). Saved in project.json ("fx"). Panel: tab «فیلتر» (sliders under the color controls).

NOT done yet in part 6 (do not report as done): Blur / Gaussian blur, Sharpen, RGB split, Glitch, Noise, Glow (bloom), Highlights /
Shadows, per-color HSL, LUT, all transitions (Fade/Dissolve/Zoom/Slide/Blur/Spin/Glitch), effects per clip (current ones are global).
Reason: they need custom GPU shaders (BaseGlShaderProgram) and, for transitions, two clips drawn together. Media3 1.4.1 has no
built-in transitions or blur effect. Next step (B) will add a custom shader effect, tested first with one effect (Sharpen/RGB split)
before the rest.

Test on the phone: tab فیلتر: move Exposure / Whites / Blacks / Hue; each effect slider; compare preview and exported video;
check that old projects still open and that export with no effects looks like before.

## Part 7 — professional export (fix170, NOT yet built or tested)

fix160 (part 6 step A) build result not yet reported when this was written.

New: `core/ExportSettings.kt` (`ExportSettings`, `ExportPlan`: output size, automatic/explicit bitrate, size estimate),
`core/DeviceCaps.kt` (asks MediaCodecList what the phone's encoders accept: size, frame rate, hardware or software, max bitrate;
`sourceFps` of the video). Saved per project in project.json ("export").
Changed: `Exporter.kt` (size from `ExportPlan.size`, frame rate via FrameDropEffect, H.264 or HEVC, bitrate; retry levels still
fall back to 30 fps / lower bitrate / 2/3 size and always back to H.264), `Editor.kt` export sheet (size 720/1080/1440/4K, fps 24/30/60,
codec, bitrate slider, size estimate, free-space check that disables the button, encoder summary), `Project.kt`, `MainViewModel.kt`.
Only options the phone really supports are shown; 60 fps only when the video itself is 50+ fps; HEVC only with a hardware encoder.
"Automatic" for every setting keeps the behaviour of earlier versions.

Known limits (do not report as done)
- Audio quality is NOT selectable: Media3 1.4.1 DefaultEncoderFactory has no audio-encoder settings (fixed 128 kbit/s).
- Frame rate can only be lowered (frame drop), never raised; 60 fps needs a 60 fps source.
- The size estimate is bitrate x duration (+ audio); real files differ a little. 4K keeps all layers/captions as full-size bitmaps: heavy on RAM, untested.
- Free-space check assumes the file is written to cache and then copied to the gallery (2.2 x estimate).
- Cancel and real progress already existed from earlier parts (progress bar + cancel button during export).

Test on the phone: open the export sheet; change size/fps/codec/bitrate and compare the estimate with the real file; export at
720p, 1080p, 1440p, HEVC and 60 fps (with a 60 fps video); check the file plays; try with little free space; cancel in the middle.

## Part 6 — step B + Part 8 (fix180, NOT yet built or tested)

Written without a compiler. Media3 1.4.1 APIs used for the first time: `BaseGlShaderProgram(false, 1)` (`configure`, `drawFrame`, `release`),
`GlProgram(String, String)` (`use`, `setSamplerTexIdUniform`, `setFloatsUniform`, `setBufferAttribute`, `bindAttributesAndUniforms`, `delete`),
`GlUtil.getNormalizedCoordinateBounds`, `VideoFrameProcessingException(Throwable[, long])`. If the build fails, look at `core/GpuFx.kt` first.

Part 6 status after this step
- DONE and shared by preview + export: look, brightness, contrast, saturation, warmth, tint, fade, exposure, whites, blacks, hue (one matrix);
  vignette, film grain, cinema bars, light leak (bitmap overlays).
- NEW, export only (custom GL shader `core/GpuFx.kt`, applied after the final size, before overlays so layers/captions stay sharp):
  sharpen, RGB split, glitch, noise, glow (bloom of bright areas), highlights, shadows. Blur is also shown in the preview on Android 12+
  (RenderEffect, similar but not pixel-identical). The others are NOT visible in the preview (the panel says so).
- NEW transitions at every cut between clips (`Fx.trKind`, `Fx.trMs`): fade, zoom, slide, spin, blur, glitch. The end of a clip leaves and the start
  of the next arrives (`TrMath` is the ONE timing function). Preview shows fade/zoom/slide/spin on the video surface and blur on Android 12+; glitch
  is export only. This is NOT a true cross-dissolve of two overlapping clips (Media3 1.4.1 has no such API).
- Saved in project.json ("fx": blur, sharpen, split, glitch, noise, glow, hi, sh, tr, trMs); old projects load with defaults.
- NOT done: per-colour HSL, LUT, effects/transitions per single clip (all are project-wide), true two-clip dissolve.
- Shader: single pass, 17-tap ring blur / 12-tap glow; heavy at 4K on weak phones (untested). Frame times are detected like the overlays (relative or absolute).

Part 8 status
- DONE (untested): SRT export of the EDITED timeline to Download/ZirCut (`core/Srt.kt`, `MainViewModel.exportSrt`); SRT import
  (cue times = edited timeline, mapped to main-video clips, words share time by length; cues on photos/extra videos are skipped; clips are frozen first);
  scene detection (`core/SceneDetect.kt`: colour histograms of 32x32 frames + bisection, hard cuts only, splits main-video clips, one undo step);
  cleanup of unused extra/layer/audio/recording files (clears undo history); project recovery (`project.json.bak` is the previous save and is used
  when project.json cannot be read; a project whose meta.json is damaged is rebuilt from its video as "پروژه بازیابی‌شده").
- Buttons: tab «ادیت» (EditPanel).
- NOT done: subtitles for extra videos, better Persian caption timing, a real media browser, performance / stability testing (needs the phone).

## Design system (ui/Theme.kt, ui/Components.kt)

Tokens in `object Z` (colors, spacing 4pt, radii), `ZType` (Estedad variable font, per-weight Typeface),
Canvas-drawn icons `ZI`, controls `ZButton ZIconButton ZChip ZSegmented ZSwitch ZSlider ZCard ZField
ZSearchField ZToggleRow ZStat`. One accent (sky blue). Use `Txt(...)` for all text, `Modifier.press` for taps.
Persian digits: `fa(...)`, `faTime(ms)`.

## Known rules for whoever continues

- Cannot compile locally; keep each part small and re-check Kotlin signatures against the real source.
- Trailing lambda binds to the LAST parameter: put `onClick/onChange` last in composables.
- Do not remove existing working features (auto subtitles, silence cut, zoom, color look, export).
- Core engine files live in `core/` (Exporter, Planner, CaptionRenderer, ...). UI in `ui/`. Projects in `project/`.


## Stability patch fix181 (source changes; Build/device test pending)
- Undo/Redo snapshots now include subtitle phrases and original phrases, plus color grade and whole-project FX. This fixes the confirmed SRT-import history gap and makes grade/FX edits undoable.
- Grade and FX edits use the existing 900 ms edit grouping convention to avoid pushing a history entry for every slider tick.
- Removed the hard-coded ARMv8.2 dot-product/FP16 compiler flags from Whisper's CMake configuration so arm64 builds do not require those optional CPU extensions. Runtime performance and device compatibility still require phone testing.
- No claim of Build success: this ZIP has not been compiled or run on a device.

## fix182 — timeline and history reliability

- Added long-press drag-and-drop reordering for clips on the main video lane. The drop target is
  resolved against the edited timeline and the change is committed through the existing EDL edit
  path, so it participates in project autosave and undo/redo.
- Added an index-based EDL reorder operation with bounds and no-op checks.
- Included export settings in undo/redo snapshots and made export-setting changes create a history step.
- Disabled Android automatic app-data backup to avoid unintentionally exporting private project media
  and local transcription/configuration data through device backup.

Build status: source changes prepared; not built in this environment (Android SDK/Gradle unavailable).
Phone gesture behavior and store release signing still require verification.


## fix183 development notes (not yet Build-verified)
- Added direct timeline trim handles for video clips: long-press and drag the left/right edge while preserving a 100 ms minimum clip duration.
- Added long-press horizontal movement for text/image layers and audio clips on their own timeline rows; duration/source in-point are preserved and edits go through the existing undo-history edit paths.
- Retained existing clip reorder behavior when dragging inside a video clip away from its edge.
- Pure Kotlin compilation check passed for `timeline/Edl.kt` with minimal model stubs. Full Android/Compose Build is not available in this environment and remains to be verified by the user's GitHub Actions run.

## fix185
- Added direct timeline edge trimming for text/image layers and music/voice clips.
- Left-edge audio trimming advances the source in-point while preserving the clip end; right-edge trimming shortens the clip.
- Selected layer/audio blocks show edge grips to communicate the trim affordance.
- Changes use the existing edit/history pipeline.


## fix189 — پارت ۶ (تغییرات رابط و کنترل لایه/صدا)
- جایگاه زیرنویس: ۹ پیش‌تنظیم شبکه‌ای (سه در سه).
- لایه‌های متن/تصویر: اندازه‌های سریع ۵۰٪، ۱۰۰٪، ۱۵۰٪ و ۲۰۰٪؛ چرخش ۹۰ درجه؛ بازنشانی جایگاه، اندازه، چرخش و شفافیت.
- صدا: پایان‌دادن کلیپ در محل نشانگر پخش؛ چهار حالت محوشدن (بدون محو، شروع، پایان، هر دو).
- Undo/Redo از نسخه fix188 موجود بود و این تغییرات از مسیر ویرایش لایه/صدا به تاریخچه فعلی متصل‌اند.


## fix191 / fix192
- fix191: fixed the two `ZChip` calls in Editor.kt (trailing lambda went to `trailing: String?`); build confirmed OK by the user.
- fix192 (professional-mission step 1, timeline): layers can be LOCKED (no move/edit/split/reorder/delete, preview gestures off) and
  HIDDEN (kept on the timeline, not drawn in preview or export). Both are saved in project.json (`lock`, `hide`), undoable,
  and shown on the timeline (amber outline for locked, dimmed for hidden). Build and phone test pending.
- Not done yet (next steps): multi-select clips, speed/freeze, crop/transform presets, keyframes, text styles, audio EQ/noise, etc.

- fix193 (timeline, step 2): frame-accurate playhead readout (m:ss:ff from the video's real frame rate) and frame stepping
  (-10 / -1 / +1 / +10 frames) in the Clip tab, so split/trim can be placed exactly. Build and phone test pending.
  Manual test: import a video, open Clip tab, tap "فریم بعد" repeatedly: readout ff increases by 1 and rolls into the next second;
  split at a stepped position; undo/redo still works.

- fix194 (text, step 1): text layers get Bold, Italic, alignment (left/centre/right, RTL-aware), line spacing, letter spacing
  (Latin only; ignored for Persian/Arabic script because it breaks joined letters) and outline colour. Drawn by LayerRenderer, so preview
  and export match; saved in project.json (b, i, al, ls, lh, oc). Build and phone test pending.
  Speed control / freeze frame were NOT started: clips are plain source Ranges and a per-clip speed needs changes in the clip model,
  preview player, audio mixer, captions and layer timing together - to be done only after the current builds are confirmed.

- fix195 (text, step 2): 7 ready text styles and "my styles": the look of a text layer (colours, outline, bold/italic, align, spacing, font)
  can be saved (max 20, stored in app preferences key text_styles) and applied to other texts or deleted. Applying a style is one undo step and
  keeps words, position, size and timing. New file timeline/TextStyles.kt. Build and phone test pending. Text in/out animations not done.

- fix196 (text/layers): entrance and exit animations for text and picture layers (fade, slide from bottom/top/left/right, zoom), length 0.1-1.5 s
  (capped to half of the layer's time). One shared function (timeline/Layers.kt LayerAnim) drives both the preview and the exporter overlay
  (per-frame alpha, offset and scale). Saved in project.json (ai, ao, am). Build and phone test pending: compare preview and exported video.
  Not done: animated subtitles, keyframes.

- fix196 (text/layers): entrance and exit animations for text and picture layers (fade, slide from bottom/top/left/right, zoom), length 0.1-1.5 s
  (capped to half of the layer's time). One shared function (timeline/Layers.kt LayerAnim) drives both the preview and the exporter overlay
  (per-frame alpha, offset and scale). Saved in project.json (ai, ao, am). Build and phone test pending: compare preview and exported video.
  Not done: animated subtitles, keyframes.

- fix197 (keyframes, step 1): keyframes for text and picture layers: position, size and opacity (up to 40 per layer, smooth ease in/out between keys,
  clamped outside the first/last key). Add/update/delete a key at the playhead, jump to previous/next key, clear all; while a layer has keys,
  moving/pinching it or using the sliders writes a keyframe at the playhead (auto-key). Dots on the timeline bar mark keys. Same maths (timeline/Layers.kt LayerKeys)
  in preview and exporter (per-frame overlay position/scale/alpha). Saved in project.json ("keys"). Split moves keys to the right half.
  NOT done: rotation keyframes (export bakes rotation into the bitmap; per-frame rotation needs OverlaySettings rotation support that I could not verify offline),
  keyframes for main-video clips, custom easing curves. Build and phone test pending.

- fix198 (video tools, step 1 — Freeze Frame): Clip tab → «ثابت‌کردن فریم زیر پلی‌هد» with length 1/2/3/5 s. The frame under the playhead
  (main video or an extra video) is read with MediaMetadataRetriever, saved as `extra_<id>.jpg` in the project folder and inserted as a photo clip
  at the playhead (the clip under it is split, so the video continues from the same frame after the still). It reuses the photo-clip path
  (preview, export, undo/redo, save, drag/trim handles), so it can be moved, shortened, duplicated and deleted like any clip.
  Limits: the still is silent; layers/audio clips do not shift when it is inserted (same known limit as other clip edits); freezing a photo clip
  is refused with a message. Speed control (0.25x-4x, ramp) is still NOT started. Build and phone test pending.
  Manual test: open a video, put the playhead mid-clip, tap the button; a grey photo clip appears of the chosen length, preview shows the still,
  video resumes after it; undo removes it; export and compare; reopen the project.

- fix199 (transform, step 1 — output aspect ratio): the old "vertical 9:16" switch became a choice of output frame: 9:16, 16:9, 1:1, 4:5 or original
  (chips in the options panel / export sheet / new-project sheet). New `core/Models.kt` object `Frame` (`aspect`, `crop`, `label`) is the ONE place
  for the frame shape; `ExportPlan.size(st, aspect, srcW, srcH, level)` now takes the aspect (0 = video's own shape); 9:16 still gives 1080x1920 like before.
  Preview frame, export size, size estimate and encoder-capability check all follow it. Saved in project.json options ("ratio");
  old projects (no "ratio") keep their old behaviour (vertical -> 9:16, else original). The clip is centre-cropped to fill the frame.
  NOT done: free (custom) ratio, zoom/pan of the picture inside the frame, free rotation, 90/180/270 rotation. Build and phone test pending.
  Test: pick each ratio, check the preview frame and the exported size; 16:9 from a vertical video crops heavily (expected); reopen an old project.

- fix200 (transform, step 2 — rotate / zoom / pan of the picture): Clip tab section «چرخش و زوم تصویر»: rotate 90° right/left, zoom 1x-4x, pan horizontal/vertical, reset.
  Project-wide for all VIDEO clips (photo clips are not transformed). Stored in `Fx` (rot, vzoom, vpanX, vpanY; project.json fx "rot","vz","vpx","vpy"),
  so it is undoable and saved. Pan is -1..1 of the room the zoom leaves, so the picture never shows empty edges.
  Preview: TextureView is sized unrotated and turned with graphicsLayer (rotation, scale, translation). Export (`Exporter.transformEffects`):
  ScaleAndRotateTransformation (rotation) then Media3 `Crop` (NDC window = zoom + pan) before the fill-the-frame Presentation. Same maths in both.
  NEW Media3 API used without a compiler: `androidx.media3.effect.Crop(left, right, bottom, top)` (NDC) and `ScaleAndRotateTransformation.Builder.setRotationDegrees`
  (counter-clockwise, so the export passes 360 - rot). If the build fails, look at `transformEffects` at the end of core/Exporter.kt first.
  NOT done: free-angle rotation, flip/mirror, per-clip transform (all video clips share it), keyframes for the main video, free aspect ratio. Build and phone test pending.
  Test: rotate 90 in preview, export and compare orientation; zoom 2x and pan to all four corners (preview vs exported file); with 16:9 and 1:1 frames; reopen the project; undo.

- fix201 (video tools, step 2 — clip SPEED): per-clip speed 0.25x-4x (presets in the Clip tab) and speed ramps («شتاب‌گیری», «کندشدن», «اوج سرعت» = the clip is cut into
  4 equal source parts with different speeds, each part editable). `Range` got `speed` (default 1) and `outLen` (= source length / speed); `len` is still the SOURCE length.
  Everything on the edited timeline now uses `outLen`: `EdlMap` (locate / srcToOut), split / trim / duplicate / merge (only equal speeds) / trimBoundary (drag position -> source time),
  `outPhrasesOf`, `CaptionTimeline` (subtitles follow the speed), SRT import, timeline drawing and snap anchors, transitions. Saved in project.json segments as [start, end, speed]
  (old projects load with speed 1). Photo clips cannot have a speed.
  Preview: `PreviewPlayer.setSpeed` (MediaPlayer PlaybackParams, pitch kept; applied when the player starts or while it runs); the playhead maps source time back with / speed.
  Export: clips with a speed are first copied WITHOUT re-encoding into a picture-only MP4 with scaled time stamps (`core/SpeedRemux.kt`, MediaExtractor + MediaMuxer),
  then Transformer trims/encodes it like any clip. The sound is rebuilt by `AudioMixer` (grain overlap-add time stretch, pitch kept; `stretch`); an export with any speed
  always uses the mixed-audio path. Temp files `zircut_speed_*.mp4` are deleted after export / error / cancel.
  Limits (do not report as done): slow motion shows frames longer (no frame blending, 0.25x looks choppy); stretched speech is slightly "warbly" at extreme speeds (no waveform
  matching); speed is per clip, not a smooth curve; layers / music clips keep their place when a clip's length changes (same limit as other clip edits);
  Freeze frames and transforms still work on top. The speed remux has never run on a phone: check hardware decoders with B-frames, rotated videos and 4x.
  Test: set 2x on a clip: timeline shortens, subtitles stay in sync, export and compare length/sound; 0.5x; ramp presets; undo/redo; split/trim a sped clip; reopen the project.

- fix202 (transform, step 3 — mirror): Clip tab «چرخش و زوم تصویر»: chips «قرینه افقی» / «قرینه عمودی» (project-wide for video clips, like rotate/zoom).
  `Fx.flipH/flipV` (project.json fx "fh","fv"; old projects = false), undoable via the same onFx path, included in «بازنشانی».
  Preview: graphicsLayer scaleX/scaleY negative (scale, then rotate). Export (`transformEffects`): one `ScaleAndRotateTransformation.setScale(±1, ±1)` BEFORE the rotation
  (same order). NEW API use not compiled offline: negative scale in `ScaleAndRotateTransformation` (if the build/export rejects it, look at the first lines of `transformEffects`).
  NOT done: free-angle rotation, per-clip transform, free aspect ratio, multi-select clips, audio EQ/noise reduction, chroma key, masks, background removal. Build and phone test pending.
  Test: tap mirror H/V, compare preview and exported file; combine with rotate 90 and zoom/pan; reopen the project; undo/redo.

- fix203 (audio, step 1 — noise reduction): Audio tab → selected music/voice clip → «حذف نویز» (ملایم / متوسط / قوی). New `core/AudioClean.kt`: 80 Hz low-cut + smooth downward expander (noise gate);
  the noise floor is measured from the quietest 15 % of the file itself. The result is a NEW 44.1 kHz stereo WAV `audio_clean_<ts>.wav` in the project folder and the clip points at it
  (`MainViewModel.cleanAudio`), so preview and export play the same sound; the old file stays, so Undo works (the unused-files cleanup removes it later, same `audio_` prefix).
  Streams through a temp `.raw` file (small memory); limit 15 min per clip; one job at a time; temp/failed files are deleted. For speech - not for music.
  NOT done: noise reduction of the video's own sound, equaliser, silence removal on audio clips (silence removal of the video already exists). Build and phone test pending.
  Test: record or add a noisy voice clip, tap each strength, listen (preview) and export; undo returns to the old sound; reopen the project; try a 10-minute file for speed.

fix202 confirmed OK by the user (build + mirror works; negative scale accepted by Media3).

- fix204 (audio, step 2 — equaliser): Audio tab → selected clip → «اکولایزر ۳ باند»: bass shelf 120 Hz, mid bell 1 kHz, treble shelf 6 kHz, each -12..+12 dB, presets (گفتار شفاف, بم قوی, روشن, صفر),
  button «اعمال اکولایزر». New `core/AudioEq.kt` (RBJ biquads, soft limiter above 0.9). Same mechanism as fix203: a NEW wav `audio_fx_*.wav`, the clip points at it, Undo works.
  `MainViewModel.reprocessAudio` is now the shared runner of noise reduction and EQ (cleaned files are also named `audio_fx_*`). Applying twice stacks the effect (sliders reset after apply).
  NOT done: EQ/noise reduction for the video's own sound, live (non-baked) EQ preview, per-band Q. Build and phone test pending.
  Test: add music, bass +7 dB, apply: bass louder in preview and in the exported file; undo; "صفر" + apply keeps sound unchanged; reopen the project.

fix203 confirmed OK by the user (build OK).

- fix205 (timeline, multi-select clips): Clip tab → «انتخاب چند کلیپ»: one numbered chip per clip (timeline order), «همه» / «هیچ‌کدام» / «برو به اولی», then
  copy (each chosen clip duplicated right after itself), delete (at least one clip must stay), and one speed (0.5/1/1.5/2x) for all chosen clips (photo clips are skipped).
  New pure functions `EdlOps.deleteMany / duplicateMany / setSpeedMany` (timeline/Edl.kt); each action is ONE undo step (goes through `applyEdl` -> `vm.setSegments`).
  The selection resets when the number of clips changes. Same known limit as other clip edits: layers/audio clips keep their place when clip lengths change.
  NOT done: selecting clips by tapping them on the timeline itself (needs timeline gesture changes), moving several clips together, applying fx per selected clip. Build and phone test pending.
  Test: 5 clips; select 2 and 4, copy -> 7 clips; select 3, delete; select several, speed 2x -> timeline shortens; undo each; all+delete is disabled; reopen the project.

fix204 confirmed OK by the user (build OK).

- fix206 (transform, step 4 — free-angle rotation): Clip tab → slider «چرخش آزاد» -45..45° (snaps to 0 near the middle), included in «بازنشانی». `Fx.fine` (project.json fx "fr"; old projects = 0).
  The whole video frame is turned about its centre AFTER the 90° rotation / flip / zoom / pan and auto-zoomed by `fineCover(deg, w, h)` (core/Fx.kt) so no empty corners show
  (worst case: the picture is exactly as big as the frame, so it zooms a little more than strictly needed). Captions, layers and effect pictures are NOT rotated (they are drawn on top).
  Preview: the TextureView is wrapped in a Box of the frame size with graphicsLayer rotationZ + scale. Export: `FineRotation` (end of core/Exporter.kt, Media3 `MatrixTransformation`, android.graphics.Matrix in NDC;
  scale to pixels -> postRotate(-deg) -> scale back * zoom), added right after the fill-the-frame Presentation. Photo clips are not rotated.
  NEW API used without a compiler: `androidx.media3.effect.MatrixTransformation.getMatrix(long): android.graphics.Matrix`. If the build fails or the export is rotated the wrong way / sheared, look at `FineRotation` first.
  NOT done: per-clip transform, keyframes for the main video, free aspect ratio. Build and phone test pending.
  Test: rotate +20 and -20: preview vs exported file must turn the same way with filled corners; with 16:9, 9:16, 1:1 frames; combine with 90° rotate, mirror, zoom/pan; undo; reopen the project.

fix205 confirmed OK by the user (build OK).

- fix207 (transform, step 5 — free aspect ratio): the ratio chips (new-project sheet, options panel, export sheet — all use `OptionToggles`) got «آزاد» and a slider «نسبت آزاد» 0.40..2.50 (width / height).
  Stored in options.ratio as "c:1.33" (`Frame.customId / Frame.free`, core/Models.kt); `Frame.aspect` and `Frame.label` understand it, so preview frame, export size (even numbers), size estimate and the
  encoder check follow automatically; old projects unaffected. Same centre-crop-to-fill behaviour as the fixed ratios.
  Limits: no drag-handle crop on the picture (use zoom/pan to frame it); the aspect is one value for the whole project. Build and phone test pending.
  Test: pick «آزاد», move the slider: preview frame changes; export: file size ratio matches; try 0.4 and 2.5; switch back to 9:16; reopen the project.

fix206 / fix207 not yet confirmed by the user when fix208 was started (fix208 is built on top of them).

- fix208 (subtitles, step 1 — short parts, exact timing, undo): Captions tab: (1) «تقسیم به بخش‌های کوتاه»: chips 2-6 words; every subtitle longer than the limit is cut into equal pieces
  (`MainViewModel.splitShort`; words keep their own timing, so sync stays exact). (2) The ACTIVE subtitle has «شروع − / + / پایان − / +» (0.1 s steps; first word start / last word end;
  `nudgePhrase`, drags within 0.9 s = one undo step). (3) Text edit, merge, delete of subtitles are now UNDOABLE (they were not before; the history already stored phrases) and update the undo counter.
  Test: generate subtitles; chip 3: long lines split; undo restores; select a subtitle, shift start/end and watch the preview; edit text then undo; merge, delete + undo; reopen the project.
  NOT done: dragging subtitle edges on the timeline, per-word timing editor, grouped style editing beyond the global styles (already project-wide). Build and phone test pending.

fix208 and everything before it confirmed OK by the user (build OK).

- fix209 (audio, step 3 — extract the video's sound): Audio tab → «استخراج صدای ویدیو». `MainViewModel.extractVideoAudio` runs the same `AudioMixer.mix` the exporter uses (no clips, main gain 1) over the EDITED timeline
  (cuts, order, speed, extra videos) into `audio_ext_<ts>.wav`, adds it as a music-kind audio clip at 0 and MUTES the original sound, in ONE undo step. So the video's own sound can now get
  noise reduction (fix203), equaliser (fix204), volume, fades, splitting, like any audio clip. Limit 15 min; one background job at a time (shared flag with noise reduction / EQ).
  Limits: the clip does NOT follow later picture cuts / speed changes (extract again after finishing the picture edit; delete the old clip and un-mute if needed); a ~10 MB/min WAV is kept in the project
  folder (the unused-files cleanup removes it when unused). Build and phone test pending.
  Test: cut/speed a video, tap the button: a clip «صدای ویدیو ۱» appears and the sound is the same as before; noise reduction on it; export: sound in sync, no doubled sound; undo restores the original sound.

fix209 confirmed OK by the user (build OK).

- fix210 (AI/video tools — colour key for PICTURE layers): Layer tab, picture layer: «حذف پس‌زمینه‌ی رنگی (کروما کی)»: off / green / blue / white / black + sensitivity slider.
  `Layer.keyMode/keyTol` (project.json "km","kt"); `ChromaKey.apply` (core/LayerRenderer.kt) edits the layer bitmap itself: green/blue by YCbCr chroma distance with soft edge and spill removal,
  white/black by brightness. Because the preview and the exporter both draw the layer from the same bitmap (`LayerRenderer.base`, cache key includes the key settings), the result is identical in both.
  Limits (do not report as done): it keys PICTURE layers only, NOT the main video or extra video clips (video chroma key needs a per-frame GPU shader + preview path; not built);
  no colour-picker (fixed key colours); no background removal of ordinary photos (needs an AI model - not included). Build and phone test pending.
  Test: add a photo with a green (or white) background as a layer; choose the key; raise sensitivity until the background disappears; check the preview and the exported video; undo; reopen the project.

fix210 not yet confirmed when fix211 was started (fix211 is built on top of it).

- fix211 (audio, step 4 — silence removal on audio clips): Audio tab → selected clip → «حذف سکوت» (فقط سکوت کامل / متوسط / شدید). `AudioOps.removeSilence` (timeline/Audio.kt) reads the clip's waveform peaks
  (50 per second, already built for the timeline), finds quiet stretches ≥ 0.5 s (peak byte < 25 / 36 / 51), leaves 0.12 s of quiet on each side of a cut and turns the loud parts into consecutive clips starting where
  the clip started (gaps closed; the first keeps the fade-in, the last the fade-out). One undo step (`MainViewModel.removeAudioSilence`). No audio is re-encoded: the pieces point into the same file.
  Limits: other clips are not moved; do not use on sound that must stay in sync with the picture (e.g. the extracted video sound); threshold is a fixed level, not adaptive. Build and phone test pending.
  Test: record a voice-over with pauses, tap each level: clip count rises, total length falls, playback has no clicks at the joins; undo; reopen the project; export sound.

fix210 confirmed OK by the user (build OK); fix211 was started in CI by the user.

- fix212 (privacy + tests + docs): (1) PRIVACY FIX: the default Config.WORKER_URL meant "automatic subtitles" sent the video's audio to an external server with no question when no local model was installed.
  Now online recognition needs a one-time consent (`UiState.cloudConsent`, pref `cloud_consent`): a dialog «ارسال صدا به سرور؟» (ZirCutApp.kt); «نه» goes to the model download screen (on-phone recognition);
  `cloudReady()` is false without consent, so no code path can upload; revoke button on the model screen (`revokeCloud`). (2) `app/src/test/.../LogicTest.kt` + `testImplementation("junit:junit:4.13.2")`: pure unit tests
  (multi-clip ops, silence removal, free ratio, rotation cover zoom) — written WITHOUT being run. (3) docs/TESTING.md (repeatable manual scenarios, section 13 of the mission) and docs/RELEASE.md (store-readiness: what is
  done and what is not: debug signing, minify off, no device profiling).
  Test: fresh install → generate subtitles with no model: the dialog appears; «اجازه» → online recognition; «نه» → model download screen; after consent no dialog again; revoke → dialog again next time.

fix211 confirmed OK by the user (build OK); fix212 not yet confirmed when fix213 was started.

- fix213 (templates): Filter tab → «قالب‌های آماده»: 5 templates (ریلز و شورتس, یوتیوب, سینمایی, پست مربعی, پست ۴:۵). New `timeline/Templates.kt` (data only). `MainViewModel.applyTemplate` sets the frame ratio
  (incl. free "c:2.35" for the cinema one), the subtitle style, the colour look (+strength; manual sliders are kept) and adds a title text layer at the start (position, entrance animation and one of the ready text styles).
  Look + title are ONE undo step; ratio and subtitle style are project settings outside the undo history (as before). Nothing is hidden: every part can be changed afterwards.
  Limits: templates are recipes, not clip-by-clip cuts/transitions/music; there is no user-made template saving yet. Build and phone test pending.
  Test: open a video, tap each template: frame shape, subtitle style, look and a title appear; edit the title; undo removes title+look; export looks like the preview.

fix212 confirmed OK by the user (build OK); fix213 was started in CI by the user.

- fix214 (templates, step 2 — your own templates): Filter tab → «قالب‌های من» → «ذخیره‌ی تنظیمات فعلی»: saves frame shape, subtitle style, look (+strength) and, if a TEXT layer is selected, that layer as the title
  (text, position, size, entrance animation, colours, bold/italic, outline, font). Up to 12, stored in app preferences as JSON (`my_templates`), listed as chips (tap = apply, ✕ = delete). Applying is the same
  one-step recipe as fix213 (`applyRecipe`: look + title are one undo step; ratio and subtitle style are outside undo). Templates are NOT stored inside projects and not shared between phones.
  Test: set 9:16 + a style + a look, select a text layer, save; open another project and apply it; delete; restart the app: the templates are still there.

fix213 and fix214 confirmed OK by the user (build OK).

- fix215 (Undo / Redo covers project look settings): the frame shape / options (`Options`), subtitle style, subtitle font and subtitle position (`CapLayout`) were NOT in the undo history (a gap against the mission:
  "all important changes must be undoable"). Now `Snap` also stores options, styleId, fontId and layout; `setStyle / setFont / setLayout / setOptions` record history (`keyedHistory`: a slider drag or a run of quick taps = one step; only
  inside an open project, so the pre-project screens do not fill the undo list); undo/redo restore them and re-save style/font/position in the preferences (`persistCaptionLook`).
  Templates (fix213/214) are now ONE complete undo step (frame shape + style + look + title).
  Not covered by undo (by design): app-wide settings such as the recognition model/language, cloud consent, saved text styles and templates.
  Test: change the ratio, subtitle style, font and position one by one → Undo steps back in reverse order, Redo returns; drag the free-ratio slider → one undo step; apply a template → one undo returns everything;
  reopen the project: values equal the last state.

fix215 built OK (user's build started).

- fix216 (audio, step 5 — auto-duck): Audio tab → switch «کم‌کردن خودکار موسیقی هنگام ویس»: MUSIC clips are turned down to 30 % while a voice-over (recorded) clip plays; the level eases down 0.35 s before the voice starts and back up
  0.35 s after it ends. `AudioOps.duckFactor` (timeline/Audio.kt) is used by BOTH the preview (`AudioPreview.update(..., duck)`) and the export mixer (`AudioMixer.mix(..., duck)`), so they sound the same.
  Saved in the project ("duck", `ProjectData.duckMusic`), part of Undo (Snap.duck), part of autosave key.
  Limits: the trigger is only «voice-over» clips (clips made with the record button), NOT the video's own speech (that would need speech detection on the main sound — not built); the level (30 %) and ramp are fixed.
  The extracted video sound (fix209) is a music-kind clip, so it is ducked too if the switch is on. Build and phone test pending.
  Test: music + a recorded voice-over, switch on: the music dips under the voice in preview; export: same dip, no clicks; switch off: no dip; undo the switch; reopen the project.

fix216 confirmed OK by the user (build OK).

- fix217 (timeline, multi-select step 2 — move together): Clip tab → «انتخاب چند کلیپ» → «جابه‌جایی انتخاب‌شده‌ها»: به اول / یکی جلوتر / یکی عقب‌تر / به آخر. `EdlOps.moveMany` (timeline/Edl.kt): the chosen clips keep their
  order among themselves; the selection follows them to their new numbers; one undo step per move; the playhead goes to the first moved clip. Unit test added to LogicTest (not run).
  Same limit as other clip edits: layers and audio clips keep their timeline position. Build and phone test pending.
  Test: 5 clips, select 2 and 4: «به آخر» → order 1,3,5,2,4; «یکی جلوتر» etc.; undo; export order = preview order.

fix217 confirmed by the user's upload (this ZIP); fix218 built on top of it.

- fix218 (audio, step 6 — auto-duck also under the VIDEO's own speech): the existing Audio-tab switch (now «کم‌کردن خودکار موسیقی هنگام صحبت») turns MUSIC clips down to 30 % not only under recorded
  voice-overs but also while the video's own speech is heard. "Speech" = the subtitle phrases mapped onto the edited timeline (`outPhrasesOf`); new `timeline/Audio.kt` class `SpeechMap` (sorted intervals, phrases
  closer than 0.6 s are joined so the music does not pump between words; binary-search distance, cheap per sample) and `AudioOps.duckFactor(..., speech)`. Same function in the preview (`AudioPreview.update(..., speech)`,
  Editor) and the export mixer (`AudioMixer.mix(..., speech)`), so they sound the same. No new saved state: the same «duck» switch, still undoable and saved. Speech is ignored when the video sound is muted / at 0 %.
  The extracted video sound clip («صدای ویدیو N») is never ducked by speech (it is the speech). Unit tests added (not run).
  Limits: speech = subtitle phrases (no subtitles → only voice-overs duck); the level (30 %) and ramp are fixed; speech of extra videos / photo clips is not known (no subtitles for them); someone who had the switch ON in
  a project now also gets the speech ducking. Build and phone test pending.
  Test: video with subtitles + music, switch on: music dips under speech in preview; export: same dip; mute the video sound → no dip; remove subtitles → no dip; extract the video sound → no pumping; undo the switch.

fix218 confirmed OK by the user (build OK); fix219 built on top of it.

- fix219 (timeline, ripple editing): Clip tab → switch «ریپل (جابه‌جایی همراه برش)» (app setting, pref `ripple`, off by default, not stored in projects). When on, every clip change that makes the total
  length different (delete, trim, drag-trim, split+delete, speed, copy, insert, freeze, scene-detect splits) also moves layers and audio clips that lie after the change: new `timeline/Ripple.kt`
  (`diff` = common start/end of the old and new clip lists -> changed stretch; `shift`; `layers`; `audio`), applied inside `MainViewModel.setSegments`, so it is the SAME undo step (history snapshot already holds layers + audio).
  Rules: items after the changed stretch move by the length difference; items inside a deleted stretch are pulled to its start; a layer that spans the change is stretched/shortened (min 100 ms); an audio clip only moves
  its start (a clip playing across the change keeps playing); LOCKED layers never move; reorder keeps the total, so nothing moves. Keyframes are relative to the layer start, so they follow.
  Limits: subtitles already follow the clips (source time); the extracted video sound does not (extract again); "reset to automatic cut" (setSegments(null)) does not ripple. Unit tests added (not run). Build and phone test pending.
  Test: 4 clips + a text layer and music after clip 3; switch on; delete clip 2 → layer and music move earlier by its length; undo → back; speed 2x on a clip → later items move; lock the layer → stays; switch off → nothing moves.

fix219 confirmed OK by the user (build OK); fix220 built on top of it.

- fix220 (keyframes, step 2 — main VIDEO picture): Clip tab → section «کی‌فریم زوم و جابه‌جایی تصویر»: «ثبت اینجا / به‌روزرسانی», previous / next key, delete this key, clear all (up to 40). `Fx.vkeys` (`VideoKey(tMs, zoom, panX, panY)`,
  time on the EDITED timeline; project.json fx "vk"), maths in `core/Fx.kt` (`VideoKeys.pose / upsert / remove / centre`; smooth ease in/out, clamped before the first and after the last key). With keys the three sliders show the look at the playhead
  and a slider change writes a key there. Reset clears the keys. Undo via the existing Fx history. Project-wide for all VIDEO clips (like rotate/zoom/pan); photos are not transformed.
  Preview: Editor reads `VideoKeys.pose(fx, posMs)` for the graphicsLayer scale/translation. Export: with keys `transformEffects` adds `KeyedZoomPan` (Media3 `MatrixTransformation`, translate window centre to the middle then scale) instead of the static
  `Crop`; same window maths (`VideoKeys.centre`) as the static crop; frame times relative/absolute detection like the layer overlays. NEW API use not compiled offline: time-dependent MatrixTransformation before the Presentation.
  If the export is wrong (zoom off-centre, jumps at cuts), look at `KeyedZoomPan` at the end of core/Exporter.kt first.
  Limits (do not report as done): keys are tied to timeline time and do NOT move when clips are cut/moved (ripple does not shift them); no rotation / opacity keys for the video; no per-clip keys (one animation for the whole project); no key dots on the timeline; ease is fixed.
  Build and phone test pending.
  Test: open a video, playhead at 0 s: set zoom 1x «ثبت اینجا»; go to 3 s, zoom 2.5x and pan: a second key appears; play: smooth zoom in preview; export and compare (same motion, centred); add a third key; delete one; undo/redo; reopen the project; with several clips check there is no jump at the cuts.

fix220 confirmed OK by the user (build OK); fix221 built on top of it.

- fix221 (transitions, step 2 — a transition per CUT): Clip tab (top): «ترنزیشن ورود کلیپ N (محل برش قبلی)» for the clip under the playhead: chips «پیش‌فرض پروژه» / بدون / محو / زوم / اسلاید / چرخش / بلور / گلیچ and a length slider for that cut.
  `Range` got `tr` (-1 = project's, else `TrKind.ordinal`, 0 = none) and `trMs` (0 = project's); they describe the cut BEFORE that clip. Saved in project.json segments as [start, end, speed, tr, trMs] (old projects load as -1 / 0).
  `Cuts.enter / exit` (core/GpuFx.kt) resolve the kind + length of each cut (own setting, else project `Fx.trKind / trMs`); `TrMath.at(..., enter, exit)` now returns the kind too, so a clip can LEAVE with the next cut's transition
  and ARRIVE with its own. Same function in the preview (Editor graphicsLayer / blur) and in the export (`ClipTiming.enter/exit` -> shader kind per frame). The project-wide chips in the Filter tab still work as the default.
  Split / ramp / insert keep the transition on the first piece only (no transition inside a clip). Undo: it is a clip edit (one step).
  Limits (do not report as done): still the "leave + arrive" style, not a real cross-dissolve of two overlapping clips; reordering clips moves the setting with the clip (it then describes the cut before it in its new place);
  per-clip EFFECTS (blur, grade, ...) are NOT done - only transitions. Build and phone test pending.
  Test: 3 clips, project transition «بدون»; clip 2: set «زوم» → only that cut animates in preview and export; clip 3: «اسلاید» with a longer time; set project «محو» and clip 2 «بدون» → that cut has none; split a clip with a transition: the new inner cut has none;
  undo/redo; reopen the project; export order and timing equal the preview.

fix221 confirmed OK by the user (build OK); fix222 built on top of it.

- fix222 (colour, step — LUT): Filter tab → «LUT رنگ (فایل .cube)»: pick a 3D .cube file (any app / Downloads), strength slider 0-100 %, remove. New `core/CubeLut.kt` (parser: LUT_3D_SIZE 2..65, DOMAIN_MIN/MAX, TITLE; 1D LUTs and broken files are refused with a message),
  `Fx.lutFile` (`lut_<time>.cube` copied into the project folder, project.json fx "lut"/"lutA", name checked against path tricks) and `Fx.lutAmt`. Undo through the normal Fx history; «برگرداندن همه» clears the LUT; the unused-files cleanup keeps the LUT in use.
  Export: the EXACT table (blended with "no change" by the strength) goes to Media3 `SingleColorLut.createFromCube` right after the colour grade matrix, on every clip; a missing/broken file just skips the LUT (export continues).
  Preview: a LUT is not a matrix, so the preview uses a least-squares 3x3 matrix + offset fitted to the LUT (`CubeLut.fit`, composed after the grade matrix) - similar for typical looks (tint, warmth, contrast), NOT exact for strong non-linear looks. The panel says so.
  NEW API use not compiled offline: `SingleColorLut.createFromCube(int[][][])` (cube[r][g][b] = ARGB). If the build fails look at the lutCube lines in core/Exporter.kt; if the look in the export is wrong (colours swapped), the r/g/b index order of `CubeLut.cube` is the first suspect.
  NOT done: per-colour HSL (needs a new GPU shader + preview; deliberately not started without a compiler), LUT per clip, LUT thumbnails/previews of looks, .png LUTs. Unit tests added (not run). Build and phone test pending.
  Test: put a .cube on the phone (Downloads), Filter tab → pick it: preview changes; slider to 0 = original; export: look matches the LUT (compare with the same LUT in another app); broken/1D file → readable message;
  undo; remove; reopen the project; delete the file by hand and export: export still works without it.

fix222 not yet confirmed when fix223 was started (fix223 is built on top of it).

- fix223 (text, step 3 — text shadow): Layer tab → text layer → «سایه»: خودکار (the old look, unchanged) / بدون / ملایم / قوی / درخشش (glow in the text colour). `Layer.shadowMode`
  (project.json "sd"; old projects = 0 = exactly the old look), part of text styles (ready and "my styles", key "sd") and of the bitmap cache key. Drawn by `LayerRenderer.text` (Canvas `setShadowLayer`),
  so preview and export are identical; with an outline the shadow is cast by the outline shape. Undo through the normal layer edit path. Unit test added (not run).
  Limits: one shadow colour per mode (black, or the text colour for glow); no X/Y offset sliders; not for subtitles (their styles already have shadow/glow). Build and phone test pending.
  Test: add a text, tap each shadow chip in preview; export and compare; «خودکار» looks as before; save as "my style" and apply to another text; undo; reopen the project.

fix223 not yet confirmed when fix224 was started (fix224 is built on top of it). Decision: release signing is left for the very end.

- fix224 (subtitles, step 2 — animation choice): Position tab (جایگاه) → «انیمیشن زیرنویس»: طبق استایل (the old behaviour, unchanged) / بدون / محو / پاپ / اسلاید از پایین / زوم / پرش کلمه (the active word makes the subtitle bounce a little each time the word changes).
  `CapLayout.anim` (project.json layout "an", app pref "lan"; old projects = 0), so it is saved, undoable (it goes through `setLayout` / the layout history) and part of the project. ONE function `CapAnim.pose` (core/Models.kt) returns scale / opacity / downward offset;
  `CaptionTimeline.State` got `wordMs` (time since the active word started). Preview (Editor canvas) and export (`CaptionOverlay.getOverlaySettings`) both call it, so they match. Unit test added (not run).
  Limits: animation is on the whole subtitle (not per letter); only entrance + word bounce, no exit animation; the choice is project-wide (all subtitles). Build and phone test pending.
  Test: generate subtitles, Position tab, tap each chip and play: the first frames of each subtitle animate; «پرش کلمه» bounces on each word; export and compare with the preview; undo; reopen the project; «طبق استایل» looks as before.

fix224 not yet confirmed when fix225 was started (fix225 is built on top of it).

- fix225 (effects, per-CLIP effects): Clip tab → section «افکت همین کلیپ N» for the clip under the playhead: brightness, contrast, saturation, warmth, blur, sharpen + «پاک‌کردن افکت این کلیپ».
  New `core/Models.kt` `ClipLook` stored in `Range.look` (default = nothing), so it follows the clip through split / duplicate / reorder / speed / multi-select / undo; saved in project.json segments as extra numbers after
  [start, end, speed, tr, trMs] (only when a clip has an effect; old projects load without). Merge of neighbouring clips is refused when their looks differ; ramp pieces inherit the look.
  Export (Exporter): after the project grade and LUT a second `GradeEffect` (the clip's colour matrix); blur / sharpen of the clip are ADDED to the project's values for that clip (`p.fx.copy(...)` into the same `GpuFxEffect`). With speed the
  look travels through the temporary clip copies (`lr.copy` instead of rebuilding the Range). Preview: `applyGrade(v, grade, lut, clipLook)` composes the clip matrix after the grade/LUT matrix (same order); blur adds the clip's blur (Android 12+).
  A slider drag on a clip effect is ONE undo step (`setSegments` detects look-only changes -> `keyedHistory("cliplook")`).
  Limits (do not report as done): sharpen is export-only (not in the preview, like the project-wide sharpen); photo clips are not graded in the preview (as with the project grade) but are in the export; no vignette/grain/glow per clip (those stay project-wide);
  no copy of a look to other clips yet. Unit test added (not run). Build and phone test pending.
  Test: 3 clips; on clip 2 set saturation 0 → only that clip turns grey in preview and export; blur on clip 3; split clip 2 → both halves keep the look; undo (a drag = one step); project filter on top still works; reopen the project; export with speed 2x on a graded clip.

fix225 not yet confirmed when fix226 was started (fix226 is built on top of it).

- fix226 (effects, per-clip step 2): in the same Clip-tab section: ready looks for the clip (زنده، گرم، سرد، سیاه‌وسفید، سینمایی، قدیمی، محو، دراماتیک، ملایم — the project looks mapped to brightness/contrast/saturation/warmth; tint and fade of those looks are not part of a clip look, so they are close but not identical to the Filter-tab looks),
  «اعمال روی همه‌ی کلیپ‌ها» and «کپی به کلیپ بعدی» (copies this clip's effects incl. blur/sharpen). All go through the same clip-edit path as fix225 (one undo step each). Build and phone test pending.
  Test: tap a look on clip 2 → only clip 2 changes; «اعمال روی همه» → every clip gets it; «کپی به کلیپ بعدی»; undo each; export compares with the preview.

fix223-226 confirmed OK by the user (builds OK); fix227 is built on top of them.

- fix227 (subtitles, step 3 — timeline edge drag + per-word timing): (1) Timeline: LONG-PRESS near the start or end of a subtitle block (about 16 dp from the edge, subtitle lane) and drag: the edge follows the finger and is committed on release
  (`MainViewModel.trimPhraseAtOut` converts the edited-timeline time back to source time of the clip the block is on, speed included, then reuses `nudgePhrase`, so it is ONE undo step and keeps the 30 ms minimum rules). The active subtitle shows grips on both edges.
  Only subtitles over main-video clips can be dragged; a drag does not cross into another clip (clamped to the clip). (2) Captions tab, active subtitle: «زمان هر کلمه» - chips for every word (tap = playhead goes to that word) and شروع/پایان ∓50 ms
  (`MainViewModel.nudgeWord`: start stays after the previous word's start +30 ms, end before the next word's end -30 ms; a run of taps within 0.9 s = one undo step). The highlight reaches a word at its start, so moving the start changes exactly when the word lights up.
  Limits: no dragging of single words on the timeline; no waveform under the subtitle lane to align by sound; one subtitle edge at a time. Build and phone test pending.
  Test: generate subtitles; long-press the right edge of a subtitle block on the timeline and drag: the block's end moves, preview keeps showing the subtitle until the new end; same for the left edge; undo; with a 2x clip; select a subtitle, tap a word chip, move its start +50 ms a few times: the highlight comes later in the preview; export and compare; reopen the project.



fix227 built on top of fix226 (user's build OK for fix223-226; fix227 build not yet confirmed when fix228 was started).

- fix228 (AI/video tools — CHROMA KEY for video clips, no AI): new tab «کروما» (black-and-white panel `ui/ChromaKeyPanel.kt`) for the clip under the playhead. Per clip, stored in `ClipLook.key` (`KeyLook`, `core/ChromaKey.kt`; project.json segments indices 11..18,
  old projects load without a key): on/off, key colour (EYEDROPPER: the source frame of the clip is read with MediaMetadataRetriever (`MainViewModel.keyFrame`), tap/drag picks the average of a 5x5 patch), tolerance, edge softness, spill suppression,
  background = transparent (checkerboard in the preview, black in the export) / solid colour / picture (`keybg_<ts>.jpg|png` in the project folder, `MainViewModel.importKeyBackground`; kept by the cleanup).
  Algorithm (`ChromaKeyMath`, ONE formula in Kotlin + the GLSL/AGSL snippet): distance of the pixel's Cb/Cr (plus a small luma term) to the key colour -> smoothstep alpha (tolerance = start, softness = width); spill = the part of the pixel's chroma that lies on the key's chroma axis is removed.
  The key colour goes through the same colour matrix (project grade, LUT fit, clip colour) as the picture, so grading does not break the key.
  Export: `ChromaKeyEffect` (Media3 `BaseGlShaderProgram`, 2 textures: video + background picture, cover-fit) is added after the fill-the-frame Presentation / free rotation and before the blur+transition shader and the overlays. Foreground and background are blended in the shader (opaque output).
  Preview: Android 13+ (API 33): AGSL `RuntimeShader` through `RenderEffect` on the TextureView (colour matrix -> key -> blur), the backdrop (checkerboard/colour/picture) is drawn behind the surface (`KeyBackdrop`). Older Android: no live preview on the video (the export still keys); the panel shows a CPU still preview (`KeyStill`) of the chosen frame on every version.
  Limits (do not report as done): NO video as background (needs a second parallel video track; Media3 1.4.1 compositing of two sequences was not verified offline) - only colour or picture; MP4 cannot carry transparency, so «transparent» is black in the file; photo clips cannot be keyed; one key per clip (no multi-colour, no garbage mask);
  blur/transition effects act on the finished composite in the export but on the foreground only in the preview; with a strong LUT the key colour is matched through the LUT's fitted matrix in the preview and export alike (not the exact LUT); it is a classic chroma key - uneven lighting/wrinkles need a higher tolerance.
  NEW APIs used without a compiler: `android.graphics.RuntimeShader`, `RenderEffect.createRuntimeShaderEffect / createChainEffect / createColorFilterEffect`, `TextureView.isOpaque`, Media3 `BaseGlShaderProgram` with two samplers (`setSamplerTexIdUniform(..., 1)`), `GLUtils.texImage2D`.
  If the build fails look at `core/ChromaKey.kt` first (then `ui/ChromaKeyPanel.kt`). If the export colours are wrong: first suspect is the background texture orientation (`1.0 - q.y` in the fragment shader); if the preview shows no key: the AGSL string (`KeyPreviewFx.AGSL`).
  Unit tests added to LogicTest (not run): key alpha for green/dark green/skin/white/red/blue, tolerance, spill, key colour through the grade, split keeps the key.


fix228 (chroma key) was the newest version in the uploaded ZIP; its build was not confirmed when fix229 was started.

- fix229 (keyframes, step 3 — TEXT/PICTURE layers: rotation + interpolation + key time). Gap found against the mission ("rotation, gradual animation, editing keyframes"): layer keyframes only covered x, y, size, opacity and said "no rotation keyframe".
  `Keyframe` (timeline/Layers.kt) now has `rotation` and `ease` (0 smooth, 1 linear, 2 hold = step at the next key; applies to the segment that STARTS at that key). `LayerPose.rotation`; `LayerKeys.pose/withPose/upsert/edited` carry the turn;
  new `LayerKeys.setEase`, `nudge` (move the key at the playhead ±100 ms, refused when it would land on another key), `rotationAnimated`.
  Rotation is mixed as plain numbers (-150 -> +150 turns 300 degrees through 0, as the slider shows), not the short way round.
  Project: keys saved as t,x,y,sc,op,rot,ez. OLD projects: a key without "rot" takes the layer's own rotation (so nothing jumps), without "ez" = smooth.
  Preview (Editor): the layer is turned by the animated pose (`pz.rotation`). Two-finger rotate gesture and the rotation slider / «چرخش ۹۰°» on a layer with keys now create/update a keyframe at the playhead (`POSE_KEYS` in MainViewModel).
  Export (Exporter): a layer whose turn is NOT animated is baked exactly as before. A layer with an animated turn is NOT pre-rotated; `LayerOverlay.getOverlaySettings` calls `OverlaySettings.Builder.setRotationDegrees(-rotation)` per frame (Media3's angle is counter-clockwise).
  Panel (Layer tab): for the key under the playhead: chips نرم / خطی / ثابت (پله‌ای) and «کلید ۱۰۰ms زودتر/دیرتر».
  VERIFIED here: the pure logic (Layers.kt) was compiled with the Kotlin 2.0.21 compiler and 18 checks pass (interpolation, hold, linear, replace keeps ease, nudge limits, clamp, rotationAnimated); all sources pass a Kotlin syntax scan.
  NOT verified (no Android SDK / no network in the build sandbox): the full Gradle build, Compose UI, and `OverlaySettings.setRotationDegrees` in Media3 1.4.1 (the sign of the angle and aspect-correct rotation on non-square frames must be checked on a phone).
  If the build fails: `ui/Editor.kt` (keyframe section of the Layer panel) and `core/Exporter.kt` (`LayerOverlay`) are the only new Android-API spots. If an exported rotating layer looks squashed or turns the wrong way, only the `ob.setRotationDegrees(-p.rotation)` line needs changing.
  Limits: layer keyframes still have one shared key list (no separate tracks per property); no curve editor; the video picture keyframes (fix220) are still zoom+pan only (no rotation / opacity); keys cannot be dragged on the timeline, only nudged.
  Test: text layer 4 s; key at 0 s (rotation 0); go to 2 s, turn 90° -> key made automatically; play: it spins; choose «خطی» on the first key: constant speed; «ثابت»: jumps at the 2 s key; nudge the key 100 ms; undo each; save, reopen; export and compare to the preview; an old project with a keyed layer opens without jumping.


fix229 built on top of fix228; neither build was confirmed when fix230 was started.

- fix230 (keyframes, step 4 — main VIDEO picture: free-angle rotation). `VideoKey` / `VideoPose` (core/Fx.kt) got `rot` (-45..45, the «چرخش آزاد» angle); `VideoKeys.pose` mixes it with the same smooth ease as zoom/pan; `Fx.hasFine` is also true when a key has a turn.
  Without keys the pose is the static `fx.fine` (nothing changes for old projects). Project.kt: key arrays are [t, zoom, panX, panY, rot]; OLD 4-number keys take the project's `fr` angle, so nothing jumps.
  Preview (Editor): the graphicsLayer rotation/cover-zoom uses `vPose.rot`. Panel (Clip tab): «چرخش آزاد» slider shows the animated value and, once keys exist, makes a key like the zoom / pan sliders; «ثبت اینجا» stores zoom + pan + turn together.
  Export (Exporter): with keys the old `FineRotation` is replaced by `KeyedFineRotation` (same matrix, angle taken per frame, same time detection as `KeyedZoomPan`). Without keys the old class is used unchanged.
  VERIFIED here: `VideoKeys`/`Fx` logic compiled with Kotlin 2.0.21 and 7 checks pass; all sources pass a syntax scan. NOT verified: Gradle build, UI, a per-frame MatrixTransformation export on a phone.
  If the build or export fails look at `KeyedFineRotation` (Exporter.kt) first. Limits: NO opacity keyframe for the main video picture (needs an alpha effect that was not verified offline; do not report as done); keys still follow timeline time, not the clip.
  Test: clip 6 s; key at 0 s (rotation 0); at 3 s set «چرخش آزاد» 20° -> key; play: it turns smoothly, no empty corners; export and compare; undo; reopen; an old project with zoom keys + a fixed angle opens without a jump.


fix230 built on top of fix229; fix229/fix230 builds not confirmed when fix231 was started.

- fix231 (keyframes, step 5 — main VIDEO picture: opacity). `VideoKey.op` / `VideoPose.op` (0..1), mixed with the same smooth ease; `Fx.hasFade`. Project.kt: key arrays are [t, zoom, panX, panY, rot, op]; older keys = opacity 1.
  Meaning: the picture fades TOWARDS BLACK (as if laid over a black background), not to transparency (MP4 has none). Preview (Editor): the video surface alpha is multiplied by `vPose.op` over the black frame.
  Export: `KeyedFade` (Exporter.kt) = an `RgbMatrix` effect scaling RGB by the opacity per frame (same time detection as `KeyedZoomPan`), added after the free-angle rotation and before the chroma key / blur. Only added when a key has opacity < 1.
  Panel (Clip tab): slider «شفافیت تصویر (محو به سیاه)»; moving it creates / updates a key at the playhead (like the zoom / pan sliders). Reset and «پاک‌کردن همه‌ی کی‌فریم‌ها» remove it with the other keys.
  VERIFIED: logic compiled with Kotlin 2.0.21 (6 checks); syntax scan of all sources. NOT verified: Gradle build, phone preview/export. If the build fails: `KeyedFade` + its one use in Exporter.kt, then the slider line in Editor.kt.
  Limits: fade to black only (no cross-fade with the next clip - use the transitions); one shared key list for zoom / pan / turn / opacity (a key stores all four); photo clips are not faded (transform skips photos, as before).
  Test: clip 6 s; key at 0 s; at 3 s set the opacity slider to 0 -> picture fades to black between them; play; export and compare; undo; reopen; with a chroma-keyed clip; with speed 2x.


fix231 built on top of fix230; builds of fix229-231 not confirmed when fix232 was started.

- fix232 (timeline + keyframes, part 3 of the remaining list). DONE: (1) `VideoKey.ease` (0 smooth, 1 linear, 2 hold; applies from that key to the next); `VideoKeys.setEase / nudge`; project.json key array is now [t, zoom, panX, panY, rot, op, ease] (older = smooth).
  Clip tab: chips نرم / خطی / ثابت and «کی‌فریم ۱۰۰ms زودتر/دیرتر» for the key under the playhead (refused when it would land on another key).
  (2) Video keys FOLLOW clip edits when the ripple switch is on: `Ripple.videoKeys` (keys after the change move by the length change, keys inside the changed stretch go to its new end, keys landing on the same time collapse into the first); done in `MainViewModel.setSegments`, same undo step as the clip edit.
  Without ripple they still stay on timeline time (as before).
  (3) Timeline: video picture keys are drawn as yellow diamonds on the ruler (`Timeline(videoKeys = ...)`); a TAP on a diamond (ruler band, within 16 dp) jumps the playhead to that key. Other taps are unchanged.
  VERIFIED: Fx/ease/nudge/Ripple.videoKeys logic compiled with Kotlin 2.0.21 and 9 checks pass; syntax scan of all sources. NOT verified: Gradle build, UI, gestures on a phone (new code: Timeline marker drawing `drawPath`, tap branch `return@detectTapGestures`, panel chips).
  NOT DONE from the timeline list: dragging keyframes / subtitle words on the timeline (the existing long-press-drag handler is large and was not touched without a compiler), waveform under the subtitle lane. Direct clip selection by tapping the video lane was added in 0.1.248.
  Test: 2 keys on the video picture; choose «ثابت» on the first: picture jumps at the 2nd; «خطی»; nudge a key; diamonds appear on the ruler, tap one -> playhead jumps; ripple switch on, delete a clip before the keys -> keys move earlier; undo restores them.


fix232 built on top of fix231; builds of fix229-232 not confirmed when fix233 was started.

- fix233 (audio, step 7 — five-band equaliser). New `core/EqMath.kt` (pure maths, no Android classes): `EqBands(bass, lowMid, mid, highMid, treble)`, `EqMath.specs / make / responseDb`. Bands: bass shelf 120 Hz, NEW low-mid bell 350 Hz (Q 1), mid bell 1 kHz, NEW high-mid bell 3.5 kHz (Q 1), treble shelf 6 kHz; each -12..+12 dB.
  `AudioEq.apply(ctx, src, dest, EqBands, cancelled)`; the old 3-number overload stays and calls it. A band with |gain| < 0.05 dB is left out of the filter chain (flat = exact bypass; `MainViewModel.eqAudio` refuses an all-zero setting with a message instead of rewriting the file).
  Audio tab: 5 sliders and presets گفتار شفاف / بم قوی / روشن / حذف صدای تو‌دماغی / صفر. Same mechanism as before: a NEW WAV, one undo step, preview = export.
  VERIFIED here: the filter maths was compiled and checked numerically (+6 dB at 350 Hz measured 6.00 dB; far bands flat; shelves; a 350 Hz sine of 0.25 came out at 0.499; gains clamped); syntax scan of all sources. NOT verified: Gradle build, UI, listening on a phone.
  NOT DONE from the effects/audio list (do not report as done): per-clip vignette / sharpen (those stay project-wide: needs a per-clip GPU shader), sharpen in the PREVIEW, exact LUT in the preview, a smarter (spectral) noise reducer, higher audio bitrate (Media3 1.4.1 encoder fixed at 128 kbit/s).
  Test: audio clip; set 350 Hz +6 and apply: low-mids louder in preview and export; all zero + apply -> message, file unchanged; undo; reopen project.

- fix234 (app language). NEW `i18n/Lang.kt` + `ui/LanguageUi.kt` + `assets/i18n/<code>.tsv`. Languages now: fa (source), en, ar, tr; the first-run picker (before the app) and the Settings chips list ONLY languages that have a pack file, so an untranslated language is never offered. The phone's language is pre-selected when a pack exists, otherwise English. Translation happens at DISPLAY time in `Txt` (ui/Theme.kt) and in two timeline canvas labels; the Persian literals in code are untouched, so logic/stored names keep working and Persian users see the old text. Matching order: exact text -> templates with {n} -> fragments (word-boundary safe). User content (project names, subtitle words, font/custom template names) is shown raw via `Lang.raw()`. Layout direction follows the language (fa/ar RTL, others LTR; timeline stays LTR). Digits/decimal marks become Western outside Persian. NOT done: es/ru/hi/fr/id/pt/de packs (add a `<code>.tsv`, nothing else), Persian-only text drawn in notifications / system share text, translated app name, real-device RTL/LTR visual check, fix234 build not confirmed. `tools`: pack format = `persian text<TAB>translation`, `\n` for newline, `{0}` placeholders must match the key.

- fix235 (offline policy cleanup). REMOVED: Whisper (JNI + cpp + CMake + NDK setting), online recognition (CloudTranscriber, Worker URL, consent dialog, connection test), model download/import (ModelManager, ModelSize, NeedModel stage), the "auto subtitles" button and the speech-language rows, the INTERNET permission. Subtitles now come from SRT import only (manual editing/styling/per-word timing of imported subtitles is unchanged). NEW: `core/SilenceScan.kt` - the video's sound is measured on the phone (20 ms frames, noise floor from the quietest 10%, threshold between floor+6 and floor+18 dB, 160 ms tail, clicks under 80 ms ignored) and `Planner.ranges` cuts the pauses (`UiState.activity`, cached in `activity.txt` per project, started when "حذف سکوت‌ها" is switched on or an old project with it on is opened). New projects start with the silence cut OFF (before, it only applied after transcription). NOT done / known: music ducking still follows subtitle times (no subtitles = no ducking); `Options.language/removeFillers`, `Planner.clean/phrases` and the spelling-fix helpers are now unused but kept for project-file compatibility; the repo's GitHub workflow may still clone whisper.cpp into app/src/main/cpp (harmless, can be deleted); fix235 build NOT confirmed; no model-based AI exists yet (planned: MediaPipe selfie segmenter + object detector bundled in the APK, next step).

- fix236 (offline AI inside the APK, step 1 — subject cut-out). Chroma tab → «روش حذف پس‌زمینه»: «رنگ پرده (کروما)» (old, unchanged) or «هوش مصنوعی (حذف پس‌زمینه سوژه)» (per clip, `KeyLook.ai/aiInvert/aiEdge`, project.json segments indices 19..21; old projects load as colour key).
  Model: Google MediaPipe selfie segmenter (Apache-2.0), bundled in the APK at `assets/ai/selfie_segmenter.tflite`. The file is fetched ONCE at BUILD time by the Gradle task `downloadAiModels` (app/build.gradle.kts, runs before preBuild, skipped when the file already exists);
  the installed app never downloads anything and needs no internet. Library: `com.google.mediapipe:tasks-vision:0.10.14` (arm64 only, like the rest). New `core/AiSegmenter.kt` (`AiSegmenter`, `AiMask`, `AiMaskMath.shape`).
  Export: `ChromaKeyEffect` got an AI mode: per frame the picture is copied to a small (256 px) offscreen texture, read back, segmented on the CPU (~one model run per frame), smoothed over time (0.65 new / 0.35 old) and used as the alpha in the SAME blend shader (colour / picture background, black for «transparent»).
  Preview: still frame inside the panel (same mask shaping + background); NO live preview on the video in AI mode (colour-key live preview is switched off for AI clips so it does not key by colour by mistake).
  Shared model: the same mask will drive the smart mask / masks step. NOT done: subject detection (object detector), motion tracking, live preview, masks other than the person, video as background.
  NOT verified (no Android SDK and no network in the authoring sandbox): the Gradle build, the model URL, MediaPipe API names (`ImageSegmenter`, `confidenceMasks`, `ByteBufferExtractor`), the GL read-back, mask orientation, APK size, speed/heat on a phone.
  If the build fails: `core/AiSegmenter.kt` first, then `ChromaKeyProgram.updateMask` in core/ChromaKey.kt. If the download step fails put `selfie_segmenter.tflite` into app/src/main/assets/ai/ by hand.
  If the person is cut out UPSIDE DOWN or the background is kept instead of the subject: tap «برعکس‌کردن انتخاب» first (mask index guess), then check the flip in `updateMask`.
  Test: clip with a person (any background): Chroma tab → AI: still preview shows the person on a checkerboard; invert; edge slider; pick a background picture; export 10 s: person on the picture, no flicker; try 1080p 60 s and note time/heat; undo; reopen the project; old colour-key projects open unchanged.

- fix237 (look, step 1): NEW launcher icon (black Z with scissors on white; adaptive icon, `drawable-nodpi/ic_launcher_foreground.png` made from the user's picture, white background, monochrome layer = the same picture); in-app logo `ZMark` uses the same picture;
  app name `ZarCut` (values/strings.xml) and «زیرکات» for Persian phones (values-fa); palette moved to neutral dark greys + a stronger blue accent, closer to CapCut (Theme.kt tokens only, no layout change).
  NOT done (do not report as done): the CapCut-style layout (transport row with undo/redo next to play, track rows with «+» icons on the left of the timeline, icon+label tool bar, X / ✓ confirm bar under each tool panel, yellow selection outline) - planned as the next steps, one screen at a time.
  AI step 2 plan (about 60 MB total, nothing bundled until its code exists): tap-to-select object mask (MediaPipe interactive segmenter), object detector for «subject», simple tracking of the mask between frames; models are fetched at BUILD time like `selfie_segmenter.tflite`.

- fix238 (source/APK mismatch fix; no feature changes). ROOT CAUSE: the old repo-root workflow ran `ls fix*.zip` and unpacked the files over each other. (1) It never matched `ZirCut-fix*.zip`, so fix202-222 and fix229-237 were never applied: CI built fix228 (no language packs, which arrived in fix234). (2) Unpacking over a tree never deletes files, so Whisper/CloudTranscriber/ModelManager (removed in fix235) stayed in the build. (3) versionCode/versionName were 1 / 0.1.0 in every snapshot, so no APK could be told apart. FIX: one flat source tree, no ZIP unpacking in the workflow; a "Source check" step fails the build if a .zip is in the repo root or app/src/main/cpp exists; versionCode 238 / versionName 0.1.238; artifact name carries run number + commit. Also: the failure hint "speech model not ready, check internet" (STT leftover) is replaced by an AI-model hint (+ en/ar/tr translations). Unused STT helpers (Planner.clean, AudioDecoder.chunkSpeech, AacEncoder, Options.language/removeFillers) have no UI path and are kept for project-file compatibility. Clean Gradle build NOT run here (no Android SDK/network): build and phone test pending.


## UI refresh — 0.1.240 (source change, build not yet verified)

- Home screen hierarchy refreshed: clearer project creation action, compact continue-editing card, cleaner project library and export list. Existing project actions and callbacks are preserved.
- Editor tool dock spacing and selected-state styling refined; all existing tool tabs and panel routing remain unchanged.
- No editing feature was intentionally removed. Auto subtitles / online transcription remain out of active UI as previously required.
- Build and device verification are still pending; this source snapshot is not represented as a tested APK.

## UI refresh — 0.1.245 (source change, build not yet verified)
- Added dedicated frame-back / frame-forward buttons in the preview transport, separate from previous/next subtitle navigation.
- Frame stepping uses the detected source FPS and the edited-timeline scrub path; it pauses playback before seeking.
- No export or clip-speed behavior intentionally changed.
- Build and phone verification are pending.

## 0.1.250 — first independent video PIP layer
- Added a project-persistent `VideoOverlay` model, with backward-compatible JSON loading for projects that predate PIP.
- Added a separate preview player and Media3 multi-sequence composition export for video overlays.
- Added MP4 PIP import, start/duration/position/size controls, hide/remove and undo/redo state.
- This first implementation supports one simultaneous PIP layer; its audio is muted. Masks, keyframes, advanced transforms, multiple simultaneous PIP tracks and per-overlay audio remain future work.
- Build and real-device checks are required; do not mark this version release-verified until both preview and exported PIP are tested.
- Follow-up source hardening: reject persisted/imported PIP ranges longer than the source file; regression test added. Android build and device playback/export remain unverified.
