# Mobile port status

## Implemented and build-verified

- The requested Rust source is pinned in the `upstream/` Git submodule.
- Native Android app (minSdk 23, compile SDK 35) with four launcher sections: Play, Versions, Files and Settings.
- First launch forces the existing CHEATLAB account page. It cannot be dismissed before login.
- CHEATLAB's real registration/login flow remains on the website, including the ring CAPTCHA and email OTP verification.
- The app reads the resulting session from the CHEATLAB WebView origin, validates it against `/api/auth/me`, and encrypts the app copy with Android Keystore AES-GCM.
- Saved account state survives app restarts and device reboots. If the API is temporarily unreachable, the local encrypted session is retained rather than erased. A server-rejected session is cleared and login is requested again.
- Logout calls the CHEATLAB logout API and clears the app's local encrypted session.
- Versions page fetches release metadata from the Horizon GitHub releases API. It does not download desktop executables as Android packages.
- Files page can persistently select the player's own Minecraft folder via Android Storage Access Framework.
- Local profile directories and the touch/controller/keyboard preferences are stored in app-private storage.
- GitHub Actions builds and uploads `horizon-mobile-debug-apk`. The latest UI/account build passed `assembleDebug` and artifact upload.

## Still not ported: required for a playable game

The original launcher uses GPUI, a desktop UI framework, and its native windowing, process and keychain integrations. Those components cannot be compiled unchanged into the Android application.

The Rust game client is a separate winit/wgpu application. The upstream project has reusable Android input-state modules (gamepad/keypad/touch), but these modules alone do not create an Android application entry point or complete game runtime. Remaining engineering work includes:

1. A real Android NativeActivity / android_main entry point.
2. Correct pause/resume and surface recreation handling for the winit event loop.
3. Building the full Rust game dependency graph for Android arm64 with NDK and Vulkan/GLES.
4. Connecting Android touch, controller and keyboard events to the game input loop.
5. Android audio integration and device GPU compatibility testing.
6. Storage/SAF import of user-owned Minecraft resources and safe preparation.
7. Secure Microsoft OAuth and Minecraft ownership/session handoff for online play.
8. Packaging the Rust runtime in the APK and end-to-end testing on physical Android devices.

The current APK is a functional launcher shell with persistent CHEATLAB binding, release discovery and local file/profile management. It does not yet start the game; Play remains disabled until the Android runtime above exists and is tested.

## Authentication boundary

CHEATLAB identifies the launcher account. It does not replace Microsoft authentication or Minecraft Java Edition ownership checks. No game-license or server-authentication bypass is implemented.
