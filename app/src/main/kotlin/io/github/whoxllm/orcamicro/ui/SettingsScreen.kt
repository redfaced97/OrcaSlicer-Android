package io.github.whoxllm.orcamicro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.profiles.PresetType
import io.github.whoxllm.orcamicro.core.profiles.string
import io.github.whoxllm.orcamicro.core.settings.OrcaOption
import io.github.whoxllm.orcamicro.core.settings.OrcaOptions
import io.github.whoxllm.orcamicro.core.settings.PrintSettings
import kotlinx.serialization.json.*

private val settingTabs = listOf(PresetType.PROCESS to "Печать", PresetType.FILAMENT to "Пластик", PresetType.MACHINE to "Принтер")

@Composable
fun SettingsScreen(state: UiState, viewModel: MainViewModel) {
    val inputs = state.inputs ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var allOptions by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var changedOnly by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable(tab) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<OrcaOption?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var transferType by rememberSaveable { mutableStateOf(PresetType.PROCESS) }
    val type = settingTabs[tab].first
    val preset = when (type) {
        PresetType.MACHINE -> inputs.machine
        PresetType.PROCESS -> inputs.process
        PresetType.FILAMENT -> inputs.filament
    }
    val base = viewModel.presetFor(type)
    val keysChanged = when (type) {
        PresetType.MACHINE -> state.edits.machine.keys + state.edits.rawMachine.keys
        PresetType.PROCESS -> state.edits.process.keys + state.edits.rawProcess.keys
        PresetType.FILAMENT -> state.edits.filament.keys + state.edits.rawFilament.keys
    }
    val options = remember(type, preset.keys) { OrcaOptions.forType(type, preset) }
    val quickKeys = remember(type) { PrintSettings.forType(type).map { it.key }.toSet() }
    val categories = remember(options) { options.map { it.categoryRu }.distinct().sorted() }
    val shown = remember(options, query, allOptions, favoritesOnly, changedOnly, category, state.favorites, keysChanged) {
        options.filter { option ->
            (allOptions || query.isNotBlank() || favoritesOnly || changedOnly || option.key in quickKeys) &&
                option.matches(query) && (category == null || category == option.categoryRu) &&
                (!favoritesOnly || "${type.name}/${option.key}" in state.favorites) &&
                (!changedOnly || option.key in keysChanged)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::consumeMessage)
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importPreset(it, transferType) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportPreset(it, transferType) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки Orca") },
                navigationIcon = { IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "На стол") } },
                actions = { IconButton(onClick = { confirmReset = true }) { Icon(Icons.Default.Refresh, "Сбросить вкладку") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { transferType = type; import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("Импорт", maxLines = 1) }
                    Button(onClick = { transferType = type; export.launch("OrcaPocket-${type.orcaName}.json") }, modifier = Modifier.weight(1f)) { Text("Экспорт", maxLines = 1) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                settingTabs.forEachIndexed { index, (_, title) -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it; category = null },
                placeholder = { Text("Найти параметр", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить поиск") } } } else null,
                singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = !allOptions, onClick = { allOptions = false; category = null }, label = { Text("Основные") }) }
                item { FilterChip(selected = allOptions, onClick = { allOptions = true }, label = { Text("Все · ${options.size}") }) }
                item { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Избранное") }) }
                item { FilterChip(selected = changedOnly, onClick = { changedOnly = !changedOnly }, label = { Text("Изменены · ${keysChanged.size}") }) }
            }
            if (allOptions) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Все разделы") }) }
                    items(categories) { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
                }
            }
            Text(
                "${preset.string("name").orEmpty()} · ${shown.size} параметров",
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Параметры не найдены. Проверьте поиск и фильтры.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { "${type.name}/${it.key}" }) { option ->
                        val value = preset[option.key]
                        ListItem(
                            overlineContent = { Text(option.key, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            headlineContent = { Text(option.labelRu, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (value == null) "По умолчанию в движке" else OrcaOptions.display(value).ifBlank { "Пусто" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = if (option.key in keysChanged) { { Badge { Text("•") } } } else null,
                            trailingContent = {
                                IconButton(onClick = { viewModel.toggleFavorite(type, option.key) }) {
                                    val favorite = "${type.name}/${option.key}" in state.favorites
                                    Icon(if (favorite) Icons.Default.Star else Icons.Default.Add, if (favorite) "Убрать из избранного" else "В избранное", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.clickable { editing = option },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    editing?.let { option ->
        OptionEditor(
            option = option, value = preset[option.key], baseValue = base?.get(option.key), changed = option.key in keysChanged,
            onSave = { viewModel.setRawEdit(type, option.key, it); editing = null },
            onReset = { viewModel.setRawEdit(type, option.key, null); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false }, title = { Text("Сбросить «${settingTabs[tab].second}»?") },
        text = { Text("Изменения и импортированный профиль этой вкладки будут удалены. Вернутся значения выбранного системного профиля.") },
        confirmButton = { TextButton(onClick = { viewModel.resetEdits(type); confirmReset = false }) { Text("Сбросить") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
    )
}

@Composable
private fun OptionEditor(option: OrcaOption, value: JsonElement?, baseValue: JsonElement?, changed: Boolean, onSave: (JsonElement) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var text by remember(option.key) { mutableStateOf(OrcaOptions.display(value)) }
    var error by remember(option.key) { mutableStateOf<String?>(null) }
    var showHelp by remember(option.key) { mutableStateOf(false) }
    val list = option.isList || value is JsonArray
    val actualOption = if (list && !option.isList) option.copy(valueType = "coStrings") else option
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(option.labelRu, style = MaterialTheme.typography.titleLarge)
            Text(option.key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            if (option.descriptionRu.isNotEmpty()) {
                TextButton(onClick = { showHelp = !showHelp }, contentPadding = PaddingValues(0.dp)) { Text(if (showHelp) "Скрыть описание" else "Описание параметра") }
                if (showHelp) Text(option.descriptionRu, style = MaterialTheme.typography.bodySmall)
            }
            if (value == null) Text("В профиле значение не задано. Пока вы его не сохраните, движок использует своё значение по умолчанию.", style = MaterialTheme.typography.bodySmall)
            if (list) Text("Полный JSON-массив. Значения разных экструдеров сохраняются отдельно. Пример: [\"210\", \"215\"].", style = MaterialTheme.typography.bodySmall)
            if (!list && option.choices.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(option.choices) { choice -> FilterChip(selected = text == choice, onClick = { text = choice; error = null }, label = { Text(choice) }) }
                }
            }
            if (option.valueType == "coBool" && !list) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Включено", Modifier.weight(1f))
                    Switch(checked = text == "1" || text == "true", onCheckedChange = { text = if (it) "1" else "0"; error = null })
                }
            } else {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    label = { Text(if (list) "JSON-массив" else "Значение${if (option.unit.isBlank()) "" else " · ${option.unit}"}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (option.isGcode || list) 4 else 1, maxLines = if (option.isGcode || list) 10 else 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (list || option.isGcode) FontFamily.Monospace else FontFamily.Default),
                    keyboardOptions = KeyboardOptions(keyboardType = if (option.isNumber) KeyboardType.Decimal else KeyboardType.Text),
                    isError = error != null, supportingText = error?.let { message -> { Text(message) } },
                )
            }
            if (baseValue != null) Text("В исходном профиле: ${OrcaOptions.display(baseValue).take(160)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (option.key in setOf("single_extruder_multi_material", "filament_map", "filament_extruder_variant") || option.key.startsWith("printhost_") || option.key in setOf("host_type", "print_host", "print_host_webui", "post_process")) {
                Text("Поле профиля сохраняется. Эта версия работает с одним материалом и не запускает сетевую отправку или внешние скрипты.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReset, enabled = changed, modifier = Modifier.weight(1f)) { Text("Вернуть") }
                Button(onClick = {
                    try { onSave(OrcaOptions.parseValue(actualOption, text)) }
                    catch (e: Exception) { error = e.message ?: "Проверьте значение" }
                }, modifier = Modifier.weight(1f), enabled = !(option.valueType == "coBool" && text.isBlank())) { Text("Сохранить") }
            }
        }
    }
}
