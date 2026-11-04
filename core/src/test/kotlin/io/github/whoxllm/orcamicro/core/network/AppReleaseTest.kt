package io.github.whoxllm.orcamicro.core.network

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class AppReleaseTest {
    private fun release(tag:String="v0.6.2",digest:String="a".repeat(64),url:String="https://github.com/redfaced97/OrcaSlicer-Android/releases/download/$tag/OrcaSlicer-arm64-v${tag.removePrefix("v")}.apk",size:Long=55228255)=buildJsonObject {
        put("tag_name",tag);put("body","## Changes\nSTOP confirmation\nUpdater");put("draft",false);put("prerelease",false)
        put("assets",buildJsonArray {add(buildJsonObject {put("name","OrcaSlicer-arm64-v${tag.removePrefix("v")}.apk");put("size",size);put("digest","sha256:$digest");put("browser_download_url",url)})})
    }
    @Test fun `numeric version comparison handles patch releases and skips unstable versions`() {
        assertTrue(ReleaseVersion.compare("0.6.10","0.6.2")>0);assertTrue(ReleaseVersion.compare("v0.6.2","0.6.0")>0)
        assertEquals(0,ReleaseVersion.compare("0.6.2","v0.6.2"));assertTrue(ReleaseVersion.compare("0.6.2","0.7.0")<0)
        for(text in listOf("0.6","0.6.3-beta","nightly","0.6.2.1","999999999999999999999.0.1"))assertNull(ReleaseVersion.parts(text))
    }
    @Test fun `only stable ARM64 releases from the app repository are offered`() {
        val update=AppReleases.decode(release())!!
        assertTrue(update.newerThan("0.6.0"));assertFalse(update.newerThan("0.6.2"));assertTrue(update.notes.contains("STOP confirmation"))
        assertEquals("OrcaSlicer-arm64-v0.6.2.apk",update.apkName)
        assertFails {AppReleases.decode(release(url="https://github.com/other/project/releases/download/v0.6.2/model.apk"))}
        assertFails {AppReleases.decode(release(digest="invalid"))}
        assertFails {AppReleases.decode(release(size=AppReleases.MAX_APK_BYTES+1))}
        assertNull(AppReleases.decode(JsonObject(release()+ ("draft" to JsonPrimitive(true)))))
        assertNull(AppReleases.decode(JsonObject(release()+ ("prerelease" to JsonPrimitive(true)))))
        assertNull(AppReleases.decode(JsonObject(release()+ ("assets" to JsonArray(emptyList())))))
    }
    @Test fun `truncated or modified bootstrap downloads cannot be installed`() {
        val file=Files.createTempFile("update",".apk").toFile()
        try {
            file.writeText("signed APK fixture")
            val digest=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it.toInt() and 255)}
            val update=AppReleases.decode(release(digest=digest,size=file.length()))!!
            AppReleases.verifyDownload(file,update)
            file.appendText("changed");assertFails {AppReleases.verifyDownload(file,update)}
            file.writeText("x".repeat(update.bytes.toInt()));assertFails {AppReleases.verifyDownload(file,update)}
        }finally{file.delete()}
    }
    @Test fun `STOP requires the user's exact case sensitive phrase`() {
        assertTrue(EmergencyStopConfirmation.matches("Stop Klipper!"))
        for(text in listOf("","Stop klipper!","Stop Klipper","stop klipper!","Stop Klipper! "," Stop Klipper!","Stop Klipper!\n","STOP KLIPPER!"))assertFalse(EmergencyStopConfirmation.matches(text),text)
    }
}
