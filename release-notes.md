Исправлено обнаружение обновлений с длинными номерами версий.

- Проверка читает опубликованные релизы GitHub и выбирает свежий совместимый APK по дате публикации, независимо от отметки Latest.
- Поддерживаются номера с любым количеством числовых частей, например `0.6.7.1.2.3.4.5`. Без дат части сравниваются слева направо с нулями для отсутствующих хвостовых частей. Сумма чисел не используется.
- Дата установленного релиза сохраняется для восстановления загрузки и проверки после перезапуска. Дата установки приложения на телефон не используется.
- Черновики, предварительные релизы и неподходящие APK исключаются. Проверки SHA-256, подписи, пакета, ARM64-движка и более нового Android versionCode сохранены.

**0.6.8 видна старому обновлятору 0.6.6.** Если 0.6.7.1 не отображалась, откройте «О программе» → ⚙ → «Обновления приложения» → «Проверить». Можно также скачать этот APK и установить поверх приложения.

Android 14+, ARM64. Подпись совместима с предыдущими APK. Проверки: 85 JVM-тестов и 3 Android-теста обновлятора; запрос к реальным релизам GitHub.

English: fixed long-version update detection, added release-list scanning and publication-date selection with unlimited numeric-component fallback, and persisted the installed release date for restart and download resumption. Version 0.6.8 is deliberately recognized by older three-component updaters. Open App updates → Check, or install this signed APK over the existing app.
