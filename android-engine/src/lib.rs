use winit::platform::android::activity::AndroidApp;

#[global_allocator]
static ALLOCATOR: horizon_threads::Allocator = horizon_threads::Allocator;

/// NativeActivity calls this exported entry point on Android's main thread.
#[unsafe(no_mangle)]
pub fn android_main(app: AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );
    if let Err(error) = horizon_client::run_android(app) {
        log::error!("Horizon Android client stopped: {error}");
    }
}
