#!/usr/bin/env python3
"""Brand Amethyst as HORIZON and add the CHEATLAB account gate."""
from pathlib import Path

gradle = Path("app_pojavlauncher/build.gradle")
source = gradle.read_text(encoding="utf-8")
replacements = {
    'applicationId "org.angelauramc.amethyst"': 'applicationId "dev.horizon.mobile.java"',
    'resValue "string", "app_name", "Amethyst (Debug)"': 'resValue "string", "app_name", "HORIZON Java"',
    'resValue "string", "app_short_name", "Amethyst (Debug)"': 'resValue "string", "app_short_name", "HORIZON"',
    "resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'": "resValue 'string', 'application_package', 'dev.horizon.mobile.java.debug'",
    "resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'": "resValue 'string', 'storageProviderAuthorities', 'dev.horizon.mobile.java.scoped.gamefolder.debug'",
    "resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'": "resValue 'string', 'shareProviderAuthority', 'dev.horizon.mobile.java.scoped.controlfolder.debug'",
    "compileSdk = 37": "compileSdk {\n        version = release(36) {\n            minorApiLevel = 1\n        }\n    }",
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f"Expected build.gradle anchor not found: {old}")
    source = source.replace(old, new, 1)
gradle.write_text(source, encoding="utf-8")

manifest_path = Path("app_pojavlauncher/src/main/AndroidManifest.xml")
manifest = manifest_path.read_text(encoding="utf-8")
old_activity = """        <activity
            android:name=".TestStorageActivity"
            android:exported="true"
            android:label="@string/app_short_name"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>"""
new_activity = """        <activity
            android:name=".TestStorageActivity"
            android:exported="false"
            android:label="@string/app_short_name"
            android:launchMode="singleTop" />
        <activity
            android:name=".HorizonGateActivity"
            android:exported="true"
            android:label="@string/app_short_name"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>"""
if old_activity not in manifest:
    raise SystemExit("Expected TestStorageActivity launcher declaration not found")
manifest_path.write_text(manifest.replace(old_activity, new_activity, 1), encoding="utf-8")

gate_path = Path("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/HorizonGateActivity.java")
gate_path.parent.mkdir(parents=True, exist_ok=True)
gate_source = r'''package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class HorizonGateActivity extends Activity {
    private static final String AUTH_URL = "https://cheatlab.fun/auth";
    private static final String API_URL = "https://cheatlab.tatarhost.workers.dev";
    private static final String PREFS = "horizon_cheatlab_session";
    private static final String KEY_ALIAS = "horizon_cheatlab_session_v1";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private boolean checking;
    private boolean finished;

    private final Runnable pollSession = new Runnable() {
        @Override public void run() {
            if (finished || webView == null || checking) return;
            readWebSession();
            handler.postDelayed(this, 1200);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        String cached = readEncrypted();
        if (cached != null) {
            try {
                JSONObject session = new JSONObject(cached);
                String token = session.optString("token", "");
                String client = session.optString("client", "");
                String nick = session.optString("nick", "");
                if (validToken(token, client)) {
                    verifySession(token, client, nick, false);
                    return;
                }
            } catch (Exception ignored) { clearEncrypted(); }
        }
        showLogin();
    }

    private void showLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 8, 8));
        TextView title = new TextView(this);
        title.setText("HORIZON");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(null, 1);
        title.setPadding(20, 18, 20, 4);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Войди в аккаунт CHEATLAB, чтобы открыть игровой лаунчер.");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(13);
        subtitle.setPadding(20, 0, 20, 12);
        root.addView(subtitle);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, 3));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                android.net.Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                if ("https".equalsIgnoreCase(uri.getScheme())
                        && (host.equals("cheatlab.fun") || host.equals("www.cheatlab.fun"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                readWebSession();
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        webView.loadUrl(AUTH_URL);
        handler.postDelayed(pollSession, 1200);
    }

    private void readWebSession() {
        if (webView == null || checking || finished) return;
        String js = "(function(){try{var s=JSON.parse(localStorage.getItem('cheatlab.session')||'null');return JSON.stringify({token:s&&s.token?s.token:'',client:localStorage.getItem('cheatlab.client')||''});}catch(e){return '{}';}})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null || checking || finished) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) return;
                JSONObject session = new JSONObject((String) decoded);
                String token = session.optString("token", "");
                String client = session.optString("client", "");
                if (validToken(token, client)) verifySession(token, client, "", true);
            } catch (Exception ignored) { }
        });
    }

    private boolean validToken(String token, String client) {
        return token.matches("[A-Za-z0-9_-]{20,80}") && client.matches("[A-Za-z0-9_-]{16,64}");
    }

    private void verifySession(String token, String client, String cachedNick, boolean fromWeb) {
        if (checking || finished) return;
        checking = true;
        executor.execute(() -> {
            String nick = null;
            boolean networkFailure = false;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(API_URL + "/api/auth/me").openConnection();
                try {
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(12000);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("x-cheatlab-client", client);
                    connection.setRequestProperty("x-cheatlab-session", token);
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                    String body = readAll(stream);
                    if (status < 200 || status >= 300) throw new IllegalStateException("CHEATLAB HTTP " + status);
                    JSONObject user = new JSONObject(body).optJSONObject("user");
                    if (user != null) nick = user.optString("nick", "");
                } finally { connection.disconnect(); }
            } catch (java.io.IOException error) {
                networkFailure = true;
            } catch (Exception ignored) { }

            final String verifiedNick = nick;
            final boolean offline = networkFailure && !cachedNick.isEmpty();
            runOnUiThread(() -> {
                checking = false;
                if (verifiedNick != null && !verifiedNick.isEmpty()) {
                    try {
                        saveEncrypted(new JSONObject().put("token", token).put("client", client).put("nick", verifiedNick).toString());
                        openLauncher();
                    } catch (Exception error) {
                        Toast.makeText(this, "Не удалось сохранить аккаунт.", Toast.LENGTH_LONG).show();
                        if (!fromWeb) showLogin();
                    }
                } else if (offline) {
                    Toast.makeText(this, "Нет сети. Используется ранее подтверждённый аккаунт CHEATLAB.", Toast.LENGTH_LONG).show();
                    openLauncher();
                } else if (!fromWeb) {
                    clearEncrypted();
                    showLogin();
                }
            });
        });
    }

    private void openLauncher() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(pollSession);
        startActivity(new Intent(this, TestStorageActivity.class));
        finish();
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private void saveEncrypted(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit();
    }

    private String readEncrypted() {
        try {
            String iv = getSharedPreferences(PREFS, MODE_PRIVATE).getString("iv", null);
            String data = getSharedPreferences(PREFS, MODE_PRIVATE).getString("data", null);
            if (iv == null || data == null) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception error) {
            clearEncrypted();
            return null;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private void clearEncrypted() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().commit();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(pollSession);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
'''
gate_path.write_text(gate_source, encoding="utf-8")
print("HORIZON branding and CHEATLAB account gate installed.")
