# HORIZON Mobile

Android port of the original **Horizon Rust client**. This repository keeps the Rust game client as the runtime; it does not substitute a separate Java launcher such as Amethyst.

## Android build

The Android app consists of:
- A native Android launcher shell with CHEATLAB account connection.
- The original Horizon Rust client compiled for Android arm64 and hosted through Android NativeActivity.
- A black-and-white mobile interface for Play, versions, files and settings.
- Android touch navigation, local profiles and Minecraft-folder access.

GitHub Actions builds the Rust Android APK on pushes to `main`:

- Workflow: `.github/workflows/android.yml`
- Artifact: `horizon-mobile-rust-engine-debug-apk`
- APK output: `android-app/app/build/outputs/apk/debug/app-debug.apk`

## Current implementation status

The Rust native client and Android wrapper compile into the APK. The launcher can start that native client after CHEATLAB sign-in.

This is **not yet a complete general-purpose Minecraft Java Edition installer**. The current APK does not automatically download and provision every Mojang version, Java runtimes, libraries and assets. The original Rust client has its own game/resource requirements; the Android port still needs the remaining resource provisioning, touch HUD/input integration and on-device gameplay testing before it can be described as fully playable.

CHEATLAB authentication is separate from Microsoft/Minecraft authentication and does not grant a Minecraft Java Edition license. The app does not bypass Microsoft sign-in, ownership checks or bundle proprietary Minecraft game assets.

## Build locally

Requires JDK 17, Android SDK Platform 35, NDK 27.2.12479018, Rust stable with `aarch64-linux-android`, and cargo-ndk 3.5.1.

```bash
git clone --recurse-submodules https://github.com/tatarhost/Minecraft-rust-mobil.git
python3 scripts/prepare-android-engine.py upstream
gradle --no-daemon -p android-app :app:assembleDebug
```

## Source and licensing

- Original Rust project: [tr4m0ryp/Minecraft-rust](https://github.com/tr4m0ryp/Minecraft-rust)
- Android port: [tatarhost/Minecraft-rust-mobil](https://github.com/tatarhost/Minecraft-rust-mobil)
- The upstream source is pinned as a Git submodule. Its MIT license and attribution are preserved.
- No proprietary Minecraft JARs, textures, sounds or other game assets are bundled.
