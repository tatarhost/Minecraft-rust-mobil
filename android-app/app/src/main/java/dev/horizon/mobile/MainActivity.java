package dev.horizon.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

public final class MainActivity extends Activity {
    private static final String SITE_URL = "https://cheatlab.fun/";
    private static final String API_URL = "https://cheatlab.tatarhost.workers.dev";
    private static final String KEY_ALIAS = "dev.horizon.mobile.cheatlab.session.v1";
    private static final String PREFS = "horizon_secure_session";

    private static final int BG = Color.rgb(8, 8, 9);
    private static final int PANEL = Color.rgb(18, 18, 20);
    private static final int PANEL_2 = Color.rgb(27, 27, 30);
    private static final int WHITE = Color.rgb(248, 248, 250);
    private static final int MUTED = Color.rgb(153, 153, 160);
    private static final int BORDER = Color.rgb(47, 47, 52);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private FrameLayout authLayer;
    private LinearLayout home;
    private TextView accountTitle;
    private TextView accountDescription;
    private TextView authStatus;
    private Button accountButton;
    private Button playButton;
    private WebView authWebView;
    private boolean authVisible;
    private boolean verificationRunning;
    private String lastSeenToken = "";

    private final Runnable authPoll = new Runnable() {
        @Override
        public void run() {
            if (!authVisible || authWebView == null || isFinishing()) return;
            readWebViewSession();
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(0);
        buildUi();
        String existingSession = readEncrypted();
        if (existingSession == null || existingSession.isEmpty()) {
            // A fresh install starts at the real CHEATLAB sign-in page.
            openAuth();
        } else {
            restoreSession();
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(dp(22), dp(24), dp(22), dp(32));
        scroll.addView(home, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("HORIZON", 25, WHITE, true);
        brand.setLetterSpacing(0.08f);
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, dp(38), 1f));
        TextView edition = label("MOBILE", 10, MUTED, true);
        edition.setPadding(dp(12), dp(7), dp(12), dp(7));
        edition.setBackground(round(PANEL_2, BORDER, 30));
        brandRow.addView(edition);
        home.addView(brandRow);

        TextView headline = label("Minecraft, собранный на Rust.", 27, WHITE, true);
        headline.setPadding(0, dp(30), 0, dp(8));
        home.addView(headline);
        TextView intro = label("Мобильная оболочка Horizon. Привяжи CHEATLAB, чтобы открыть функции аккаунта лаунчера.", 14, MUTED, false);
        intro.setLineSpacing(dp(4), 1f);
        home.addView(intro);

        LinearLayout accountCard = card();
        accountCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        accountCard.setOrientation(LinearLayout.VERTICAL);
        accountCard.addView(label("АККАУНТ CHEATLAB", 10, MUTED, true));
        accountTitle = label("Аккаунт не подключён", 19, WHITE, true);
        accountTitle.setPadding(0, dp(13), 0, dp(5));
        accountCard.addView(accountTitle);
        accountDescription = label("Регистрация и вход откроются на cheatlab.fun. Ключ входа для приложения не нужен.", 13, MUTED, false);
        accountDescription.setLineSpacing(dp(3), 1f);
        accountCard.addView(accountDescription);

        accountButton = button("Подключить аккаунт", true);
        accountButton.setOnClickListener(v -> openAuth());
        LinearLayout.LayoutParams authButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        authButtonParams.topMargin = dp(18);
        accountCard.addView(accountButton, authButtonParams);

        Button logoutButton = button("Отвязать аккаунт", false);
        logoutButton.setOnClickListener(v -> logout());
        LinearLayout.LayoutParams logoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        logoutParams.topMargin = dp(8);
        accountCard.addView(logoutButton, logoutParams);
        home.addView(accountCard, topMargin(24));

        LinearLayout runtimeCard = card();
        runtimeCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        runtimeCard.setOrientation(LinearLayout.VERTICAL);
        runtimeCard.addView(label("ИГРОВОЙ ДВИЖОК", 10, MUTED, true));
        TextView runtimeTitle = label("Android runtime ещё не подключён", 18, WHITE, true);
        runtimeTitle.setPadding(0, dp(13), 0, dp(6));
        runtimeCard.addView(runtimeTitle);
        TextView runtimeText = label("Исходный Rust-клиент уже подключён в репозитории. Для запуска на телефоне требуется отдельная адаптация Android lifecycle, графики и сенсорного управления.", 13, MUTED, false);
        runtimeText.setLineSpacing(dp(3), 1f);
        runtimeCard.addView(runtimeText);
        playButton = button("Играть — скоро", false);
        playButton.setEnabled(false);
        playButton.setAlpha(0.48f);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        playParams.topMargin = dp(18);
        runtimeCard.addView(playButton, playParams);
        home.addView(runtimeCard, topMargin(14));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(3), dp(20), dp(3), 0);
        details.addView(label("CHEATLAB", 11, WHITE, true));
        TextView site = label("cheatlab.fun  ↗", 13, MUTED, false);
        site.setPadding(0, dp(8), 0, dp(12));
        site.setOnClickListener(v -> openExternal(SITE_URL));
        details.addView(site);
        TextView source = label("Открытый исходный код · MIT", 13, MUTED, false);
        source.setOnClickListener(v -> openExternal("https://github.com/tatarhost/Minecraft-rust-mobil"));
        details.addView(source);
        home.addView(details, topMargin(2));

        authLayer = new FrameLayout(this);
        authLayer.setBackgroundColor(BG);
        authLayer.setVisibility(View.GONE);
        root.addView(authLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        setAccountState(null, "Войдите или создайте аккаунт на CHEATLAB.");
    }

    private void openAuth() {
        authVisible = true;
        authLayer.setVisibility(View.VISIBLE);
        if (authWebView == null) {
            authWebView = new WebView(this);
            WebSettings settings = authWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setGeolocationEnabled(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(authWebView, false);
            authWebView.setWebChromeClient(new WebChromeClient());
            authWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                    if ("https".equalsIgnoreCase(uri.getScheme())
                            && (host.equals("cheatlab.fun") || host.equals("www.cheatlab.fun"))) {
                        return false;
                    }
                    openExternal(uri.toString());
                    return true;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    readWebViewSession();
                }
            });

            LinearLayout layer = new LinearLayout(this);
            layer.setOrientation(LinearLayout.VERTICAL);
            layer.setBackgroundColor(BG);

            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(16), dp(10), dp(16), dp(10));
            TextView title = label("CHEATLAB · ВХОД", 13, WHITE, true);
            bar.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
            Button close = button("Закрыть", false);
            close.setMinWidth(dp(88));
            close.setOnClickListener(v -> closeAuth());
            bar.addView(close);
            layer.addView(bar);

            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(true);
            progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(WHITE));
            layer.addView(progress, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
            layer.addView(authWebView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            authLayer.addView(layer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            authWebView.loadUrl(SITE_URL + "auth");
        }
        handler.removeCallbacks(authPoll);
        handler.post(authPoll);
    }

    private void closeAuth() {
        authVisible = false;
        handler.removeCallbacks(authPoll);
        authLayer.setVisibility(View.GONE);
    }

    private void readWebViewSession() {
        if (authWebView == null || verificationRunning) return;
        String script = "(function(){try{var s=JSON.parse(localStorage.getItem('cheatlab.session')||'null');return JSON.stringify({token:s&&s.token?s.token:'',client:localStorage.getItem('cheatlab.client')||''});}catch(e){return '{}';}})()";
        authWebView.evaluateJavascript(script, value -> {
            if (!authVisible || verificationRunning || value == null) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) return;
                JSONObject data = new JSONObject((String) decoded);
                String token = data.optString("token", "");
                String client = data.optString("client", "");
                if (!token.matches("[A-Za-z0-9_-]{20,80}")
                        || !client.matches("[A-Za-z0-9_-]{16,64}")
                        || token.equals(lastSeenToken)) return;
                lastSeenToken = token;
                verificationRunning = true;
                verifySession(token, client, true);
            } catch (Exception ignored) {
                // No session yet; the account form may still be open.
            }
        });
    }

    private void restoreSession() {
        String saved = readEncrypted();
        if (saved == null || saved.isEmpty()) return;
        try {
            JSONObject data = new JSONObject(saved);
            String token = data.optString("token", "");
            String client = data.optString("client", "");
            if (token.isEmpty() || client.isEmpty()) {
                clearEncrypted();
                return;
            }
            verificationRunning = true;
            verifySession(token, client, false);
        } catch (Exception error) {
            clearEncrypted();
            setAccountState(null, "Сохранённая сессия повреждена. Подключите аккаунт заново.");
        }
    }

    private void verifySession(String token, String client, boolean fromWebView) {
        io.execute(() -> {
            String nickname = null;
            String failure = null;
            try {
                nickname = requestMe(token, client);
                if (nickname == null) failure = "Сессия CHEATLAB недействительна. Войдите снова.";
                else saveEncrypted(new JSONObject()
                        .put("token", token)
                        .put("client", client)
                        .put("nick", nickname).toString());
            } catch (Exception error) {
                failure = "Не удалось проверить CHEATLAB. Проверьте подключение к интернету.";
            }

            final String verifiedNick = nickname;
            final String message = failure;
            runOnUiThread(() -> {
                verificationRunning = false;
                if (verifiedNick != null) {
                    setAccountState(verifiedNick, "Аккаунт подтверждён сервером CHEATLAB.");
                    closeAuth();
                } else if (fromWebView) {
                    lastSeenToken = "";
                    setAccountState(null, message == null ? "Не удалось войти." : message);
                } else if (message != null && message.startsWith("Сессия")) {
                    clearEncrypted();
                    setAccountState(null, message);
                    openAuth();
                } else {
                    setAccountState(null, message);
                }
            });
        });
    }

    private String requestMe(String token, String client) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL + "/api/auth/me").openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-cheatlab-client", client);
            connection.setRequestProperty("x-cheatlab-session", token);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (status < 200 || status >= 300) throw new IllegalStateException("CHEATLAB HTTP " + status);
            JSONObject response = new JSONObject(body);
            JSONObject user = response.optJSONObject("user");
            return user == null ? null : user.optString("nick", null);
        } finally {
            connection.disconnect();
        }
    }

    private void logout() {
        String saved = readEncrypted();
        if (saved == null) {
            setAccountState(null, "Аккаунт уже отключён.");
            return;
        }
        accountButton.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject data = new JSONObject(saved);
                HttpURLConnection connection = (HttpURLConnection) new URL(API_URL + "/api/auth/logout").openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("x-cheatlab-client", data.optString("client"));
                    connection.setRequestProperty("x-cheatlab-session", data.optString("token"));
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write("{}".getBytes(StandardCharsets.UTF_8));
                    }
                    connection.getResponseCode();
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                // Local sign-out still removes this device's credential.
            }
            clearEncrypted();
            runOnUiThread(() -> {
                accountButton.setEnabled(true);
                setAccountState(null, "Аккаунт CHEATLAB отвязан от этого устройства.");
                if (authWebView != null) {
                    authWebView.evaluateJavascript("localStorage.removeItem('cheatlab.session');true;", null);
                    authWebView.loadUrl(SITE_URL + "auth");
                }
                openAuth();
            });
        });
    }

    private void setAccountState(String nickname, String description) {
        if (accountTitle == null) return;
        if (nickname == null || nickname.isEmpty()) {
            accountTitle.setText("Аккаунт не подключён");
            accountButton.setText("Подключить аккаунт");
        } else {
            accountTitle.setText("@" + nickname);
            accountButton.setText("Аккаунт подключён");
        }
        accountButton.setEnabled(true);
        accountDescription.setText(description);
    }

    private void saveEncrypted(String plainText) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    private String readEncrypted() {
        try {
            String ivValue = getSharedPreferences(PREFS, MODE_PRIVATE).getString("iv", null);
            String dataValue = getSharedPreferences(PREFS, MODE_PRIVATE).getString("data", null);
            if (ivValue == null || dataValue == null) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(ivValue, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(dataValue, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception error) {
            clearEncrypted();
            return null;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private void clearEncrypted() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
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

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception ignored) {
            // No external handler is installed.
        }
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(round(PANEL, BORDER, 22));
        return view;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setIncludeFontPadding(true);
        return text;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? BG : WHITE);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(round(primary ? WHITE : PANEL_2, primary ? WHITE : BORDER, 16));
        return button;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(value);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (authVisible) {
            if (authWebView != null && authWebView.canGoBack()) authWebView.goBack();
            else closeAuth();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(authPoll);
        if (authWebView != null) {
            authWebView.stopLoading();
            authWebView.destroy();
            authWebView = null;
        }
        io.shutdownNow();
        super.onDestroy();
    }
}
