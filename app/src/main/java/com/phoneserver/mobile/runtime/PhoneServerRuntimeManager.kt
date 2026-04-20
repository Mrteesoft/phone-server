package com.phoneserver.mobile.runtime

import android.content.Context
import com.phoneserver.mobile.storage.TerminalHistoryRecord
import com.phoneserver.mobile.storage.TerminalHistoryStore
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ManagedServiceStatus {
    STARTING,
    RUNNING,
    STOPPED,
    FAILED
}

data class ManagedServiceSnapshot(
        val id: String,
        val name: String,
        val command: String,
        val workingDirectory: String,
        val status: ManagedServiceStatus,
        val startedAt: String,
        val updatedAt: String,
        val outputPreview: String,
        val lastExitCode: Int?
)

private data class ManagedServiceRuntime(
        val id: String,
        val name: String,
        val command: String,
        val workingDirectory: File,
        val process: Process,
        var stopRequested: Boolean
)

object PhoneServerRuntimeManager {

    private val runtimeMutex = Mutex()
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _managedServices = MutableStateFlow<List<ManagedServiceSnapshot>>(emptyList())
    private val _runtimeServiceActive = MutableStateFlow(false)
    private val _terminalRuntime = MutableStateFlow(
            TerminalRuntimeSnapshot(
                    kind = TerminalBackendKind.ANDROID_LOCAL,
                    displayName = "Android local shell",
                    detail = "Runs commands through /system/bin/sh inside the app sandbox.",
                    homeDirectory = "",
                    commandHint = "Use pwd, ls, mkdir, cat, echo, touch, cp, mv, rm, or app-local scripts."
            )
    )
    private val _ubuntuRuntime = MutableStateFlow(
            UbuntuRuntimeSnapshot(
                    releaseLabel = "Ubuntu 22.04 LTS",
                    codename = "jammy",
                    architecture = "",
                    prootAssetAbi = "",
                    phase = UbuntuInstallPhase.NOT_INSTALLED,
                    detail = "Ubuntu 22.04 runtime is not installed yet.",
                    runtimeRootPath = "",
                    rootfsPath = "",
                    homePath = "",
                    cachePath = "",
                    archivePath = "",
                    archiveFileName = "",
                    prootPath = "",
                    runtimeLauncherPath = "",
                    sourceUrl = "",
                    sourcePageUrl = "",
                    expectedSha256 = "",
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    workspaceMountPath = "",
                    backendReady = false,
                    lastUpdatedAt = "",
                    errorMessage = null
            )
    )

    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var androidShellBackend: AndroidShellBackend
    private lateinit var ubuntuProotBackend: UbuntuProotBackend
    private lateinit var ubuntuRootfsInstaller: UbuntuRootfsInstaller
    private lateinit var bundledProotInstaller: BundledProotInstaller
    private lateinit var ubuntuRuntimeCoordinator: UbuntuRuntimeCoordinator
    private lateinit var terminalHistoryStore: TerminalHistoryStore
    private var activeTerminalBackendKind: TerminalBackendKind = TerminalBackendKind.ANDROID_LOCAL
    private var ubuntuInstallJob: Job? = null
    private val serviceSnapshots = linkedMapOf<String, ManagedServiceSnapshot>()
    private val activeProcesses = mutableMapOf<String, ManagedServiceRuntime>()

    val managedServices: StateFlow<List<ManagedServiceSnapshot>> = _managedServices.asStateFlow()
    val runtimeServiceActive: StateFlow<Boolean> = _runtimeServiceActive.asStateFlow()
    val terminalRuntime: StateFlow<TerminalRuntimeSnapshot> = _terminalRuntime.asStateFlow()
    val ubuntuRuntime: StateFlow<UbuntuRuntimeSnapshot> = _ubuntuRuntime.asStateFlow()

    suspend fun initialize(context: Context) {
        runtimeMutex.withLock {
            if (initialized) {
                return
            }

            appContext = context.applicationContext
            val homeDirectory = File(appContext.filesDir, "workspaces").apply { mkdirs() }
            androidShellBackend = AndroidShellBackend(homeDirectory)
            ubuntuRuntimeCoordinator = UbuntuRuntimeCoordinator(appContext)
            bundledProotInstaller = BundledProotInstaller(
                    context = appContext,
                    installRoot = ubuntuRuntimeCoordinator.bundledRuntimeInstallRoot()
            )
            runCatching { bundledProotInstaller.install() }
            ubuntuRootfsInstaller = UbuntuRootfsInstaller(ubuntuRuntimeCoordinator)
            val ubuntuSnapshot = ubuntuRuntimeCoordinator.initialize()
            ubuntuProotBackend = UbuntuProotBackend { _ubuntuRuntime.value }
            terminalHistoryStore = TerminalHistoryStore(appContext)
            _ubuntuRuntime.value = ubuntuSnapshot
            publishTerminalRuntimeLocked()
            initialized = true
        }
    }

