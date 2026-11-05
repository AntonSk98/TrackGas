# Release deployment

TrackGas releases are signed APKs published as GitHub Releases. Pushes to the
`delivery` branch build and publish the version configured in
`app/build.gradle.kts`.

## One-time setup

1. Generate a release keystore in a secure local directory. Keep the keystore
   password and key password distinct if possible:

   ```text
   keytool -genkeypair -v -keystore trackgas-release.jks -alias trackgas-release -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Make at least one encrypted offline backup of the keystore and store its
   passwords securely. Losing the keystore prevents updates from installing
   over existing installations. Never commit the keystore or its passwords.
3. Base64-encode the keystore without line breaks. In Git Bash, copy the
   encoded value to the clipboard:

   ```bash
   base64 -w 0 trackgas-release.jks | clip.exe
   ```

   Add the copied value as a repository Actions secret, then clear the
   clipboard with `printf '' | clip.exe`.
4. In the repository's **Settings > Secrets and variables > Actions**, add
   these four repository secrets:

   | Secret | Value |
   | --- | --- |
   | `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore contents |
   | `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
   | `RELEASE_KEY_ALIAS` | `trackgas-release` (or the alias chosen above) |
   | `RELEASE_KEY_PASSWORD` | Password for the key |

5. Create and push the `delivery` branch from `main`:

   ```bash
   git switch main
   git switch -c delivery
   git push -u origin delivery
   ```

## Per-release checklist

1. In `app/build.gradle.kts`, increase `versionCode` and set `versionName` to
   the new release version. Each `versionName` must be unique because it
   becomes the immutable tag `v<versionName>`.
2. Merge the updated `main` branch into `delivery`, resolve any conflicts, and
   push `delivery`:

   ```bash
   git switch delivery
   git merge main
   git push origin delivery
   ```

3. Watch the **Release APK** workflow in the repository's Actions tab. It
   checks the version tag, builds `:app:assembleRelease`, and publishes
   `TrackGas-<versionName>.apk` as a GitHub Release.
4. Verify the workflow succeeded, the `v<versionName>` GitHub Release exists,
   and its APK asset downloads successfully.
5. Install the APK on a test device and smoke-test startup, reading history,
   and recording a reading. Test an update over the previous signed release
   when possible; use the same release keystore for every update.

## Troubleshooting

- **Tag already exists:** Do not delete or overwrite the published tag. Increase
  both `versionCode` and `versionName`, merge the change into `delivery`, and
  push again.
- **Missing signing secrets:** Configure all four repository secrets with the
  exact names above, then rerun the failed workflow. The job deliberately fails
  rather than publishing an unsigned APK.
- **R8 build failure or release-only behavior:** Review the workflow's Gradle
  output, reproduce with `:app:assembleRelease` locally, and add narrowly
  scoped keep rules to `app/proguard-rules.pro` when required. Rebuild and
  smoke-test before publishing.
- **Keystore or signing failure:** Confirm the base64 value was copied without
  truncation, and verify the alias and both passwords. Do not generate a
  replacement key for an ordinary release; an unrelated key cannot update
  existing installations.

## Rollback and hotfixes

Do not reuse a version tag or lower `versionCode`. To roll back a bad release,
restore the known-good app code on `main`, assign a new, higher `versionCode`
and a new `versionName`, then release that version through `delivery`. This
publishes the known-good behavior as a valid update. Keep the same keystore so
users can install it over the affected release. For a hotfix, make the fix on
`main`, bump both version values, and follow the normal merge and release
checklist.
