# Сборка и проверка

[← README](../README.md)

Команды ниже выполняются из корня репозитория в Linux или WSL. Нужны JDK 17, Android SDK 35, build-tools 35, Python 3, Git, curl и unzip. Путь к SDK задаётся в игнорируемом `local.properties` либо через `ANDROID_HOME`.

```sh
git clone https://github.com/redfaced97/OrcaSlicer-Android.git
cd OrcaSlicer-Android
```

## Быстрая debug-сборка

```sh
ORCAMICRO_ORCA_DIR=/path/to/orca bash native/scripts/fetch-orca.sh --profiles-only
# Быстрый вариант: проверяемый опубликованный ARM64-артефакт upstream.
bash scripts/fetch-prebuilt-engine.sh
sh ./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug -PorcaResourcesDir=/path/to/orca/resources \
  -PsourceUrl=https://github.com/redfaced97/OrcaSlicer-Android --max-workers=2
```

## Сборка движка из исходников

Опубликованный native-артефакт может истечь. Тогда соберите движок из сохранённых исходников; дополнительно нужны CMake, Ninja, компилятор C/C++, NDK r27c и несколько часов. Используйте полную копию OrcaSlicer в отдельной папке, а не sparse checkout из быстрой сборки:

```sh
export ANDROID_NDK_ROOT=/path/to/ndk/27.2.12479018
export ORCAMICRO_ORCA_DIR=/path/to/orca-full
export ORCAMICRO_JOBS=2
bash native/scripts/fetch-orca.sh
bash native/scripts/build-deps.sh
bash native/scripts/build-engine.sh app/src/main/jniLibs
sh ./gradlew :app:assembleDebug \
  -PorcaResourcesDir="$ORCAMICRO_ORCA_DIR/resources" --max-workers=2
```

Исходники native bridge сохранены без изменений. Движок, использованный в локальном APK, собран upstream на коммите `d92230eafe9a876baafa6ce623451d57ffa8fb5f`; дерево `native/` совпадает с исходной базой этого форка. SHA-256 ZIP: `58988793c158e9e117557958ead7e9571c9cad70ca2be1ceec8afc066cd5e7d3` (проверен по GitHub API).

## Каталог настроек и тесты

Перегенерация каталога и русских сообщений читает исходники через Git и работает также с profiles-only checkout (дополнения переводов хранятся в `scripts/ru-overrides.json`):

```sh
python3 scripts/generate-options.py /path/to/orca
```

Чистые JVM-тесты без Android SDK:

```sh
sh ./gradlew :core:test -PcoreOnly -PorcaResourcesDir=/path/to/orca/resources
```

Тесты настоящей нарезки на Android 14 ARM64 или x86_64 Google APIs с трансляцией ARM64:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  io.github.redfaced97.orcapocket.test/androidx.test.runner.AndroidJUnitRunner
```

Пакет приложения — `io.github.redfaced97.orcapocket`; исходные Kotlin/JNI namespace сохранены для совместимости с движком. Опциональный `-PsourceUrl=https://github.com/OWNER/REPO` включает ссылку на исходники в экране «О программе».

## Где искать APK

После сборки файл находится в `app/build/outputs/apk/debug/app-debug.apk`. В GitHub Actions артефакт называется `OrcaSlicer-Android-arm64-debug`; ZIP содержит `app-debug.apk`.

## Подпись и обновления

Локальные debug-сборки и сборки разных CI-запусков могут иметь разные подписи. Для обновления поверх установленного приложения нужна та же подпись; `versionCode` не должен уменьшаться. Для постоянных обновлений используйте один ключ. Конфигурация release-подписи в `app/build.gradle.kts` принимает `ORCAMICRO_KEYSTORE`, `ORCAMICRO_KEYSTORE_PASSWORD` и `ORCAMICRO_KEY_ALIAS`. Сам ключ остаётся вне репозитория. Без этих переменных release-сборка использует debug-ключ.

## Жесты

`SceneGestureTest` отправляет события в настоящие Android GestureDetector и ScaleGestureDetector: длинный перенос после двойного тапа вверх/вниз не должен менять матрицу камеры; щипок двумя пальцами должен менять масштаб, обычный свайп — вращать камеру.

## Публикация релиза

В Releases отправляется уже собранный и проверенный APK с постоянной локальной подписью. Повторная сборка в Actions не меняет ключ установки. Бинарные файлы хранятся в отдельной ветке `release-artifacts`, исходники — в `main` и тегах релизов.

После проверки APK, сохранения исходников в коммите и push основной ветки:

```sh
python3 scripts/publish-release.py 0.5.0 \
  dist/OrcaSlicer-arm64-v0.5.0.apk SOURCE_COMMIT release-notes.md
```

Скрипт отправляет APK, его SHA-256, описание и точный коммит исходников в `release-artifacts` через SSH. Workflow `releases.yml` проверяет манифест и создаёт релиз с APK, `SHA256SUMS` и тегом на коммит исходников. GitHub добавляет архивы Source code автоматически. Уже опубликованные файлы не перезаписываются. Локальный GitHub API-токен не нужен: публикацию выполняет `GITHUB_TOKEN` Actions с `contents: write`. Ключ подписи не передаётся в GitHub.
