# Releasing Child Lock

## One-time: signing key

Create an upload key and keep it somewhere safe. Losing it does not lose the
app (Play App Signing holds the real key), but keep it anyway.

```
keytool -genkeypair -v -keystore childlock-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then create `keystore.properties` in the repository root (git-ignored):

```
storeFile=/absolute/path/childlock-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

CI can sign too: set the secrets `CHILDLOCK_KEYSTORE_BASE64`,
`CHILDLOCK_STOREPASSWORD`, `CHILDLOCK_KEYALIAS`, `CHILDLOCK_KEYPASSWORD` and the
workflow's release job produces a signed bundle.

## Each release

1. Bump `versionName` in `app/build.gradle.kts`. The version code is derived
   from the commit count in CI, so it always increases.
2. Green CI on the commit: `testSideloadDebugUnitTest testPlayDebugUnitTest`,
   `lintPlayDebug lintPlayRelease`, `assembleSideloadDebug assemblePlayRelease`
   all run on every push. Locally the same is
   `./gradlew testPlayDebugUnitTest lintPlayRelease bundlePlayRelease`.
3. Regenerate the store screenshots if a screen changed:
   `CHILDLOCK_SHOTS=store/assets/src ./gradlew testPlayDebugUnitTest --tests '*ScreenshotTest*'`
   then `scripts/render-store-assets.sh`.
4. Tag the commit on `main`: `git tag v1.0.0 && git push --tags`. The
   `release` job signs `bundlePlayRelease` with the CI secrets, uploads the
   bundle as the `childlock-play-release-bundle` artifact of that run and
   attaches it to a GitHub Release for the tag. If you cannot push tags, open
   Actions → Android → "Run workflow", pick the branch and enter the tag; the
   job creates the tag and the Release itself.
5. Download the `.aab` from the Release (or the artifact) and upload it to
   Play Console (Internal testing first), or run
   `scripts/play-upload.py --key <service-account.json> --aab <file>`.

## Hardware pass before a release

Unit tests cannot see the system gesture layer, so on a real phone with
gesture navigation, once per release:

- Lock with the volume pattern; swipe from the top, bottom and both sides.
  Nothing may land. Pull the shade: it closes at once.
- Try every unlock the settings do NOT show (corners, PIN, notification
  button when switched off). None may work. Then the ones that are shown.
- Three-finger triple tap once (stays locked), twice (unlocks).
- Add a video app under Auto-lock, play a video full screen, wait 5 s.
- Force a restart while locked (power for 10 to 30 s; Samsung power + volume
  down). The phone must come back unlocked.

## First submission checklist

- Play Console app created, package `com.gbhall.childlock` (cannot change later).
- Store listing from `store/listing.md`, assets from `store/assets/`.
- Privacy policy URL points at `PRIVACY.md` on the default branch.
- Declarations completed: accessibility service, foreground service
  (special use, with a short screen recording), display over other apps,
  data safety (nothing collected), target audience (not for children), ads (none).
  The screen recording should show: the setup guide's disclosure, arming from
  the app, the badge while locked, a swipe achieving nothing, the volume
  pattern unlocking. Under a minute is enough.
- Closed testing: Play requires new personal developer accounts to run a
  closed test with at least 12 testers for 14 days before production. Use it
  to cover Samsung, Pixel and one Xiaomi.
- Play App Signing enrolled (default for new apps).

## Two flavours

- `sideload` keeps the skip-ad feature, for your own phone.
- `play` is compiled without it and is the only flavour to publish.

`./gradlew assembleSideloadDebug` builds the personal one.

## Before the first paid release

- Complete the Play trader declaration (name, postal address, phone, email);
  the EEA cannot be served without it.
- Publish terms covering the 14-day right of withdrawal and refunds, and name
  the trader as the data controller in the privacy policy.
- Wire Play Billing and flip `FeatureGate.BILLING_READY`; until then the app
  never offers a purchase.
- Add a visible "Restore purchase" row.

## Notes

- Target SDK is 36 (Android 16), which Play requires for new apps from
  31 August 2026. No native code, so the 16 KB page-size requirement is met.
- Sideloaded installs face Android's restricted-settings gate for the two
  permissions; Play installs do not.
