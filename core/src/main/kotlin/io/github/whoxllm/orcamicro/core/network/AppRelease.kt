package io.github.whoxllm.orcamicro.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** Only stable ARM64 releases from this application's own GitHub repository. */
@Serializable
data class AppRelease(val version:String,val notes:String,val pageUrl:String,val apkUrl:String,val apkName:String,val bytes:Long,val sha256:String) {
    fun newerThan(installed:String)=ReleaseVersion.compare(version,installed)>0
}

object ReleaseVersion {
    private val pattern=Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)")
    fun parts(text:String):List<Long>? { return pattern.matchEntire(text)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null } }
    fun compare(first:String,second:String):Int {
        val a=parts(first) ?: error("Некорректная версия релиза");val b=parts(second) ?: error("Некорректная установленная версия")
        for(i in 0..2)if(a[i]!=b[i])return a[i].compareTo(b[i])
        return 0
    }
}

object AppReleases {
    const val REPOSITORY="redfaced97/OrcaSlicer-Android"
    const val API="https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val MAX_APK_BYTES=96L*1024*1024
    fun latest():AppRelease?=decode(getJson(API))
    fun decode(root:JsonElement):AppRelease? {
        val obj=root as? JsonObject ?: error("Неполный ответ GitHub")
        if(obj["draft"]?.jsonPrimitive?.booleanOrNull==true||obj["prerelease"]?.jsonPrimitive?.booleanOrNull==true)return null
        val tag=obj["tag_name"]?.jsonPrimitive?.content ?: error("В релизе нет версии")
        if(ReleaseVersion.parts(tag)==null)return null
        val version=tag.removePrefix("v")
        val name="OrcaSlicer-arm64-v$version.apk"
        val asset=(obj["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.singleOrNull { it["name"]?.jsonPrimitive?.content==name } ?: return null
        val size=asset["size"]?.jsonPrimitive?.longOrNull ?: return null
        require(size in 1..MAX_APK_BYTES) { "Размер обновления не поддерживается" }
        val digest=asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:") ?: return null
        require(digest.matches(Regex("[a-fA-F0-9]{64}"))) { "GitHub не предоставил SHA-256 обновления" }
        val page="https://github.com/$REPOSITORY/releases/tag/$tag"
        val url=asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
        require(url=="https://github.com/$REPOSITORY/releases/download/$tag/$name") { "Неверный источник APK обновления" }
        return AppRelease(version,obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(48_000),page,url,name,size,digest.lowercase())
    }
    fun verifyDownload(file:File,release:AppRelease) {
        require(file.isFile&&file.length()==release.bytes) { "Обновление скачано не полностью. Повторите загрузку." }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input->val b=ByteArray(64*1024);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)} }
        val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
        require(actual==release.sha256) { "Контрольная сумма обновления не совпала. APK не будет установлен." }
    }
    private fun getJson(url:String):JsonElement {
        val http=(URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout=12_000;readTimeout=15_000;instanceFollowRedirects=false
            setRequestProperty("Accept","application/vnd.github+json");setRequestProperty("User-Agent","OrcaSlicer-Android-Updater")
        }
        try {
            if(http.responseCode==404)return buildJsonObject { put("draft",true) }
            require(http.responseCode in 200..299) { if(http.responseCode in setOf(403,429))"GitHub временно ограничил проверки. Повторите позже." else "Проверка обновления: HTTP ${http.responseCode}" }
            val bytes=http.inputStream.use { it.readNBytes(1024*1024+1) };require(bytes.size<=1024*1024) { "Слишком большой ответ GitHub" }
            return Json.parseToJsonElement(bytes.decodeToString())
        }finally{http.disconnect()}
    }
}

/** Exact, case-sensitive text. No whitespace normalization or saved authorization. */
object EmergencyStopConfirmation {
    const val TEXT="Stop Klipper!"
    fun matches(text:String)=text==TEXT
}
