---
name: android-stack-versioning
description: Researches and aligns TrackGas Android build, dependency, SDK, JDK, and GitHub Actions versions. Use when asked to audit, explain, plan, or update versions in the Android stack.
---

# TrackGas Android Stack Versioning

Keep the Android build stack mutually compatible and current, rather than
independently selecting the newest release of each component. Use live
first-party documentation and release notes for every version decision; do not
rely on remembered version numbers. Record the date of the research and link
the sources used when reporting a recommendation or change.

## Inventory the project

Before researching or editing, inspect the current values and all related
surfaces:

- `build.gradle.kts`: Android Gradle Plugin, Kotlin Compose plugin, and KSP.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle wrapper.
- `app/build.gradle.kts`: `compileSdk`, `targetSdk`, `minSdk`, Java/Kotlin
  targets, and app/test dependencies.
- `gradle.properties` and `settings.gradle.kts`: build settings and repository
  configuration.
- `.github/workflows/*.yml`: CI JDK and GitHub Actions versions.
- Android Studio version when the requested change affects IDE sync or support.

Dependency versions are currently declared directly in
`app/build.gradle.kts`. Keep that convention; do not add or reintroduce
`gradle/libs.versions.toml` or another version catalog unless the user
explicitly requests one.

## Research authoritative versions

Use the current official compatibility pages and release notes. Check stable
releases separately from previews and prereleases.

### Build tools and platform

- [Kotlin Gradle Plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin):
  use the KGP table to find the fully supported Gradle and AGP ranges.
- [Kotlin releases](https://kotlinlang.org/docs/releases.html) and
  [KSP releases](https://github.com/google/ksp/releases): check compatible
  plugin releases and migration notes.
- [Android Gradle Plugin release notes](https://developer.android.com/build/releases/gradle-plugin):
  verify the AGP-required Gradle, SDK Build Tools, JDK, and maximum API levels.
- [Android Studio releases and AGP compatibility](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility):
  ensure the selected AGP can sync in the user's supported IDE.
- [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
  and [Gradle releases](https://gradle.org/releases/): verify the wrapper,
  embedded Kotlin, supported Java runtime, and release status.
- [Android JDK configuration](https://developer.android.com/build/jdks):
  verify which JDK runs Gradle and keep Java/Kotlin compilation targets
  intentional and aligned.
- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk):
  verify the current submission requirement. `compileSdk` and `targetSdk` are
  different decisions; do not raise `targetSdk` just because a dependency
  requires a higher `compileSdk`. Explain runtime behavior changes and get user
  approval before changing the target API.

### Android and third-party libraries

- [AndroidX versions and release notes](https://developer.android.com/jetpack/androidx/versions):
  check stable releases and each library's compatibility notes.
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping):
  choose a BOM and use its mapped versions for BOM-managed Compose artifacts;
  do not add separate versions to those artifacts.
- [Vico getting started](https://guide.vico.patrykandpatrick.com/getting-started)
  and [Vico releases](https://github.com/patrykandpatrick/vico/releases):
  check Android `minSdk`, artifact names, API changes, and migration notes.
- [Kotlin Coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases)
  and [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml).
- [org.json Maven Central metadata](https://repo.maven.apache.org/maven2/org/json/json/maven-metadata.xml).
- [JUnit 4 releases](https://github.com/junit-team/junit4/releases).

For artifacts without a clear vendor release table, use the artifact's official
repository metadata (for example, Maven Central) and confirm that the selected
version is stable. Read major-version migration notes before changing APIs.

### GitHub Actions

Check the official release history and runtime requirements for
[actions/checkout](https://github.com/actions/checkout/releases),
[actions/setup-java](https://github.com/actions/setup-java/releases), and
[gradle/actions](https://github.com/gradle/actions/releases). Update every
usage across `.github/workflows/`, not just the release workflow, and keep the
workflow JDK consistent with the chosen build JDK.

## Choose a compatible set

1. Select the desired stable Kotlin/KGP release, then intersect its fully
   supported Gradle and AGP ranges. Do not select each tool's individual latest
   version if their compatibility ranges do not overlap.
2. Confirm AGP's Gradle, JDK, SDK Build Tools, and Android Studio requirements.
   Record the JDK used to run Gradle separately from Java/Kotlin bytecode
   targets.
3. Check KSP compatibility with the chosen Kotlin compiler version. Keep Room's
   runtime, KTX, and compiler artifacts on a compatible release.
4. Check every library's `minSdk` and `minCompileSdk` requirements against the
   app. A library may require a higher `compileSdk`; that alone does not require
   a higher `targetSdk`.
5. Align Compose libraries through the selected BOM and the Kotlin Compose
   plugin. Treat major library upgrades (especially Vico) as code migrations,
   not version-only edits.
6. Preserve the current `minSdk` and Java/Kotlin bytecode targets unless a
   documented compatibility requirement or the user's request requires a
   change. Ask before optional changes that affect device support or runtime
   behavior.
7. Prefer stable versions; do not use alpha, beta, RC, snapshot, or next
   releases unless the user explicitly asks for them.

## Apply and verify

- Change only the requested scope, but update every coupled version and
  configuration surface required to keep the stack compatible.
- Update dependency declarations, wrapper/plugin versions, SDK levels,
  workflows, and directly related docs wherever they are actually affected.
- Review release notes for breaking changes and migrate source/tests when
  required. Do not silently downgrade or replace a dependency to avoid a
  migration.
- Run the smallest relevant checks, then at least:

  ```sh
  ./gradlew --version
  ./gradlew :app:testDebugUnitTest :app:assembleRelease --warning-mode all
  git diff --check
  ```

- Run `actionlint` on changed workflow files when it is already available.
  Do not install tools just for this check unless requested.
- For target SDK changes, separately call out required on-device testing; a
  successful build does not verify Android runtime behavior.
- Report the as-of date, selected versions, compatibility sources, validation
  results, remaining warnings, and any device or Android Studio checks not
  completed.
