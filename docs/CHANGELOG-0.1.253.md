# ZirCut 0.1.253 — video overlay masks

## Included
- Adds non-destructive alpha masks to independent video overlays: none, circle, and rounded rectangle.
- Preview clips the overlay surface using the selected mask.
- Export applies the same mask to the overlay video stream with a Media3 `GlEffect` before composition.
- Mask type is persisted in project JSON; older projects default to no mask.
- Adds model validation tests for supported and invalid mask types.

## Limitations
- Masks are currently limited to circle and rounded rectangle; freehand paths, feathering, inversion, and animated masks are not included.
- Masks apply to independent video overlays (PIP), not the main timeline video or photo/text layers.

## Verification
- Source-level checks and `git diff --check` are required before commit.
- Android Gradle build, preview/export parity, and real-device performance must be verified through GitHub Actions and on a phone. They are not claimed as passed in this source package.
