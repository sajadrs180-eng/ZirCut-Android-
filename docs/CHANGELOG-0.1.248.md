# ZirCut 0.1.248

## Timeline clip selection
- Tapping a video/photo clip on the timeline now selects that clip directly and seeks to the tapped position.
- The Clip panel opens automatically for the selected clip.
- The selected clip is marked with a distinct amber outline, separate from the playhead's current-clip highlight.
- Timeline selection and the Clip panel multi-selection controls now share state; selecting a clip on the timeline replaces the current multi-selection with that clip.
- Existing clip editing, multi-select operations, drag/reorder, trim, snapping, and project format are preserved.

## Build
- Version code/name: 248 / 0.1.248.
- GitHub Actions also triggers on `feature/0.1.248-reviewed`.
- Android build and device gesture verification must be confirmed by GitHub Actions and on-device testing.
