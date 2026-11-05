# Tasks

## 1. Decision logic and persistence

- [x] 1.1 Add `ACCESS_COARSE_LOCATION` (and no other location permission) to `AndroidManifest.xml`; verify with `./gradlew :app:assembleDebug` and inspecting the merged manifest
- [x] 1.2 Add a SharedPreferences-backed permission outcome store for never-requested, declined, or previously-granted state, plus pure `shouldRequestLocation(granted, previousState)` logic; test suppression after decline and re-request after revocation

## 2. UI and wiring

- [x] 2.1 Add EN and DE strings with concise location rationale copy, Continue, and Not now; verify `LocalizedStringResourcesTest` passes
- [x] 2.2 Add the rationale `AlertDialog` composable in the existing dialog style, with 48 dp targets and rotation-safe visibility; verify layout at large font sizes
- [x] 2.3 Ensure `MainActivity` does not request location on launch; expose a contextual rationale/request flow that suppresses retries after decline, re-asks after a previously observed grant is revoked, and reports permission outcome so the feature does no location work unless granted
- [x] 2.4 Update `README.md` if it claims the app needs no permissions; verify with a grep for "permission"

## 3. Integration checks

- [x] 3.1 Run `./gradlew :app:testDebugUnitTest` and confirm all tests pass
