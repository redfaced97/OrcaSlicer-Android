package io.github.whoxllm.orcamicro.core.settings

import io.github.whoxllm.orcamicro.core.profiles.PresetType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

/** The preset option lists, labels and help text from the pinned OrcaSlicer sources. */
@Serializable
data class OrcaOption(
    val key: String,
    val type: PresetType,
    val label: String,
    val labelRu: String = label,
    val category: String = "Other",
    val categoryRu: String = category,
    val description: String = "",
    val descriptionRu: String = description,
    val valueType: String = "coString",
    val unit: String = "",
    val choices: List<String> = emptyList(),
) {
    val isList: Boolean get() = valueType in setOf("coFloats", "coInts", "coStrings", "coPercents", "coBools", "coEnums", "coFloatsOrPercents", "coPoints", "coPointsGroups")
    val isGcode: Boolean get() = "gcode" in key
    val isNumber: Boolean get() = valueType in setOf("coFloat", "coInt", "coPercent", "coFloatOrPercent")

    fun matches(query: String): Boolean = query.trim().split(Regex("\\s+")).all { word ->
        listOf(key, label, labelRu, category, categoryRu).any { it.contains(word, ignoreCase = true) }
    }
}

object OrcaOptions {
    val all: List<OrcaOption> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/orca-options.json")) { "Orca option catalog missing" }
        stream.bufferedReader().use { Json.decodeFromString(ListSerializer(OrcaOption.serializer()), it.readText()) }
    }

    fun forType(type: PresetType, preset: JsonObject): List<OrcaOption> {
        val known = all.filter { it.type == type }
        val knownKeys = known.map { it.key }.toSet()
        return known + preset.keys.filter { it !in knownKeys && it !in metadata }.map { key ->
            OrcaOption(key, type, key, categoryRu = "Из профиля", valueType = if (preset[key] is JsonArray) "coStrings" else "coString")
        }
    }

    val metadata = setOf("name", "from", "type", "version", "inherits", "instantiation", "setting_id", "base_id", "is_custom_defined", "filament_id")

    /** Scalars stay Orca strings. Lists use complete JSON so extruder values stay distinct. */
    fun parseValue(option: OrcaOption, text: String): JsonElement {
        if (option.isList) {
            val array = Json.parseToJsonElement(text) as? JsonArray ?: error("Нужен JSON-массив, например [\"210\", \"215\"]")
            require(array.all { it is JsonPrimitive || (option.valueType == "coPointsGroups" && it is JsonArray) }) { "Недопустимая структура массива" }
            return array
        }
        if (option.isNumber) {
            val number = text.trim().replace(',', '.').removeSuffix("%").toDoubleOrNull()
            require(number != null && number.isFinite()) { "Введите конечное число" }
            require(option.valueType != "coInt" || number == Math.rint(number)) { "Введите целое число" }
            return JsonPrimitive(text.trim().replace(',', '.'))
        }
        return JsonPrimitive(text)
    }

    fun display(value: JsonElement?): String = when (value) {
        null -> ""
        is JsonPrimitive -> value.contentOrNull.orEmpty()
        else -> value.toString()
    }
}
