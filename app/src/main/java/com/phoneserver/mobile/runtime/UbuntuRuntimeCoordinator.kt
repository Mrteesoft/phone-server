package com.phoneserver.mobile.runtime

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant
import org.json.JSONObject

class UbuntuRuntimeCoordinator(
        context: Context
) {

    private val appContext = context.applicationContext
    private val layout = UbuntuRuntimeLayout(
            runtimeRoot = File(appContext.filesDir, "distros/ubuntu-22.04"),
            rootfsDirectory = File(appContext.filesDir, "distros/ubuntu-22.04/rootfs"),
            persistentHomeDirectory = File(appContext.filesDir, "distros/ubuntu-22.04/home"),
            cacheDirectory = File(appContext.filesDir, "distros/ubuntu-22.04/cache"),
            workspaceMountDirectory = File(appContext.filesDir, "workspaces"),
            prootRuntimeDirectory = File(appContext.filesDir, "distros/ubuntu-22.04/proot-runtime"),
            commandShimDirectory = File(appContext.filesDir, "distros/ubuntu-22.04/command-shims"),
            diagnosticsDirectory = File(appContext.filesDir, "distros/ubuntu-22.04/diagnostics"),
            launcherScript = File(appContext.filesDir, "distros/ubuntu-22.04/start-ubuntu.sh"),
            runtimeNote = File(appContext.filesDir, "distros/ubuntu-22.04/ubuntu-runtime.txt"),
            bootLogFile = File(appContext.filesDir, "distros/ubuntu-22.04/diagnostics/boot.log")
    )
    private val store = UbuntuRuntimeStore(appContext)
    private val bootstrap = UbuntuRuntimeBootstrap(layout)

    fun initialize(): UbuntuRuntimeSnapshot {
        bootstrap.ensureHostDirectories()
        val source = resolveRootfsSource()
        val persisted = store.load()

        val snapshot = when {
            source == null -> unsupportedArchitectureSnapshot()

            rootfsLooksInstalled() -> buildInstalledRuntimeSnapshot(
                    source = source,
                    archiveBytes = archiveFileFor(source).length().coerceAtLeast(0L)
            )

            persisted == null -> buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.NOT_INSTALLED,
                    detail = "Ubuntu 22.04 runtime is not installed yet. Prepare the scaffold, then install Ubuntu Base into app storage.",
                    downloadedBytes = 0L,
                    totalBytes = 0L
            )

            persisted.phase in setOf(
                    UbuntuInstallPhase.DOWNLOADING_ROOTFS,
                    UbuntuInstallPhase.EXTRACTING_ROOTFS,
                    UbuntuInstallPhase.VERIFYING_BOOT
            ) -> buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.FAILED,
                    detail = "The previous Ubuntu install did not finish cleanly. Run Install Ubuntu again to rebuild the runtime.",
                    downloadedBytes = persisted.downloadedBytes,
                    totalBytes = persisted.totalBytes,
                    errorMessage = "Ubuntu setup was interrupted before runtime verification completed."
            )

            else -> buildSnapshot(
                    source = source,
                    phase = persisted.phase,
                    detail = persisted.detail.ifBlank {
                        "Ubuntu scaffold is ready. Install Ubuntu Base to build the local userspace runtime."
                    },
                    downloadedBytes = persisted.downloadedBytes,
                    totalBytes = persisted.totalBytes,
                    errorMessage = persisted.errorMessage
            )
        }

        source?.let { bootstrap.writePreparationFiles(it, resolveBundledRuntime()) }
        return persist(snapshot)
    }

    fun prepareScaffold(): UbuntuRuntimeSnapshot {
        bootstrap.ensureHostDirectories()
        val source = resolveRootfsSource() ?: return persist(unsupportedArchitectureSnapshot())
        bootstrap.writePreparationFiles(source, resolveBundledRuntime())

        val snapshot = if (rootfsLooksInstalled()) {
            buildInstalledRuntimeSnapshot(
                    source = source,
                    archiveBytes = archiveFileFor(source).length().coerceAtLeast(0L)
            )
        } else {
            buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.SCAFFOLD_READY,
                    detail = "Ubuntu directories are prepared. Next step: download Ubuntu Base ${source.imageVersion}, verify its checksum, and extract it into the local rootfs.",
                    downloadedBytes = 0L,
                    totalBytes = 0L
            )
        }

        return persist(snapshot)
    }

    fun currentSnapshot(): UbuntuRuntimeSnapshot {
        return store.load() ?: initialize()
    }

    fun requireRootfsSource(): UbuntuRootfsSource {
        return resolveRootfsSource()
                ?: throw IllegalStateException(
                        "Ubuntu userspace is not available for ${resolveUbuntuArchitecture()} on this build."
                )
    }

    fun archiveFileFor(source: UbuntuRootfsSource): File {
        return File(layout.cacheDirectory, source.fileName)
    }

    fun rootfsDirectory(): File = layout.rootfsDirectory

    fun bundledRuntimeInstallRoot(): File = layout.prootRuntimeDirectory

    fun markDownloadStarting(source: UbuntuRootfsSource): UbuntuRuntimeSnapshot {
        bootstrap.writePreparationFiles(source, resolveBundledRuntime())
        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.DOWNLOADING_ROOTFS,
                        detail = "Downloading ${source.fileName} from Canonical.",
                        downloadedBytes = 0L,
                        totalBytes = 0L
                )
        )
    }

    fun updateDownloadProgress(
            source: UbuntuRootfsSource,
            downloadedBytes: Long,
            totalBytes: Long
    ): UbuntuRuntimeSnapshot {
        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.DOWNLOADING_ROOTFS,
                        detail = "Downloading ${source.fileName}: ${formatBytes(downloadedBytes)} of ${formatBytes(totalBytes)}.",
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes
                )
        )
    }

    fun markExtracting(
            source: UbuntuRootfsSource,
            archiveBytes: Long
    ): UbuntuRuntimeSnapshot {
        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.EXTRACTING_ROOTFS,
                        detail = "Extracting Ubuntu Base into the local rootfs.",
                        downloadedBytes = archiveBytes,
                        totalBytes = archiveBytes
                )
        )
    }

    fun updateExtractionProgress(
            source: UbuntuRootfsSource,
            extractedEntries: Int,
            archiveBytes: Long
    ): UbuntuRuntimeSnapshot {
        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.EXTRACTING_ROOTFS,
                        detail = "Extracting Ubuntu rootfs: $extractedEntries archive entries unpacked.",
                        downloadedBytes = archiveBytes,
                        totalBytes = archiveBytes
                )
        )
    }

    fun markVerifyingBoot(
            source: UbuntuRootfsSource,
            archiveBytes: Long
    ): UbuntuRuntimeSnapshot {
        bootstrap.writePreparationFiles(source, resolveBundledRuntime())
        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.VERIFYING_BOOT,
                        detail = "Launching Ubuntu through proot to verify the login shell, environment, and writable temp directories.",
                        downloadedBytes = archiveBytes,
                        totalBytes = archiveBytes
                )
        )
    }

    fun markReady(source: UbuntuRootfsSource): UbuntuRuntimeSnapshot {
        return persist(
                buildInstalledRuntimeSnapshot(
                        source = source,
                        archiveBytes = archiveFileFor(source).length().coerceAtLeast(0L)
                )
        )
    }

    fun markFailure(errorMessage: String): UbuntuRuntimeSnapshot {
        val source = resolveRootfsSource()
        val currentSnapshot = store.load()
        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.FAILED,
                        detail = errorMessage,
                        downloadedBytes = currentSnapshot?.downloadedBytes ?: 0L,
                        totalBytes = currentSnapshot?.totalBytes ?: 0L,
                        errorMessage = errorMessage
                )
        )
    }

    fun resetRootfsDirectory() {
        bootstrap.ensureHostDirectories()
        val expectedRoot = layout.runtimeRoot.canonicalFile
        val actualRootfs = layout.rootfsDirectory.canonicalFile
        require(actualRootfs.toPath().startsWith(expectedRoot.toPath())) {
            "Refusing to clear an unexpected rootfs path."
        }
        layout.rootfsDirectory.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
        layout.rootfsDirectory.mkdirs()
    }

    private fun buildInstalledRuntimeSnapshot(
            source: UbuntuRootfsSource,
            archiveBytes: Long
    ): UbuntuRuntimeSnapshot {
        val bundledRuntime = resolveBundledRuntime()
        bootstrap.writePreparationFiles(source, bundledRuntime)
        val verification = bootstrap.verifyBoot(bundledRuntime)
        return if (verification.success) {
            buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.READY,
                    detail = verification.detail,
                    downloadedBytes = archiveBytes,
                    totalBytes = archiveBytes
            )
        } else {
            buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.FAILED,
                    detail = verification.detail,
                    downloadedBytes = archiveBytes,
                    totalBytes = archiveBytes,
                    errorMessage = verification.errorMessage
            )
        }
    }

    private fun unsupportedArchitectureSnapshot(): UbuntuRuntimeSnapshot {
        val architecture = resolveUbuntuArchitecture()
        return buildSnapshot(
                source = null,
                phase = UbuntuInstallPhase.FAILED,
                detail = "Ubuntu Base 22.04 is not configured for this device architecture yet: $architecture.",
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorMessage = "Unsupported Ubuntu architecture: $architecture"
        )
    }

    private fun buildSnapshot(
            source: UbuntuRootfsSource?,
            phase: UbuntuInstallPhase,
            detail: String,
            downloadedBytes: Long,
            totalBytes: Long,
            errorMessage: String? = null
    ): UbuntuRuntimeSnapshot {
        val resolvedSource = source ?: resolveRootfsSource()
        val archiveFile = resolvedSource?.let(::archiveFileFor)
        val bundledRuntime = resolveBundledRuntime()

        return UbuntuRuntimeSnapshot(
                releaseLabel = resolvedSource?.releaseLabel ?: "Ubuntu 22.04 LTS",
                codename = resolvedSource?.codename ?: "jammy",
                architecture = resolvedSource?.architecture ?: resolveUbuntuArchitecture(),
                prootAssetAbi = bundledRuntime?.assetAbi.orEmpty(),
                phase = phase,
                detail = detail,
                runtimeRootPath = layout.runtimeRoot.absolutePath,
                rootfsPath = layout.rootfsDirectory.absolutePath,
                homePath = layout.persistentHomeDirectory.absolutePath,
                guestHomePath = layout.guestHomePath,
                guestWorkspacePath = layout.guestWorkspacePath,
                defaultUsername = layout.defaultUsername,
                cachePath = layout.cacheDirectory.absolutePath,
                archivePath = archiveFile?.absolutePath.orEmpty(),
                archiveFileName = resolvedSource?.fileName.orEmpty(),
                prootPath = bundledRuntime?.prootBinary?.absolutePath.orEmpty(),
                runtimeLauncherPath = layout.launcherScript.absolutePath.takeIf { layout.launcherScript.exists() }.orEmpty(),
                diagnosticsPath = layout.bootLogFile.absolutePath,
                sourceUrl = resolvedSource?.downloadUrl.orEmpty(),
                sourcePageUrl = resolvedSource?.sourcePageUrl.orEmpty(),
                expectedSha256 = resolvedSource?.sha256.orEmpty(),
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                workspaceMountPath = layout.workspaceMountDirectory.absolutePath,
                backendReady = phase == UbuntuInstallPhase.READY,
                lastUpdatedAt = Instant.now().toString(),
                errorMessage = errorMessage
        )
    }

    private fun rootfsLooksInstalled(): Boolean {
        return File(layout.rootfsDirectory, "usr").exists() &&
                File(layout.rootfsDirectory, "etc").exists() &&
                File(layout.rootfsDirectory, "bin").exists()
    }

    private fun resolveBundledRuntime(): BundledProotRuntime? {
        val prootBinary = File(layout.prootRuntimeDirectory, "bin/proot")
        val loaderBinary = File(layout.prootRuntimeDirectory, "bin/loader")
        val loader32Binary = File(layout.prootRuntimeDirectory, "bin/loader32").takeIf { it.exists() }
        val libraryDirectory = File(layout.prootRuntimeDirectory, "lib")
        val tallocLibrary = File(libraryDirectory, "libtalloc.so.2")
        val tempDirectory = File(layout.prootRuntimeDirectory, "tmp").apply { mkdirs() }

        if (!prootBinary.exists() || !loaderBinary.exists() || !tallocLibrary.exists()) {
            return null
        }

        val metadataFile = File(layout.prootRuntimeDirectory, "metadata.json")
        val assetAbi = runCatching {
            JSONObject(metadataFile.readText()).optString("androidAbi")
        }.getOrNull().orEmpty()

        return BundledProotRuntime(
                assetAbi = assetAbi,
                installRoot = layout.prootRuntimeDirectory,
                prootBinary = prootBinary,
                loaderBinary = loaderBinary,
                loader32Binary = loader32Binary,
                hostLinkerPath = when (assetAbi) {
                    "arm64-v8a", "x86_64" -> "/system/bin/linker64"
                    else -> "/system/bin/linker"
                },
                libraryDirectory = libraryDirectory,
                tempDirectory = tempDirectory
        )
    }

    private fun resolveRootfsSource(): UbuntuRootfsSource? {
        return UbuntuRootfsCatalog.resolve(resolveUbuntuArchitecture())
    }

    private fun resolveUbuntuArchitecture(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        return when {
            "arm64" in abi || "aarch64" in abi -> "arm64"
            "armeabi" in abi || "armv7" in abi -> "armhf"
            "x86_64" in abi || "amd64" in abi -> "amd64"
            "x86" in abi -> "i386"
            else -> abi.ifBlank { "unknown" }
        }
    }

    private fun persist(snapshot: UbuntuRuntimeSnapshot): UbuntuRuntimeSnapshot {
        store.save(snapshot)
        return snapshot
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "0 B"
        }

        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return String.format("%.1f %s", value, units[index])
    }
}
