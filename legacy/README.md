# legacy/ — archived, NOT compiled, NOT part of the app

Kept only so no earlier work is lost. Nothing here is referenced by Gradle.

- removed-in-fix235-offline-policy/: Whisper (JNI + CMake), CloudTranscriber (online recognition), ModelManager, Config,
  WhisperBridge — exactly as they were in fix234. fix235 removed them on purpose (project rule: no speech-to-text,
  no online API, no INTERNET permission). To restore, rename `*.txt` back and re-add the CMake/NDK block + the
  fix234 MainViewModel/ZirCutApp wiring (see ../docs/PROGRESS.md, fix235 entry).
- old-root-workflow-build.yml.txt: the repo-root workflow (unzipped fix*.zip files, cloned whisper.cpp, fetched fonts).
  Obsolete now that the project is a single flattened tree; `.github/workflows/build.yml` is the active one.
- ic_launcher_foreground_old.xml.txt: old vector launcher icon, replaced by the PNG icon in fix237.
