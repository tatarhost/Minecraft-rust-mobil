#!/usr/bin/env python3
"""Apply the Android-only runtime adaptation to the pinned Horizon source tree."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream").resolve()
if not (root / "Cargo.toml").is_file():
    raise SystemExit(f"not an upstream Horizon checkout: {root}")

def replace_once(relative, old, new):
    path = root / relative
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{relative}: expected one patch anchor, found {count}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(
    "crates/horizon-client/src/lib.rs",
    "#![forbid(unsafe_code)]\n",
    """#![forbid(unsafe_code)]

// Android embeds the existing game loop in the client library.
#[cfg(target_os = "android")]
extern crate self as horizon_client;
#[cfg(target_os = "android")]
#[path = "game.rs"]
mod android_game;
""",
)

lib_path = root / "crates/horizon-client/src/lib.rs"
lib_text = lib_path.read_text(encoding="utf-8")
if "pub fn run_android(app:" not in lib_text:
    lib_text += r'''

/// Start the real Horizon client from Android NativeActivity.
#[cfg(target_os = "android")]
pub fn run_android(app: winit::platform::android::activity::AndroidApp) -> Result<()> {
    let app_data = app.internal_data_path().ok_or_else(|| {
        std::io::Error::new(std::io::ErrorKind::NotFound, "Android app-private data path is unavailable")
    })?;
    let game_home = app_data.join("horizon").join("game");
    std::fs::create_dir_all(&game_home)?;
    std::fs::create_dir_all(app_data.join("horizon").join("resources"))?;
    std::env::set_current_dir(&game_home)?;

    logging::set_game_home(game_home.clone());
    android_game::set_android_app(app);
    horizon_threads::configure_allocator();
    telemetry::mark_start();
    logging::init(&game_home, "Render thread");
    log::info!("Horizon Android client starting");
    crash::install(game_home, "client");
    android_game::run()
}
'''
    lib_path.write_text(lib_text, encoding="utf-8")

game_file = "crates/horizon-client/src/game.rs"
replace_once(
    game_file,
    "#[derive(Clone)]\nstruct Options {",
    r'''#[cfg(target_os = "android")]
static ANDROID_APP: std::sync::OnceLock<winit::platform::android::activity::AndroidApp> =
    std::sync::OnceLock::new();

#[cfg(target_os = "android")]
pub(crate) fn set_android_app(app: winit::platform::android::activity::AndroidApp) {
    let _ = ANDROID_APP.set(app);
}

#[cfg(target_os = "android")]
fn new_event_loop() -> Result<EventLoop<()>> {
    use winit::platform::android::EventLoopBuilderExtAndroid;
    let app = ANDROID_APP.get().cloned().ok_or_else(|| {
        std::io::Error::new(std::io::ErrorKind::NotFound, "AndroidApp was not supplied by NativeActivity")
    })?;
    let mut builder = EventLoop::builder();
    builder.with_android_app(app);
    Ok(builder.build()?)
}

#[cfg(not(target_os = "android"))]
fn new_event_loop() -> Result<EventLoop<()>> {
    Ok(EventLoop::new()?)
}

#[cfg(target_os = "android")]
fn default_resources() -> PathBuf {
    ANDROID_APP
        .get()
        .and_then(|app| app.internal_data_path())
        .unwrap_or_else(|| PathBuf::from("."))
        .join("horizon")
        .join("resources")
}

#[cfg(not(target_os = "android"))]
fn default_resources() -> PathBuf {
    let sibling = std::env::current_exe()
        .ok()
        .and_then(|exe| exe.parent().map(|dir| dir.join("resources")))
        .unwrap_or_else(|| PathBuf::from("resources"));
    if sibling.join("horizon-resources.json").exists() {
        sibling
    } else {
        PathBuf::from(".local/horizon-client").join(horizon_core::VANILLA_VERSION)
    }
}

#[derive(Clone)]
struct Options {''',
)

replace_once(
    game_file,
    """        let sibling = std::env::current_exe()?
            .parent()
            .ok_or("missing executable directory")?
            .join("resources");
        let mut o = Self {
            resources: if sibling.join("horizon-resources.json").exists() {
                sibling
            } else {
                std::path::Path::new(".local/horizon-client").join(horizon_core::VANILLA_VERSION)
            },""",
    """        let mut o = Self {
            resources: default_resources(),""",
)

game_path = root / game_file
game_text = game_path.read_text(encoding="utf-8")
count = game_text.count("EventLoop::new()?.run_app(")
if count != 2:
    if count == 0 and game_text.count("new_event_loop()?.run_app(") == 2:
        pass
    else:
        raise SystemExit(f"{game_file}: expected two event-loop start sites, found {count}")
else:
    game_path.write_text(game_text.replace("EventLoop::new()?.run_app(", "new_event_loop()?.run_app("), encoding="utf-8")

clipboard_file = "crates/horizon-client-input/src/clipboard.rs"
replace_once(
    clipboard_file,
    r'''/// The system clipboard (ClipboardManager). On Wayland, arboard uses the
/// data-control protocol and falls back to X11 (XWayland) on compositors
/// without it. When no system clipboard can be reached, text copied in the
/// game can still be pasted in the game.
#[derive(Default)]
pub struct SystemClipboard {
    system: Option<arboard::Clipboard>,
    /// The text Horizon last copied (horizon-ctl status).
    copied: Option<String>,
}

impl SystemClipboard {
    /// The text last copied by the game.
    pub fn copied(&self) -> Option<&str> {
        self.copied.as_deref()
    }
    fn system(&mut self) -> Option<&mut arboard::Clipboard> {
        if self.system.is_none() {
            self.system = arboard::Clipboard::new().ok();
        }
        self.system.as_mut()
    }
}

impl Clipboard for SystemClipboard {
    fn get(&mut self) -> String {
        let Some(system) = self.system() else {
            return self.copied.clone().unwrap_or_default();
        };
        match system.get_text() {
            Ok(text) => text,
            Err(arboard::Error::ContentNotAvailable) => String::new(),
            Err(_) => {
                // A lost connection (a restarted compositor): reconnect next time.
                self.system = None;
                String::new()
            }
        }
    }
    fn set(&mut self, text: &str) {
        if text.is_empty() {
            return;
        }
        self.copied = Some(text.to_owned());
        if let Some(system) = self.system()
            && system.set_text(text.to_owned()).is_err()
        {
            self.system = None;
        }
    }
}''',
    r'''/// Desktop system clipboard.
#[cfg(not(target_os = "android"))]
#[derive(Default)]
pub struct SystemClipboard {
    system: Option<arboard::Clipboard>,
    copied: Option<String>,
}

#[cfg(not(target_os = "android"))]
impl SystemClipboard {
    pub fn copied(&self) -> Option<&str> {
        self.copied.as_deref()
    }
    fn system(&mut self) -> Option<&mut arboard::Clipboard> {
        if self.system.is_none() {
            self.system = arboard::Clipboard::new().ok();
        }
        self.system.as_mut()
    }
}

#[cfg(not(target_os = "android"))]
impl Clipboard for SystemClipboard {
    fn get(&mut self) -> String {
        let Some(system) = self.system() else {
            return self.copied.clone().unwrap_or_default();
        };
        match system.get_text() {
            Ok(text) => text,
            Err(arboard::Error::ContentNotAvailable) => String::new(),
            Err(_) => {
                self.system = None;
                String::new()
            }
        }
    }
    fn set(&mut self, text: &str) {
        if text.is_empty() {
            return;
        }
        self.copied = Some(text.to_owned());
        if let Some(system) = self.system()
            && system.set_text(text.to_owned()).is_err()
        {
            self.system = None;
        }
    }
}

/// Android in-game clipboard fallback. Text remains available to in-game paste.
#[cfg(target_os = "android")]
#[derive(Default)]
pub struct SystemClipboard {
    copied: Option<String>,
}

#[cfg(target_os = "android")]
impl SystemClipboard {
    pub fn copied(&self) -> Option<&str> {
        self.copied.as_deref()
    }
}

#[cfg(target_os = "android")]
impl Clipboard for SystemClipboard {
    fn get(&mut self) -> String {
        self.copied.clone().unwrap_or_default()
    }
    fn set(&mut self, text: &str) {
        if !text.is_empty() {
            self.copied = Some(text.to_owned());
        }
    }
}''',
)

input_cargo = root / "crates/horizon-client-input/Cargo.toml"
cargo_text = input_cargo.read_text(encoding="utf-8")
arboard = 'arboard = { version = "3.6.1", default-features = false, features = ["wayland-data-control"] }\n'
if arboard in cargo_text:
    cargo_text = cargo_text.replace(arboard, "")
if "[target.'cfg(not(target_os = \"android\"))'.dependencies]" not in cargo_text:
    cargo_text += '\n[target.\'cfg(not(target_os = "android"))\'.dependencies]\n' + arboard
input_cargo.write_text(cargo_text, encoding="utf-8")

for rel in ("crates/horizon-client/Cargo.toml", "crates/horizon-client-render/Cargo.toml"):
    p = root / rel
    text = p.read_text(encoding="utf-8")
    text = text.replace('features = ["std", "parking_lot", "vulkan", "metal", "wgsl", "naga-ir"]',
                        'features = ["std", "parking_lot", "vulkan", "wgsl", "naga-ir"]')
    if '[target.\'cfg(target_os = "macos")\'.dependencies]' not in text:
        text += '\n[target.\'cfg(target_os = "macos")\'.dependencies]\n'
        text += 'wgpu = { version = "30.0.1", default-features = false, features = ["std", "parking_lot", "metal", "wgsl", "naga-ir"] }\n'
    p.write_text(text, encoding="utf-8")

client_cargo = root / "crates/horizon-client/Cargo.toml"
text = client_cargo.read_text(encoding="utf-8")
if '[target.\'cfg(target_os = "android")\'.dependencies]' not in text:
    text += '\n[target.\'cfg(target_os = "android")\'.dependencies]\nwinit = { version = "0.30.13", features = ["android-native-activity"] }\n'
client_cargo.write_text(text, encoding="utf-8")

print("Android runtime adaptations applied to", root)
