package io.github.whoxllm.orcamicro.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.whoxllm.orcamicro.core.gcode.GcodeParser
import io.github.whoxllm.orcamicro.core.mesh.Mesh
import io.github.whoxllm.orcamicro.core.mesh.Placement
import io.github.whoxllm.orcamicro.core.mesh.StlFormatException
import io.github.whoxllm.orcamicro.core.mesh.StlReader
import io.github.whoxllm.orcamicro.core.profiles.Catalog
import io.github.whoxllm.orcamicro.core.profiles.PresetRef
import io.github.whoxllm.orcamicro.core.profiles.PresetType
import io.github.whoxllm.orcamicro.core.profiles.PresetExchange
import io.github.whoxllm.orcamicro.core.profiles.first
import io.github.whoxllm.orcamicro.core.profiles.string
import io.github.whoxllm.orcamicro.core.network.PrinterConnection
import io.github.whoxllm.orcamicro.core.network.MoonrakerClient
import io.github.whoxllm.orcamicro.core.settings.BedShape
import io.github.whoxllm.orcamicro.core.settings.PresetEdits
import io.github.whoxllm.orcamicro.core.slicing.EngineResponse
import io.github.whoxllm.orcamicro.core.slicing.SliceInputs
import io.github.whoxllm.orcamicro.data.PresetSelection
import io.github.whoxllm.orcamicro.data.ProfileRepository
import io.github.whoxllm.orcamicro.data.SavedState
import io.github.whoxllm.orcamicro.data.SettingsStore
import io.github.whoxllm.orcamicro.engine.EngineClient
import io.github.whoxllm.orcamicro.engine.EngineEvent
import io.github.whoxllm.orcamicro.engine.EngineResources
import io.github.whoxllm.orcamicro.engine.SliceJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import io.github.whoxllm.orcamicro.core.profiles.UserPreset
import io.github.whoxllm.orcamicro.core.profiles.UserPresets
import io.github.whoxllm.orcamicro.core.settings.RenderOptions
import io.github.whoxllm.orcamicro.core.settings.Russian
import java.io.File

