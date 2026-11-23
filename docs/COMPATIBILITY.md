# Compatibility & Car Testing Guide

## Supported Targets

| Environment | Status | How to Install & Use |
| :--- | :--- | :--- |
| **Any Phone + Bluetooth** | **Supported (Audio)** | Install Roadie on your phone and pair via Bluetooth. Audio, track metadata, and steering-wheel controls work automatically. |
| **Any Phone + USB / Wireless Android Auto** | **Supported (Audio Browse & Play)** | Enable **Android Auto Developer Settings → Unknown Sources** on your phone. Roadie's `MediaLibraryService` lets you browse, search, and play audio from the car screen. *(Video UI is blocked by Google's Android Auto projection protocol on non-rooted phones.)* |
| **Aftermarket Android Head Units** (API 26+) | **Supported (Full Video)** | Download the APK directly or copy via USB drive and tap **Install**. Adapts from `800×480` to `1280×720+`. |
| **Android AI Boxes** (Carlinkit, Ottocast, etc.) | **Supported (Full Video)** | Plug into your car's factory wired CarPlay/Android Auto USB port, install the Roadie APK, and run full-screen on the OEM display. |
| **Android Automotive OS (AAOS)** | **Supported (Experimental)** | Install via `adb install -r app-debug.apk` on developer-unlocked units or the AAOS emulator. OEM-locked Google Built-In vehicles require Google Play Internal/Open Testing distribution. |

## Layout Thresholds

- **Browse grid:** `3` columns (`>= 780dp`), `2` columns (`500–779dp`), `1` column (`< 500dp`).
- **Split watch mode:** `width >= 820dp && height >= 300dp && width > height`.
- **Fullscreen mode:** Tap the top-right fullscreen icon or the player's bottom-right fullscreen toggle to hide the top bar, mini-rail, and `Up next` queue.
