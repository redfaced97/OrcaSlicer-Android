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
            Text("Команда", style = MaterialTheme.typography.titleMedium)
            Text("Продюсер — redfaced97")
            Text("Программист — Codex GPT-6.1-Sol")
            Text("Бета-тестер — redfaced97 и Ender 3 v3 SE (Klipper)")
            Text("Generated with AI assistant, sorry(", style = MaterialTheme.typography.labelMedium)
            Text("Источники", style = MaterialTheme.typography.titleMedium)
            Text("Android-основа и JNI-мост — OrcaMicro, whoxllm и участники проекта. Исходники адаптированы для этой версии.")
            OutlinedButton(onClick = { open("https://github.com/whoxllm/OrcaMicro") }) { Text("Исходники OrcaMicro") }
            Text("Движок libslic3r, профили, русские подписи и подсказки — OrcaSlicer ${BuildConfig.ORCA_VERSION}, SoftFever и участники; на основе Bambu Studio, PrusaSlicer и Slic3r.")
            OutlinedButton(onClick = { open("https://github.com/OrcaSlicer/OrcaSlicer") }) { Text("Исходники OrcaSlicer") }
            Text("Иконка — официальный репозиторий OrcaSlicer: resources/images/OrcaSlicer.svg. Акцент #009688 и серые тона — из настольного интерфейса OrcaSlicer. Android-представление иконки преобразовано из оригинального SVG.")
            OutlinedButton(onClick = { open("https://www.orcaslicer.com/") }) { Text("Официальный сайт OrcaSlicer") }
            Text("Загрузка G-code — собственный HTTP-клиент по документации Moonraker. Вкладка принтера открывает установленный на принтере Mainsail или Fluidd через Android WebView.")
            Text("Возможности этой версии", style = MaterialTheme.typography.titleMedium)
            Text("STL, один объект и материал, 638 параметров, импорт профилей, просмотр слоёв и загрузка G-code. 3MF-проекты, покраска и мультиматериальная нарезка пока отсутствуют. Совместимость 90% не измерена.")
            Text("Лицензия AGPL-3.0. Изменённые исходники поставляются в архиве OrcaSlicer-source.zip вместе с APK.", style = MaterialTheme.typography.bodySmall)
            if (BuildConfig.SOURCE_URL.isNotBlank()) OutlinedButton(onClick = { open(BuildConfig.SOURCE_URL) }) { Text("Исходники этой адаптации") }
            Text("Библиотеки", style = MaterialTheme.typography.titleMedium)
            for (line in thirdParty) Text("• $line", style = MaterialTheme.typography.bodySmall)
        }
    }
}
