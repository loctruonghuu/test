# MiCTS+ Live Translate & Edge Swipe

A standalone Android project inspired by [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS) that combines:
1. **Circle to Search Trigger** (MiCTS VIS `showSessionFromSession` reflection via `HiddenApiBypass`)
2. **On-Screen Live Translate** (Android `MediaProjection` + Google ML Kit On-Device OCR & Translation)
3. **Edge Screen Swipe Trigger** (`WindowManager` `SYSTEM_ALERT_WINDOW` overlay handle)

**Zero Accessibility Services. Zero Developer Options / ADB. Zero Root.**

## Build APK via GitHub Actions
1. Create a new GitHub repository and upload all files from this ZIP.
2. Go to the **Actions** tab and run **Build Android APK** (or push to `main`).
3. Download `MiCTS-LiveTranslate-Edge-debug-apk` from the workflow artifacts.

## Setup on Phone
1. Install the APK.
2. Open **MiCTS+ Live Translate**:
   - Tap **Default Assistant** → set **Google** as your default digital assistant (required for Circle to Search).
   - Tap **Grant Overlay** → allow **Display over other apps** (required for Edge Swipe & Live Translate overlay).
   - Toggle **Enable Edge Swipe Handle** and customize the side, height, and gesture actions.
   - Disable battery optimization / allow background auto-start for both **Google** and **MiCTS+ Live Translate** so the edge handle stays alive.
