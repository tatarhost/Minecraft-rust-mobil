package dev.horizon.mobile;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
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
    private static final String HORIZON_API = "https://api.github.com/repos/horizon-rust/horizon";
    private static final String KEY_ALIAS = "dev.horizon.mobile.cheatlab.session.v1";
    private static final String PREFS = "horizon_secure_session";
    private static final String DATA_PREFS = "horizon_mobile_data";
    private static final int PICK_MINECRAFT_FOLDER = 4102;

    private static final int BG = Color.rgb(8, 8, 9);
    private static final int PANEL = Color.rgb(18, 18, 20);
    private static final int PANEL_2 = Color.rgb(27, 27, 30);
    private static final int WHITE = Color.rgb(248, 248, 250);
    private static final int MUTED = Color.rgb(153, 153, 160);
    private static final int BORDER = Color.rgb(47, 47, 52);
    private static final int SOFT = Color.rgb(205, 205, 210);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private FrameLayout authLayer;
    private LinearLayout home;
    private LinearLayout navBar;
    private TextView accountTitle;
    private TextView accountDescription;
    private Button accountButton;
    private WebView authWebView;

    private String activePage = "play";
    private String currentNick = "";
    private String accountMessage = "Войдите или создайте аккаунт CHEATLAB.";
    private String lastSeenToken = "";
    private boolean authVisible;
    private boolean authRequired = true;
    private boolean verificationRunning;
    private int renderVersion;

    private final Runnable authPoll = new Runnable() {
        @Override
        public void run() {
            if (!authVisible || authWebView == null || isFinishing()) return;
            readWebViewSession();
            handler.postDelayed(this, 1100);
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

        String saved = readEncrypted();
        if (saved == null || saved.isEmpty()) {
            authRequired = true;
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
        scroll.setClipToPadding(false);
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(dp(20), dp(18), dp(20), dp(104));
        scroll.addView(home, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        navBar.setBackground(round(BG, BORDER, 18));
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM);
        navParams.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(navBar, navParams);

        authLayer = new FrameLayout(this);
        authLayer.setBackgroundColor(BG);
        authLayer.setVisibility(View.GONE);
        root.addView(authLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        renderPage();
    }

    private void renderPage() {
        renderVersion++;
        home.removeAllViews();
        addTopBar();

        if ("play".equals(activePage)) {
            addPlayPage();
        } else if ("versions".equals(activePage)) {
            addVersionsPage(renderVersion);
        } else if ("files".equals(activePage)) {
            addFilesPage();
        } else {
            addSettingsPage();
        }
        renderNavigation();
    }

    private void addTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label("HORIZON", 23, WHITE, true);
        logo.setLetterSpacing(0.09f);
        row.addView(logo, new LinearLayout.LayoutParams(0, dp(38), 1f));

        TextView mobile = label("MOBILE", 10, MUTED, true);
        mobile.setPadding(dp(11), dp(7), dp(11), dp(7));
        mobile.setBackground(round(PANEL_2, BORDER, 20));
        row.addView(mobile);

        TextView account = label(currentNick.isEmpty() ? "CHEATLAB ↗" : "@" + currentNick, 12, SOFT, true);
        account.setPadding(dp(10), dp(8), 0, dp(8));
        account.setOnClickListener(v -> openAuth());
        row.addView(account);
        home.addView(row);

        View line = new View(this);
        line.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lineParams.topMargin = dp(15);
        home.addView(line, lineParams);
    }

    private void addPlayPage() {
        TextView eyebrow = label("JAVA EDITION · RUST ENGINE", 10, MUTED, true);
        eyebrow.setPadding(0, dp(24), 0, dp(8));
        home.addView(eyebrow);

        TextView headline = label("Твой мир.\nНа телефоне.", 34, WHITE, true);
        headline.setLineSpacing(dp(1), 1f);
        home.addView(headline);

        TextView intro = label("Horizon — независимая реализация Minecraft Java Edition на Rust. Мобильный интерфейс готовится отдельно от настольного лаунчера.", 14, MUTED, false);
        intro.setLineSpacing(dp(4), 1.05f);
        intro.setPadding(0, dp(12), 0, 0);
        home.addView(intro);

        LinearLayout gameCard = card();
        gameCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        gameCard.setOrientation(LinearLayout.VERTICAL);
        gameCard.addView(label("ИГРОВОЙ ДВИЖОК", 10, MUTED, true));
        TextView gameState = label("Rust engine · Android arm64", 19, WHITE, true);
        gameState.setPadding(0, dp(13), 0, dp(7));
        gameCard.addView(gameState);
        TextView gameInfo = label("В APK встраивается нативный игровой клиент Horizon на Rust с winit и wgpu. Для входа в мир нужны подготовленные игровые ресурсы из собственной установки Minecraft Java Edition.", 13, MUTED, false);
        gameInfo.setLineSpacing(dp(3), 1f);
        gameCard.addView(gameInfo);

        Button play = button("ЗАПУСТИТЬ RUST-КЛИЕНТ", true);
        play.setOnClickListener(v -> startGame());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        playParams.topMargin = dp(18);
        gameCard.addView(play, playParams);
        home.addView(gameCard, topMargin(22));

        LinearLayout accountCard = card();
        accountCard.setPadding(dp(18), dp(17), dp(18), dp(17));
        accountCard.setOrientation(LinearLayout.VERTICAL);
        accountCard.addView(label("АККАУНТ CHEATLAB", 10, MUTED, true));
        accountTitle = label(currentNick.isEmpty() ? "Аккаунт не подключён" : "@" + currentNick, 19, WHITE, true);
        accountTitle.setPadding(0, dp(12), 0, dp(5));
        accountCard.addView(accountTitle);
        accountDescription = label(accountMessage, 13, MUTED, false);
        accountDescription.setLineSpacing(dp(3), 1f);
        accountCard.addView(accountDescription);

        accountButton = button(currentNick.isEmpty() ? "Подключить аккаунт" : "Управление аккаунтом", true);
        accountButton.setOnClickListener(v -> openAuth());
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        connectParams.topMargin = dp(16);
        accountCard.addView(accountButton, connectParams);
        home.addView(accountCard, topMargin(14));

        LinearLayout latest = card();
        latest.setPadding(dp(18), dp(17), dp(18), dp(17));
        latest.setOrientation(LinearLayout.VERTICAL);
        latest.addView(label("ПОСЛЕДНЯЯ ВЕРСИЯ", 10, MUTED, true));
        TextView release = label("Horizon 0.2.97", 18, WHITE, true);
        release.setPadding(0, dp(10), 0, dp(4));
        latest.addView(release);
        latest.addView(label("Стабильная версия исходного проекта. В релизе нет Android-бинарника.", 13, MUTED, false));
        Button versions = button("Открыть версии", false);
        versions.setOnClickListener(v -> navigate("versions"));
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45));
        versionParams.topMargin = dp(14);
        latest.addView(versions, versionParams);
        home.addView(latest, topMargin(14));

        TextView notice = label("CHEATLAB даёт доступ к функциям лаунчера, но не заменяет Microsoft-вход и лицензию Minecraft Java Edition.", 11, MUTED, false);
        notice.setLineSpacing(dp(3), 1f);
        notice.setPadding(dp(3), dp(18), dp(3), 0);
        home.addView(notice);
    }

    private void addVersionsPage(int versionToken) {
        addPageHeading("Версии", "Релизы игрового движка и заметки разработчиков.");
        LinearLayout latestCard = card();
        latestCard.setPadding(dp(17), dp(17), dp(17), dp(17));
        latestCard.setOrientation(LinearLayout.VERTICAL);
        latestCard.addView(label("ПОСЛЕДНИЙ СТАБИЛЬНЫЙ РЕЛИЗ", 10, MUTED, true));
        TextView latestName = label("Загрузка…", 19, WHITE, true);
        latestName.setPadding(0, dp(10), 0, dp(7));
        latestCard.addView(latestName);
        TextView latestDate = label("Проверяю GitHub Releases", 12, MUTED, false);
        latestCard.addView(latestDate);
        TextView latestNotes = label("", 13, SOFT, false);
        latestNotes.setLineSpacing(dp(3), 1f);
        latestNotes.setPadding(0, dp(10), 0, 0);
        latestCard.addView(latestNotes);
        Button openRelease = button("Открыть релиз", false);
        openRelease.setEnabled(false);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        actionParams.topMargin = dp(13);
        latestCard.addView(openRelease, actionParams);
        home.addView(latestCard, topMargin(18));

        addSectionLabel("ПОСЛЕДНИЕ СБОРКИ");
        TextView feedStatus = label("Загружаю список релизов…", 13, MUTED, false);
        feedStatus.setPadding(0, dp(8), 0, dp(8));
        home.addView(feedStatus);
        LinearLayout feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        home.addView(feed);

        io.execute(() -> {
            JSONObject stable = null;
            JSONArray releases = null;
            String failure = null;
            try {
                stable = fetchJsonObject(HORIZON_API + "/releases/latest");
                releases = fetchJsonArray(HORIZON_API + "/releases?per_page=30");
            } catch (Exception error) {
                failure = "Не удалось загрузить релизы: " + safeMessage(error);
            }
            JSONObject finalStable = stable;
            JSONArray finalReleases = releases;
            String finalFailure = failure;
            runOnUiThread(() -> {
                if (isFinishing() || !("versions".equals(activePage)) || versionToken != renderVersion) return;
                if (finalStable == null) {
                    latestName.setText("Версия недоступна");
                    latestDate.setText(finalFailure == null ? "Нет ответа от GitHub." : finalFailure);
                    feedStatus.setText("Проверь подключение к интернету.");
                    return;
                }
                String tag = finalStable.optString("tag_name", "unknown");
                String name = finalStable.optString("name", tag);
                latestName.setText(name);
                latestDate.setText("Тег " + tag + " · " + finalStable.optString("published_at", "").replace("T", " ").replace("Z", " UTC"));
                latestNotes.setText(trimText(finalStable.optString("body", "Нет описания релиза."), 900));
                String url = finalStable.optString("html_url", "https://github.com/horizon-rust/horizon/releases");
                openRelease.setEnabled(true);
                openRelease.setOnClickListener(v -> openExternal(url));

                feed.removeAllViews();
                int shown = 0;
                if (finalReleases != null) {
                    for (int i = 0; i < finalReleases.length() && shown < 12; i++) {
                        JSONObject item = finalReleases.optJSONObject(i);
                        if (item == null) continue;
                        String itemTag = item.optString("tag_name", "");
                        if (!(itemTag.startsWith("v") || itemTag.startsWith("nightly-"))) continue;
                        addReleaseRow(feed, item);
                        shown++;
                    }
                }
                if (shown == 0) {
                    feedStatus.setText("Список пуст. Открой страницу релизов GitHub.");
                } else {
                    feedStatus.setText("Показано " + shown + " релизов. Android-пакет в публичных релизах пока не опубликован.");
                }
            });
        });

        TextView disclaimer = label("Важно: страница показывает реальные релизы Horizon. Бинарники Linux / Windows / macOS нельзя устанавливать на Android. Кнопка запуска останется недоступной, пока не появится и не будет проверена Android-сборка игрового движка.", 11, MUTED, false);
        disclaimer.setLineSpacing(dp(3), 1f);
        disclaimer.setPadding(dp(2), dp(18), dp(2), 0);
        home.addView(disclaimer);
    }

    private void addReleaseRow(LinearLayout parent, JSONObject release) {
        LinearLayout row = card();
        row.setPadding(dp(15), dp(14), dp(15), dp(14));
        row.setOrientation(LinearLayout.VERTICAL);
        String name = release.optString("name", release.optString("tag_name", "Horizon"));
        String tag = release.optString("tag_name", "");
        String published = release.optString("published_at", "");
        row.addView(label(name, 15, WHITE, true));
        TextView meta = label(tag + (published.isEmpty() ? "" : " · " + published.substring(0, Math.min(10, published.length()))), 11, MUTED, false);
        meta.setPadding(0, dp(5), 0, dp(4));
        row.addView(meta);
        JSONArray assets = release.optJSONArray("assets");
        boolean androidAsset = false;
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null && asset.optString("name", "").toLowerCase().contains("android")) {
                    androidAsset = true;
                    break;
                }
            }
        }
        row.addView(label(androidAsset ? "Android asset найден в релизе" : "Android-пакета нет · только заметки и исходники", 11, androidAsset ? SOFT : MUTED, false));
        Button details = button("Заметки релиза", false);
        details.setOnClickListener(v -> openExternal(release.optString("html_url", "https://github.com/horizon-rust/horizon/releases")));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        p.topMargin = dp(10);
        row.addView(details, p);
        parent.addView(row, topMargin(9));
    }

    private void addFilesPage() {
        addPageHeading("Файлы и профили", "Локальные данные остаются на этом устройстве.");
        LinearLayout folderCard = card();
        folderCard.setPadding(dp(17), dp(17), dp(17), dp(17));
        folderCard.setOrientation(LinearLayout.VERTICAL);
        folderCard.addView(label("ПАПКА MINECRAFT JAVA", 10, MUTED, true));
        String savedUri = getSharedPreferences(DATA_PREFS, MODE_PRIVATE).getString("minecraft_tree", "");
        TextView folderState = label(savedUri.isEmpty() ? "Папка ещё не выбрана" : "Папка подключена через Android Storage Access Framework", 14, WHITE, true);
        folderState.setPadding(0, dp(10), 0, dp(7));
        folderCard.addView(folderState);
        if (!savedUri.isEmpty()) {
            TextView uriLabel = label(savedUri, 10, MUTED, false);
            uriLabel.setTextIsSelectable(true);
            folderCard.addView(uriLabel);
        }
        folderCard.addView(label("Выбери свою папку с файлами Minecraft Java Edition. Приложение не отправляет её содержимое на сервер.", 12, MUTED, false));
        Button selectFolder = button(savedUri.isEmpty() ? "Выбрать папку Minecraft" : "Изменить папку", true);
        selectFolder.setOnClickListener(v -> chooseMinecraftFolder());
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(47));
        selectParams.topMargin = dp(14);
        folderCard.addView(selectFolder, selectParams);
        home.addView(folderCard, topMargin(18));

        File dataRoot = new File(getFilesDir(), "horizon");
        String[] names = {"instances", "versions", "worlds", "mods", "resourcepacks", "logs"};
        for (String name : names) new File(dataRoot, name).mkdirs();

        LinearLayout storageCard = card();
        storageCard.setPadding(dp(17), dp(17), dp(17), dp(17));
        storageCard.setOrientation(LinearLayout.VERTICAL);
        storageCard.addView(label("ХРАНИЛИЩЕ ПРИЛОЖЕНИЯ", 10, MUTED, true));
        storageCard.addView(label(dataRoot.getAbsolutePath(), 11, SOFT, false));
        for (String name : names) {
            File folder = new File(dataRoot, name);
            TextView item = label(name + "  ·  " + countChildren(folder) + " объектов", 13, WHITE, false);
            item.setPadding(0, dp(9), 0, 0);
            storageCard.addView(item);
        }
        home.addView(storageCard, topMargin(12));

        addSectionLabel("ПРОФИЛИ ИГРЫ");
        TextView instanceNote = label("Профили создаются локально и могут хранить отдельные настройки. Запуск и перенос игровых файлов подключатся после Android-порта движка.", 12, MUTED, false);
        instanceNote.setLineSpacing(dp(3), 1f);
        instanceNote.setPadding(0, dp(5), 0, dp(10));
        home.addView(instanceNote);

        Button create = button("＋  Создать профиль", true);
        create.setOnClickListener(v -> createInstance());
        home.addView(create, topMargin(4));
        LinearLayout instances = new LinearLayout(this);
        instances.setOrientation(LinearLayout.VERTICAL);
        home.addView(instances, topMargin(10));
        renderInstances(instances);
    }

    private void startGame() {
        try {
            startActivity(new Intent(this, android.app.NativeActivity.class));
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось запустить нативный Rust-клиент: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void renderInstances(LinearLayout container) {
        container.removeAllViews();
        JSONArray values = getInstances();
        if (values.length() == 0) {
            TextView empty = label("Пока нет профилей. Создай отдельный профиль для будущей игровой конфигурации.", 12, MUTED, false);
            empty.setPadding(dp(2), dp(7), dp(2), dp(7));
            container.addView(empty);
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "Horizon");
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(10), dp(10));
            TextView title = label(name, 14, WHITE, true);
            row.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1f));
            Button remove = button("Удалить", false);
            remove.setMinWidth(dp(80));
            final int index = i;
            remove.setOnClickListener(v -> confirmDeleteInstance(index, name));
            row.addView(remove);
            container.addView(row, topMargin(7));
        }
    }

    private void addSettingsPage() {
        addPageHeading("Настройки", "Аккаунт, управление, приватность и данные приложения.");

        LinearLayout account = card();
        account.setPadding(dp(17), dp(17), dp(17), dp(17));
        account.setOrientation(LinearLayout.VERTICAL);
        account.addView(label("CHEATLAB", 10, MUTED, true));
        TextView user = label(currentNick.isEmpty() ? "Не подключён" : "@" + currentNick, 19, WHITE, true);
        user.setPadding(0, dp(10), 0, dp(5));
        account.addView(user);
        TextView detail = label(accountMessage, 12, MUTED, false);
        detail.setLineSpacing(dp(3), 1f);
        account.addView(detail);
        Button connect = button(currentNick.isEmpty() ? "Войти или зарегистрироваться" : "Открыть аккаунт CHEATLAB", true);
        connect.setOnClickListener(v -> openAuth());
        account.addView(connect, topMargin(14));
        Button logout = button("Выйти и отвязать устройство", false);
        logout.setOnClickListener(v -> logout());
        account.addView(logout, topMargin(8));
        home.addView(account, topMargin(18));

        addSectionLabel("УПРАВЛЕНИЕ");
        CheckBox touch = check("Сенсорные кнопки", "touch_controls", true);
        CheckBox gamepad = check("Поддержка геймпада", "gamepad_enabled", true);
        CheckBox keyboard = check("Клавиатура и мышь", "keyboard_enabled", true);
        home.addView(touch, topMargin(7));
        home.addView(gamepad, topMargin(3));
        home.addView(keyboard, topMargin(3));
        TextView controlsNote = label("Настройки сохраняются на устройстве. В игровом процессе они начнут действовать после подключения Android runtime.", 11, MUTED, false);
        controlsNote.setPadding(dp(2), dp(5), dp(2), 0);
        home.addView(controlsNote);

        addSectionLabel("ПРИЛОЖЕНИЕ");
        LinearLayout info = card();
        info.setPadding(dp(17), dp(16), dp(17), dp(16));
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label("Horizon Mobile · 0.1.0", 14, WHITE, true));
        info.addView(label("Минимум Android 6.0 · данные аккаунта защищены Android Keystore AES-GCM.", 12, MUTED, false), topMargin(6));
        info.addView(label("Сайт: cheatlab.fun", 12, MUTED, false), topMargin(7));
        home.addView(info, topMargin(11));

        Button website = button("Открыть CHEATLAB", false);
        website.setOnClickListener(v -> openExternal(SITE_URL));
        home.addView(website, topMargin(11));

        Button clearCache = button("Очистить временный кеш", false);
        clearCache.setOnClickListener(v -> confirmClearCache());
        home.addView(clearCache, topMargin(8));
    }

    private CheckBox check(String title, String key, boolean defaultValue) {
        CheckBox box = new CheckBox(this);
        box.setText(title);
        box.setTextSize(14);
        box.setTextColor(WHITE);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(WHITE));
        box.setChecked(getSharedPreferences(DATA_PREFS, MODE_PRIVATE).getBoolean(key, defaultValue));
        box.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences(DATA_PREFS, MODE_PRIVATE).edit().putBoolean(key, checked).apply());
        return box;
    }

    private void addPageHeading(String title, String subtitle) {
        TextView heading = label(title, 29, WHITE, true);
        heading.setPadding(0, dp(25), 0, dp(5));
        home.addView(heading);
        TextView sub = label(subtitle, 13, MUTED, false);
        sub.setLineSpacing(dp(3), 1f);
        home.addView(sub);
    }

    private void addSectionLabel(String value) {
        TextView title = label(value, 10, MUTED, true);
        title.setPadding(dp(2), dp(20), 0, dp(5));
        home.addView(title);
    }

    private void renderNavigation() {
        navBar.removeAllViews();
        addNavItem("play", "Играть", "play".equals(activePage));
        addNavItem("versions", "Версии", "versions".equals(activePage));
        addNavItem("files", "Файлы", "files".equals(activePage));
        addNavItem("settings", "Настройки", "settings".equals(activePage));
    }

    private void addNavItem(String page, String title, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(5), dp(3), dp(4));
        item.setBackground(selected ? round(PANEL_2, BORDER, 14) : round(BG, Color.TRANSPARENT, 14));
        TextView label = label(title, 11, selected ? WHITE : MUTED, selected);
        item.addView(label);
        item.setOnClickListener(v -> navigate(page));
        navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void navigate(String page) {
        if (!("play".equals(page) || "versions".equals(page) || "files".equals(page) || "settings".equals(page))) return;
        activePage = page;
        renderPage();
    }

    private void chooseMinecraftFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_MINECRAFT_FOLDER);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть выбор папки.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MINECRAFT_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            getSharedPreferences(DATA_PREFS, MODE_PRIVATE).edit().putString("minecraft_tree", uri.toString()).apply();
            Toast.makeText(this, "Папка Minecraft сохранена на устройстве.", Toast.LENGTH_LONG).show();
            renderPage();
        } catch (Exception error) {
            Toast.makeText(this, "Android не выдал постоянный доступ к этой папке.", Toast.LENGTH_LONG).show();
        }
    }

    private void createInstance() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("Например, Survival");
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(PANEL_2, BORDER, 10));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый профиль")
                .setView(wrap)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.length() < 2 || name.length() > 24) {
                input.setError("От 2 до 24 символов.");
                return;
            }
            JSONArray values = getInstances();
            for (int i = 0; i < values.length(); i++) {
                JSONObject existing = values.optJSONObject(i);
                if (existing != null && existing.optString("name").equalsIgnoreCase(name)) {
                    input.setError("Такой профиль уже есть.");
                    return;
                }
            }
            try {
                values.put(new JSONObject().put("name", name).put("createdAt", System.currentTimeMillis()));
                getSharedPreferences(DATA_PREFS, MODE_PRIVATE).edit().putString("instances", values.toString()).apply();
                String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
                new File(new File(getFilesDir(), "horizon/instances"), safe).mkdirs();
                dialog.dismiss();
                renderPage();
                Toast.makeText(this, "Профиль сохранён.", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                input.setError("Не удалось сохранить профиль.");
            }
        }));
        dialog.show();
    }

    private void confirmDeleteInstance(int index, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить профиль?")
                .setMessage("Профиль «" + name + "» будет удалён с этого устройства.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    JSONArray values = getInstances();
                    JSONArray next = new JSONArray();
                    for (int i = 0; i < values.length(); i++) {
                        if (i == index) continue;
                        JSONObject item = values.optJSONObject(i);
                        if (item != null) next.put(item);
                    }
                    getSharedPreferences(DATA_PREFS, MODE_PRIVATE).edit().putString("instances", next.toString()).apply();
                    String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
                    deleteTree(new File(new File(getFilesDir(), "horizon/instances"), safe));
                    renderPage();
                }).show();
    }

    private JSONArray getInstances() {
        try {
            return new JSONArray(getSharedPreferences(DATA_PREFS, MODE_PRIVATE).getString("instances", "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить кеш?")
                .setMessage("Будут удалены только временные файлы приложения. Сохранённый аккаунт, профили и выбранная папка Minecraft останутся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    deleteTree(getCacheDir());
                    Toast.makeText(this, "Временный кеш очищен.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void addHomeSpacer() {
        View v = new View(this);
        home.addView(v, new LinearLayout.LayoutParams(1, dp(3)));
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(round(PANEL, BORDER, 20));
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
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? BG : WHITE);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(round(primary ? WHITE : PANEL_2, primary ? WHITE : BORDER, 14));
        return button;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
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

    private int countChildren(File folder) {
        File[] entries = folder.listFiles();
        return entries == null ? 0 : entries.length;
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.equals(getFilesDir()) && !file.equals(getCacheDir())) file.delete();
    }

    private JSONObject fetchJsonObject(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Horizon-Mobile-Android");
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (status < 200 || status >= 300) throw new IllegalStateException("GitHub HTTP " + status);
            return new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private JSONArray fetchJsonArray(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Horizon-Mobile-Android");
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (status < 200 || status >= 300) throw new IllegalStateException("GitHub HTTP " + status);
            return new JSONArray(body);
        } finally {
            connection.disconnect();
        }
    }

    private String trimText(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() <= max) return clean.isEmpty() ? "Нет описания релиза." : clean;
        return clean.substring(0, max).trim() + "…";
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось открыть ссылку.", Toast.LENGTH_SHORT).show();
        }
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
            TextView title = label("CHEATLAB · ПРИВЯЗКА АККАУНТА", 12, WHITE, true);
            bar.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
            Button close = button("Закрыть", false);
            close.setMinWidth(dp(82));
            close.setVisibility(authRequired ? View.GONE : View.VISIBLE);
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
        } else {
            authWebView.loadUrl(SITE_URL + "auth");
        }
        handler.removeCallbacks(authPoll);
        handler.post(authPoll);
    }

    private void closeAuth() {
        if (authRequired) return;
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
                verifySession(token, client, true, "");
            } catch (Exception ignored) {
                // No session yet; the real CHEATLAB account form may still be open.
            }
        });
    }

    private void restoreSession() {
        String saved = readEncrypted();
        if (saved == null || saved.isEmpty()) {
            authRequired = true;
            openAuth();
            return;
        }
        try {
            JSONObject data = new JSONObject(saved);
            String token = data.optString("token", "");
            String client = data.optString("client", "");
            String nickname = data.optString("nick", "");
            if (!token.matches("[A-Za-z0-9_-]{20,80}") || !client.matches("[A-Za-z0-9_-]{16,64}")) {
                clearEncrypted();
                authRequired = true;
                openAuth();
                return;
            }
            // Show the cached account immediately, even when the network is offline.
            currentNick = nickname;
            authRequired = false;
            accountMessage = "Аккаунт сохранён на этом устройстве. Проверяю сессию…";
            activePage = "play";
            renderPage();
            verificationRunning = true;
            verifySession(token, client, false, nickname);
        } catch (Exception error) {
            clearEncrypted();
            authRequired = true;
            accountMessage = "Сохранённая сессия повреждена. Подключите аккаунт заново.";
            openAuth();
        }
    }

    private void verifySession(String token, String client, boolean fromWebView, String cachedNickname) {
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
                failure = "Нет соединения с CHEATLAB. Сохранённый аккаунт оставлен на устройстве.";
            }

            final String verifiedNick = nickname;
            final String message = failure;
            runOnUiThread(() -> {
                verificationRunning = false;
                if (verifiedNick != null) {
                    currentNick = verifiedNick;
                    authRequired = false;
                    accountMessage = "Аккаунт подтверждён сервером CHEATLAB и сохранён на устройстве.";
                    renderPage();
                    if (authVisible) closeAuth();
                } else if (fromWebView) {
                    accountMessage = message == null ? "Не удалось войти." : message;
                    setAccountState("", accountMessage);
                } else if (message != null && message.startsWith("Сессия")) {
                    clearEncrypted();
                    currentNick = "";
                    authRequired = true;
                    accountMessage = message;
                    renderPage();
                    openAuth();
                } else {
                    // Offline launch: keep the encrypted cache and its displayed account.
                    currentNick = cachedNickname;
                    authRequired = false;
                    accountMessage = message == null ? "Сохранённый аккаунт доступен офлайн." : message;
                    renderPage();
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
        if (saved == null || saved.isEmpty()) {
            currentNick = "";
            authRequired = true;
            accountMessage = "Аккаунт отключён.";
            renderPage();
            openAuth();
            return;
        }
        if (accountButton != null) accountButton.setEnabled(false);
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
                // Local sign-out still clears this device's credential.
            }
            clearEncrypted();
            runOnUiThread(() -> {
                currentNick = "";
                authRequired = true;
                accountMessage = "Аккаунт CHEATLAB отвязан от этого устройства.";
                lastSeenToken = "";
                renderPage();
                if (authWebView != null) {
                    authWebView.evaluateJavascript("localStorage.removeItem('cheatlab.session');true;", ignored ->
                            authWebView.loadUrl(SITE_URL + "auth"));
                }
                openAuth();
            });
        });
    }

    private void setAccountState(String nickname, String description) {
        currentNick = nickname == null ? "" : nickname;
        accountMessage = description == null ? "" : description;
        if (accountTitle != null) accountTitle.setText(currentNick.isEmpty() ? "Аккаунт не подключён" : "@" + currentNick);
        if (accountDescription != null) accountDescription.setText(accountMessage);
        if (accountButton != null) accountButton.setText(currentNick.isEmpty() ? "Подключить аккаунт" : "Управление аккаунтом");
    }

    private void saveEncrypted(String plainText) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        boolean persisted = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit();
        if (!persisted) throw new IllegalStateException("Could not persist the encrypted CHEATLAB session.");
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
        // Commit before leaving the screen so a just-requested logout cannot
        // resurrect the old credential after process death.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().commit();
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

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (authVisible) {
            if (authWebView != null && authWebView.canGoBack()) {
                authWebView.goBack();
            } else if (!authRequired) {
                closeAuth();
            }
            return;
        }
        if (!"play".equals(activePage)) {
            navigate("play");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!authRequired && !currentNick.isEmpty() && !verificationRunning) {
            String saved = readEncrypted();
            if (saved != null && !saved.isEmpty()) {
                try {
                    JSONObject data = new JSONObject(saved);
                    verificationRunning = true;
                    verifySession(data.optString("token"), data.optString("client"), false, currentNick);
                } catch (Exception ignored) {
                    // A damaged session is handled at the next explicit account action.
                }
            }
        }
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