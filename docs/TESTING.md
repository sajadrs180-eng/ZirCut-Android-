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

## 12. Timeline snapping and precision trimming — 0.1.246
1. Add a video with 2-3 clips, a text layer and a music clip. Magnet button (next to zoom) is blue = on. Long-press near the right edge of the music clip and drag: a clip-coloured frame follows, a pill shows the time and length.
2. Drag the edge near the playhead: it catches (small vibration), a yellow line shows, the pill says «چسبیده به پلی‌هد». Keep the finger moving slightly: the edge stays stuck, no flicker. Move away: it lets go smoothly.
3. Repeat toward: start of the project, end of the project, a video clip joint, a layer edge, another audio clip edge. Each shows its own label.
4. Move a whole layer / audio clip (long-press the middle): either of its edges snaps; it cannot leave 0..project end.
5. Trim an audio clip to the shortest possible: it stops at the minimum length. Trim its start: the sound stays in place (play it). Try to lengthen the end past the file's length: it stops at the file end.
6. Zoom in a lot and out a lot: the catch distance feels the same on screen. Scroll and zoom stay smooth.
7. Turn the magnet off: no catching, no yellow line; the pill and limits still work. Turn off, close and reopen the app: it stays off.
8. Undo/redo after each drag: exactly one step each; a drag that returns to the start adds none. Quick second drag of the same clip = a second step.
9. Layer with keyframes: trim its start earlier/later; animation timing on the timeline stays the same. A locked layer does not move or trim.
10. Open an older project: nothing changes in it; save, reopen: identical.


## 0.1.253 — video overlay masks
- [ ] Load a project created before 0.1.253; all video overlays should remain unmasked.
- [ ] Set each overlay mask to none, circle, and rounded rectangle; confirm the preview shape.
- [ ] Save and reopen the project; confirm each overlay retains its mask.
- [ ] Export a project with a masked overlay over a visible background; confirm transparent pixels reveal the lower track.
- [ ] Compare preview and exported frames for mask geometry and edge placement.
- [ ] Test portrait and landscape source videos, rotation, opacity, keyframes, and multiple simultaneous overlays.
- [ ] Check export speed, memory use, and playback smoothness on a real device.
