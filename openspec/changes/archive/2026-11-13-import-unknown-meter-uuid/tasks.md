# Tasks

## 1. Import Unknown Meter Identities

- [x] 1.1 Accept valid UUIDs absent from the tracker during CSV validation while keeping malformed UUIDs invalid; update parser tests and verify `./gradlew :app:testDebugUnitTest` passes.
- [x] 1.2 Add installations for previously unknown UUIDs when building and committing import candidates; test imported readings, existing-meter replacement, omitted-meter preservation, and transaction rollback with `./gradlew :app:testDebugUnitTest`.

## 2. Update Import Messaging and Documentation

- [x] 2.1 Update the preview warning, remove obsolete unknown-meter error presentation, and document that valid unknown UUIDs create meters; verify localized resources and README wording, then run `./gradlew :app:testDebugUnitTest`.
