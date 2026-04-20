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
    private val runtimeRoot = File(appContext.filesDir, "distros/ubuntu-22.04").apply { mkdirs() }
    private val rootfsDirectory = File(runtimeRoot, "rootfs")
    private val homeDirectory = File(runtimeRoot, "home")
    private val cacheDirectory = File(runtimeRoot, "cache")
    private val workspaceMountDirectory = File(appContext.filesDir, "workspaces").apply { mkdirs() }
    private val prootRuntimeDirectory = File(runtimeRoot, "proot-runtime")
    private val launcherScript = File(runtimeRoot, "start-ubuntu.sh")
    private val runtimeNote = File(runtimeRoot, "ubuntu-runtime.txt")
    private val store = UbuntuRuntimeStore(appContext)

    fun initialize(): UbuntuRuntimeSnapshot {
        ensureDirectories()
        val source = resolveRootfsSource()
        val persisted = store.load()

        val snapshot = when {
            source == null -> unsupportedArchitectureSnapshot()
            persisted == null -> buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.NOT_INSTALLED,
                    detail = "Ubuntu 22.04 runtime is not installed yet. Prepare the scaffold, then install Ubuntu Base into app storage.",
                    downloadedBytes = 0L,
                    totalBytes = 0L
            )

            persisted.phase == UbuntuInstallPhase.DOWNLOADING_ROOTFS ||
                    persisted.phase == UbuntuInstallPhase.EXTRACTING_ROOTFS -> buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.FAILED,
                    detail = "The previous Ubuntu install was interrupted. Run the install step again to continue.",
                    downloadedBytes = persisted.downloadedBytes,
                    totalBytes = persisted.totalBytes,
                    errorMessage = "Ubuntu rootfs setup was interrupted before completion."
            )

            persisted.phase == UbuntuInstallPhase.READY && !rootfsLooksInstalled() -> buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.SCAFFOLD_READY,
                    detail = "Ubuntu rootfs metadata exists, but the extracted filesystem is missing. Reinstall Ubuntu Base to rebuild it.",
                    downloadedBytes = persisted.downloadedBytes,
                    totalBytes = persisted.totalBytes,
                    errorMessage = "Ubuntu rootfs is missing from storage."
            )

            else -> normalizeSnapshot(source, persisted)
        }

        source?.let(::writePreparationFiles)
        return persist(snapshot)
    }

    fun prepareScaffold(): UbuntuRuntimeSnapshot {
        ensureDirectories()
        val source = resolveRootfsSource() ?: return persist(unsupportedArchitectureSnapshot())
        writePreparationFiles(source)

        val bundledRuntime = resolveBundledRuntime()
        val snapshot = if (rootfsLooksInstalled()) {
            buildSnapshot(
                    source = source,
                    phase = UbuntuInstallPhase.READY,
                    detail = if (bundledRuntime != null) {
                        "Ubuntu Base ${source.imageVersion} is staged in app storage. Bundled proot support and the PTY launcher are ready for the Ubuntu terminal."
                    } else {
                        "Ubuntu Base ${source.imageVersion} is staged in app storage. Bundled proot support is missing, so the Ubuntu shell cannot launch yet."
                    },
                    downloadedBytes = archiveFileFor(source).length().coerceAtLeast(0L),
                    totalBytes = archiveFileFor(source).length().coerceAtLeast(0L)
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
        return File(cacheDirectory, source.fileName)
    }

    fun rootfsDirectory(): File = rootfsDirectory

    fun bundledRuntimeInstallRoot(): File = prootRuntimeDirectory

    fun markDownloadStarting(source: UbuntuRootfsSource): UbuntuRuntimeSnapshot {
        writePreparationFiles(source)
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

    fun markReady(source: UbuntuRootfsSource): UbuntuRuntimeSnapshot {
        ensureUbuntuNetworkConfiguration()
        writePreparationFiles(source)
        val bundledRuntime = resolveBundledRuntime()

        return persist(
                buildSnapshot(
                        source = source,
                        phase = UbuntuInstallPhase.READY,
                        detail = if (bundledRuntime != null) {
                            "Ubuntu Base ${source.imageVersion} is extracted into app storage. The bundled proot runtime and PTY launcher are ready."
                        } else {
                            "Ubuntu Base ${source.imageVersion} is extracted into app storage, but bundled proot support was not found in the app assets."
                        },
                        downloadedBytes = archiveFileFor(source).length().coerceAtLeast(0L),
                        totalBytes = archiveFileFor(source).length().coerceAtLeast(0L)
                )
        )
    }

    fun markFailure(errorMessage: String): UbuntuRuntimeSnapshot {
        val source = resolveRootfsSource()
        val currentSnapshot = store.load()
        val snapshot = buildSnapshot(
                source = source,
                phase = UbuntuInstallPhase.FAILED,
                detail = errorMessage,
                downloadedBytes = currentSnapshot?.downloadedBytes ?: 0L,
                totalBytes = currentSnapshot?.totalBytes ?: 0L,
                errorMessage = errorMessage
        )
        return persist(snapshot)
    }

    fun resetRootfsDirectory() {
        ensureDirectories()
        val expectedRoot = runtimeRoot.canonicalFile
        val actualRootfs = rootfsDirectory.canonicalFile
        require(actualRootfs.toPath().startsWith(expectedRoot.toPath())) {
            "Refusing to clear an unexpected rootfs path."
        }
        rootfsDirectory.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
        rootfsDirectory.mkdirs()
    }

    private fun normalizeSnapshot(
            source: UbuntuRootfsSource,
            persisted: UbuntuRuntimeSnapshot
    ): UbuntuRuntimeSnapshot {
        val normalizedPhase = if (persisted.phase == UbuntuInstallPhase.READY && !rootfsLooksInstalled()) {
            UbuntuInstallPhase.SCAFFOLD_READY
        } else {
            persisted.phase
        }

        val detail = when {
            persisted.detail.isBlank() && normalizedPhase == UbuntuInstallPhase.READY && resolveBundledRuntime() != null ->
                "Ubuntu Base ${source.imageVersion} is staged in app storage and the Ubuntu terminal launcher is ready."

            persisted.detail.isBlank() && normalizedPhase == UbuntuInstallPhase.READY ->
                "Ubuntu Base ${source.imageVersion} is staged in app storage."

            persisted.detail.isBlank() ->
                "Ubuntu 22.04 runtime is prepared for local install."

            else -> persisted.detail
        }

        return buildSnapshot(
                source = source,
                phase = normalizedPhase,
                detail = detail,
                downloadedBytes = persisted.downloadedBytes,
                totalBytes = persisted.totalBytes,
                errorMessage = persisted.errorMessage
        )
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
        val backendReady = phase == UbuntuInstallPhase.READY &&
                rootfsLooksInstalled() &&
                bundledRuntime != null &&
                launcherScript.exists()

        return UbuntuRuntimeSnapshot(
                releaseLabel = resolvedSource?.releaseLabel ?: "Ubuntu 22.04 LTS",
                codename = resolvedSource?.codename ?: "jammy",
                architecture = resolvedSource?.architecture ?: resolveUbuntuArchitecture(),
                prootAssetAbi = bundledRuntime?.assetAbi.orEmpty(),
                phase = phase,
                detail = detail,
                runtimeRootPath = runtimeRoot.absolutePath,
                rootfsPath = rootfsDirectory.absolutePath,
                homePath = homeDirectory.absolutePath,
                cachePath = cacheDirectory.absolutePath,
                archivePath = archiveFile?.absolutePath.orEmpty(),
                archiveFileName = resolvedSource?.fileName.orEmpty(),
                prootPath = bundledRuntime?.prootBinary?.absolutePath.orEmpty(),
                runtimeLauncherPath = launcherScript.absolutePath.takeIf { launcherScript.exists() }.orEmpty(),
                sourceUrl = resolvedSource?.downloadUrl.orEmpty(),
                sourcePageUrl = resolvedSource?.sourcePageUrl.orEmpty(),
                expectedSha256 = resolvedSource?.sha256.orEmpty(),
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                workspaceMountPath = workspaceMountDirectory.absolutePath,
                backendReady = backendReady,
                lastUpdatedAt = Instant.now().toString(),
                errorMessage = errorMessage
        )
    }

    private fun ensureDirectories() {
        runtimeRoot.mkdirs()
        rootfsDirectory.mkdirs()
        homeDirectory.mkdirs()
        cacheDirectory.mkdirs()
        workspaceMountDirectory.mkdirs()
        prootRuntimeDirectory.mkdirs()
    }

    private fun writePreparationFiles(source: UbuntuRootfsSource) {
        val bundledRuntime = resolveBundledRuntime()
        if (rootfsLooksInstalled()) {
            ensureUbuntuNetworkConfiguration()
        }

        File(homeDirectory, ".phoneserver-profile").writeText(
                buildString {
                    appendLine("Phone Server Ubuntu scaffold")
                    appendLine("Release: ${source.releaseLabel} (${source.codename})")
                    appendLine("Image version: ${source.imageVersion}")
                    appendLine("Architecture: ${source.architecture}")
                    appendLine("Workspace mount target: /workspace")
                    appendLine("Rootfs cache archive: ${archiveFileFor(source).absolutePath}")
                    if (bundledRuntime != null) {
                        appendLine("Bundled proot ABI: ${bundledRuntime.assetAbi}")
                        appendLine("Bundled proot path: ${bundledRuntime.prootBinary.absolutePath}")
                    }
                }
        )

        val launcherContents = if (bundledRuntime != null && rootfsLooksInstalled()) {
            buildLauncherScript(bundledRuntime)
        } else {
            buildString {
                appendLine("#!/system/bin/sh")
                appendLine("echo \"Ubuntu rootfs path: ${rootfsDirectory.absolutePath}\"")
                appendLine("echo \"Ubuntu archive: ${archiveFileFor(source).absolutePath}\"")
                appendLine("echo \"Ubuntu cannot launch yet because the rootfs or bundled proot runtime is still missing.\"")
            }
        }

        launcherScript.writeText(launcherContents)
        launcherScript.setExecutable(true, true)

        runtimeNote.writeText(
                buildString {
                    appendLine("Phone Server Ubuntu runtime note")
                    appendLine("Release: ${source.releaseLabel}")
                    appendLine("Image source: ${source.downloadUrl}")
                    appendLine("Source page: ${source.sourcePageUrl}")
                    appendLine("Expected SHA256: ${source.sha256}")
                    appendLine("Rootfs path: ${rootfsDirectory.absolutePath}")
                    appendLine("Launcher path: ${launcherScript.absolutePath}")
                    appendLine(
                            if (bundledRuntime != null) {
                                "Bundled proot asset ABI: ${bundledRuntime.assetAbi}"
                            } else {
                                "Bundled proot assets were not found in app storage."
                            }
                    )
                }
        )
    }

    private fun buildLauncherScript(runtime: BundledProotRuntime): String {
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set -eu")
            appendLine("PROOT_BIN=${runtime.prootBinary.absolutePath.shellQuoted()}")
            appendLine("PROOT_LOADER_PATH=${runtime.loaderBinary.absolutePath.shellQuoted()}")
            appendLine("ROOTFS=${rootfsDirectory.absolutePath.shellQuoted()}")
            appendLine("HOME_BIND=${homeDirectory.absolutePath.shellQuoted()}")
            appendLine("WORKSPACE_BIND=${workspaceMountDirectory.absolutePath.shellQuoted()}")
            appendLine("TMPDIR_PATH=${runtime.tempDirectory.absolutePath.shellQuoted()}")
            appendLine("mkdir -p \"${'$'}TMPDIR_PATH\" \"${'$'}HOME_BIND\" \"${'$'}WORKSPACE_BIND\"")
            appendLine("export PROOT_TMPDIR=\"${'$'}TMPDIR_PATH\"")
            appendLine("export PROOT_LOADER=\"${'$'}PROOT_LOADER_PATH\"")
            appendLine("export HOME=/root")
            appendLine("export TERM=\"${'$'}{TERM:-xterm-256color}\"")
            appendLine("ARGS=\"--kill-on-exit -0 -r ${'$'}ROOTFS -b /dev:/dev -b /proc:/proc -b /sys:/sys -b ${'$'}HOME_BIND:/root -b ${'$'}WORKSPACE_BIND:/workspace -w /root\"")
            appendLine("if [ -d /system ]; then ARGS=\"${'$'}ARGS -b /system:/system\"; fi")
            appendLine("if [ -d /vendor ]; then ARGS=\"${'$'}ARGS -b /vendor:/vendor\"; fi")
            appendLine("if [ -d /apex ]; then ARGS=\"${'$'}ARGS -b /apex:/apex\"; fi")
            appendLine("if [ -d /storage ]; then ARGS=\"${'$'}ARGS -b /storage:/storage\"; fi")
            appendLine("if [ -e /linkerconfig/ld.config.txt ]; then ARGS=\"${'$'}ARGS -b /linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt\"; fi")
            appendLine("exec \"${'$'}PROOT_BIN\" ${'$'}ARGS /usr/bin/env -i HOME=/root TERM=\"${'$'}TERM\" PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash --noprofile --norc")
        }
    }

    private fun ensureUbuntuNetworkConfiguration() {
        val etcDirectory = File(rootfsDirectory, "etc").apply { mkdirs() }
        File(etcDirectory, "resolv.conf").writeText(
                buildString {
                    appendLine("nameserver 1.1.1.1")
                    appendLine("nameserver 8.8.8.8")
                }
        )
    }

    private fun resolveBundledRuntime(): BundledProotRuntime? {
        val prootBinary = File(prootRuntimeDirectory, "bin/proot")
        val loaderBinary = File(prootRuntimeDirectory, "bin/loader")
        val loader32Binary = File(prootRuntimeDirectory, "bin/loader32").takeIf { it.exists() }
        val libraryDirectory = File(prootRuntimeDirectory, "lib")
        val tallocLibrary = File(libraryDirectory, "libtalloc.so.2")
        val tempDirectory = File(prootRuntimeDirectory, "tmp").apply { mkdirs() }

        if (!prootBinary.exists() || !loaderBinary.exists() || !tallocLibrary.exists()) {
            return null
        }

        val metadataFile = File(prootRuntimeDirectory, "metadata.json")
        val assetAbi = runCatching {
            JSONObject(metadataFile.readText()).optString("androidAbi")
        }.getOrNull().orEmpty()

        return BundledProotRuntime(
                assetAbi = assetAbi,
                installRoot = prootRuntimeDirectory,
                prootBinary = prootBinary,
                loaderBinary = loaderBinary,
                loader32Binary = loader32Binary,
                libraryDirectory = libraryDirectory,
                tempDirectory = tempDirectory
        )
    }

    private fun rootfsLooksInstalled(): Boolean {
        return File(rootfsDirectory, "usr").exists() &&
                File(rootfsDirectory, "etc").exists() &&
                File(rootfsDirectory, "bin").exists()
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

    private fun String.shellQuoted(): String = "'" + replace("'", "'\"'\"'") + "'"
}
