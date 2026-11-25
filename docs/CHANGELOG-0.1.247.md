# ZirCut 0.1.247

## Timeline regression hardening
- Added regression coverage for deterministic snap-target priority when the playhead, video, layer, and audio edges share a timestamp.
- Added regression coverage proving that dragging one audio clip excludes only that clip from snap targets while other audio clips remain available.
- Kept the 0.1.246 timeline behavior and project format unchanged; no editor features were removed.

## Build workflow
- The GitHub Actions workflow now also runs on `feature/0.1.247-reviewed`, matching the reviewed release branch.
- Version code/name: 247 / 0.1.247.

## Verification status
- Source-level checks: pending.
- Android Gradle build and device gesture checks: must be confirmed by GitHub Actions and on-device testing.
