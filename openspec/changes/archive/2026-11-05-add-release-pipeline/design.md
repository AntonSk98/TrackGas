## Context

`app/build.gradle.kts` defines no signing config or custom build types; only debug is built. Gradle 9.8.2 wrapper, AGP with Kotlin Compose and KSP/Room 2.8.4, JDK 17. Existing workflow in `.github/workflows` is only the Copilot setup. `.gitignore` already excludes `*.jks`/`*.keystore`. Distribution is via GitHub Releases only.

## Goals / Non-Goals

**Goals:** signed, minified release APK on push to `delivery`; GitHub Release with tag from `versionName`; no secrets in repo.
**Non-Goals:** Play Store/AAB publishing, automatic version bumping, debug CI on PRs, changelog automation.

## Decisions

- **Signing via env vars**: `signingConfigs.release` reads `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; it is assigned to the release build type only when all are set, otherwise the release build is unsigned (local builds still work). Alternative (always require) rejected: breaks local `assembleRelease`.
- **R8 enabled** (`isMinifyEnabled`, `isShrinkResources`) with `proguard-android-optimize.txt` + `app/proguard-rules.pro`. Room generates code via KSP and Compose ships consumer rules, so custom rules should be minimal; verify by installing the release APK (risk: runtime breakage, mitigated by smoke test and rules additions).
- **Workflow `release.yml`**: `on: push: branches: [delivery]`, `permissions: contents: write`, ubuntu-latest, `actions/setup-java` Temurin 17, `gradle/actions/setup-gradle`. Steps: decode base64 secret `RELEASE_KEYSTORE_BASE64` to a temp file, read `versionName` from `app/build.gradle.kts`, fail if tag `v<versionName>` exists, run `./gradlew :app:assembleRelease`, rename APK to `TrackGas-<versionName>.apk`, create the release with `gh release create` (target the pushed commit, with generated notes) attaching the APK.
- **Tag guard**: check with `git ls-remote --tags`/`gh release view`; fail with clear message. Manual version bumps in `build.gradle.kts` (`versionCode`, `versionName`).
- **Secrets**: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Keystore is generated once with `keytool` and must be backed up (loss prevents updates over installed apps).

- **Release documentation**: a root-level `DEPLOYMENT.md` is the single source for the release checklist (setup, per-release steps, failure handling), so the process lives in the repo rather than in memory.

## Risks / Trade-offs

- R8 may strip needed code → smoke-test release APK before first publish.
- Lost keystore → users must reinstall; back it up offline.
- Forgetting to bump version → pipeline fails (intentional).
- Assumes a `delivery` branch is created and gets pushed to by merging from `main`.
