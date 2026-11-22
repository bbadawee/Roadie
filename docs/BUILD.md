# Build & Installation Guide

## Prerequisites

- **JDK 17 or 21**
- **Android SDK** with **Platform API 35** and **Build-Tools 35.0.0**
- Optional: `adb` for installing onto a head unit or emulator

## Build from Command Line

```sh
git clone https://github.com/bbadawee/Roadie.git
cd Roadie
./gradlew testDebugUnitTest assembleDebug
```

Output APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build a Minified Release APK

```sh
./gradlew assembleRelease
```

*(Configure a signing key in `app/build.gradle.kts` before distributing release builds.)*

## Install on a Device or AAOS Emulator

1. Enable **USB Debugging** (or Wireless ADB) on your Android head unit or AAOS emulator.
2. Run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
