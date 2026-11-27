# MiCTS+ Honor Screen Translate & Bottom-Right Edge Swipe (v3)

Standalone Android project based on [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS):
1. **Circle to Search Trigger** (MiCTS VIS `showSessionFromSession` via `HiddenApiBypass`)
2. **Honor MagicOS Screen Translation Trigger** — skips the main Translate app launcher and targets exported **Screen Translation Activities, Services, and Broadcast Receivers** directly (`com.hihonor.magictranslate`, `com.hihonor.magictext`, `com.hihonor.hitouch`, `com.hihonor.vision`, `com.hihonor.scanner`, etc.), with a built-in **Deep Scanner** that lets you tap & immediately test any entry.
3. **Bottom-Right Edge Handle up to 150%** — uses full physical screen height (`currentWindowMetrics`) + `FLAG_LAYOUT_NO_LIMITS` so the handle can sit all the way down in the bottom-right corner bezel (`10%–150%`, default `105%`).
4. **Custom Time Delays** — configurable Trigger Time Delay (`0–1500 ms`) and Swipe-and-Hold Delay (`0–1000 ms`).

**Zero Accessibility Services. Zero Developer Options / ADB. Zero Root.**
