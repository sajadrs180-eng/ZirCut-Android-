# ZirCut 0.1.251 — multiple independent video overlay tracks

## Added
- Supports up to three independent video overlay layers in one project.
- Allows multiple overlay videos to be active at the same time, each with its own timeline range, position, and size.
- Preview creates and synchronizes a dedicated secondary player per overlay, so simultaneous overlays can be previewed together with the main video.
- Export creates a Media3 composition sequence for every visible overlay layer instead of exporting only the first one.
- Preserves existing overlay selection, edit, hide, delete, project save/load, and undo/redo behavior.
- Adds a capacity regression test for the three-layer limit and releases preview players when their layer is deleted.

## Current limits
- Overlay video audio remains muted.
- Maximum of three overlay layers per project to limit decoder and compositor load on phones.
- No masks, chroma key per overlay, keyframes, or drag-to-position gestures for video overlays.
- Android build, simultaneous playback, export composition order, and real-device performance remain unverified until GitHub Actions and device tests complete.