    fun markRuntimeServiceActive(active: Boolean) {
        _runtimeServiceActive.value = active
    }

    suspend fun runTerminalCommand(
            command: String,
            timeoutSeconds: Int = 30,
            onOutputLine: (String) -> Unit = {}
    ): TerminalCommandResult {
        val backend = requireActiveTerminalBackend()
        val result = backend.execute(command, timeoutSeconds, onOutputLine)
        appendTerminalHistory(command, result)
        return result
    }

    suspend fun changeDirectory(
            targetDirectory: File,
            timeoutSeconds: Int = 10
    ): TerminalCommandResult {
        val backend = requireActiveTerminalBackend()
        val result = backend.changeDirectory(targetDirectory, timeoutSeconds)
        appendTerminalHistory("cd ${targetDirectory.absolutePath}", result)
        return result
    }

    suspend fun prepareUbuntuRuntime(): UbuntuRuntimeSnapshot = runtimeMutex.withLock {
        val snapshot = ubuntuRuntimeCoordinator.prepareScaffold()
        publishUbuntuSnapshotLocked(snapshot)
        snapshot
    }

    suspend fun installUbuntuRuntime(): RuntimeActionResult = runtimeMutex.withLock {
        check(initialized) { "Runtime manager has not been initialized." }

        if (ubuntuInstallJob?.isActive == true) {
            return@withLock RuntimeActionResult(
                    success = false,
                    message = "Ubuntu install is already in progress."
            )
        }

        val currentSnapshot = _ubuntuRuntime.value
        if (currentSnapshot.phase == UbuntuInstallPhase.READY && currentSnapshot.rootfsPath.isNotBlank()) {
            return@withLock RuntimeActionResult(
                    success = true,
                    message = if (currentSnapshot.backendReady) {
                        "Ubuntu Base and the bundled proot launcher are already ready."
                    } else {
                        "Ubuntu Base is already staged locally, but the bundled Ubuntu launcher is still unavailable."
                    }
            )
        }

        val source = runCatching { ubuntuRuntimeCoordinator.requireRootfsSource() }
                .getOrElse { error ->
                    val snapshot = ubuntuRuntimeCoordinator.markFailure(
                            error.message ?: "Ubuntu userspace is not supported on this device architecture."
                    )
                    publishUbuntuSnapshotLocked(snapshot)
                    return@withLock RuntimeActionResult(
                            success = false,
                            message = snapshot.detail
                    )
                }

        ubuntuInstallJob = runtimeScope.launch {
            runCatching {
                bundledProotInstaller.install()
                ubuntuRootfsInstaller.install { snapshot ->
                    runtimeMutex.withLock {
                        publishUbuntuSnapshotLocked(snapshot)
                    }
                }
            }.onFailure { error ->
                runtimeMutex.withLock {
                    val snapshot = ubuntuRuntimeCoordinator.markFailure(
                            error.message ?: "Ubuntu installation failed."
                    )
                    publishUbuntuSnapshotLocked(snapshot)
                }
            }

            runtimeMutex.withLock {
                ubuntuInstallJob = null
            }
        }

        RuntimeActionResult(
                success = true,
                message = "Ubuntu rootfs install started for ${source.architecture}. Keep the app open while the archive downloads and extracts."
        )
    }

