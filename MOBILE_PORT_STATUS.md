# Mobile port status

## Completed in this milestone

- The upstream Rust project is linked as a pinned Git submodule at \`upstream/\`.
- A native Android application project is configured for API 23+ / compile SDK 35.
- The app can open the existing CHEATLAB account UI on \`cheatlab.fun\`.
- The website remains responsible for its real registration/login, ring CAPTCHA, and email OTP flow.
- The app reads the website's resulting session from its own WebView origin, validates it against \`/api/auth/me\`, and stores the app copy encrypted with an Android Keystore AES-GCM key.
- The app can revalidate and revoke that CHEATLAB session.
- A GitHub Actions workflow builds and uploads a debug APK.

## Not yet ported

The original launcher is a desktop GPUI application. Its screens, OS keychain integration, file pickers, window management, process launching, and desktop-specific services cannot be compiled as an Android app unchanged.

The game client is a separate Rust crate built around winit/wgpu. Android support still needs real implementation and verification for:

1. Android native activity entry point and lifecycle pause/resume/surface recreation.
2. Android target compilation for the full Rust workspace and all transitive crates.
3. Vulkan/OpenGL ES surface and device selection on Android GPUs.
4. Storage paths, scoped storage, SAF import/export, and cache cleanup.
5. Touch controls, virtual keyboard, focus, controller mapping, and mobile HUD.
6. Packaging the Rust game runtime as an Android native library and connecting it to the app.
7. Microsoft OAuth, Minecraft ownership verification, and secure session handoff for online play.

The current APK is an account-linked Android shell. It does not yet start the Rust game. The Play button is intentionally disabled rather than pretending the runtime is available.

## Authentication boundary

CHEATLAB authentication grants access to CHEATLAB-linked launcher features only. It is independent from Microsoft/Minecraft authentication. The app does not generate or bypass Minecraft ownership, Microsoft OAuth, or server authentication.
