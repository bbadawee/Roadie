# Changelog

## 0.4.0

- **Google OAuth TV Device-Code Sign-In:** Sign in via `google.com/device` on your phone without requiring Google Play Services on the head unit
- **YouTube Playlists & Subscriptions Sync:** Browse your personal playlists in the `Saved` tab and sync subscriptions
- **Watch Resolution Selector & Saved Bookmarks:** Pick `Auto / 1080p / 720p / 480p / 360p` and save videos locally via Room DB
- **Head-Unit Release & Startup Fixes:** Hardened R8/ProGuard rules, safe `enableEdgeToEdge()`, finite viewport scaling, and split-screen tests

## 0.3.0

- **DeArrow, Chapters, Queue & Companion:** Clean titles, clickable description chapters, Up-Next queue, Takeout/NewPipe import, Audio-Only, Hotspot Saver, and Wi-Fi Companion

## 0.2.0

- **Resume Playback Position:** Automatically saves and resumes `lastPositionMs` per video via Room DB
- **Playback Speed Selector (`0.75x – 2x`):** One-tap speed pill on the Watch screen
- **Drive Audio-Only Bandwidth Saver:** Streams audio-only (`audio/mp4`) when switched to `Drive` mode
- **Android Auto `MediaLibraryService` Folders:** Browse `Home`, `Subscriptions`, and `History` directly on Android Auto
- **SponsorBlock Auto-Skip, `CarUxRestrictionsManager`, Room Subscriptions/History & Rotary Focus**

## 0.1.0

- Initial clean-room Android Automotive & car head-unit release (`io.roadie.app`)
- Responsive `1024×600` car UI with `1–3` column Browse grid and split Watch + Up-Next queue
- Built-in English & Arabic `CarTouchKeyboard` with `أ / إ / آ` variants
- Clean-room InnerTube client (`Home`, `Search`, `Player`, `Next`) + `Media3` playback & `MediaLibraryService`