    suspend fun switchTerminalRuntime(kind: TerminalBackendKind): RuntimeSwitchResult = runtimeMutex.withLock {
        check(initialized) { "Runtime manager has not been initialized." }
        when (kind) {
            TerminalBackendKind.ANDROID_LOCAL -> {
                activeTerminalBackendKind = kind
                publishTerminalRuntimeLocked()
                RuntimeSwitchResult(
                        success = true,
                        message = "Terminal runtime switched to Android local shell."
                )
            }

            TerminalBackendKind.UBUNTU_2204 -> {
                val ubuntu = _ubuntuRuntime.value
                if (!ubuntu.backendReady) {
                    RuntimeSwitchResult(
                            success = false,
                            message = when (ubuntu.phase) {
                                UbuntuInstallPhase.NOT_INSTALLED ->
                                    "Ubuntu 22.04 is not installed yet. Prepare the runtime first."

                                UbuntuInstallPhase.SCAFFOLD_READY ->
                                    "Ubuntu scaffold is ready. Install Ubuntu Base first."

                                UbuntuInstallPhase.DOWNLOADING_ROOTFS,
                                UbuntuInstallPhase.EXTRACTING_ROOTFS ->
                                    "Ubuntu setup is still in progress."

                                UbuntuInstallPhase.READY ->
                                    "Ubuntu Base is staged locally, but the Ubuntu launcher is still unavailable."

                                UbuntuInstallPhase.FAILED ->
                                    ubuntu.errorMessage ?: "Ubuntu runtime setup failed."
                            }
                    )
                } else {
                    activeTerminalBackendKind = kind
                    publishTerminalRuntimeLocked()
                    RuntimeSwitchResult(
                            success = true,
                            message = "Terminal runtime switched to Ubuntu 22.04."
                    )
                }
            }
        }
    }

    suspend fun startManagedService(
            command: String,
            workingDirectory: File,
            displayName: String? = null,
            preferredId: String? = null
    ): ManagedServiceSnapshot = runtimeMutex.withLock {
        val trimmed = command.trim()
        require(trimmed.isNotEmpty()) { "Service command cannot be empty." }
        require(workingDirectory.exists() && workingDirectory.isDirectory) {
            "Working directory does not exist."
        }

        val id = preferredId ?: UUID.randomUUID().toString()
        if (preferredId != null) {
            activeProcesses[preferredId]?.let { runtime ->
                runtime.stopRequested = true
                runCatching { runtime.process.destroy() }
                activeProcesses.remove(preferredId)
            }
        }

        val process = ProcessBuilder(resolveShellPath(), "-c", trimmed)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()

        val now = Instant.now().toString()
        val snapshot = ManagedServiceSnapshot(
                id = id,
                name = displayName ?: workingDirectory.name.ifBlank { "service" },
                command = trimmed,
                workingDirectory = workingDirectory.absolutePath,
                status = ManagedServiceStatus.RUNNING,
                startedAt = now,
                updatedAt = now,
                outputPreview = "",
                lastExitCode = null
        )
        serviceSnapshots[id] = snapshot
        activeProcesses[id] = ManagedServiceRuntime(
                id = id,
                name = snapshot.name,
                command = trimmed,
                workingDirectory = workingDirectory,
                process = process,
                stopRequested = false
        )
        publishServiceSnapshotsLocked()
        observeServiceProcess(id, process)
        snapshot
    }

    suspend fun stopManagedService(serviceId: String): Boolean = runtimeMutex.withLock {
        val runtime = activeProcesses[serviceId] ?: return@withLock false
        runtime.stopRequested = true
        runCatching { runtime.process.destroy() }
        updateSnapshotLocked(serviceId) { snapshot ->
            snapshot.copy(
                    status = ManagedServiceStatus.STOPPED,
                    updatedAt = Instant.now().toString()
            )
        }
        publishServiceSnapshotsLocked()
        true
    }

    suspend fun restartManagedService(serviceId: String): Boolean = runtimeMutex.withLock {
        val existingSnapshot = serviceSnapshots[serviceId] ?: return@withLock false
        activeProcesses[serviceId]?.let { runtime ->
            runtime.stopRequested = true
            runCatching { runtime.process.destroy() }
            activeProcesses.remove(serviceId)
        }

        val process = ProcessBuilder(resolveShellPath(), "-c", existingSnapshot.command)
                .directory(File(existingSnapshot.workingDirectory))
                .redirectErrorStream(true)
                .start()

        val now = Instant.now().toString()
        val restartedSnapshot = existingSnapshot.copy(
                status = ManagedServiceStatus.RUNNING,
                startedAt = now,
                updatedAt = now,
                outputPreview = existingSnapshot.outputPreview,
                lastExitCode = null
        )
        serviceSnapshots[serviceId] = restartedSnapshot
        activeProcesses[serviceId] = ManagedServiceRuntime(
                id = serviceId,
                name = restartedSnapshot.name,
                command = restartedSnapshot.command,
                workingDirectory = File(restartedSnapshot.workingDirectory),
                process = process,
                stopRequested = false
        )
        publishServiceSnapshotsLocked()
        observeServiceProcess(serviceId, process)
        true
    }

