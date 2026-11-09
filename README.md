# Horizon Mobile

Android-проект для мобильного порта **Horizon / Minecraft-rust**.

Исходный игровой проект подключён как Git submodule в каталоге \`upstream/\` и закреплён на проверенном коммите \`2381ba865fee7292e263794c827fe694d4851bdf\`. Его исходная лицензия MIT сохранена в \`upstream/LICENSE\`; уведомление об авторских правах также находится в корневом \`LICENSE\`.

## Что уже есть в мобильной ветке

- Нативная Android-оболочка с адаптивной компоновкой.
- Привязка аккаунта CHEATLAB через официальный сайт \`https://cheatlab.fun\`.
- Вход и регистрация используют существующие формы сайта, включая CAPTCHA и подтверждение email.
- Сессия CHEATLAB проверяется через Worker API и хранится в Android Keystore.
- Автоматическая сборка debug APK через GitHub Actions.

## Сборка APK

Требуются JDK 17, Android SDK Platform 35 и Gradle 8.9:

\`\`\`bash
git clone --recurse-submodules https://github.com/tatarhost/Minecraft-rust-mobil.git
gradle -p android-app :app:assembleDebug
\`\`\`

APK: \`android-app/app/build/outputs/apk/debug/app-debug.apk\`.

Также можно скачать APK из раздела **Actions → Android APK → Artifacts**.

## Состояние игрового порта

Это первый мобильный этап, а не готовая играбельная сборка. Оригинальный лаунчер использует GPUI — настольный UI-фреймворк. Игровой клиент отдельно использует Rust, winit и wgpu, но для Android ему ещё требуется мобильная точка входа, обработка lifecycle, сенсорное управление, Android-хранилище и упаковка native library. Поэтому кнопка запуска игры остаётся отключённой до появления реального Android runtime.

CHEATLAB — это аккаунт лаунчера, а не лицензия Minecraft. Для доступа к Minecraft Java Edition должна быть сохранена штатная авторизация Microsoft и проверка владения игрой. Не используйте этот проект для обхода авторизации Minecraft или распространения игровых файлов Mojang.

## Исходный проект и права

- Исходный код: [tr4m0ryp/Minecraft-rust](https://github.com/tr4m0ryp/Minecraft-rust).
- Ветка исходника зафиксирована в submodule, чтобы обновления upstream не меняли мобильную сборку незаметно.
- Собственные изменения этого репозитория распространяются под MIT.
- Сборка не содержит Minecraft JAR, текстур, звуков или других проприетарных файлов игры.
