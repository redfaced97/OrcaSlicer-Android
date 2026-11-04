package io.github.whoxllm.orcamicro.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

@Serializable
data class PrinterConnection(val apiUrl: String = "", val webUrl: String = "", val apiKey: String = "") {
    fun normalized(): PrinterConnection {
        val api = normalizeUrl(apiUrl)
        val parsed = URI(api)
        val defaultWeb = if (parsed.port == 7125) URI(parsed.scheme, null, parsed.host, -1, null, null, null).toString() else api
        return copy(apiUrl = api, webUrl = normalizeUrl(webUrl.ifBlank { defaultWeb }))
    }

    companion object {
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim()
            require(trimmed.isNotEmpty()) { "Укажите адрес принтера" }
            val uri = try { URI(if ("://" in trimmed) trimmed else "http://$trimmed") }
                catch (e: java.net.URISyntaxException) { throw IllegalArgumentException("Проверьте адрес принтера") }
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.port in -1..65535) { "Нужен адрес вида http://printer.local или http://192.168.1.10:7125" }
            return uri.toASCIIString().trimEnd('/')
        }
    }
}

/** Moonraker HTTP API. Upload and start are separate requests. */
class MoonrakerClient(connection: PrinterConnection) {
    private val config = connection.normalized()
    private var objects: Set<String>? = null
    private var heaterConfig: JsonObject? = null

