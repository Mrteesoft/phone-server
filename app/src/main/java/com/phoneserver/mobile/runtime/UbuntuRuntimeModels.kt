package com.phoneserver.mobile.runtime

enum class UbuntuInstallPhase {
    NOT_INSTALLED,
    SCAFFOLD_READY,
    DOWNLOADING_ROOTFS,
    EXTRACTING_ROOTFS,
    READY,
    FAILED
}

data class UbuntuRootfsSource(
        val releaseLabel: String,
        val codename: String,
        val imageVersion: String,
        val architecture: String,
        val fileName: String,
        val downloadUrl: String,
        val sha256: String,
        val sourcePageUrl: String
)

data class UbuntuRuntimeSnapshot(
        val releaseLabel: String,
        val codename: String,
        val architecture: String,
        val prootAssetAbi: String,
        val phase: UbuntuInstallPhase,
        val detail: String,
        val runtimeRootPath: String,
        val rootfsPath: String,
        val homePath: String,
        val cachePath: String,
        val archivePath: String,
        val archiveFileName: String,
        val prootPath: String,
        val runtimeLauncherPath: String,
        val sourceUrl: String,
        val sourcePageUrl: String,
        val expectedSha256: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val workspaceMountPath: String,
        val backendReady: Boolean,
        val lastUpdatedAt: String,
        val errorMessage: String?
)
