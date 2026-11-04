# Design

## Context

The manifest does not declare an application icon, the shared toolbar renders only the app-name text, and the two CSV vector resources both depict a downward arrow over a tray. The project targets Android 26+, and its light theme uses teal accents. See proposal.md for the motivation and the spec deltas for the user-visible contract.

## Goals / Non-Goals

**Goals:**
- Keep one recognizable brand mark consistent between the launcher and the in-app toolbar.
- Make the CSV actions visually distinguishable at menu-icon size.
- Use native Android resources without adding a graphics dependency.

**Non-Goals:**
- Change CSV format, import/export behavior, menu ordering, app name, or other tracker icons.
- Add splash-screen branding or separate platform-specific artwork.

## Decisions

1. **Use native vector resources for the mark.** Create a simple gas-meter identity mark using the existing teal visual language, without lettering so it remains legible at launcher and toolbar sizes. Use the same foreground vector for the adaptive launcher icon and the toolbar title rather than maintaining duplicate artwork. A raster-only icon would require multiple density assets and make this small graphic harder to keep consistent.

2. **Register an adaptive launcher icon.** Add a background and foreground adaptive-icon resource and reference it from the application manifest. The app's minimum SDK is 26, so the adaptive format covers the supported Android versions without a legacy launcher resource. Keep the foreground centered and safely inset for launcher masks.

3. **Pair the brand mark with the existing title.** Render the mark and localized app name together in the shared top-app-bar title, with the icon treated as decorative because the adjacent text already names the app. Preserve the existing title typography and toolbar layout.

4. **Give CSV arrows opposing directions.** Draw export as an upward arrow leaving a tray and import as a downward arrow entering one. Keep the current menu labels and item order; their visible text continues to provide accessible names, while the icons clarify the direction.

## Risks / Trade-offs

- [Launcher masks or small display sizes can obscure details] → Keep the vector silhouette simple, centered within the adaptive safe area, and inspect it at launcher and toolbar sizes.
- [A new title mark could crowd the toolbar on compact screens] → Keep it compact and retain the existing app-name typography and available action controls.

## Migration Plan

No data migration is needed. The next APK update supplies the launcher resources and toolbar artwork; installing it replaces the previous launcher presentation automatically. Reverting the release can restore the previous manifest and drawable resources without affecting user data.
