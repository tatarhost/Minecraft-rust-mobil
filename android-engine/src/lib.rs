use std::sync::OnceLock;
use winit::platform::android::activity::AndroidApp;

#[global_allocator]
static ALLOCATOR: horizon_threads::Allocator = horizon_threads::Allocator;

static ANDROID_APP: OnceLock<AndroidApp> = OnceLock::new();

/// Use Android's ACTION_VIEW so the system browser opens the launcher's URL.
fn open_android_uri(uri: &str) {
    let Some(app) = ANDROID_APP.get().cloned() else {
        log::error!("Horizon: cannot open {uri}: Android Activity is not available");
        return;
    };
    let uri = uri.to_owned();
    app.clone().run_on_java_main_thread(Box::new(move || {
        let vm = match unsafe { jni::JavaVM::from_raw(app.vm_as_ptr().cast()) } {
            Ok(vm) => vm,
            Err(error) => {
                log::error!("Horizon: cannot attach to Android JVM: {error}");
                return;
            }
        };
        let result = (|| -> jni::errors::Result<()> {
            let mut env = vm.attach_current_thread()?;
            use jni::objects::{JObject, JValue};
            let raw_activity = app.activity_as_ptr() as jni::sys::jobject;
            // AndroidApp owns this global reference; JObject is only a borrowed wrapper here.
            let activity = unsafe { JObject::from_raw(raw_activity) };
            let action: JObject = env.new_string("android.intent.action.VIEW")?.into();
            let url: JObject = env.new_string(uri.as_str())?.into();
            let parsed_uri = env
                .call_static_method(
                    "android/net/Uri",
                    "parse",
                    "(Ljava/lang/String;)Landroid/net/Uri;",
                    &[JValue::Object(&url)],
                )?
                .l()?;
            let intent = env.new_object(
                "android/content/Intent",
                "(Ljava/lang/String;Landroid/net/Uri;)V",
                &[JValue::Object(&action), JValue::Object(&parsed_uri)],
            )?;
            env.call_method(
                &activity,
                "startActivity",
                "(Landroid/content/Intent;)V",
                &[JValue::Object(&intent)],
            )?;
            Ok(())
        })();
        if let Err(error) = result {
            log::error!("Horizon: could not open URI {uri}: {error}");
        }
    }));
}

/// NativeActivity entry point; the original game loop lives in horizon-client.
#[unsafe(no_mangle)]
pub fn android_main(app: AndroidApp) {
    let _ = ANDROID_APP.set(app.clone());
    horizon_client::set_android_open_uri_handler(open_android_uri);
    if let Err(error) = horizon_client::run_android(app) {
        log::error!("Horizon Android client stopped: {error}");
        horizon_client::logging::flush();
    }
}
