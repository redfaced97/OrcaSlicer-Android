package io.github.whoxllm.orcamicro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.mesh.Placement
import io.github.whoxllm.orcamicro.core.profiles.PresetType
import io.github.whoxllm.orcamicro.core.profiles.string
import io.github.whoxllm.orcamicro.core.settings.BedShape
import io.github.whoxllm.orcamicro.render.PlateRenderer
import io.github.whoxllm.orcamicro.render.SceneView
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PlateScreen(state: UiState, viewModel: MainViewModel) {
    val openModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::openModel) }
    var showPlacement by rememberSaveable { mutableStateOf(false) }
    var profilesVisible by rememberSaveable { mutableStateOf(true) }
    val shortScreen = LocalConfiguration.current.screenHeightDp < 500
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::consumeMessage)
    Scaffold(
        topBar = {
            DesktopHeader(state, viewModel) {
                IconButton(onClick = { if (shortScreen) viewModel.navigate(Screen.PRESETS) else profilesVisible = !profilesVisible }) { Icon(Icons.Default.Menu, "Показать или скрыть профили") }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.openSettings() }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                        Text("Параметры", Modifier.padding(start = 6.dp))
                    }
                    Button(onClick = viewModel::slice, enabled = state.model?.fits == true && state.slice !is SliceState.Running, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Text("Нарезать", Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (profilesVisible && !shortScreen) DesktopProfiles(state, viewModel)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth()) {
                    PlateTool(Icons.Default.Add, "Добавить", true) { openModel.launch(arrayOf("*/*")) }
                    PlateTool(Icons.Default.Edit, "Положение", state.model != null) { showPlacement = true }
                    PlateTool(Icons.Default.Refresh, "В центр", state.model != null) { viewModel.centerModel() }
                    PlateTool(Icons.Default.Delete, "Удалить", state.model != null) { viewModel.removeModel() }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val renderer = remember { PlateRenderer() }
                val framedBed = remember { arrayOfNulls<BedShape>(1) }
                SceneView(renderer, Modifier.fillMaxSize()) { r ->
                    val bed = state.bed
                    if (bed != null && framedBed[0] != bed) {
                        r.camera.frame(bed.centerX, bed.centerY, 0f, max(bed.width, bed.depth) * 0.6f)
                        framedBed[0] = bed
                    }
                    r.setBed(bed, state.printableHeight)
                    r.setModel(state.model?.placed, state.model?.fits ?: true)
                }
                Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                    Text("Стол 1", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
                val model = state.model
                if (model == null) {
                    Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Добавьте модель на стол", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { openModel.launch(arrayOf("*/*")) }, shape = MaterialTheme.shapes.small) { Text("Открыть STL") }
                        }
                    }
                } else {
                    Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                            Text(model.name, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                        ModelInfo(model)
                    }
                }
            }
        }
    }
    if (showPlacement && state.model != null) PlacementSheet(state.model, state, viewModel::setPlacement, viewModel::centerModel) { showPlacement = false }
    if (state.screen == Screen.SETTINGS) {
        ModalBottomSheet(onDismissRequest = viewModel::back, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) { SettingsScreen(state, viewModel) }
        }
    }
}

@Composable
private fun RowScope.PlateTool(icon: ImageVector, title: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.weight(1f).heightIn(min = 52.dp), contentPadding = PaddingValues(2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, title, Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun DesktopProfiles(state: UiState, viewModel: MainViewModel) {
    val inputs = state.inputs ?: return
    val rows = listOf(
        Triple(PresetType.MACHINE, "Принтер", inputs.machine.string("name").orEmpty()),
        Triple(PresetType.FILAMENT, "Пластик", inputs.filament.string("name").orEmpty()),
        Triple(PresetType.PROCESS, "Процесс", inputs.process.string("name").orEmpty()),
    )
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            for ((type, label, value) in rows) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { viewModel.openPreset(type) }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(66.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { viewModel.openSettings(type) }) { Icon(Icons.Default.Settings, "Параметры: $label", Modifier.size(18.dp)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ModelInfo(model: ModelState, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "%.1f × %.1f × %.1f mm".format(model.size.x, model.size.y, model.size.z),
                style = MaterialTheme.typography.labelLarge,
            )
            if (!model.fits) {
                Text("Модель выходит за границы стола", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlacementSheet(
    model: ModelState,
    state: UiState,
    onChange: (Placement) -> Unit,
    onCenter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val p = model.placement
    val bed = state.bed
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Положение модели", style = MaterialTheme.typography.titleMedium)
            Text(
                "%.1f × %.1f × %.1f mm".format(model.size.x, model.size.y, model.size.z) +
                    if (model.fits) "" else "  ·  does not fit",
                color = if (model.fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            HorizontalDivider()
            Text("Поворот", style = MaterialTheme.typography.labelLarge)
            RotateRow("X", p.rotationX) { onChange(p.copy(rotationX = it)) }
            RotateRow("Y", p.rotationY) { onChange(p.copy(rotationY = it)) }
            RotateRow("Z", p.rotationZ) { onChange(p.copy(rotationZ = it)) }
            LabeledSlider("Turn on the bed", "${p.rotationZ.roundToInt()}°", p.rotationZ, -180f..180f) {
                onChange(p.copy(rotationZ = it.roundToInt().toFloat()))
            }
            HorizontalDivider()
            LabeledSlider("Масштаб", "${p.scalePercent.roundToInt()}%", p.scalePercent, 10f..400f) {
                onChange(p.copy(scalePercent = it.roundToInt().toFloat()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in listOf(50f, 100f, 200f)) {
                    OutlinedButton(onClick = { onChange(p.copy(scalePercent = preset)) }) { Text("${preset.roundToInt()}%") }
                }
            }
            if (bed != null) {
                HorizontalDivider()
                LabeledSlider("Position X", "%.1f mm".format(p.centerX), p.centerX, bed.minX..bed.maxX) {
                    onChange(p.copy(centerX = it))
                }
                LabeledSlider("Position Y", "%.1f mm".format(p.centerY), p.centerY, bed.minY..bed.maxY) {
                    onChange(p.copy(centerY = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = onCenter) { Text("В центр") }
                OutlinedButton(onClick = {
                    onChange(Placement(centerX = bed?.centerX ?: p.centerX, centerY = bed?.centerY ?: p.centerY))
                }) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Готово") }
            }
        }
    }
}

@Composable
private fun RotateRow(axis: String, degrees: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$axis  ${normalize(degrees).roundToInt()}°", modifier = Modifier.width(72.dp))
        OutlinedButton(onClick = { onChange(normalize(degrees - 90f)) }) { Text("−90°") }
        OutlinedButton(onClick = { onChange(normalize(degrees + 90f)) }) { Text("+90°") }
        TextButton(onClick = { onChange(0f) }) { Text("0°") }
    }
}

private fun normalize(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = current.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

/** Shows [message] in [host] once, then clears it. */
@Composable
fun MessageEffect(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            onShown()
            host.showSnackbar(message)
        }
    }
}
