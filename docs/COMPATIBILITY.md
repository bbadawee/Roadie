# Compatibility & Car Testing Guide

## Supported Targets

| Environment | Status | How to Install & Test |
| :--- | :--- | :--- |
| **Aftermarket Android Head Units** (API 26+) | **Supported** | Download the APK directly or copy via USB drive and tap **Install**. Adapts from `800×480` to `1280×720+`. |
| **Android AI Boxes** (Carlinkit, Ottocast, etc.) | **Supported** | Plug into your car's factory wired CarPlay/Android Auto USB port, install the Roadie APK, and run full-screen on the OEM display. |
| **Android Automotive OS (AAOS)** | **Supported (Experimental)** | Install via `adb install -r app-debug.apk` on developer-unlocked units or the Android Studio AAOS emulator. OEM-locked Google Built-In vehicles require Google Play Internal/Open Testing distribution. |
| **Android Tablets & Phones** | **Supported** | Great for testing; portrait phones automatically switch to the stacked Watch + `Up next` layout. |
| **Phone-Projected Android Auto** | **Not Supported for Video** | Android Auto projection restricts third-party apps to `CarAppService` templates and audio `MediaLibraryService` and blocks arbitrary `Activity` video rendering even when Unknown Sources is enabled. Use an **Android AI Box** for stock factory screens. |

## Layout Thresholds

- **Browse grid:** `3` columns (`>= 780dp`), `2` columns (`500–779dp`), `1` column (`< 500dp`).
- **Split watch mode:** `width >= 820dp && height >= 300dp && width > height`.
- **Fullscreen mode:** Tap the top-right fullscreen icon or the player's bottom-right fullscreen toggle to hide the top bar, mini-rail, and `Up next` queue.
