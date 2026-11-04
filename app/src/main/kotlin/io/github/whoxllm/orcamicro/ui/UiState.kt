package io.github.whoxllm.orcamicro.ui

import io.github.whoxllm.orcamicro.core.gcode.Toolpaths
import io.github.whoxllm.orcamicro.core.mesh.Mesh
import io.github.whoxllm.orcamicro.core.mesh.Placement
import io.github.whoxllm.orcamicro.core.mesh.Vec3
import io.github.whoxllm.orcamicro.core.profiles.Catalog
import io.github.whoxllm.orcamicro.core.settings.BedShape
import io.github.whoxllm.orcamicro.core.settings.PresetEdits
import io.github.whoxllm.orcamicro.core.slicing.SliceInputs
import io.github.whoxllm.orcamicro.data.PresetSelection
import io.github.whoxllm.orcamicro.core.profiles.UserPreset
import io.github.whoxllm.orcamicro.core.settings.RenderOptions
import java.io.File
import io.github.whoxllm.orcamicro.core.network.PrinterConnection
import io.github.whoxllm.orcamicro.core.profiles.PresetType
import io.github.whoxllm.orcamicro.core.network.*

enum class Screen { PLATE, PRESETS, SETTINGS, PREVIEW, DEVICE, ABOUT, LIBRARY, PRINT_SETUP }

/** The loaded model: the mesh as read, how it is placed, and the placed result for display. */
data class ModelState(
    val name: String,
    val mesh: Mesh,
    val placement: Placement,
    val placed: Mesh,
    val size: Vec3,
    val fits: Boolean,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val placedBounds: io.github.whoxllm.orcamicro.core.mesh.Bounds = placed.bounds(),
)

sealed interface SliceState {
    data object Idle : SliceState
    data class Running(val percent: Int, val text: String) : SliceState
    data class Done(val gcode: File, val toolpaths: Toolpaths, val warnings: List<String>, val modelName: String) : SliceState
    data class Failed(val message: String, val details: String?) : SliceState
}

data class UiState(
    val ready: Boolean = false,
    val loadError: String? = null,
    val catalog: Catalog? = null,
    val selection: PresetSelection? = null,
    val edits: PresetEdits = PresetEdits(),
    /** The selected presets with the user's edits applied, as they will be sliced. */
    val inputs: SliceInputs? = null,
    val bed: BedShape? = null,
    val printableHeight: Float = 250f,
    val model: ModelState? = null,
    val slice: SliceState = SliceState.Idle,
    /** The last finished slice, kept while a new model or settings are being prepared. */
    val lastResult: SliceState.Done? = null,
    val screen: Screen = Screen.PLATE,
    val message: String? = null,
    val favorites: Set<String> = emptySet(),
    val settingsType: PresetType = PresetType.PROCESS,
    val presetPicker: PresetType? = null,
    val connection: PrinterConnection = PrinterConnection(),
    val userPresets: List<UserPreset> = emptyList(),
    val renderOptions: RenderOptions = RenderOptions(),
    val printerStatus: String? = null,
    val connecting: Boolean = false,
    val uploadPercent: Int? = null,
    val uploadedFile: String? = null,
    val uploadError: String? = null,
    val printers: List<SavedPrinter> = emptyList(),
    val activePrinterId: String? = null,
    val telemetry: KlipperSnapshot? = null,
    val temperatureHistory: List<TemperatureSeries> = emptyList(),
    val temperatureHistoryError: String? = null,
    val telemetryAt: Long? = null,
    val deviceError: String? = null,
    val deviceBusy: Boolean = false,
    val libraryModels: List<LibraryModel> = emptyList(),
    val libraryBrowser: String? = null,
    val libraryBusy: Boolean = false,
    val libraryProgress: String? = null,
    val archiveChoices: List<String> = emptyList(),
    val pendingLibraryId: String? = null,
    val printDialog: Boolean = false,
    val modelCacheDays: Int = 7,
    val modelCacheBytes: Long = 0,
)
