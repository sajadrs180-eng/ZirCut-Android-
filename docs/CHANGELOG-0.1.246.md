# ZirCut 0.1.246

## Precision timeline: snapping and exact trimming
- New magnet button beside the timeline zoom buttons turns snapping on or off. The choice is an app setting (saved with the other preferences), not part of the project file.
- While dragging or trimming, an edge sticks to: the playhead, the start and the end of the project, both edges of every video clip, and both edges of other layers and audio clips (the dragged item itself is ignored).
- Snap distance is 10 dp on screen, so it is converted to time for the current zoom: zoomed in the magnet is tight, zoomed out it is wider (limited to 25-400 ms). Once stuck, the edge stays on the landmark until the finger is 1.6 windows away, so it does not flicker.
- Works for: trimming video clips, trimming layers, trimming audio, moving layers, moving audio. Subtitle edges and clip reordering are unchanged.
- Feedback while dragging: a yellow vertical line at the landmark, a small haptic tick when the edge catches, and a pill above the timeline with the exact time (m:ss.cc), the clip length and what it snapped to.
- Audio and layer drags now show the selected range clearly (tinted block, strong outline, grips; the dragged grip is brighter). Video trims dim the part that will be cut away.

## Precision audio trimming
- The edge keeps the distance between the finger and the edge at the moment of grabbing, so the clip does not jump when a drag begins.
- A long press on the middle of a clip moves it; near its edges it trims it. The edge zone shrinks on very short clips so they can still be moved, and a trim never turns into a move by accident.
- Limits come from one place (`TrimOps`): minimum length (`AudioClip.MIN_MS` / `Layer.MIN_MS`), not before the start of the audio file, not past its end, and not outside the project. The drag preview and the saved result use the same functions, so they always agree.
- Start trims move the in-point of the file: the sound stays where it was while the edge moves, and the waveform is drawn from that in-point.
- Layer start trims keep keyframes at the same timeline time (earlier) or keep the pose at the new start (later).
- Locked layers do not move or trim.

## Undo / redo
- Every finished drag is exactly one undo step, even two quick drags of the same item (`editAudio` / `editLayer` got a `newStep` flag).
- A drag that ends where it started adds no undo step.
- A committed layer or audio drag stays drawn at its new place until the project data catches up (no one-frame jump back).

## Not changed
- Project file format, export, subtitles, cloud services: untouched.

## Notes
- Version code/name: 246 / 0.1.246.
- The pure logic (`TimelineSnap`, `TrimOps`) has 21 unit tests (`TimelineSnapTest`) that were run. The Android build and real-device gesture checks are still required (see docs/TESTING.md section 12).
