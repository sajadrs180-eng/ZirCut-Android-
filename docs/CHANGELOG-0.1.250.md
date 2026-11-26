# ZirCut 0.1.250 — first independent video PIP layer

## Added
- Introduces a separate `VideoOverlay` project model. Video overlays are independent of the main clip sequence and use edited-timeline start/end times.
- Saves and restores overlay metadata in `project.json`; projects from older versions load with an empty overlay list.
- Adds MP4 import, select, remove, hide, timeline start/duration, position and size controls for the video overlay.
- Adds a second preview player for simultaneous main-video and PIP playback.
- Adds Media3 1.4.1-compatible Composition sequences for PIP, transparent timing gaps and a transparent top sequence for text/photo layers and captions, so those layers remain above PIP in the exported frame.
- Adds overlay entries to undo/redo snapshots and project autosave state.
- Tightens project validation: a PIP layer cannot claim a display duration longer than its source video; adds a regression test for this case.

## Current limits
- PIP audio is muted in this version; independent overlay audio controls are planned for a later version.
- The overlay is a simple rectangular video layer. Masks, rounded-corner export, keyframes and multiple simultaneous PIP tracks are not part of this first implementation.
- A successful Android build and on-device playback/export test are still required before calling this release verified.