    fun snapshot(): KlipperSnapshot {
        val available = objects ?: get("/printer/objects/list")["objects"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet().also { objects = it }
        val names = available.filter { it in setOf("webhooks", "heaters", "toolhead", "print_stats", "virtual_sdcard", "fan") ||
            it.startsWith("extruder") || it == "heater_bed" || it.startsWith("heater_generic ") || it.startsWith("temperature_sensor ") || it.startsWith("temperature_fan ") ||
            (it == "configfile" && heaterConfig == null) }
        val status = post("/printer/objects/query", buildJsonObject { put("objects", buildJsonObject { names.forEach { put(it, JsonNull) } }) })["status"]?.jsonObject ?: error("Нет телеметрии Klipper")
        if (heaterConfig == null) heaterConfig = status["configfile"]?.jsonObject?.get("config") as? JsonObject
        fun fields(name: String) = status[name] as? JsonObject ?: JsonObject(emptyMap())
        fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun JsonObject.float(key: String, default: Float = 0f) = this[key]?.jsonPrimitive?.floatOrNull?.takeIf { it.isFinite() } ?: default
        fun vector(key: String) = (fields("toolhead")[key] as? JsonArray)?.take(3)?.map { it.jsonPrimitive.floatOrNull?.takeIf(Float::isFinite) ?: 0f }?.takeIf { it.size == 3 } ?: listOf(0f, 0f, 0f)
        val heaters = status.mapNotNull { (name, value) ->
            val data = value as? JsonObject ?: return@mapNotNull null
            val temperature = data["temperature"]?.jsonPrimitive?.floatOrNull?.takeIf(Float::isFinite) ?: return@mapNotNull null
            val limits = heaterConfig?.get(name) as? JsonObject
            HeaterState(name, temperature, data["target"]?.jsonPrimitive?.floatOrNull, limits?.float("min_temp") ?: 0f,
                limits?.get("max_temp")?.jsonPrimitive?.floatOrNull?.takeIf(Float::isFinite))
        }.sortedBy { when(it.name) { "extruder" -> "0"; "heater_bed" -> "1"; else -> it.name } }
        return KlipperSnapshot(fields("webhooks").text("state") == "ready", fields("webhooks").text("state_message"), heaters,
            fields("toolhead").text("homed_axes"), vector("position"), vector("axis_minimum"), vector("axis_maximum"),
            fields("print_stats").text("state"), fields("print_stats").text("filename"), fields("virtual_sdcard").float("progress").coerceIn(0f, 1f),
            if("fan" in available) fields("fan").float("speed") else null)
    }

    fun temperatureHistory(): List<TemperatureSeries> = get("/server/temperature_store").mapNotNull { (name, value) ->
        val data = value as? JsonObject ?: return@mapNotNull null
        fun series(key: String) = (data[key] as? JsonArray)?.takeLast(600)?.map { it.jsonPrimitive.floatOrNull?.takeIf(Float::isFinite) }.orEmpty()
        TemperatureSeries(name, series("temperatures"), series("targets")).takeIf { it.temperatures.isNotEmpty() }
    }

    fun setHeater(name: String, target: Float) = script(KlipperControl.heater(snapshot(), name, target))
    fun jog(axis: String, amount: Float) = script(KlipperControl.move(snapshot(), axis, amount))
    fun home(axes: String = "") = script(KlipperControl.home(snapshot(), axes))
    fun setFan(percent: Int) = script(KlipperControl.fan(snapshot(), percent))
    fun heatersOff() = script("TURN_OFF_HEATERS")
    fun motorsOff() { val s = snapshot(); require(s.ready && !s.printing) { "Двигатели заняты печатью" }; script("M84") }
    fun emergencyStop() { post("/printer/emergency_stop") }
    fun pause() { post("/printer/print/pause") }
    fun resume() { post("/printer/print/resume") }
    fun cancelPrint() { post("/printer/print/cancel") }
    fun startPrint(filename: String) {
        require(filename.isNotBlank() && !filename.contains('\n') && !filename.startsWith('/') && filename.split('/').none { it == ".." })
        val s = snapshot()
        require(s.ready && !s.printing) { "Принтер не готов к новой печати" }
        post("/printer/print/start", buildJsonObject { put("filename", filename) })
    }

    private fun script(command: String) { post("/printer/gcode/script", buildJsonObject { put("script", command) }) }
    private fun get(path: String): JsonObject {
        val http = connect(path)
        return try { readResponse(http)["result"]?.jsonObject ?: error("Пустой ответ Moonraker") } finally { http.disconnect() }
    }
    private fun post(path: String, body: JsonObject = JsonObject(emptyMap())): JsonObject {
        val http = connect(path).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        return try {
            http.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val result = readResponse(http)["result"]
            result as? JsonObject ?: JsonObject(emptyMap())
        } finally { http.disconnect() }
    }

    fun info(): String {
        val http = connect("/server/info")
        return try {
            val result = readResponse(http)["result"]?.jsonObject ?: error("Адрес ответил без данных Moonraker")
            val state = result["klippy_state"]?.jsonPrimitive?.content.orEmpty()
            if (state == "ready") "Klipper готов" else {
                val label = when(state) { "error" -> "ошибка Klipper"; "shutdown" -> "Klipper остановлен"; "startup" -> "Klipper запускается"; "disconnected" -> "нет связи с Klipper"; else -> state }
                val detail = if(state in setOf("error","shutdown")) runCatching {
                    val printer=connect("/printer/info")
                    try { readResponse(printer)["result"]?.jsonObject?.get("state_message")?.jsonPrimitive?.content?.lineSequence()?.firstOrNull() }
                    finally { printer.disconnect() }
                }.getOrNull() else null
                "Moonraker подключён · $label" + (detail?.let { "\n$it" } ?: "")
            }
        } finally { http.disconnect() }
    }

    fun upload(file: File, name: String, onProgress: (Int) -> Unit = {}): String {
        require(file.isFile && file.length() > 0) { "G-code пуст или недоступен" }
        val filename = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\r\\n\"\\\\]"), "_")
        require(filename.endsWith(".gcode", true) || filename.endsWith(".gco", true) || filename.endsWith(".gc", true)) { "Выберите файл G-code" }
        val boundary = "OrcaAndroid${UUID.randomUUID()}"
        val http = connect("/server/files/upload").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            onProgress(0)
            http.outputStream.buffered(64 * 1024).use { out ->
                fun text(value: String) = out.write(value.toByteArray(Charsets.UTF_8))
                text("--$boundary\r\nContent-Disposition: form-data; name=\"root\"\r\n\r\ngcodes\r\n")
                text("--$boundary\r\nContent-Disposition: form-data; name=\"print\"\r\n\r\nfalse\r\n")
                text("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    var previous = -1
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        out.write(buffer, 0, size)
                        sent += size
                        val percent = (sent * 99 / file.length()).toInt()
                        if (percent != previous) { onProgress(percent); previous = percent }
                    }
                }
                text("\r\n--$boundary--\r\n")
            }
            val response = readResponse(http)
            require(response["result"] != null || response["item"] != null) { "Moonraker не подтвердил загрузку" }
            onProgress(100)
            val item = (response["result"] as? JsonObject)?.get("item") as? JsonObject ?: response["item"] as? JsonObject
            return item?.get("path")?.jsonPrimitive?.contentOrNull ?: filename
        } finally { http.disconnect() }
    }

    private fun connect(path: String): HttpURLConnection = (URI(config.apiUrl + path).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        if (config.apiKey.isNotBlank()) setRequestProperty("X-Api-Key", config.apiKey)
    }

    private fun readResponse(http: HttpURLConnection): JsonObject {
        val code = http.responseCode
        if (code == 401 || code == 403) error("Moonraker требует авторизацию. Укажите API-ключ или разрешите телефон в trusted_clients.")
        require(code in 200..299) { "Moonraker: HTTP $code" }
        val body = http.inputStream.use { stream -> stream.readBytesLimited(4 * 1024 * 1024) }
        val json = Json.parseToJsonElement(body) as? JsonObject ?: error("Адрес вернул не ответ Moonraker")
        require(json["error"] == null) { "Moonraker: " + ((json["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "запрос отклонён") }
        return json
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size() + count <= limit) { "Ответ принтера слишком большой" }
            out.write(buffer, 0, count)
        }
        return out.toString("UTF-8")
    }
}
