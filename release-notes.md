## 0.6.9

- Стартовый экран: новый проект, открытие STL / 3MF, продолжение текущей работы и карточки последних проектов с миниатюрами.
- Автосохранение геометрии, положения, поворота, масштаба, цвета филамента и пресетов с изменениями. Проекты восстанавливаются после перезапуска и сохраняются при очистке кэша моделей.
- Логотип Orca в верхней панели возвращает на главную. Удаление сохранённого проекта требует подтверждения.
- Нарезка встраивает в G-code две прозрачные PNG-миниатюры: 32×32 и 400×400. Предпросмотр учитывает поворот и цвет модели, освещение и перекрытие граней. Изображения не зависят от камеры интерфейса.
- Миниатюры записываются стандартными блоками OrcaSlicer, с сохранением заголовка и команд печати. Они включены в экспортируемый и отправляемый на принтер G-code. Внешний готовый G-code остаётся без изменений.
- Русский и английский интерфейс для новых экранов; README и руководство дополнены примерами.

ARM64 · Android 14+ · versionCode 15. Подпись сохранена для установки поверх предыдущих релизов.

## English

- Home screen with new/open actions, continue working and recent project cards with model thumbnails.
- Automatic local snapshots retain geometry, placement, rotation, scale, filament colour and edited presets across restarts and model-cache cleanup.
- Tap the Orca logo to return Home. Project deletion requires confirmation.
- Completed slices embed transparent 32×32 and 400×400 PNG previews in standard OrcaSlicer G-code blocks. Depth-tested, shaded model previews follow rotation and filament colour, independently of the interactive camera.
- Printer uploads and local exports include both images while preserving print commands and the slicer header. Existing external G-code is not modified.
- Updated Russian / English UI and documentation.
