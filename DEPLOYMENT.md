# Releases

Pushing to `delivery` builds a signed APK and publishes it as a GitHub Release.
The version comes from `app/build.gradle.kts`.

## One-time setup

1. Generate a keystore and keep a secure offline backup:

   ```bash
   keytool -genkeypair -v -keystore trackgas-release.jks -alias trackgas-release -keyalg RSA -keysize 2048 -validity 10000
   ```

   Keep its passwords safe and never commit the keystore.
2. Encode it as one line with `base64 -w 0 trackgas-release.jks`. Copy the
   output into the `RELEASE_KEYSTORE_BASE64` Actions secret.
3. Add these repository secrets under **Settings > Secrets and variables >
   Actions**:

   | Secret | Value |
   | --- | --- |
   | `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore |
   | `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
   | `RELEASE_KEY_ALIAS` | `trackgas-release` (or your chosen alias) |

4. Create and push `delivery` from `main`:

   ```bash
   git switch main
   git switch -c delivery
   git push -u origin delivery
   ```

## Publish a release

1. Increase `versionCode` and set a unique `versionName` in
   `app/build.gradle.kts`. Merge `main` into `delivery` and push:

   ```bash
   git switch delivery
   git merge main
   git push origin delivery
   ```

2. In Actions, verify the **Release APK** workflow succeeds and the GitHub
   Release contains `TrackGas-<versionName>.apk`.
3. Install and smoke-test the APK. Keep using the same keystore so users can
   install updates over existing versions.

Never reuse a published version tag. If one already exists, increase both
version values and release again.