    suspend fun close() {
        runtimeMutex.withLock {
            activeProcesses.values.forEach { runtime ->
                runtime.stopRequested = true
                runCatching { runtime.process.destroy() }
                runCatching { runtime.process.destroyForcibly() }
            }
            ubuntuInstallJob?.cancel()
            ubuntuInstallJob = null
            activeProcesses.clear()
            serviceSnapshots.clear()
            publishServiceSnapshotsLocked()
            if (initialized) {
                androidShellBackend.close()
                ubuntuProotBackend.close()
            }
            activeTerminalBackendKind = TerminalBackendKind.ANDROID_LOCAL
            initialized = false
            _runtimeServiceActive.value = false
        }
    }

    private fun observeServiceProcess(serviceId: String, process: Process) {
        runtimeScope.launch {
            val outputLines = ArrayDeque<String>()
            runCatching {
                val reader = InputStreamReader(process.inputStream).buffered()
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        outputLines.addLast(line)
                        while (outputLines.size > 20) {
                            outputLines.removeFirst()
                        }

                        runtimeMutex.withLock {
                            updateSnapshotLocked(serviceId) { snapshot ->
                                snapshot.copy(
                                        outputPreview = outputLines.joinToString("\n"),
                                        updatedAt = Instant.now().toString(),
                                        status = ManagedServiceStatus.RUNNING
                                )
                            }
                            publishServiceSnapshotsLocked()
                        }
                    }
                } finally {
                    runCatching { reader.close() }
                }
            }

            val exitCode = runCatching { process.waitFor() }.getOrDefault(1)

            runtimeMutex.withLock {
                val runtime = activeProcesses.remove(serviceId)
                val finalStatus = when {
                    runtime?.stopRequested == true -> ManagedServiceStatus.STOPPED
                    exitCode == 0 -> ManagedServiceStatus.STOPPED
                    else -> ManagedServiceStatus.FAILED
                }

                updateSnapshotLocked(serviceId) { snapshot ->
                    snapshot.copy(
                            status = finalStatus,
                            updatedAt = Instant.now().toString(),
                            lastExitCode = exitCode
                    )
                }
                publishServiceSnapshotsLocked()
            }
        }
    }

    private suspend fun appendTerminalHistory(command: String, result: TerminalCommandResult) {
        if (!initialized) {
            return
        }

        terminalHistoryStore.append(
                TerminalHistoryRecord(
                        command = command,
                        output = result.output,
                        exitCode = result.exitCode,
                        currentDirectory = result.currentDirectory.absolutePath,
                        recordedAt = Instant.now().toString()
                )
        )
    }

    private fun updateSnapshotLocked(
            serviceId: String,
            transform: (ManagedServiceSnapshot) -> ManagedServiceSnapshot
    ) {
        val current = serviceSnapshots[serviceId] ?: return
        serviceSnapshots[serviceId] = transform(current)
    }

    private fun publishServiceSnapshotsLocked() {
        _managedServices.value = serviceSnapshots.values
                .sortedWith(compareByDescending<ManagedServiceSnapshot> { it.updatedAt }.thenBy { it.name.lowercase() })
    }

    private fun requireActiveTerminalBackend(): TerminalBackend {
        check(initialized) { "Runtime manager has not been initialized." }
        return when (activeTerminalBackendKind) {
            TerminalBackendKind.ANDROID_LOCAL -> androidShellBackend
            TerminalBackendKind.UBUNTU_2204 -> ubuntuProotBackend
        }
    }

    private fun publishTerminalRuntimeLocked() {
        val backend = when (activeTerminalBackendKind) {
            TerminalBackendKind.ANDROID_LOCAL -> androidShellBackend
            TerminalBackendKind.UBUNTU_2204 -> ubuntuProotBackend
        }
        _terminalRuntime.value = TerminalRuntimeSnapshot(
                kind = backend.kind,
                displayName = backend.displayName,
                detail = backend.detail,
                homeDirectory = backend.homeDirectory.absolutePath,
                commandHint = backend.commandHint
        )
    }

    private fun publishUbuntuSnapshotLocked(snapshot: UbuntuRuntimeSnapshot) {
        _ubuntuRuntime.value = snapshot
        if (activeTerminalBackendKind == TerminalBackendKind.UBUNTU_2204) {
            publishTerminalRuntimeLocked()
        }
    }

    private fun resolveShellPath(): String {
        val candidates = listOf("/system/bin/sh", "/system/xbin/sh", "sh")
        return candidates.firstOrNull { candidate -> candidate == "sh" || File(candidate).exists() }
                ?: "sh"
    }
}
