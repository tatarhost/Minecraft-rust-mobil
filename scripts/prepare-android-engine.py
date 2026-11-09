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

# Expose the game module from the existing client library on Android only.
replace_once(
    "crates/horizon-client/src/lib.rs",
    "#![forbid(unsafe_code)]\n",
    """#![forbid(unsafe_code)]

#[cfg(target_os = "android")]
extern crate self as horizon_client;
#[cfg(target_os = "android")]
#[path = "game/mod.rs"]
pub mod game;
""",
)

lib_path = root / "crates/horizon-client/src/lib.rs"
lib_text = lib_path.read_text(encoding="utf-8")
if "pub fn run_android(app:" not in lib_text:
    lib_text += r'''

/// Native Android entry point for the original Horizon game loop.
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
    game::set_android_app(app);
    horizon_threads::configure_allocator();
    telemetry::mark_start();
    logging::init(&game_home, "Render thread");
    log::info!("Horizon Android Rust client starting");
    crash::install(game_home, "client");
    game::run()
}
'''
    lib_path.write_text(lib_text, encoding="utf-8")

lib_text = lib_path.read_text(encoding="utf-8")
if "pub fn set_android_open_uri_handler" not in lib_text:
    lib_text += r'''

/// The Android wrapper owns JNI and registers the platform URL opener here.
#[cfg(target_os = "android")]
static ANDROID_OPEN_URI_HANDLER: std::sync::OnceLock<fn(&str)> = std::sync::OnceLock::new();

#[cfg(target_os = "android")]
pub fn set_android_open_uri_handler(handler: fn(&str)) {
    let _ = ANDROID_OPEN_URI_HANDLER.set(handler);
}

/// Open a URL using the Android wrapper's system-browser bridge.
#[cfg(target_os = "android")]
pub fn open_android_uri(uri: &str) {
    if let Some(handler) = ANDROID_OPEN_URI_HANDLER.get() {
        handler(uri);
    } else {
        log::error!("Horizon: Android URL handler is not registered");
    }
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
    "crates/horizon-client-input/src/keybinds.rs",
    r'''pub fn learn_layout(event: &winit::event::KeyEvent) {
    use winit::keyboard::{Key, PhysicalKey};
    use winit::platform::modifier_supplement::KeyEventExtModifierSupplement;
    let PhysicalKey::Code(code) = event.physical_key else {
        return;
    };
    let Some(name) = key_name(code) else {
        return;
    };
    let learn = horizon_client_core::key_display::learn_layout;
    match event.key_without_modifiers() {
        Key::Character(text) => learn(name, &text),
        Key::Dead(Some(c)) => learn(name, c.encode_utf8(&mut [0; 4])),
        _ => {}
    }
}''',
    r'''pub fn learn_layout(event: &winit::event::KeyEvent) {
    use winit::keyboard::PhysicalKey;
    let PhysicalKey::Code(code) = event.physical_key else {
        return;
    };
    let Some(name) = key_name(code) else {
        return;
    };
    let learn = horizon_client_core::key_display::learn_layout;

    #[cfg(not(target_os = "android"))]
    {
        use winit::keyboard::Key;
        use winit::platform::modifier_supplement::KeyEventExtModifierSupplement;
        match event.key_without_modifiers() {
            Key::Character(text) => learn(name, &text),
            Key::Dead(Some(c)) => learn(name, c.encode_utf8(&mut [0; 4])),
            _ => {}
        }
    }

    #[cfg(target_os = "android")]
    {
        use winit::keyboard::Key;
        match &event.logical_key {
            Key::Character(text) => learn(name, text),
            Key::Dead(Some(c)) => learn(name, (*c).encode_utf8(&mut [0; 4])),
            _ => {}
        }
    }
}''',
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

# The game shows a native missing-resources screen before a world can start.
# It shares the same Android event loop builder as the regular loading screen.
missing_screen = root / "crates/horizon-client/src/game/no_resources.rs"
missing_text = missing_screen.read_text(encoding="utf-8")
missing_text = missing_text.replace("EventLoop::new()?.run_app(", "new_event_loop()?.run_app(")
missing_screen.write_text(missing_text, encoding="utf-8")

clipboard_file = "crates/horizon-client-input/src/clipboard.rs"
clipboard_path = root / clipboard_file
clipboard_text = clipboard_path.read_text(encoding="utf-8")
start = clipboard_text.find("/// The system clipboard")
end = clipboard_text.find("/// A clipboard in memory.", start)
if start < 0 or end < 0:
    if "pub struct SystemClipboard" not in clipboard_text:
        raise SystemExit("clipboard.rs: could not locate SystemClipboard")
else:
    android_clipboard = r'''/// Desktop system clipboard.
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

/// Android in-game clipboard fallback.
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
}

