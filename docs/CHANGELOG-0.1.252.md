# ZirCut 0.1.252 — animated video-overlay transforms

## Included
- Video overlays now have keyframeable position, scale, rotation, and opacity.
- The editor panel can add/update/remove a keyframe at the playhead and choose smooth, linear, or hold interpolation.
- Preview and Media3 export evaluate the same `VideoOverlayKeys` pose at the output-timeline time.
- Overlay transform keyframes and base rotation/opacity are saved in project JSON.
- Old projects remain compatible: missing rotation, opacity, and keyframes use the previous static overlay appearance.
- Added unit tests for interpolation, insertion/removal, and invalid keyframe ranges.

## Not included in this checkpoint
- Masks and blend modes are not implemented in this version. They require a real shared preview/export compositing path; they are not represented as fake controls.
- Overlay audio remains muted as before.

## Verification
- `git diff --check` and source-level inspection should be run before pushing.
- Android Gradle build and real-device preview/export verification must be confirmed by GitHub Actions and on-device testing; they are not claimed as completed here.
