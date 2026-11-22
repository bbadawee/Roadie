<div align="center">

<img src="assets/roadie-mark.svg" width="88" height="88" alt="Roadie logo">

# Roadie

### Native YouTube for Android Automotive & Car Head Units

**Open source. Native playback. Zero ads. No root.**

A car-first YouTube experience built from the ground up in Kotlin, Jetpack Compose, and Media3—with responsive dashboard grids, video beside your Up-Next queue, and a built-in English & Arabic touch keyboard.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)
[![Platform: Android Automotive](https://img.shields.io/badge/Android-Automotive%20%26%20Head%20Units-3DDC84?style=flat-square&logo=android&logoColor=white)](#can-i-install-it)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1%20%2B%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](app/build.gradle.kts)
[![Status: Experimental](https://img.shields.io/badge/Status-Experimental-orange?style=flat-square)](docs/COMPATIBILITY.md)

[**Features**](#features) · [**Can I install it?**](#can-i-install-it) · [**Build Roadie**](#build-roadie) · [**How it works**](#how-it-works) · [**FAQ**](#frequently-asked-questions) · [**Contributing**](CONTRIBUTING.md)

</div>

---

## Why Roadie?

Most YouTube workarounds for Android cars either **break on every YouTube APK update** due to R8 obfuscation, **require root / LSPosed**, or wrap a **sluggish mobile web page** that wasn't designed for a dashboard.

**Roadie** is a clean-room, open-source Android client engineered specifically for car screens. It is not a WebView wrapper, and it never patches Google's proprietary APK.

## Features

- **Native Media3 (ExoPlayer) playback.** Hardware-accelerated HLS and progressive playback with zero pre-roll ads and `MediaSessionService` support for steering-wheel controls.
- **Responsive car feed.** Video grids adapt automatically to your screen (`1`, `2`, or `3` columns) instead of stretching a single phone-sized card across a widescreen dashboard.
- **Video on the left, queue on the right.** Wide car displays automatically switch to a split watch view with the player on the left and Up-Next recommendations on the right.
- **Compact display scaling.** Targets a `1024×600` logical floor so even smaller `800×480` aftermarket head units keep comfortable multi-column and split-watch ergonomics.
- **Built-in English & Arabic car keyboard.** Large `50dp` touch targets, instant `EN / عربي` switching, RTL/LTR entry bar, Arabic numerals (`١٢٣`), and long-press `أ / إ / آ` variants—works even when the head unit's system keyboard is missing or broken.
- **No root, no Google Play Services.** Runs cleanly on de-Googled Android head units and AAOS systems alike.
- **Single-folder Kotlin codebase.** Open in Android Studio, build in one command, and contribute easily.

> For use while parked. Follow local traffic laws and your vehicle's restrictions. This is an experimental, independent open-source project—not an official Google or YouTube app.

---

## Can I install it?

**Yes—one APK, no root, no patching, and no signing hoops.**

| Requirement | In plain language |
| :--- | :--- |
| **Compatible device** | **Android Automotive OS (AAOS)**, **Android car head units** (Android 8.0+ / API 26+), or any Android tablet/phone for testing. |
| **Root / LSPosed** | **Not required.** Roadie installs as a standard standalone APK. |
| **Google Play Services** | **Not required.** Uses a clean-room InnerTube client (`OkHttp` + `kotlinx.serialization`). |
| **For building from source** | **JDK 17 or 21** + **Android SDK (API 35)** on macOS, Linux, or Windows. |

> **Note on phone-projected Android Auto:** Standard phone-to-car Android Auto projection restricts third-party apps to audio/template APIs and blocks arbitrary video surfaces. Roadie is built for **native Android Automotive OS (AAOS)** and **Android head units** where apps render directly on the dashboard display. See [Compatibility](docs/COMPATIBILITY.md).

---

## Build Roadie

```sh
git clone https://github.com/bbadawee/Roadie.git
cd Roadie
./gradlew testDebugUnitTest assembleDebug
```

This runs the unit tests and builds `app/build/outputs/apk/debug/app-debug.apk`.

To install directly onto a connected Android head unit, tablet, or AAOS emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**[Full build & emulator guide →](docs/BUILD.md)**

---

## How it works

Roadie is designed as a clean, single-module Android app organized into four focused packages:

1. **Responsive viewport engine (`io.roadie.carui`).** `CarViewportMetrics` measures the real display bounds, applies a `1024×600` logical floor, chooses `1–3` browse columns (`>= 500dp` / `>= 780dp`), and enables split watch layout when `width >= 820dp && height >= 300dp && landscape`.
2. **Clean-room InnerTube client (`io.roadie.innertube`).** Queries structured JSON endpoints (`browse`, `search`, `player`, `next`) without HTML scraping or Google Play Services dependencies.
3. **Native playback & steering-wheel controls (`io.roadie.app`).** `Dash` playback runs on `Media3` (`ExoPlayer`) paired with `RoadiePlaybackService` (`MediaSessionService`) for background audio and hardware media keys.
4. **Self-contained bilingual car keyboard (`io.roadie.keyboard`).** `CarTouchKeyboard` renders a dark, high-contrast touch keyboard directly inside the Compose hierarchy so search works reliably on any car display.

**[Architecture walkthrough →](docs/ARCHITECTURE.md)**

---

## Project Structure

```text
Roadie/
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                  # AAOS distractionOptimized & MediaSessionService
│       │   └── kotlin/io/roadie/
│       │       ├── app/
│       │       │   ├── MainActivity.kt              # Edge-to-edge Compose host
│       │       │   └── RoadiePlaybackService.kt     # Media3 background & steering-wheel service
│       │       ├── carui/
│       │       │   ├── CarViewportMetrics.kt        # 1024x600 scaling & layout breakpoints
│       │       │   ├── CarTheme.kt                  # High-contrast automotive dark palette
│       │       │   ├── RoadieViewModel.kt           # State management (Home, Search, Watch, Parked)
│       │       │   └── RoadieScreens.kt             # Sidebar rail, Browse grid, Split Watch + Queue
│       │       ├── innertube/
│       │       │   ├── InnerTubeModels.kt           # VideoItem & ResolvedStream models
│       │       │   └── InnerTubeClient.kt           # Structured InnerTube API client
│       │       └── keyboard/
│       │           └── CarTouchKeyboard.kt          # EN / AR / Symbols car keyboard
│       └── test/kotlin/io/roadie/                   # Unit tests for layout metrics & JSON parsing
├── assets/
│   └── roadie-mark.svg
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD.md
│   └── COMPATIBILITY.md
└── .github/workflows/ci.yml
```

---

## Frequently asked questions

**Is it open source?**

Yes—Roadie is licensed under **GPL-3.0**. Everything in this repository is 100% original Kotlin/Compose code with no proprietary binaries. See [LICENSE](LICENSE).

**Does it block ads?**

Yes. Roadie resolves video streams directly via InnerTube and plays them with `Media3` (`ExoPlayer`), bypassing YouTube's ad insertion pipeline.

**Why doesn't Roadie patch the official YouTube APK?**

On Android, the official YouTube APK is heavily R8/ProGuard-obfuscated on every release, which breaks class/method hooks constantly and requires root or manual APK patching. Building a clean-room native client means anyone can install one APK and updates never break the UI.

**Does it support Arabic search?**

Yes. Tap the Search bar and switch between `EN` and `عربي` directly on Roadie's built-in car keyboard—including RTL text direction, Arabic numerals, and long-press `أ / إ / آ` variants.

---

## Roadmap

- [ ] **SponsorBlock** segment auto-skipping in `ExoPlayer`
- [ ] **AAOS `CarUxRestrictionsManager`** live driving-state listener
- [ ] **Kurdish (Sorani) & Persian** keyboard layouts in `CarTouchKeyboard`
- [ ] **Subscriptions & Watch History** (local Room DB, zero-account privacy mode)
- [ ] **Rotary knob / D-Pad focus navigation** for non-touch OEM cars

---

## Help improve it

If you want to follow the project or help it reach more drivers, **⭐ star the repository**! Bug reports, display-size feedback, and pull requests are welcome.

When opening an issue, include your Android / AAOS version, head-unit screen resolution, and steps to reproduce.

[Contributing](CONTRIBUTING.md) · [Build guide](docs/BUILD.md) · [Compatibility](docs/COMPATIBILITY.md) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md)

---

## Credits

Inspired by **[YouDash](https://github.com/sa4hnd/YouDash)**'s car-first UX principles (responsive dashboard grids, `1024×600` logical floor, split watch layout, and bilingual English/Arabic car keyboard).

YouTube and Android Automotive are trademarks of their respective owners. Roadie is an independent project and is not affiliated with Google LLC.
