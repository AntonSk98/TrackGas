# Proposal

## Why

The app is only built as a debug APK today. The app will be distributed directly via GitHub (not Google Play), so a signed, optimized release APK must be produced reproducibly and published without manual steps.

## What Changes

- Add a signed `release` build type to the Android module with R8 minification and resource shrinking enabled, plus ProGuard/R8 rules needed for Room and Compose.
- Read signing credentials from environment variables / Gradle properties so no secret is committed; the release build falls back to unsigned when they are absent (local dev unaffected).
- Add a GitHub Actions workflow triggered by pushes to a new `delivery` branch that builds the release APK, tags `v<versionName>`, and publishes a GitHub Release with the APK attached.
- The pipeline fails (does not overwrite) if the tag for the current `versionName` already exists; version bumps (`versionName`, `versionCode`) stay manual.
- Add `DEPLOYMENT.md` defining the release process: one-time setup (keystore, GitHub Secrets, `delivery` branch) and the per-release checklist (version bump, merge to `delivery`, verification, failure handling).

## Capabilities

### New Capabilities
- `release-distribution`: Producing and publishing a signed, minified release APK as a GitHub Release when changes land on the `delivery` branch.

### Modified Capabilities
<!-- none -->

## Impact

- `app/build.gradle.kts` (signingConfigs, release buildType), new `app/proguard-rules.pro`.
- New `.github/workflows/release.yml`.
- GitHub repo settings: secrets for keystore (base64), keystore password, key alias, key password; `delivery` branch.
- New `DEPLOYMENT.md` (release process documentation). No runtime behavior change in the app besides R8 shrinking of release builds.
