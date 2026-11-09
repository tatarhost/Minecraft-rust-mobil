use winit::platform::android::activity::AndroidApp;

#[global_allocator]
static ALLOCATOR: horizon_threads::Allocator = horizon_threads::Allocator;

/// NativeActivity entry point; the original game loop lives in horizon-client.
#[unsafe(no_mangle)]
pub fn android_main(app: AndroidApp) {
    if let Err(error) = horizon_client::run_android(app) {
        log::error!("Horizon Android client stopped: {error}");
        horizon_client::logging::flush();
    }
}
