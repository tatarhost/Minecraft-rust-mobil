# Horizon Mobile

Open-source Android launcher project for **Horizon / Minecraft-rust**.

The requested source repository is retained as a pinned Git submodule in `upstream/` at `2381ba865fee7292e263794c827fe694d4851bdf`. Its MIT license and attribution are preserved. This repo's Android app is written in Java and built with Gradle.

## Android app features currently implemented

- Native black-and-white launcher UI with Play, Versions, Files and Settings sections.
- First launch opens the real CHEATLAB sign-in / registration page.
- Registration and login use CHEATLAB's own CAPTCHA and email-code verification.
- The app validates the resulting account session against the CHEATLAB Worker API.
- A validated session is encrypted using Android Keystore AES-GCM and retained across app restarts and device reboots. A network outage does not erase the local session.
- Logout revokes the server session and clears the local encrypted copy.
- Versions page reads real Horizon release metadata from GitHub and clearly distinguishes desktop releases from Android builds.
- Files page lets the user grant persistent access to their own Minecraft folder through Android's Storage Access Framework.
- Local profile folders can be created and removed inside the app's private storage.
- Touch, controller and keyboard preference switches are saved locally for the later game runtime.
- GitHub Actions builds and uploads the debug APK on every push to `main`.

## Build

Requires JDK 17, Android SDK Platform 35 and Gradle 8.9:

```bash
git clone --recurse-submodules https://github.com/tatarhost/Minecraft-rust-mobil.git
gradle -p android-app :app:assembleDebug
```

APK output:

`android-app/app/build/outputs/apk/debug/app-debug.apk`

Or download `horizon-mobile-debug-apk` from **Actions → Android APK → Artifacts**.

## Game runtime status

**This is not yet a playable Minecraft Android build.** The desktop launcher uses GPUI, which is not an Android UI toolkit. The Rust game client is separate and uses winit/wgpu; it still needs a native Android entry point, Android lifecycle and surface handling, target-specific dependency/build integration, touch HUD wiring, Android storage integration and device testing. The Play button is intentionally disabled until a real Android game runtime is compiled and packaged.

The mobile app currently implements the launcher shell, CHEATLAB account binding, release metadata, local profile folders and Minecraft-folder selection. It does not claim that those profile folders are playable game instances.

CHEATLAB authentication is separate from Microsoft/Minecraft authentication and does not grant a Minecraft Java Edition license. Online Minecraft play must continue to use the supported Microsoft sign-in and ownership checks. The app does not bypass either system and does not bundle Mojang game files.

## Repositories and licensing

- Requested source: [tr4m0ryp/Minecraft-rust](https://github.com/tr4m0ryp/Minecraft-rust)
- Mobile app: [tatarhost/Minecraft-rust-mobil](https://github.com/tatarhost/Minecraft-rust-mobil)
- The pinned source submodule makes upstream changes explicit and reviewable.
- The original project's MIT notices remain in `upstream/LICENSE` and this repository's `LICENSE`.
- The Android build contains no Minecraft JARs, textures, sounds or other proprietary game assets.
