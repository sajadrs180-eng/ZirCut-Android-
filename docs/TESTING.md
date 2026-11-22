# ZirCut — repeatable manual tests (fix212)

Run after every build, in this order. Each line: do → expect. "Undo" = the undo button once per step.
Automated: `./gradlew testDebugUnitTest` (app/src/test/.../LogicTest.kt — pure logic: multi-clip ops, silence removal, free ratio, rotation zoom; written without being run).

## 1. Import
- Open a 30 s video from the gallery → editor opens, preview plays, no crash.
- Open a broken / zero-byte / renamed .txt as video → a readable error, app stays alive.
- Open a 4K video on a mid-range phone → loads, preview plays (may be slower), no out-of-memory.

## 2. Several clips + multi-select
- Split at 3 clips, tap numbers 1 and 3 in «انتخاب چند کلیپ»: copy → 5 clips; delete → clips removed; "all" + delete disabled.
- Select 2 clips, speed 2× → timeline gets shorter; Undo → back.

## 3. Layers
- Add 2 texts + 1 picture; lock one, hide one: locked cannot be moved; hidden is not in preview or export.
- Keyframes on a text: move at 1 s and 3 s → smooth movement in preview and in the exported file.
- Picture with white/green background: colour key removes it in preview and export.

## 4. Undo / Redo
- After each of: split, delete clip, speed, text edit, subtitle edit/merge/delete, audio volume, noise reduction, EQ, extraction, silence removal → one Undo goes back exactly one step, Redo returns.

## 5. Save and restore
- Edit, press Home, kill the app from recents, reopen → the project is offered/restored with clips, layers, audio, subtitles, rotation/mirror/free ratio.
- Rename, copy, delete a project from the list; the copy opens independently.

## 6. Text and subtitles
- Generate subtitles (online needs the consent dialog; "no" leads to the model download screen). Persian text shows correctly RTL.
- Split into 3-word parts; shift one subtitle's start/end by 0.1 s and watch the preview.
- Import / export SRT round trip.
- Long-press a subtitle block's edge on the timeline and drag: edge moves, one Undo returns it. Active subtitle: pick a word chip, move its start ±50 ms: the highlight changes when it reaches that word.

## 7. Audio
- Add music + record a voice-over; volume, fades, mute; noise reduction on a noisy voice; EQ; silence removal; extract the video's sound (original becomes muted; no doubled sound in the export).

## 8. Effects and transitions
- Brightness/contrast/saturation/temperature/tint/sharpen/blur/vignette in preview vs export; each transition between two clips in preview vs export.

## 9. Export
- 720p, 1080p, (4K if supported) at 24/30 fps and different bitrates; progress moves; estimated size is in the right range.
- Cancel at 50 % → no partial file in the gallery; start another export right away.
- Low storage → a readable error, no partial file.
- Check sound/picture sync at the start and end of the exported file (clap test).

## 10. Corrupt / edge
- Delete a project's audio file by hand (file manager) → reopening shows an error for that clip, not a crash.
- Rotate the phone, switch apps during export → export continues or fails with a message.

## 11. Chroma key (green screen) — fix228
1. Import a video shot on a green (or blue) screen. Tab «کروما». The frame of the clip under the playhead appears; tap a flat part of the screen: a ring marks the spot, the swatch shows the colour, the key switches on.
2. «نتیجه روی همین فریم» shows the subject over a checkerboard. Move tolerance: more or less of the screen goes; edge softness: the edge gets softer; spill: the green cast on hair/skin edges goes.
3. Android 13+: the main preview above shows the same result live while you play and scrub (checkerboard behind the subject). Android 12 or older: only the still preview in the panel (the note says so).
4. Background: «رنگ ساده» + a colour; «عکس» + pick a picture: it fills the frame behind the subject (cover-fit) in the preview and in the export.
5. Export: the keyed clip must look like the preview. «شفاف» exports as black (MP4 has no transparency). Compare edges, check sound/picture sync is unchanged.
6. Undo/redo (a slider drag = one step); split the clip: both halves keep the key; reopen the project: the key and background are still there; remove the key from the clip.
7. Mixed: key on clip 2 only, project grade/LUT on: the key still works (the key colour goes through the same grade); a photo clip shows the note «only video clips».
