<div align="center">

<img src="assets/roadie-mark.svg" width="92" height="92" alt="Roadie logo" />

# Roadie: Native YouTube for Android Cars

### One clean-room Android app for Android Automotive OS and Android car head units.

**No root. No APK patching. Zero ads. Built in 100% Kotlin, Jetpack Compose, and Media3.**

[![GitHub stars](https://img.shields.io/github/stars/bbadawee/Roadie?style=flat-square&logo=github)](https://github.com/bbadawee/Roadie/stargazers)
[![Android CI](https://img.shields.io/github/actions/workflow/status/bbadawee/Roadie/ci.yml?branch=main&label=CI&style=flat-square&logo=githubactions&logoColor=white)](https://github.com/bbadawee/Roadie/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square&logo=android&logoColor=white)](app/build.gradle.kts)
[![Kotlin 2.1](https://img.shields.io/badge/Kotlin-2.1%20%2B%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![Media3](https://img.shields.io/badge/Media3-ExoPlayer%201.6-FF0033?style=flat-square)](app/build.gradle.kts)

[Quick start](#quick-start) · [Highlights](#highlights) · [Comparison](#how-roadie-compares) · [How it works](#how-it-works) · [FAQ](#faq) · [Roadmap](#roadmap)

<code>./gradlew testDebugUnitTest assembleDebug</code>

</div>

> [!IMPORTANT]
> Get official Roadie source and CI builds only from **[bbadawee/Roadie](https://github.com/bbadawee/Roadie)**.
> Roadie never redistributes proprietary Google binaries or patched APKs.
> Intended for use while parked—always obey local traffic laws and vehicle restrictions.

<div align="center">

<br />

<table>
  <tr>
    <td colspan="2" align="center">
      <img src="assets/screenshots/home-feed.png" alt="Roadie running on Android Automotive OS: 3-column responsive feed with category chips and floating sidebar rail" width="100%" /><br />
      <strong>Browse</strong> — Android TV-grade 3-column feed with category pills on Android Automotive OS (<code>1024×768</code>)
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="assets/screenshots/watch-split.png" alt="Roadie split Watch view with Media3 video player on the left and Up Next queue on the right" /><br />
      <strong>Watch</strong> — Media3 player on the left, Up Next queue on the right
    </td>
    <td width="50%" align="center">
      <img src="assets/screenshots/search-keyboard.png" alt="Roadie Search screen with built-in English and Arabic car touch keyboard" /><br />
      <strong>Search</strong> — Built-in English &amp; Arabic (<code>عربي</code>) car touch keyboard
    </td>
  </tr>
</table>

<br />

</div>

---

Every existing YouTube workaround for Android cars forces an ugly compromise: **APK patchers** break whenever YouTube ships new R8 obfuscation, **Xposed modules** demand root, and **WebView wrappers** feel sluggish on car hardware.

**Roadie** is built from scratch for car dashboards. It resolves streams directly via a clean-room InnerTube client, plays them hardware-accelerated through **Media3 (`ExoPlayer`)**, scales cleanly to compact `800×480` and widescreen `1280×720+` displays, and ships its own **English + Arabic touch keyboard** so search works even on head units with broken IMEs.

## Quick start

### Option 1: Any Phone + Car via Bluetooth (Audio + Steering-Wheel Controls)

1. Install `Roadie` on your Android phone (from **[Releases](https://github.com/bbadawee/Roadie/releases)** or **[GitHub Actions](https://github.com/bbadawee/Roadie/actions)**).
2. Connect your phone to your car via **Bluetooth** as usual.
3. Play any video in Roadie—audio, track metadata, and steering-wheel Play/Pause/Next/Previous controls work automatically (toggle **Drive** mode in the top bar for audio-only).

### Option 2: Any Phone + Car via USB / Wireless Android Auto (Audio Browse & Play)

Roadie implements a `MediaLibraryService` so you can browse feeds, search, and play audio directly from your car's Android Auto screen without root:

1. On your phone, open **Settings → Apps → Android Auto → Additional settings in the app** (or **Connected devices → Connection preferences → Android Auto**).
2. Scroll to the bottom and tap **Version** **10 times** until **Developer mode** is enabled.
3. Tap the **⋮** menu (top right) → **Developer settings** → check **Unknown sources**.
4. Connect your phone to your car via USB cable or wireless Android Auto and open **Roadie** in the car's media/app drawer.

> **Why is Android Auto audio-only from a phone?** Google's phone-to-car Android Auto projection protocol blocks all third-party `Activity` video UIs on non-rooted phones, even with Unknown Sources enabled. For **full video on your car screen**, use **Option 3** below (or your head unit's wireless/wired Screen Mirroring / SmartView while parked).

### Option 3: Full Video on Car Screen (Android Head Unit, AI Box, or AAOS)

- **Aftermarket Android Head Units** (Teyes, Atoto, Joying, Dasaita, etc.) & **CarPlay/AA Android AI Boxes** (Carlinkit, Ottocast, etc.): Download the APK directly or copy via USB drive and tap **Install** for full native video + touch UI.
- **Android Automotive OS (AAOS) / Emulator:** Enable **USB/Wireless Debugging** and run:

```bash
adb install -r app-debug.apk
```

### Option 4: Build from source

Requires **JDK 17 or 21** and **Android SDK (API 35)**:

```bash
git clone https://github.com/bbadawee/Roadie.git
cd Roadie
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Highlights

<table>
<tbody>
<tr>
<td width="50%" valign="top">
<h3>Responsive 1024×600 Car Canvas</h3>
<p><code>CarViewportMetrics</code> scales compact head units to preserve a <code>1024×600</code> logical floor, switches Browse grids between 1, 2, and 3 columns, and unlocks a split <strong>Player + Up Next</strong> layout on wide dashboards.</p>
</td>
<td width="50%" valign="top">
<h3>Zero-Ad Media3 Playback</h3>
<p>Hardware-accelerated HLS and progressive playback via <code>androidx.media3</code>, paired with <code>RoadiePlaybackService</code> (<code>MediaSessionService</code>) for steering-wheel media keys and background audio.</p>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<h3>Built-in EN / عربي Car Keyboard</h3>
<p><code>CarTouchKeyboard</code> renders <code>50dp</code> driver-friendly keys inside Compose with instant <code>EN / عربي</code> switching, RTL/LTR entry bar, Arabic numerals (<code>١٢٣</code>), and long-press <code>أ / إ / آ</code> variants.</p>
</td>
<td width="50%" valign="top">
<h3>Clean-Room InnerTube Client</h3>
<p><code>InnerTubeClient</code> queries structured JSON endpoints (<code>browse</code>, <code>search</code>, <code>player</code>, <code>next</code>) with <code>OkHttp</code> and <code>kotlinx.serialization</code>—zero HTML scraping and zero Google Play Services.</p>
</td>
</tr>
</tbody>
</table>

---

## How Roadie compares

| Capability | **Roadie** | Patched YouTube APKs | WebView Wrappers |
| :--- | :---: | :---: | :---: |
| **Single-APK install (no root / no LSPosed)** | ✅ | ⚠️ Often needs root/LSPatch | ✅ |
| **Survives YouTube updates (no R8 breakage)** | ✅ | ❌ Breaks on obfuscation shifts | ✅ |
| **Native Media3 hardware playback** | ✅ | ✅ | ❌ |
| **Split Watch + Up-Next car layout** | ✅ | ❌ | ❌ |
| **Built-in English + Arabic car keyboard** | ✅ | ❌ | ❌ |
| **Works without Google Play Services** | ✅ | ❌ | ⚠️ |
| **100% open-source Kotlin + Compose** | ✅ | ⚠️ Patch scripts only | ✅ |

---

## How it works

```mermaid
flowchart LR
  Host["MainActivity (AAOS distractionOptimized)"] --> UI["RoadieScreens (Compose Car UI)"]
  UI --> Metrics["CarViewportMetrics (1024x600 floor)"]
  UI --> KB["CarTouchKeyboard (EN / AR / Symbols)"]
  UI --> IT["InnerTubeClient (Browse / Search / Player / Next)"]
  UI --> Exo["Media3 ExoPlayer + MediaSessionService"]
```

<details>
<summary><strong>Explore responsive breakpoints &amp; single-folder package layout</strong></summary>

### Responsive Breakpoints

| Logical Viewport | Browse Columns | Watch Layout |
| :--- | :---: | :--- |
| `< 500 dp` | `1` | Stacked (Player top, Queue bottom) |
| `500 – 779 dp` | `2` | Stacked |
| `>= 780 dp` | `3` | Split-screen when `width >= 820 dp && height >= 300 dp && landscape` |

### Package Structure

```text
Roadie/
├── app/src/main/
│   ├── AndroidManifest.xml                  # AAOS distractionOptimized & MediaSessionService
│   └── kotlin/io/roadie/
│       ├── app/
│       │   ├── MainActivity.kt              # Edge-to-edge Compose host
│       │   └── RoadiePlaybackService.kt     # Media3 background & steering-wheel service
│       ├── carui/
│       │   ├── CarViewportMetrics.kt        # 1024x600 scaling & layout breakpoints
│       │   ├── CarTheme.kt                  # High-contrast automotive dark palette
│       │   ├── RoadieViewModel.kt           # State management (Home, Search, Watch, Parked)
│       │   └── RoadieScreens.kt             # Sidebar rail, Browse grid, Split Watch + Queue
│       ├── innertube/
│       │   ├── InnerTubeModels.kt           # VideoItem & ResolvedStream models
│       │   └── InnerTubeClient.kt           # Structured InnerTube API client
│       └── keyboard/
│           └── CarTouchKeyboard.kt          # EN / AR / Symbols car keyboard
└── app/src/test/kotlin/io/roadie/           # Unit tests for layout metrics & JSON parsing
```

</details>

---

## FAQ

<details>
<summary><strong>Does Roadie work on phone-projected Android Auto?</strong></summary>

Standard phone-to-car Android Auto projection restricts third-party apps to template/audio APIs and blocks arbitrary video surfaces. Roadie is built for **Android Automotive OS (AAOS)** and **Android car head units** where apps render directly on the dashboard screen. See [Compatibility](docs/COMPATIBILITY.md).

</details>

<details>
<summary><strong>Why build a clean-room client instead of patching the YouTube APK?</strong></summary>

On Android, the official YouTube APK is heavily R8/ProGuard-obfuscated on every release, causing class/method hooks to break constantly. A clean-room Kotlin + Media3 client installs as a single APK, survives updates, and stays easy for contributors to hack on.

</details>

<details>
<summary><strong>Does it block ads and work without Google Play Services?</strong></summary>

Yes. Roadie resolves streams directly via InnerTube and plays them with `Media3` (`ExoPlayer`), bypassing ad insertion and requiring zero Google Play Services.

</details>

---

## Roadmap

- [x] **SponsorBlock** segment auto-skipping in `ExoPlayer`
- [x] **AAOS `CarUxRestrictionsManager`** live driving-state listener
- [x] **Local Subscriptions & Watch History** (Room DB, zero-account privacy mode)
- [x] **Rotary Knob / D-Pad Focus Navigation** for non-touch OEM dashboards

---

## Contributing & Community

If Roadie helps you or inspires your own car project, **⭐ star the repository** so more drivers and Android devs can find it!

PRs, head-unit display reports, and feature ideas are welcome—see [CONTRIBUTING.md](CONTRIBUTING.md), [BUILD.md](docs/BUILD.md), [COMPATIBILITY.md](docs/COMPATIBILITY.md), [ARCHITECTURE.md](docs/ARCHITECTURE.md), and [SECURITY.md](SECURITY.md).

---

<sub>YouTube and Android Automotive are trademarks of their respective owners. Roadie is an independent open-source project licensed under GPL-3.0.</sub>
