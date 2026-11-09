use winit::platform::android::activity::AndroidApp;

// Include the original game module at its source-relative path. Its child modules
// (world, inventory, screens, rendering and networking) remain in upstream/crates/horizon-client/src/game/.
#[path = "../../horizon-client/src/game.rs"]
mod game;

#[global_allocator]
static ALLOCATOR: horizon_threads::Allocator = horizon_threads::Allocator;

/// NativeActivity calls this exported entry point on Android's main thread.
#[unsafe(no_mangle)]
pub fn android_main(app: AndroidApp) {
    let Some(app_data) = app.internal_data_path() else {
        eprintln!("Horizon: Android app-private storage is unavailable");
        return;
    };
    let game_home = app_data.join("horizon").join("game");
    let resources = app_data.join("horizon").join("resources");
    if let Err(error) = std::fs::create_dir_all(&game_home)
        .and_then(|_| std::fs::create_dir_all(&resources))
        .and_then(|_| std::env::set_current_dir(&game_home))
    {
        eprintln!("Horizon: could not prepare app storage: {error}");
        return;
    }

    horizon_client::logging::set_game_home(game_home.clone());
    game::set_android_app(app);
    horizon_threads::configure_allocator();
    horizon_client::telemetry::mark_start();
    horizon_client::logging::init(&game_home, "Render thread");
    log::info!("Horizon Android Rust client starting");
    horizon_client::crash::install(game_home, "client");

    if let Err(error) = game::run() {
        log::error!("Horizon Android client stopped: {error}");
        horizon_client::logging::flush();
    }
}
