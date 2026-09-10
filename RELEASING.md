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

1. Bump `versionCode` (always +1) and `versionName` in `app/build.gradle.kts`.
2. `./gradlew test lint bundleRelease`
3. Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console.
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

## Notes

- Target SDK is 36 (Android 16), which Play requires for new apps from
  31 August 2026. No native code, so the 16 KB page-size requirement is met.
- Sideloaded installs face Android's restricted-settings gate for the two
  permissions; Play installs do not.
