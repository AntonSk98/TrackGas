## ADDED Requirements

### Requirement: Release build on delivery branch
The system SHALL build a signed, minified release APK whenever a commit is pushed to the `delivery` branch.

#### Scenario: Push to delivery
- **WHEN** a commit is pushed to the `delivery` branch
- **THEN** the pipeline builds the `release` variant (not debug) and produces a signed APK

#### Scenario: Push to other branches
- **WHEN** a commit is pushed to any branch other than `delivery`
- **THEN** the release pipeline does not run

### Requirement: Release signing from secrets
The system SHALL sign the release APK using a keystore and credentials supplied via GitHub Secrets, and SHALL NOT store signing material in the repository.

#### Scenario: Secrets available in CI
- **WHEN** the pipeline runs with the keystore secrets configured
- **THEN** the keystore is decoded at build time and the APK is signed with it

#### Scenario: Secrets missing locally
- **WHEN** a developer builds release locally without signing credentials
- **THEN** the build completes without failing on signing and no signing material is required in the repo

### Requirement: Minified release build
The release build type SHALL enable R8 code minification and resource shrinking with rules preserving Room and Compose behavior.

#### Scenario: Release APK runs
- **WHEN** the release APK is installed
- **THEN** the app starts and database and UI functionality behave as in debug

### Requirement: GitHub Release publication
The system SHALL publish the APK as an asset of a GitHub Release tagged `v<versionName>`.

#### Scenario: New version
- **WHEN** the tag `v<versionName>` does not exist
- **THEN** the tag and a GitHub Release are created with the signed APK attached

#### Scenario: Version not bumped
- **WHEN** the tag `v<versionName>` already exists
- **THEN** the pipeline fails with a message asking to bump the version, and no release is overwritten
