package io.github.whoxllm.orcamicro.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.whoxllm.orcamicro.core.network.PrinterConnection
import java.net.URI

@Composable
fun PrinterScreen(state: UiState, viewModel: MainViewModel) {
    var showWeb by rememberSaveable { mutableStateOf(state.connection.apiUrl.isNotBlank()) }
    var refresh by remember { mutableIntStateOf(0) }
    var api by remember(state.connection) { mutableStateOf(state.connection.apiUrl) }
    var web by remember(state.connection) { mutableStateOf(state.connection.webUrl) }
    var apiKey by remember(state.connection) { mutableStateOf(state.connection.apiKey) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::consumeMessage)
    val pickGcode = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::uploadPickedGcode) }
    val context = LocalContext.current
    fun save() = viewModel.saveConnection(PrinterConnection(api, web, apiKey))
    Scaffold(
        topBar = { DesktopHeader(state, viewModel) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showWeb = !showWeb }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text(if (showWeb) "Подключение" else "Веб-интерфейс") }
                if (showWeb) OutlinedButton(onClick = { refresh++ }, shape = MaterialTheme.shapes.small) { Text("Обновить") }
            }
            if (showWeb && state.connection.apiUrl.isNotBlank()) {
                key(state.connection.webUrl, refresh) { EmbeddedKlipper(state.connection, Modifier.weight(1f).fillMaxWidth()) }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Klipper / Moonraker", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(api, { api = it }, label = { Text("Адрес Moonraker") }, placeholder = { Text("http://printer.local:7125") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(web, { web = it }, label = { Text("Mainsail / Fluidd") }, placeholder = { Text("http://printer.local") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API-ключ, если требуется") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (save()) viewModel.testPrinter() }, enabled = !state.connecting, modifier = Modifier.weight(1f)) { Text(if (state.connecting) "Проверка…" else "Проверить") }
                        Button(onClick = { if (save()) showWeb = true }, modifier = Modifier.weight(1f)) { Text("Открыть") }
                    }
                    state.printerStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Телефон и принтер должны быть в одной сети. Загрузка сохраняет файл на принтере; запуск печати доступен в его веб-интерфейсе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.connection.webUrl.isNotBlank()) TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.connection.webUrl))) } }) { Text("Открыть в браузере") }
                    Spacer(Modifier.height(12.dp))
                }
            }
            state.uploadedFile?.let { Text("Загружен: $it", Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (state.connection.apiUrl.isBlank() && !save()) return@OutlinedButton; pickGcode.launch(arrayOf("*/*")) }, enabled = state.uploadPercent == null, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Выбрать G-code") }
                    Button(onClick = viewModel::uploadLastGcode, enabled = state.lastResult != null && state.uploadPercent == null, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("Загрузить стол") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedKlipper(connection: PrinterConnection, modifier: Modifier) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val url = connection.webUrl.ifBlank { connection.apiUrl }
    val result = remember(url) { runCatching {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, target: String?, favicon: Bitmap?) { loading = true; error = null }
                override fun onPageFinished(view: WebView?, target: String?) { loading = false }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, problem: WebResourceError?) {
                    if (request?.isForMainFrame == true) { loading = false; error = "Не удалось открыть Klipper: ${problem?.description}" }
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url ?: return true
                    if (target.scheme !in listOf("http", "https")) return true
                    val initial = URI(url)
                    if (target.host == initial.host && target.port == initial.port && target.scheme == initial.scheme) return false
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    return true
                }
            }
            // The web application manages its own login; no Android JS bridge is exposed.
            loadUrl(url)
        }
    } }
    val browser = result.getOrNull()
    DisposableEffect(browser) { onDispose { browser?.stopLoading(); browser?.destroy() } }
    BackHandler(enabled = browser?.canGoBack() == true) { browser?.goBack() }
    Column(modifier) {
        if (loading && browser != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (browser == null) Text("Системный WebView недоступен. Откройте адрес в браузере.", Modifier.padding(16.dp))
        error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (browser != null) AndroidView(factory = { browser }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}