/** Raw (unedited) presets for the current selection. */
private class ResolvedPresets(val selection: PresetSelection, val machine: JsonObject, val process: JsonObject, val filament: JsonObject)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val profiles = ProfileRepository(application.assets)
    private val store = SettingsStore(application)
    private val engine = EngineClient(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var presets: ResolvedPresets? = null
    private var placementJob: Job? = null
    private var sliceJob: Job? = null
    private val saves = Channel<SavedState>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (saved in saves) runCatching { store.save(saved) }.onFailure { showMessage("Не удалось сохранить настройки: ${it.message}") }
        }
        viewModelScope.launch { load() }
    }

    // ---- loading -------------------------------------------------------------------------

    private suspend fun load() {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            val catalog = withContext(Dispatchers.IO) { profiles.catalog }
            val selection = saved.selection?.takeIf { isValid(catalog, it) } ?: defaultSelection(catalog)
            val resolved = withContext(Dispatchers.IO) { resolve(selection) }
            presets = resolved
            val firstRun = saved.selection == null
            _state.update {
                it.copy(
                    ready = true,
                    catalog = catalog,
                    selection = selection,
                    edits = if (firstRun) PresetEdits() else saved.edits,
                    favorites = saved.favorites,
                    connection = saved.connection,
                    userPresets = UserPresets.merge(saved.userPresets, listOfNotNull(
                        saved.edits.importedMachine?.let { UserPreset(PresetType.MACHINE, it) },
                        saved.edits.importedProcess?.let { UserPreset(PresetType.PROCESS, it) },
                        saved.edits.importedFilament?.let { UserPreset(PresetType.FILAMENT, it) })),
                    renderOptions = saved.renderOptions,
                    screen = if (firstRun) Screen.PRESETS else Screen.PLATE,
                )
            }
            refreshInputs()
            restoreModel(saved)
        } catch (e: Exception) {
            _state.update { it.copy(ready = true, loadError = "Не удалось загрузить профили принтера: ${e.message}") }
        }
    }

    private suspend fun restoreModel(saved: SavedState) {
        val name = saved.modelName ?: return
        val mesh = withContext(Dispatchers.IO) {
            runCatching { StlReader.read(store.modelFile.readBytes()) }.getOrNull()
        } ?: return
        setModel(name, mesh, saved.placement ?: centeredPlacement())
    }

    private fun isValid(catalog: Catalog, selection: PresetSelection): Boolean {
        val printer = catalog.printer(selection.vendor, selection.printer) ?: return false
        return selection.process in catalog.processesFor(selection.vendor, printer) &&
            selection.filament in catalog.filamentsFor(selection.vendor, printer)
    }

    private fun defaultSelection(catalog: Catalog): PresetSelection {
        val vendor = catalog.vendor("Custom")?.takeIf { it.printers.isNotEmpty() } ?: catalog.printerVendors.first()
        val printer = vendor.printers.firstOrNull { it.name == "MyKlipper 0.4 nozzle" } ?: vendor.printers.first()
        return selectionFor(catalog, vendor.name, printer.name)
    }

    private fun selectionFor(catalog: Catalog, vendor: String, printerName: String): PresetSelection {
        val printer = requireNotNull(catalog.printer(vendor, printerName))
        val filaments = catalog.filamentsFor(vendor, printer)
        val process = requireNotNull(catalog.defaultProcessFor(vendor, printer)) { "$printerName has no process presets" }
        val filament = printer.defaultFilament?.takeIf { it in filaments }
            ?: filaments.firstOrNull { it.name == "Generic PLA @System" }
            ?: filaments.first()
        return PresetSelection(vendor, printerName, process, filament)
    }

    private fun resolve(selection: PresetSelection) = ResolvedPresets(
        selection = selection,
        machine = profiles.resolve(selection.vendor, selection.printer),
        process = profiles.resolve(selection.vendor, selection.process),
        filament = profiles.resolve(selection.filament.vendor, selection.filament.name),
    )

    /** Recomputes the edited presets and everything derived from the printer (bed, fit). */
    private fun refreshInputs() {
        val resolved = presets ?: return
        val edits = _state.value.edits
        val inputs = SliceInputs.build(resolved.machine, resolved.process, resolved.filament, edits)
        val bed = BedShape.of(inputs.machine) ?: BedShape.rectangle(0f, 0f, 220f, 220f)
        val height = inputs.machine.first("printable_height")?.toFloatOrNull() ?: 250f
        _state.update { state ->
            state.copy(
                inputs = inputs,
                bed = bed,
                printableHeight = height,
                model = state.model?.let { withFit(it, bed, height) },
            )
        }
    }

    // ---- presets and settings ------------------------------------------------------------

    fun selectPrinter(vendor: String, printer: String) {
        val catalog = _state.value.catalog ?: return
        applySelection(selectionFor(catalog, vendor, printer), PresetEdits())
    }

    fun selectProcess(name: String) {
        val selection = _state.value.selection ?: return
        applySelection(selection.copy(process = name), _state.value.edits.copy(process = emptyMap(), rawProcess = emptyMap(), importedProcess = null))
    }

    fun selectFilament(ref: PresetRef) {
        val selection = _state.value.selection ?: return
        applySelection(selection.copy(filament = ref), _state.value.edits.copy(filament = emptyMap(), rawFilament = emptyMap(), importedFilament = null))
    }

    private fun applySelection(selection: PresetSelection, edits: PresetEdits) {
        viewModelScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { resolve(selection) }
                presets = resolved
                _state.update { it.copy(selection = selection, edits = edits) }
                refreshInputs()
                persist()
            } catch (e: Exception) {
                showMessage("Не удалось загрузить профиль: ${e.message}")
            }
        }
    }

    fun setEdit(type: PresetType, key: String, value: String?) {
        _state.update { state ->
            fun Map<String, String>.with() = if (value == null) this - key else this + (key to value)
            val edits = state.edits
            state.copy(
                edits = when (type) {
                    PresetType.MACHINE -> edits.copy(machine = edits.machine.with(), rawMachine = edits.rawMachine - key)
                    PresetType.PROCESS -> edits.copy(process = edits.process.with(), rawProcess = edits.rawProcess - key)
                    PresetType.FILAMENT -> edits.copy(filament = edits.filament.with(), rawFilament = edits.rawFilament - key)
                },
            )
        }
        refreshInputs()
        persist()
    }

    fun resetEdits(type: PresetType) {
        _state.update { state ->
            val edits = state.edits
            state.copy(
                edits = when (type) {
                    PresetType.MACHINE -> edits.copy(machine = emptyMap(), rawMachine = emptyMap())
                    PresetType.PROCESS -> edits.copy(process = emptyMap(), rawProcess = emptyMap())
                    PresetType.FILAMENT -> edits.copy(filament = emptyMap(), rawFilament = emptyMap())
                },
            )
        }
        refreshInputs()
        persist()
    }

    /** The unedited preset value, to show what a reset would go back to. */
    fun presetFor(type: PresetType): JsonObject? = when (type) {
        PresetType.MACHINE -> _state.value.edits.importedMachine ?: presets?.machine
        PresetType.PROCESS -> _state.value.edits.importedProcess ?: presets?.process
        PresetType.FILAMENT -> _state.value.edits.importedFilament ?: presets?.filament
    }

    fun setRawEdit(type: PresetType, key: String, value: JsonElement?) {
        _state.update { state ->
            val e = state.edits
            fun Map<String, JsonElement>.edited() = if (value == null) this - key else this + (key to value)
            state.copy(edits = when (type) {
                PresetType.MACHINE -> e.copy(rawMachine = e.rawMachine.edited(), machine = e.machine - key)
                PresetType.PROCESS -> e.copy(rawProcess = e.rawProcess.edited(), process = e.process - key)
                PresetType.FILAMENT -> e.copy(rawFilament = e.rawFilament.edited(), filament = e.filament - key)
            })
        }
        refreshInputs()
        persist()
    }

    fun toggleFavorite(type: PresetType, key: String) {
        val id = "${type.name}/$key"
        _state.update { it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id) }
        persist()
    }

    fun selectUserPreset(preset: UserPreset) {
        _state.update { state -> state.copy(edits = activate(state.edits, preset), lastResult = null) }
        refreshInputs()
        persist()
        if (preset.type == PresetType.MACHINE && _state.value.connection.apiUrl.isBlank()) {
            preset.preset.string("print_host")?.takeIf { it.isNotBlank() }?.let { host ->
                saveConnection(PrinterConnection(host, preset.preset.string("print_host_webui").orEmpty().ifBlank { host }))
            }
        }
    }

    private fun activate(e: PresetEdits, p: UserPreset): PresetEdits = when (p.type) {
        PresetType.MACHINE -> e.copy(machine = emptyMap(), rawMachine = emptyMap(), importedMachine = p.preset)
        PresetType.PROCESS -> e.copy(process = emptyMap(), rawProcess = emptyMap(), importedProcess = p.preset)
        PresetType.FILAMENT -> e.copy(filament = emptyMap(), rawFilament = emptyMap(), importedFilament = p.preset)
    }

    fun saveUserPreset(type: PresetType, name: String) {
        val inputs = _state.value.inputs ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        val base = when(type) { PresetType.MACHINE -> inputs.machine; PresetType.PROCESS -> inputs.process; PresetType.FILAMENT -> inputs.filament }
        val p = UserPreset(type, JsonObject(base + mapOf("name" to JsonPrimitive(clean), "from" to JsonPrimitive("User"))))
        _state.update { it.copy(userPresets = UserPresets.merge(it.userPresets, listOf(p))) }
        selectUserPreset(p)
        showMessage("Профиль «$clean» сохранён в Мои профили")
    }

    fun deleteUserPreset(p: UserPreset) {
        _state.update { it.copy(userPresets = it.userPresets.filterNot { entry -> entry.id == p.id }) }
        // An active preset remains a standalone base until another preset is selected.
        persist()
    }

    fun importPreset(uri: Uri, type: PresetType) {
        viewModelScope.launch {
            try {
                val before = _state.value
                val imported = withContext(Dispatchers.IO) {
                    val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buffer = CharArray(16 * 1024 * 1024 + 1)
                        var size = 0
                        while (size < buffer.size) { val n = reader.read(buffer, size, buffer.size - size); if (n < 0) break; size += n }
                        require(size < buffer.size) { "Набор превышает 16 МБ" }
                        String(buffer, 0, size)
                    } ?: error("Файл недоступен")
                    UserPresets.import(text, type) { kind, parent ->
                        before.userPresets.firstOrNull { it.type == kind && it.name == parent }?.preset
                            ?: profiles.importPreset(JsonObject(mapOf("name" to JsonPrimitive("resolve"), "inherits" to JsonPrimitive(parent))).toString(), kind, before.selection?.vendor.orEmpty(), listOfNotNull(presetFor(kind)))
                    }
                }
                _state.update { s ->
                    var e = s.edits
                    PresetType.entries.forEach { kind -> imported.firstOrNull { it.type == kind }?.let { e = activate(e, it) } }
                    s.copy(userPresets = UserPresets.merge(s.userPresets, imported), edits = e, lastResult = null)
                }
                refreshInputs()
                persist()
                imported.firstOrNull { it.type == PresetType.MACHINE }?.let { selectUserPreset(it) }
                showMessage("Импортировано профилей: ${imported.size}. Они доступны в «Мои профили»")
            } catch (e: Exception) { showMessage("Не удалось импортировать: ${e.message}") }
        }
    }

    fun setModelColor(hex: String) {
        val value = hex.removePrefix("#").uppercase()
        if (!value.matches(Regex("[0-9A-F]{6}"))) { return }
        setRawEdit(PresetType.FILAMENT, "default_filament_colour", JsonArray(listOf(JsonPrimitive("#$value"))))
    }

    fun setRenderOptions(options: RenderOptions) {
        _state.update { it.copy(renderOptions = options) }; persist()
    }

    fun moveModelBy(dx: Float, dy: Float) {
        val m = _state.value.model ?: return
        updatePlacement(m.placement.copy(centerX = m.placement.centerX + dx, centerY = m.placement.centerY + dy), false)
    }

    fun exportPreset(uri: Uri, type: PresetType) {
        val inputs = _state.value.inputs ?: return
        val preset = when (type) {
            PresetType.MACHINE -> inputs.machine
            PresetType.PROCESS -> inputs.process
            PresetType.FILAMENT -> inputs.filament
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(PresetExchange.export(preset, type)) }
                        ?: error("Не удалось открыть место сохранения")
                }
                showMessage("JSON-профиль сохранён")
            } catch (e: Exception) {
                showMessage("Ошибка сохранения: ${e.message}")
            }
        }
    }

    // ---- model ---------------------------------------------------------------------------

    fun openModel(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, mesh) = withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw StlFormatException("Не удалось открыть файл")
                    val mesh = StlReader.read(bytes)
                    store.modelFile.writeBytes(bytes)
                    displayName(uri) to mesh
                }
                setModel(name, mesh, centeredPlacement())
                _state.update { it.copy(lastResult = null) }
                persist()
            } catch (e: StlFormatException) {
                showMessage("Не удалось прочитать STL: ${e.message}")
            } catch (e: OutOfMemoryError) {
                showMessage("Модель слишком большая для этого устройства")
            } catch (e: Exception) {
                showMessage("Не удалось открыть модель: ${e.message}")
            }
        }
    }

    private fun displayName(uri: Uri): String {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.stl"
    }

    private fun centeredPlacement(): Placement {
        val bed = _state.value.bed
        return Placement(centerX = bed?.centerX ?: 110f, centerY = bed?.centerY ?: 110f)
    }

    private suspend fun setModel(name: String, mesh: Mesh, placement: Placement) {
        val state = _state.value
        val model = withContext(Dispatchers.Default) {
            placedModel(name, mesh, placement, state.bed, state.printableHeight)
        }
        _state.update { it.copy(model = model) }
    }

    fun finishModelMove() { persist() }

    fun setPlacement(placement: Placement) = updatePlacement(placement, true)

    private fun updatePlacement(placement: Placement, save: Boolean) {
        val current = _state.value.model ?: return
        val previous = current.placement
        if (placementJob?.isActive != true && placement.rotationX == previous.rotationX && placement.rotationY == previous.rotationY && placement.rotationZ == previous.rotationZ && placement.scalePercent == previous.scalePercent) {
            val moved = current.copy(placement = placement, offsetX = current.offsetX+placement.centerX-previous.centerX, offsetY = current.offsetY+placement.centerY-previous.centerY)
            _state.update { with -> with.copy(model = withFit(moved, with.bed, with.printableHeight)) }
            if(save) persist()
            return
        }
        _state.update { it.copy(model = current.copy(placement = placement)) }
        placementJob?.cancel()
        placementJob = viewModelScope.launch {
            val state = _state.value
            val model = withContext(Dispatchers.Default) {
                placedModel(current.name, current.mesh, placement, state.bed, state.printableHeight)
            }
            _state.update { it.copy(model = model) }
            persist()
        }
    }

    fun centerModel() {
        val model = _state.value.model ?: return
        val bed = _state.value.bed ?: return
        setPlacement(model.placement.copy(centerX = bed.centerX, centerY = bed.centerY))
    }

    fun removeModel() {
        _state.update { it.copy(model = null, lastResult = null, slice = SliceState.Idle) }
        viewModelScope.launch(Dispatchers.IO) { store.modelFile.delete() }
        persist()
    }

    private fun placedModel(name: String, mesh: Mesh, placement: Placement, bed: BedShape?, height: Float): ModelState {
        val placed = placement.place(mesh)
        val size = placed.bounds().size
        return withFit(ModelState(name, mesh, placement, placed, size, fits = true), bed, height)
    }

    private fun withFit(model: ModelState, bed: BedShape?, height: Float): ModelState {
        if (bed == null) return model
        val b = model.placedBounds
        val margin = 0.01f
        val fits = b.min.x + model.offsetX >= bed.minX - margin && b.max.x + model.offsetX <= bed.maxX + margin &&
            b.min.y + model.offsetY >= bed.minY - margin && b.max.y + model.offsetY <= bed.maxY + margin &&
            b.max.z <= height + margin
        return model.copy(fits = fits)
    }

    // ---- slicing -------------------------------------------------------------------------

    fun slice() {
        val state = _state.value
        val model = state.model ?: return showMessage("Сначала откройте STL-модель")
        val inputs = state.inputs ?: return
        if (!model.fits) return showMessage("Модель выходит за границы области печати. Переместите её или уменьшите масштаб.")
        if (state.slice is SliceState.Running) return
        sliceJob = viewModelScope.launch {
            _state.update { it.copy(slice = SliceState.Running(0, "Подготовка")) }
            val job = withContext(Dispatchers.IO) { SliceJob.create(app, model.mesh, model.placement, inputs) }
            val resources = withContext(Dispatchers.IO) { EngineResources.ensure(app) }
            var response: EngineResponse? = null
            var crashed = false
            engine.slice(job.request(resources, model.placement).toJson()).collect { event ->
                when (event) {
                    is EngineEvent.Progress -> _state.update {
                        it.copy(slice = SliceState.Running(event.progress.percent, Russian.text(event.progress.text)))
                    }
                    is EngineEvent.Finished -> {
                        response = event.response
                    }
                    EngineEvent.Crashed -> {
                        crashed = true
                    }
                }
            }
            val result = response
            when {
                crashed -> fail("Движок нарезки неожиданно остановился.", job.logTail())
                result == null -> fail("Движок нарезки не ответил.", job.logTail())
                !result.ok -> fail(explain(result), job.logTail())
                else -> showPreview(job, result, model.name)
            }
        }
    }

    private suspend fun showPreview(job: SliceJob, result: EngineResponse, modelName: String) {
        _state.update { it.copy(slice = SliceState.Running(100, "Подготовка предпросмотра")) }
        val gcode = result.gcodePath?.let { File(it) }?.takeIf { it.isFile } ?: job.gcode
        try {
            val toolpaths = withContext(Dispatchers.Default) { gcode.bufferedReader().use { GcodeParser.parse(it) } }
            val done = SliceState.Done(gcode, toolpaths, result.warnings.map(Russian::text), modelName)
            _state.update { it.copy(slice = SliceState.Idle, lastResult = done, screen = Screen.PREVIEW) }
        } catch (e: OutOfMemoryError) {
            fail("G-code создан, но слишком велик для предпросмотра на этом устройстве.", null)
        }
    }

    private fun explain(result: EngineResponse): String {
        val stage = when (result.stage) {
            "config" -> "Параметры печати отклонены"
            "model" -> "Не удалось прочитать модель"
            "validate" -> "OrcaSlicer не может напечатать эту модель"
            "export" -> "Ошибка записи G-code"
            else -> "Ошибка нарезки"
        }
        return "$stage: ${Russian.text(result.error ?: "неизвестная ошибка")}"
    }

    private fun fail(message: String, details: String?) {
        _state.update { it.copy(slice = SliceState.Failed(message, details)) }
    }

    fun cancelSlice() {
        sliceJob?.cancel()
        sliceJob = null
        _state.update { it.copy(slice = SliceState.Idle) }
    }

    fun dismissSliceError() {
        _state.update { it.copy(slice = SliceState.Idle) }
    }

    fun exportGcode(uri: Uri) {
        val result = _state.value.lastResult ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out -> result.gcode.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("Не удалось открыть место сохранения")
                }
                showMessage("G-code сохранён")
            } catch (e: Exception) {
                showMessage("Ошибка сохранения: ${e.message}")
            }
        }
    }

    // ---- navigation and messages ---------------------------------------------------------

    fun openSettings(type: PresetType = PresetType.PROCESS) = _state.update { it.copy(screen = Screen.SETTINGS, settingsType = type) }

    fun openPreset(type: PresetType) = _state.update { it.copy(screen = Screen.PRESETS, presetPicker = type) }

    fun saveConnection(connection: PrinterConnection): Boolean = try {
        val normalized = connection.normalized()
        require(!normalized.apiKey.contains('\n') && !normalized.apiKey.contains('\r')) { "Недопустимый API-ключ" }
        _state.update { it.copy(connection = normalized, printerStatus = null) }
        persist()
        true
    } catch (e: Exception) { showMessage(e.message ?: "Проверьте адрес"); false }

    fun dismissUploadError() = _state.update { it.copy(uploadError = null) }

    fun testPrinter() {
        if (_state.value.connecting) return
        val connection = _state.value.connection
        _state.update { it.copy(connecting = true, printerStatus = null) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                runCatching { MoonrakerClient(connection).info() }.getOrElse { "Нет подключения: ${it.message}" }
            }
            _state.update { it.copy(connecting = false, printerStatus = status) }
        }
    }

    fun uploadLastGcode() {
        val result = _state.value.lastResult ?: return
        upload(result.gcode, result.modelName.substringBeforeLast('.') + ".gcode")
    }

    fun uploadPickedGcode(uri: Uri) {
        if (_state.value.uploadPercent != null) return
        val name = displayName(uri)
        if (!name.endsWith(".gcode", true) && !name.endsWith(".gco", true) && !name.endsWith(".gc", true)) {
            showMessage("Выберите файл .gcode, .gco или .gc"); return
        }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    File(app.cacheDir, "upload.gcode").also { target ->
                        app.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                            ?: error("Не удалось открыть файл")
                    }
                }
                upload(file, name)
            } catch (e: Exception) { showMessage("Не удалось прочитать G-code: ${e.message}") }
        }
    }

    private fun upload(file: File, name: String) {
        val connection = _state.value.connection
        if (connection.apiUrl.isBlank()) {
            _state.update { it.copy(screen = Screen.DEVICE, message = "Укажите адрес Moonraker для загрузки") }; return
        }
        if (_state.value.uploadPercent != null) return
        _state.update { it.copy(uploadPercent = 0, uploadError = null, uploadedFile = null) }
        viewModelScope.launch {
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    MoonrakerClient(connection).upload(file, name) { percent -> _state.update { it.copy(uploadPercent = percent) } }
                }
                _state.update { it.copy(uploadPercent = null, uploadedFile = uploaded, message = "Загружено: $uploaded") }
            } catch (e: Exception) {
                _state.update { it.copy(uploadPercent = null, uploadError = "Ошибка загрузки: ${e.message}") }
            }
        }
    }

    fun navigate(screen: Screen) = _state.update { it.copy(screen = screen, presetPicker = null) }

    fun back() = _state.update { it.copy(screen = Screen.PLATE) }

    fun showMessage(message: String) = _state.update { it.copy(message = message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun persist() {
        val state = _state.value
        val saved = SavedState(state.selection, state.edits, state.model?.name, state.model?.placement, state.favorites, state.connection, state.userPresets, state.renderOptions)
        saves.trySend(saved)
    }
}
