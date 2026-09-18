# Releasing Child Lock

## One-time: signing key

Create an upload key and keep it somewhere safe. Losing it does not lose the
app (Play App Signing holds the real key), but keep it anyway.

```
keytool -genkeypair -v -keystore childlock-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Modern `keytool` writes a PKCS12 keystore, which has a single password: the
key password is the store password, and a different `-keypass` is silently
ignored. Use the same value for `keyPassword` / `CHILDLOCK_KEYPASSWORD`.

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
- About → Purchase shows the days left; Buy opens the Play sheet with the
  right price; as a licence tester complete it, and the row says "Bought".
  Refund it in Play Console and, after a relaunch, the trial line is back.
- Three real locks of over two minutes each, ended with the gesture; on the
  next open of the app the Play "rate this app" sheet appears once (as a
  licence tester it may be blank or skipped: Play's quota, not a bug).
- More → "Preview the trial as over": the home screen says "Free trial
  over", the big button offers the purchase, the volume pattern and the tile
  no longer lock, Practise still does.
- Force a restart while locked (power for 10 to 30 s; Samsung power + volume
  down). The phone must come back unlocked.

## First submission checklist

- Play Console app created, package `com.gbhall.childlock` (cannot change later).
- Store listing from `store/listing.md`, assets from `store/assets/`.
- Privacy policy and terms URLs point at `PRIVACY.md` and `TERMS.md` on the
  default branch. Play and users must be able to open them without signing
  in, so the repository has to be public at submission and stay public.
- Declarations completed: accessibility service, foreground service
  (special use, with a short screen recording), display over other apps,
  data safety (nothing collected by the app; Play handles the purchase),
  target audience (not for children), ads (none), in-app purchases (yes).
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

## The purchase

The Play build is free for 30 days from first launch, then needs one purchase
(`FeatureGate.PRODUCT_ID`, `childlock_full`). Before the first release:

- Play Console → Monetise → Products → In-app products → Create: product id
  `childlock_full`, name "Child Lock", description "Unlocks Child Lock for
  good. One purchase, no subscription.", base price £2.99, then **Activate**.
  The app shows whatever price Play reports, so the price can change in the
  console at any time without a release.
- Play Console → Setup → Licence testing: add the Google accounts of everyone
  testing, so their purchases are free and refund instantly.
- Complete the Play trader declaration (name, postal address, phone, email);
  the EEA cannot be served without it, and selling makes you a trader there.
- The trader in `TERMS.md` and `PRIVACY.md` is the personal account holder;
  both documents send readers to the Play listing for the postal address and
  phone, so those need only be entered in Play Console. The terms cover the
  14-day right of withdrawal and refunds, and the listing links to them.

Play does not offer free trials on one-time products, so the trial clock is
the app's own: first launch is stored on the device, the purchase is restored
from Play. Clearing the app's data restarts the trial; a reinstall never
loses the purchase. The sideload flavour has no store and is simply unlocked.

To see the paid state on a test build: More → "Preview the trial as over".

## Notes

- Target SDK is 36 (Android 16), which Play requires for new apps from
  31 August 2026. No native code, so the 16 KB page-size requirement is met.
- Sideloaded installs face Android's restricted-settings gate for the two
  permissions; Play installs do not.
