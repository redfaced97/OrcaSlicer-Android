package io.github.whoxllm.orcamicro.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.BuildConfig

private val thirdParty = listOf(
    "OrcaSlicer (libslic3r), AGPL-3.0, by SoftFever and contributors, based on Bambu Studio, PrusaSlicer and Slic3r",
    "Boost, Boost Software License 1.0",
    "oneTBB, Apache-2.0",
    "CGAL, GPL-3.0 / LGPL-3.0",
    "Eigen, MPL-2.0",
    "Open CASCADE Technology, LGPL-2.1 with exception",
    "OpenCV, Apache-2.0",
    "GMP and MPFR, LGPL-3.0",
    "NLopt, MIT / LGPL-2.1",
    "Draco, Apache-2.0",
    "libnoise, LGPL-2.1",
    "cereal, BSD-3-Clause",
    "expat, MIT",
    "libpng, PNG Reference Library License",
    "libjpeg-turbo, IJG / BSD-3-Clause",
    "FreeType, FreeType License",
    "Clipper and Clipper2, Boost Software License 1.0",
    "Qhull, Qhull License",
    "admesh, libigl, mcut, miniz, nlohmann/json and the other libraries bundled with OrcaSlicer",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization, AndroidX and Jetpack Compose, Apache-2.0",
)

@Composable
fun AboutScreen(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    Scaffold(topBar = { DesktopHeader(state, viewModel) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("OrcaSlicer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Text("Неофициальная Android-адаптация. Интерфейс по мотивам настольной OrcaSlicer, для сенсорного экрана телефона.")
            UpdateCard()
            Text("Команда", style = MaterialTheme.typography.titleMedium)
            Text("Продюсер — redfaced97")
            Text("Программист — Codex GPT-6.1-Sol")
            Text("Бета-тестер — redfaced97 и Ender 3 v3 SE (Klipper)")
            Text("Источники", style = MaterialTheme.typography.titleMedium)
            Text("Android-основа и JNI-мост — OrcaMicro, whoxllm и участники проекта. Исходники адаптированы для этой версии.")
            OutlinedButton(onClick = { open("https://github.com/whoxllm/OrcaMicro") }) { Text("Исходники OrcaMicro") }
            Text("Движок libslic3r, профили, основа русских подписей и подсказок — OrcaSlicer ${BuildConfig.ORCA_VERSION}, SoftFever и участники; на основе Bambu Studio, PrusaSlicer и Slic3r. Недостающие русские подписи и краткие пояснения дополнены в этой адаптации.")
            OutlinedButton(onClick = { open("https://github.com/OrcaSlicer/OrcaSlicer") }) { Text("Исходники OrcaSlicer") }
            Text("Шрифт Inter — Rasmus Andersson и участники, SIL OFL 1.1. Иконки действий и иконка приложения — официальный репозиторий OrcaSlicer: resources/images/OrcaSlicer.svg. Акцент #009688 и серые тона — из настольного интерфейса OrcaSlicer. Android-представление иконки преобразовано из оригинального SVG.")
            OutlinedButton(onClick = { open("https://www.orcaslicer.com/") }) { Text("Официальный сайт OrcaSlicer") }
            Text("Пульт, графики температур и отправка G-code — собственный HTTP-клиент по документации Moonraker и Klipper. Веб-интерфейс открывает установленный Mainsail или Fluidd. Каталоги моделей — нативный интерфейс и HTTP-клиенты JSON API Thingiverse, Printables, Cults3D и MakerWorld. Карточки показывают автора, источник, лицензию и доступную цену. Quick Print использует ваши профили и локальную нарезку. Образец компоновки — экран Prepare to Print в Bambu Handy; код этого экрана написан для Android-адаптации. API Cults не предоставляет файлы.")
            Text("Возможности этой версии", style = MaterialTheme.typography.titleMedium)
            Text("STL, ZIP и геометрия 3MF; один объект и материал, 638 параметров, свои профили, карточки моделей, поиск, теги, кэш, просмотр слоёв с Dev menu и несколько принтеров Klipper. Настройки 3MF-проектов, покраска и мультиматериальная нарезка пока не поддерживаются. Совместимость 90% не измерена.")
            Text("Лицензия AGPL-3.0. Исходники доступны по ссылке ниже и в разделе Source code у каждого релиза GitHub.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { open("https://github.com/redfaced97/OrcaSlicer-Android/releases") }) { Text("Релизы и обновления") }
            if (BuildConfig.SOURCE_URL.isNotBlank()) OutlinedButton(onClick = { open(BuildConfig.SOURCE_URL) }) { Text("Исходники этой адаптации") }
            Text("Библиотеки", style = MaterialTheme.typography.titleMedium)
            for (line in thirdParty) Text("• $line", style = MaterialTheme.typography.bodySmall)
        }
    }
}
