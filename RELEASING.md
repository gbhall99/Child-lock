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
2. `./gradlew testPlayDebugUnitTest lintPlayRelease bundlePlayRelease`
3. Upload `app/build/outputs/bundle/playRelease/app-play-release.aab`.
4. Fill in release notes. Tag the commit: `git tag v1.0.0 && git push --tags`.

## First submission checklist

- Play Console app created, package `com.gbhall.childlock` (cannot change later).
- Store listing from `store/listing.md`, assets from `store/assets/`.
- Privacy policy URL points at `PRIVACY.md` on the default branch.
- Declarations completed: accessibility service, foreground service
  (special use, with a short screen recording), display over other apps,
  data safety (nothing collected), target audience (not for children), ads (none).
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