'''
    clipboard_text = clipboard_text[:start] + android_clipboard + clipboard_text[end:]
    clipboard_path.write_text(clipboard_text, encoding="utf-8")

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

# Android browser launching belongs to the NativeActivity wrapper (which is allowed
# to use JNI); the core game client keeps its forbid(unsafe_code) guarantee.
replace_once(
    "crates/horizon-client/src/game/packs.rs",
    r'''pub fn open_uri(uri: &str) {
    // URLs need Windows' registered protocol handler, not Explorer's path
    // handling. ShellExecute also preserves the URL as one Unicode value,
    // without interpolating its query string into a command interpreter.
    #[cfg(windows)]
    if let Err(error) = open::that_detached(uri) {
        log::warn!("Horizon: could not open URI {uri}: {error}");
    }
    #[cfg(not(windows))]
    open_folder(Path::new(uri));
}''',
    r'''pub fn open_uri(uri: &str) {
    #[cfg(target_os = "android")]
    horizon_client::open_android_uri("horizon://launcher");

    // URLs need Windows' registered protocol handler, not Explorer's path
    // handling. ShellExecute also preserves the URL as one Unicode value,
    // without interpolating its query string into a command interpreter.
    #[cfg(windows)]
    if let Err(error) = open::that_detached(uri) {
        log::warn!("Horizon: could not open URI {uri}: {error}");
    }

    #[cfg(all(not(windows), not(target_os = "android")))]
    open_folder(Path::new(uri));
}''',
)


# The Android game is hosted by this same APK's launcher Activity. Route the
# missing-resources action back into the installed launcher instead of a website.
replace_once(
    "crates/horizon-client/src/no_resources.rs",
    'pub const DOWNLOAD_URL: &str = "https://playhorizon.dev";',
    'pub const DOWNLOAD_URL: &str = "horizon://launcher";',
)
replace_once(
    "crates/horizon-client/src/no_resources.rs",
    '"Download the launcher at playhorizon.dev, then press Play there.",',
    '"Return to the installed Horizon launcher and prepare your game files.",',
)
replace_once(
    "crates/horizon-client/src/no_resources.rs",
    '("Download the launcher", Action::Download),',
    '("Return to the launcher", Action::Download),',
)

# Native Android taps arrive as Winit Touch events, not necessarily MouseInput.
replace_once(
    "crates/horizon-client/src/game/no_resources.rs",
    "use winit::event::MouseButton;",
    "use winit::event::{MouseButton, TouchPhase};",
)
replace_once(
    "crates/horizon-client/src/game/no_resources.rs",
    r'''            WindowEvent::CursorMoved { position, .. } => {
                self.pointer = (position.x, position.y);
                window.request_redraw();
            }
            WindowEvent::MouseInput {''',
    r'''            WindowEvent::CursorMoved { position, .. } => {
                self.pointer = (position.x, position.y);
                window.request_redraw();
            }
            WindowEvent::Touch(touch) => {
                self.pointer = (touch.location.x, touch.location.y);
                window.request_redraw();
                if touch.phase == TouchPhase::Ended {
                    self.click(event_loop);
                }
            }
            WindowEvent::MouseInput {''',
)


# Build the NativeActivity wrapper as a member of Horizon's own workspace so
# Cargo uses the pinned upstream lockfile and resolves all path crates together.
workspace_root = Path(__file__).resolve().parents[1]
wrapper_source = workspace_root / "android-engine"
wrapper_target = root / "crates/horizon-android-engine"
(wrapper_target / "src").mkdir(parents=True, exist_ok=True)
wrapper_cargo = (wrapper_source / "Cargo.toml").read_text(encoding="utf-8")
wrapper_cargo = wrapper_cargo.replace("../upstream/crates/horizon-client", "../horizon-client")
wrapper_cargo = wrapper_cargo.replace("../upstream/crates/horizon-threads", "../horizon-threads")
if 'jni = "0.21.1"' not in wrapper_cargo:
    wrapper_cargo += '\njni = "0.21.1"\n'
(wrapper_target / "Cargo.toml").write_text(wrapper_cargo, encoding="utf-8")
(wrapper_target / "src/lib.rs").write_text(
    (wrapper_source / "src/lib.rs").read_text(encoding="utf-8"),
    encoding="utf-8",
)
workspace_manifest = root / "Cargo.toml"
workspace_text = workspace_manifest.read_text(encoding="utf-8")
member = '    "crates/horizon-android-engine",\n'
if member not in workspace_text:
    anchor = '    "crates/horizon-client",\n'
    if anchor not in workspace_text:
        raise SystemExit("upstream workspace member anchor was not found")
    workspace_text = workspace_text.replace(anchor, anchor + member, 1)
    workspace_manifest.write_text(workspace_text, encoding="utf-8")

game_source = root / "crates/horizon-client/src/game.rs"
game_module = root / "crates/horizon-client/src/game/mod.rs"
game_module.write_text(game_source.read_text(encoding="utf-8"), encoding="utf-8")

print("Android runtime adaptations and NativeActivity crate applied to", root)
