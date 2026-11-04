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

/** Moonraker HTTP API. Uploads only; never asks the printer to start a job. */
class MoonrakerClient(connection: PrinterConnection) {
    private val config = connection.normalized()

    fun info(): String {
        val http = connect("/server/info")
        return try {
            val result = readResponse(http)["result"]?.jsonObject ?: error("Адрес ответил без данных Moonraker")
            val state = result["klippy_state"]?.jsonPrimitive?.content.orEmpty()
            if (state == "ready") "Klipper готов" else "Moonraker подключён · $state"
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
            return filename
        } finally { http.disconnect() }
    }

    private fun connect(path: String): HttpURLConnection = (URI(config.apiUrl + path).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 60_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        if (config.apiKey.isNotBlank()) setRequestProperty("X-Api-Key", config.apiKey)
    }

    private fun readResponse(http: HttpURLConnection): JsonObject {
        val code = http.responseCode
        if (code == 401 || code == 403) error("Moonraker требует авторизацию. Укажите API-ключ или разрешите телефон в trusted_clients.")
        require(code in 200..299) { "Moonraker: HTTP $code" }
        val body = http.inputStream.use { stream -> stream.readBytesLimited(128 * 1024) }
        val json = Json.parseToJsonElement(body) as? JsonObject ?: error("Адрес вернул не ответ Moonraker")
        require(json["error"] == null) { "Moonraker отклонил запрос" }
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
