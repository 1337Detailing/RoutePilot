# Routix UI — website alignment

Reference: `docs/index.html`, inspected at base commit 775ed621. The website is a typographic identity (`routix.`), with no pictorial logo. Its Mocha base is #1e1e2e, mantle #181825, text #cdd6f4, secondary text #a6adc8 and border #45475a. Buttons have 12 px corners, feature panels 22 px; surfaces are matte, without glass blur. Typography uses the site's sans-serif fallback on Android, with short headings and restrained one-shot transitions.

## Implementation

- Repaired malformed literal newline escapes, assignments to final fields, missing theme declaration, and settings initialization order in the previous phase-3 commit.
- Central Catppuccin palettes for Mocha, Macchiato, Frappé and Latte; all fourteen accents. Contrast-selected text on colored buttons, native light system bars in Latte, shared surface/border treatment.
- Permission-only onboarding scrolls on short screens and handles permanently denied location permission by opening Android settings.
- Shared matte components for recording, folders, routes, hours, settings, GPX tools, finish summary, menus and dialogs. Recording markers have a separate control row. Dedicated route detail page retains start, printable plan, folders, rename, sharing and advanced tools.
- The speed display, route previews, map overlays and comparison UI use theme tokens. Default route color follows the chosen accent; explicit custom trace/marker colors remain supported.
- Online OpenFreeMap layers and offline Mapsforge templates derive colors from the selected palette. Explicit light/dark and automatic-night map options are preserved.
- Adaptive vector r. launcher monogram and monochrome notification icon derived from the site's wordmark.
- One-shot animations respect the Android animator setting and application preference. Removed the recurring arrow-opacity timer.

## Validation scope

GitHub Actions runs the existing navigation/storage tests and Robolectric screen rendering checks at 320, 390 and 430 dp. Added contrast checks for all 56 flavor/accent combinations and map appearance preference checks. Rendered PNGs are retained with the build artifacts. Real GPS driving, direct sunlight and physical-device battery use require field validation; layout tests do not establish those properties.
