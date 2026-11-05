## 1. Gradle release configuration

- [x] 1.1 Add `signingConfigs.release` in `app/build.gradle.kts` reading `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` from the environment
- [x] 1.2 Add `release` build type with `isMinifyEnabled = true`, `isShrinkResources = true`, default optimize ProGuard file + `proguard-rules.pro`; apply the signing config only when all variables are set
- [x] 1.3 Create `app/proguard-rules.pro` with any rules required for Room/Compose

## 2. GitHub Actions workflow

- [x] 2.1 Create `.github/workflows/release.yml` triggered on push to `delivery` with `contents: write`
- [x] 2.2 Add steps: checkout, JDK 17, Gradle setup, decode `RELEASE_KEYSTORE_BASE64` to a temp keystore and export env vars
- [x] 2.3 Add step to read `versionName` and fail if tag `v<versionName>` already exists
- [x] 2.4 Run `./gradlew :app:assembleRelease`, rename APK to `TrackGas-<versionName>.apk`
- [x] 2.5 Create tag/GitHub Release `v<versionName>` with the APK attached via `gh release create`

## 3. Documentation and verification

- [x] 3.1 Create `DEPLOYMENT.md` at the repo root with the release checklist: one-time setup (generate keystore with `keytool`, back it up, base64-encode it, add the four GitHub Secrets, create `delivery` branch); per-release steps (bump `versionCode`/`versionName` in `app/build.gradle.kts`, merge `main` into `delivery`, watch the workflow, verify the GitHub Release and APK, smoke-test install); failure handling (tag already exists, missing secrets, R8 issues); rollback/hotfix notes
- [x] 3.2 Link `DEPLOYMENT.md` from the README (if present)
- [x] 3.3 Run `./gradlew :app:assembleRelease` locally (unsigned) and verify R8 build succeeds
- [x] 3.4 Verify signing locally with a throwaway keystore (`apksigner verify`) and smoke-test the release APK
