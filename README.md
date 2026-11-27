# MiCTS+ Honor AI Translate & Bottom-Right Edge Swipe (v2)

A lightweight, standalone Android project based on [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS) with:
1. **Circle to Search Trigger** (MiCTS VIS `showSessionFromSession` via `HiddenApiBypass`)
2. **Honor MagicOS AI Translate Trigger** (auto-detects `com.hihonor.magictranslate` / `com.hihonor.magicvoice` / `com.hihonor.lens`, plus a built-in **Activity Scanner** to pick the exact screen/face-to-face translate activity on your MagicOS version)
3. **Bottom-Right Edge Screen Swipe Handle** (defaults to Right Edge, 90% vertical position, with `systemGestureExclusionRects`)
4. **Custom Time Delay Controls**:
   - **Trigger Time Delay (0–1500 ms)**: custom delay before firing the action
   - **Swipe-and-Hold Delay (0–1000 ms)**: optional hold time after swiping to prevent accidental triggers

**Zero Accessibility Services. Zero Developer Options / ADB. Zero Root. No MediaProjection popups.**

## Build APK via GitHub Actions
1. Upload the files to a GitHub repository.
2. Run **Actions → Build Android APK**.
3. Download `MiCTS-HonorTranslate-Edge-v2-debug-apk`.
