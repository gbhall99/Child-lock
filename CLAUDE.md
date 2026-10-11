# Working with the owner

- **Give a URL to click wherever possible, rather than just steps**: the exact
  GitHub, Play Console or settings page. Add steps under the link only when
  the page itself needs them.
- Keep explanations short and simple.

# Project notes

- Android app "Child Lock: Touch Freeze", package `ai.inovata.childlock`,
  published from the Play developer account inovata.ai.
- Google Maven is blocked in the cloud sandbox, so the app cannot be built
  locally: CI (`.github/workflows/android.yml`) is the compiler. The emulator
  run (`store-media.yml`) publishes a test APK at
  https://github.com/gbhall99/Child-lock/releases/download/store-media/app-sideload-debug.apk
- Releasing (see RELEASING.md): bump `versionName` in `app/build.gradle.kts`
  through a PR; once merged, run `android.yml` on `main` with tag `vX.Y.Z`.
  The signed bundle is then at
  `https://github.com/gbhall99/Child-lock/releases/download/vX.Y.Z/app-play-release.aab`.
  No Play service-account key is set, so the owner uploads the bundle in Play
  Console by hand.
