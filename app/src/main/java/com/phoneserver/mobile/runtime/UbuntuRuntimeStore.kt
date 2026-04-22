package com.phoneserver.mobile.runtime

import android.content.Context
import java.io.File
import org.json.JSONObject

class UbuntuRuntimeStore(context: Context) {

    private val storageFile = File(context.filesDir, "ubuntu-runtime.json")

    @Synchronized
    fun load(): UbuntuRuntimeSnapshot? {
        if (!storageFile.exists()) {
            return null
        }

        val content = storageFile.readText().trim()
        if (content.isEmpty()) {
            return null
        }

        val json = runCatching { JSONObject(content) }.getOrElse { return null }
        return UbuntuRuntimeSnapshot(
                releaseLabel = json.optString("releaseLabel", "Ubuntu 22.04 LTS"),
                codename = json.optString("codename", "jammy"),
                architecture = json.optString("architecture"),
                prootAssetAbi = json.optString("prootAssetAbi"),
                phase = runCatching {
                    UbuntuInstallPhase.valueOf(json.optString("phase", UbuntuInstallPhase.NOT_INSTALLED.name))
                }.getOrDefault(UbuntuInstallPhase.NOT_INSTALLED),
                detail = json.optString("detail"),
                runtimeRootPath = json.optString("runtimeRootPath"),
                rootfsPath = json.optString("rootfsPath"),
                homePath = json.optString("homePath"),
                guestHomePath = json.optString("guestHomePath", "/root"),
                guestWorkspacePath = json.optString("guestWorkspacePath", "/workspace"),
                defaultUsername = json.optString("defaultUsername", "root"),
                cachePath = json.optString("cachePath"),
                archivePath = json.optString("archivePath"),
                archiveFileName = json.optString("archiveFileName"),
                prootPath = json.optString("prootPath"),
                runtimeLauncherPath = json.optString("runtimeLauncherPath"),
                diagnosticsPath = json.optString("diagnosticsPath"),
                sourceUrl = json.optString("sourceUrl"),
                sourcePageUrl = json.optString("sourcePageUrl"),
                expectedSha256 = json.optString("expectedSha256"),
                downloadedBytes = json.optLong("downloadedBytes"),
                totalBytes = json.optLong("totalBytes"),
                workspaceMountPath = json.optString("workspaceMountPath"),
                backendReady = json.optBoolean("backendReady", false),
                lastUpdatedAt = json.optString("lastUpdatedAt"),
                errorMessage = json.optString("errorMessage").takeIf { it.isNotBlank() }
        )
    }

    @Synchronized
    fun save(snapshot: UbuntuRuntimeSnapshot) {
        val json = JSONObject()
                .put("releaseLabel", snapshot.releaseLabel)
                .put("codename", snapshot.codename)
                .put("architecture", snapshot.architecture)
                .put("prootAssetAbi", snapshot.prootAssetAbi)
                .put("phase", snapshot.phase.name)
                .put("detail", snapshot.detail)
                .put("runtimeRootPath", snapshot.runtimeRootPath)
                .put("rootfsPath", snapshot.rootfsPath)
                .put("homePath", snapshot.homePath)
                .put("guestHomePath", snapshot.guestHomePath)
                .put("guestWorkspacePath", snapshot.guestWorkspacePath)
                .put("defaultUsername", snapshot.defaultUsername)
                .put("cachePath", snapshot.cachePath)
                .put("archivePath", snapshot.archivePath)
                .put("archiveFileName", snapshot.archiveFileName)
                .put("prootPath", snapshot.prootPath)
                .put("runtimeLauncherPath", snapshot.runtimeLauncherPath)
                .put("diagnosticsPath", snapshot.diagnosticsPath)
                .put("sourceUrl", snapshot.sourceUrl)
                .put("sourcePageUrl", snapshot.sourcePageUrl)
                .put("expectedSha256", snapshot.expectedSha256)
                .put("downloadedBytes", snapshot.downloadedBytes)
                .put("totalBytes", snapshot.totalBytes)
                .put("workspaceMountPath", snapshot.workspaceMountPath)
                .put("backendReady", snapshot.backendReady)
                .put("lastUpdatedAt", snapshot.lastUpdatedAt)
                .put("errorMessage", snapshot.errorMessage ?: "")

        storageFile.parentFile?.mkdirs()
        storageFile.writeText(json.toString())
    }
}
