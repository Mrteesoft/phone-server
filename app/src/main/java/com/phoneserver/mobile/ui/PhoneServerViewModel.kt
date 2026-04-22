package com.phoneserver.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneserver.mobile.runtime.ManagedServiceSnapshot
import com.phoneserver.mobile.runtime.ManagedServiceStatus
import com.phoneserver.mobile.runtime.PhoneServerRuntimeManager
import com.phoneserver.mobile.runtime.TerminalBackendKind
import com.phoneserver.mobile.runtime.TerminalRuntimeSnapshot
import com.phoneserver.mobile.runtime.UbuntuInstallPhase
import com.phoneserver.mobile.runtime.UbuntuRuntimeSnapshot
import com.phoneserver.mobile.runtime.WorkspaceManager
import com.phoneserver.mobile.storage.TerminalHistoryStore
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalLine(
        val id: Long,
        val kind: TerminalLineKind,
        val text: String
)

enum class TerminalLineKind {
    COMMAND,
    OUTPUT,
    STATUS,
    ERROR
}

data class WorkspaceSummary(
        val name: String,
        val path: String,
        val fileCount: Int,
        val createdAt: String
)

data class ServiceSummary(
        val name: String,
        val status: String,
        val detail: String
)

data class ManagedServiceSummary(
        val id: String,
        val name: String,
        val command: String,
        val status: String,
        val workingDirectory: String,
        val outputPreview: String,
        val lastExitCode: Int?,
        val startedAt: String
)

data class TerminalRuntimeSummary(
        val kind: TerminalBackendKind,
        val displayName: String,
        val detail: String,
        val homeDirectory: String,
        val commandHint: String
)

data class UbuntuRuntimeSummary(
        val releaseLabel: String,
        val phase: String,
        val detail: String,
        val architecture: String,
        val rootfsPath: String,
        val archivePath: String,
        val sourceUrl: String,
        val workspaceMountPath: String,
        val backendReady: Boolean,
        val progressLabel: String,
        val canPrepare: Boolean,
        val canInstall: Boolean,
        val canUse: Boolean,
        val installButtonLabel: String
)

data class PhoneServerUiState(
        val currentDirectory: String,
        val workspaces: List<WorkspaceSummary>,
        val terminalLines: List<TerminalLine>,
        val runningCommand: Boolean,
        val services: List<ServiceSummary>,
        val managedServices: List<ManagedServiceSummary>,
        val terminalRuntime: TerminalRuntimeSummary,
        val ubuntuRuntime: UbuntuRuntimeSummary
)

private data class UbuntuRuntimeTerminalEvent(
        val key: String,
        val kind: TerminalLineKind,
        val message: String
)

class PhoneServerViewModel(application: Application) : AndroidViewModel(application) {

    private val workspaceManager = WorkspaceManager(application)
    private val terminalHistoryStore = TerminalHistoryStore(application)
    private val nextLineId = AtomicLong(0L)
    private val initialWorkspaces = workspaceManager.listWorkspaces()
    private var pendingUbuntuAutoSwitch = false
    private var hasObservedUbuntuRuntime = false
    private var lastUbuntuRuntimeEventKey: String? = null

    private val _uiState = MutableStateFlow(
            PhoneServerUiState(
                    currentDirectory = workspaceManager.rootDirectory().absolutePath,
                    workspaces = initialWorkspaces,
                    terminalLines = buildInitialTerminalLines(workspaceManager.rootDirectory()),
                    runningCommand = false,
                    services = buildServices(
                            workspaceCount = initialWorkspaces.size,
                            currentDirectory = workspaceManager.rootDirectory().absolutePath,
                            runningCommand = false,
                            managedServiceCount = 0,
                            runtimeServiceActive = false
                    ),
                    managedServices = emptyList(),
                    terminalRuntime = TerminalRuntimeSummary(
                            kind = TerminalBackendKind.ANDROID_LOCAL,
                            displayName = "Android local shell",
                            detail = "Runs commands through /system/bin/sh inside the app sandbox.",
                            homeDirectory = workspaceManager.rootDirectory().absolutePath,
                            commandHint = "Use pwd, ls, mkdir, cat, echo, touch, cp, mv, rm, or app-local scripts."
                    ),
                    ubuntuRuntime = UbuntuRuntimeSummary(
                            releaseLabel = "Ubuntu 22.04 LTS",
                            phase = "NOT_INSTALLED",
                            detail = "Ubuntu 22.04 runtime is not installed yet.",
                            architecture = "",
                            rootfsPath = "",
                            archivePath = "",
                            sourceUrl = "",
                            workspaceMountPath = workspaceManager.rootDirectory().absolutePath,
                            backendReady = false,
                            progressLabel = "",
                            canPrepare = false,
                            canInstall = false,
                            canUse = false,
                            installButtonLabel = "Install Ubuntu"
                    )
            )
    )
    val uiState: StateFlow<PhoneServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            PhoneServerRuntimeManager.initialize(application)
            PhoneServerRuntimeManager.managedServices.collect { snapshots ->
                val mappedServices = snapshots.map(::mapManagedService)
                _uiState.update { current ->
                    current.copy(
                            managedServices = mappedServices,
                            services = buildServices(
                                    workspaceCount = current.workspaces.size,
                                    currentDirectory = current.currentDirectory,
                                    runningCommand = current.runningCommand,
                                    managedServiceCount = mappedServices.count { it.status == "RUNNING" },
                                    runtimeServiceActive = PhoneServerRuntimeManager.runtimeServiceActive.value
                            )
                    )
                }
            }
        }

        viewModelScope.launch {
            PhoneServerRuntimeManager.runtimeServiceActive.collect { runtimeActive ->
                _uiState.update { current ->
                    current.copy(
                            services = buildServices(
                                    workspaceCount = current.workspaces.size,
                                    currentDirectory = current.currentDirectory,
                                    runningCommand = current.runningCommand,
                                    managedServiceCount = current.managedServices.count { it.status == "RUNNING" },
                                    runtimeServiceActive = runtimeActive
                            )
                    )
                }
            }
        }

        viewModelScope.launch {
            PhoneServerRuntimeManager.terminalRuntime.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                            terminalRuntime = mapTerminalRuntime(snapshot)
                    )
                }
            }
        }

        viewModelScope.launch {
            PhoneServerRuntimeManager.ubuntuRuntime.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                            ubuntuRuntime = mapUbuntuRuntime(snapshot)
                    )
                }
                appendUbuntuRuntimeEventIfNeeded(snapshot)
                maybeAutoSwitchToUbuntu(snapshot)
            }
        }
    }

    fun refreshWorkspaces() {
        val workspaces = workspaceManager.listWorkspaces()
        _uiState.update { current ->
            current.copy(
                    workspaces = workspaces,
                    services = buildServices(
                            workspaceCount = workspaces.size,
                            currentDirectory = current.currentDirectory,
                            runningCommand = current.runningCommand,
                            managedServiceCount = current.managedServices.count { it.status == "RUNNING" },
                            runtimeServiceActive = PhoneServerRuntimeManager.runtimeServiceActive.value
                    )
            )
        }
    }

    fun createWorkspace(rawName: String) {
        val workspace = runCatching { workspaceManager.createWorkspace(rawName) }
                .getOrElse { exception ->
                    appendLine(
                            kind = TerminalLineKind.ERROR,
                            text = exception.message ?: "Failed to create workspace."
                    )
                    return
                }

        _uiState.update { current ->
            val workspaces = workspaceManager.listWorkspaces()
            current.copy(
                    workspaces = workspaces,
                    services = buildServices(
                            workspaceCount = workspaces.size,
                            currentDirectory = current.currentDirectory,
                            runningCommand = current.runningCommand,
                            managedServiceCount = current.managedServices.count { it.status == "RUNNING" },
                            runtimeServiceActive = PhoneServerRuntimeManager.runtimeServiceActive.value
                    )
            )
        }

        appendLine(TerminalLineKind.STATUS, "Workspace created: ${workspace.path}")
    }

    fun useWorkspace(path: String) {
        if (_uiState.value.runningCommand) {
            appendLine(TerminalLineKind.STATUS, "Wait for the current command to finish first.")
            return
        }

        setCommandRunning(true)
        viewModelScope.launch {
            val result = PhoneServerRuntimeManager.changeDirectory(File(path))
            if (result.exitCode == 0) {
                workspaceManager.markOpened(path)
                refreshWorkspaces()
            }
            applyCommandResult(
                    result,
                    streamedOutput = false,
                    successMessage = "Terminal moved to ${result.currentDirectory.absolutePath}"
            )
        }
    }

    fun clearTerminal() {
        _uiState.update { current -> current.copy(terminalLines = emptyList()) }
    }

    fun runCommand(rawCommand: String) {
        val command = rawCommand.trim()
        val runtime = _uiState.value.terminalRuntime.kind
        if (command.isEmpty()) {
            return
        }
        if (isRootOnlyCommand(command) && runtime == TerminalBackendKind.ANDROID_LOCAL) {
            appendLine(TerminalLineKind.COMMAND, "$ ${command}")
            appendLine(
                    TerminalLineKind.ERROR,
                    buildRootOnlyCommandUnavailableMessage(command, _uiState.value.ubuntuRuntime)
            )
            return
        }
        if (isLinuxUserspaceCommand(command) &&
                runtime == TerminalBackendKind.ANDROID_LOCAL) {
            appendLine(TerminalLineKind.COMMAND, "$ ${command}")
            appendLine(
                    TerminalLineKind.ERROR,
                    buildUserspaceCommandUnavailableMessage(command, _uiState.value.ubuntuRuntime)
            )
            return
        }
        if (_uiState.value.runningCommand) {
            appendLine(TerminalLineKind.STATUS, "A command is already running.")
            return
        }

        appendLine(TerminalLineKind.COMMAND, "$ ${command}")
        setCommandRunning(true)

        viewModelScope.launch {
            var streamedOutput = false
            val result = PhoneServerRuntimeManager.runTerminalCommand(
                    command = command,
                    onOutputLine = { line ->
                        streamedOutput = true
                        appendLine(TerminalLineKind.OUTPUT, line)
                    }
            )

            applyCommandResult(result, streamedOutput = streamedOutput)
        }
    }

    fun startManagedService(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isEmpty()) {
            appendLine(TerminalLineKind.STATUS, "Service command cannot be empty.")
            return
        }

        viewModelScope.launch {
            val snapshot = runCatching {
                PhoneServerRuntimeManager.startManagedService(
                        command = command,
                        workingDirectory = _uiState.value.currentDirectory
                )
            }.getOrElse { exception ->
                appendLine(
                        TerminalLineKind.ERROR,
                        exception.message ?: "Failed to start managed service."
                )
                return@launch
            }

            appendLine(
                    TerminalLineKind.STATUS,
                    "Managed service started: ${snapshot.command} in ${snapshot.workingDirectory}"
            )
        }
    }

    fun stopManagedService(serviceId: String) {
        viewModelScope.launch {
            val stopped = PhoneServerRuntimeManager.stopManagedService(serviceId)
            if (!stopped) {
                appendLine(TerminalLineKind.ERROR, "Managed service not found.")
            } else {
                appendLine(TerminalLineKind.STATUS, "Managed service stop requested.")
            }
        }
    }

    fun restartManagedService(serviceId: String) {
        viewModelScope.launch {
            val restarted = PhoneServerRuntimeManager.restartManagedService(serviceId)
            if (!restarted) {
                appendLine(TerminalLineKind.ERROR, "Managed service could not be restarted.")
            } else {
                appendLine(TerminalLineKind.STATUS, "Managed service restarted.")
            }
        }
    }

    fun prepareUbuntuRuntime() {
        viewModelScope.launch {
            val snapshot = PhoneServerRuntimeManager.prepareUbuntuRuntime()
            appendLine(TerminalLineKind.STATUS, "Ubuntu scaffold prepared at ${snapshot.runtimeRootPath}")
        }
    }

    fun installUbuntuRuntime() {
        viewModelScope.launch {
            val result = PhoneServerRuntimeManager.installUbuntuRuntime()
            if (result.success) {
                pendingUbuntuAutoSwitch = true
            }
            appendLine(
                    kind = if (result.success) TerminalLineKind.STATUS else TerminalLineKind.ERROR,
                    text = result.message
            )
        }
    }

    fun useAndroidRuntime() {
        viewModelScope.launch {
            val sourceKind = _uiState.value.terminalRuntime.kind
            val result = PhoneServerRuntimeManager.switchTerminalRuntime(TerminalBackendKind.ANDROID_LOCAL)
            if (result.success) {
                updateDirectoryForRuntimeSwitch(
                        sourceKind = sourceKind,
                        targetKind = TerminalBackendKind.ANDROID_LOCAL
                )
            }
            appendLine(
                    kind = if (result.success) TerminalLineKind.STATUS else TerminalLineKind.ERROR,
                    text = result.message
            )
        }
    }

    fun useUbuntuRuntime() {
        viewModelScope.launch {
            val sourceKind = _uiState.value.terminalRuntime.kind
            val result = PhoneServerRuntimeManager.switchTerminalRuntime(TerminalBackendKind.UBUNTU_2204)
            if (result.success) {
                updateDirectoryForRuntimeSwitch(
                        sourceKind = sourceKind,
                        targetKind = TerminalBackendKind.UBUNTU_2204
                )
            }
            appendLine(
                    kind = if (result.success) TerminalLineKind.STATUS else TerminalLineKind.ERROR,
                    text = result.message
            )
        }
    }

    private fun appendLine(kind: TerminalLineKind, text: String) {
        _uiState.update { current ->
            current.copy(
                    terminalLines = current.terminalLines + TerminalLine(nextLineId.getAndIncrement(), kind, text)
            )
        }
    }

    private fun appendUbuntuRuntimeEventIfNeeded(snapshot: UbuntuRuntimeSnapshot) {
        val event = buildUbuntuRuntimeTerminalEvent(snapshot)
        hasObservedUbuntuRuntime = true
        if (event == null || event.key == lastUbuntuRuntimeEventKey) {
            return
        }

        lastUbuntuRuntimeEventKey = event.key
        appendLine(event.kind, event.message)
    }

    private fun applyCommandResult(
            result: com.phoneserver.mobile.runtime.TerminalCommandResult,
            streamedOutput: Boolean,
            successMessage: String? = null
    ) {
        if (result.cleared) {
            _uiState.update { current -> current.copy(terminalLines = emptyList()) }
        } else {
            if (!streamedOutput && result.output.isNotBlank()) {
                appendLine(
                        kind = if (result.exitCode == 0) TerminalLineKind.OUTPUT else TerminalLineKind.ERROR,
                        text = result.output
                )
            }

            when {
                successMessage != null && result.exitCode == 0 -> appendLine(TerminalLineKind.STATUS, successMessage)
                result.exitCode == 0 && !streamedOutput && result.output.isBlank() ->
                    appendLine(TerminalLineKind.STATUS, "Command completed with no output.")
                result.exitCode != 0 && (streamedOutput || result.output.isBlank()) ->
                    appendLine(TerminalLineKind.ERROR, "Command exited with code ${result.exitCode}.")
            }
        }

        _uiState.update { current ->
            current.copy(
                    currentDirectory = result.currentDirectory.absolutePath,
                    runningCommand = false,
                    services = buildServices(
                            workspaceCount = current.workspaces.size,
                            currentDirectory = result.currentDirectory.absolutePath,
                            runningCommand = false,
                            managedServiceCount = current.managedServices.count { it.status == "RUNNING" },
                            runtimeServiceActive = PhoneServerRuntimeManager.runtimeServiceActive.value
                    )
            )
        }
    }

    private fun setCommandRunning(running: Boolean) {
        _uiState.update { current ->
            current.copy(
                    runningCommand = running,
                    services = buildServices(
                            workspaceCount = current.workspaces.size,
                            currentDirectory = current.currentDirectory,
                            runningCommand = running,
                            managedServiceCount = current.managedServices.count { it.status == "RUNNING" },
                            runtimeServiceActive = PhoneServerRuntimeManager.runtimeServiceActive.value
                    )
            )
        }
    }

    private fun updateDirectoryForRuntimeSwitch(
            sourceKind: TerminalBackendKind,
            targetKind: TerminalBackendKind
    ) {
        _uiState.update { current ->
            val mappedDirectory = PhoneServerRuntimeManager.remapDisplayedPathForRuntimeSwitch(
                    path = current.currentDirectory,
                    sourceKind = sourceKind,
                    targetKind = targetKind
            )

            current.copy(
                    currentDirectory = mappedDirectory,
                    services = buildServices(
                            workspaceCount = current.workspaces.size,
                            currentDirectory = mappedDirectory,
                            runningCommand = current.runningCommand,
                            managedServiceCount = current.managedServices.count { it.status == "RUNNING" },
                            runtimeServiceActive = PhoneServerRuntimeManager.runtimeServiceActive.value
                    )
            )
        }
    }

    private suspend fun maybeAutoSwitchToUbuntu(snapshot: UbuntuRuntimeSnapshot) {
        if (snapshot.phase == UbuntuInstallPhase.FAILED) {
            pendingUbuntuAutoSwitch = false
            return
        }

        if (!pendingUbuntuAutoSwitch ||
                snapshot.phase != UbuntuInstallPhase.READY ||
                !snapshot.backendReady) {
            return
        }

        pendingUbuntuAutoSwitch = false
        val sourceKind = _uiState.value.terminalRuntime.kind
        if (sourceKind == TerminalBackendKind.UBUNTU_2204) {
            return
        }

        val result = PhoneServerRuntimeManager.switchTerminalRuntime(TerminalBackendKind.UBUNTU_2204)
        if (result.success) {
            updateDirectoryForRuntimeSwitch(
                    sourceKind = sourceKind,
                    targetKind = TerminalBackendKind.UBUNTU_2204
            )
        }
        appendLine(
                kind = if (result.success) TerminalLineKind.STATUS else TerminalLineKind.ERROR,
                text = if (result.success) {
                    "Ubuntu install complete. ${result.message}"
                } else {
                    result.message
                }
        )
    }

    private fun isRootOnlyCommand(command: String): Boolean {
        val firstToken = command.substringBefore(' ').lowercase()
        return firstToken == "sudo" || firstToken == "su"
    }

    private fun isLinuxUserspaceCommand(command: String): Boolean {
        val firstToken = command.substringBefore(' ').lowercase()
        return firstToken in setOf(
                "apt",
                "apt-get",
                "dpkg",
                "systemctl",
                "service",
                "adduser",
                "useradd",
                "passwd",
                "bash",
                "shopt"
        )
    }

    private fun buildUserspaceCommandUnavailableMessage(
            command: String,
            ubuntuRuntime: UbuntuRuntimeSummary
    ): String {
        val executable = command.substringBefore(' ')
        val prefix = "`${executable}` is not available in the Android local shell."

        return when (ubuntuRuntime.phase) {
            UbuntuInstallPhase.NOT_INSTALLED.name ->
                "$prefix Open Services and run Prepare Ubuntu, then Install Ubuntu if you want a Linux userspace rootfs on the phone."

            UbuntuInstallPhase.SCAFFOLD_READY.name ->
                "$prefix The Ubuntu scaffold exists, but Ubuntu Base is not installed yet. Open Services and press Install Ubuntu."

            UbuntuInstallPhase.DOWNLOADING_ROOTFS.name,
            UbuntuInstallPhase.EXTRACTING_ROOTFS.name ->
                "$prefix Ubuntu setup is still running in the background. Wait for installation to finish before trying Linux package commands."

            UbuntuInstallPhase.READY.name ->
                if (ubuntuRuntime.backendReady) {
                    "$prefix Ubuntu 22.04 is ready, but this screen is still attached to the Android local shell. Open Services and switch the terminal runtime to Ubuntu first."
                } else {
                    "$prefix Ubuntu Base is staged locally, but the Ubuntu launcher is still unavailable. Until that launcher is ready, apt-style commands cannot run here."
                }

            UbuntuInstallPhase.FAILED.name ->
                "$prefix Ubuntu setup failed: ${ubuntuRuntime.detail}. Open Services, check the runtime state, and repair the Ubuntu install."

            else ->
                "$prefix This terminal is still the Android app sandbox, not a real Ubuntu shell."
        }
    }

    private fun buildRootOnlyCommandUnavailableMessage(
            command: String,
            ubuntuRuntime: UbuntuRuntimeSummary
    ): String {
        val executable = command.substringBefore(' ')
        val prefix = "This shell is running inside the Android app sandbox. `${executable}` is not available here."

        return when {
            ubuntuRuntime.canUse ->
                "$prefix Ubuntu userspace is ready now. Tap `use ubuntu` in the terminal header and run the command there."

            ubuntuRuntime.canInstall ->
                "$prefix Ubuntu scaffold is ready. Install Ubuntu Base first, then switch the terminal runtime to Ubuntu."

            ubuntuRuntime.canPrepare ->
                "$prefix Prepare Ubuntu first if you want a Linux userspace terminal on the phone."

            else ->
                "$prefix Run the command without sudo if it only needs app-local access, or use a rooted device / userspace distro for root-like operations."
        }
    }

    private fun buildInitialTerminalLines(rootDirectory: File): List<TerminalLine> {
        val welcomeLines = listOf(
                TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.OUTPUT, "Phone Server terminal"),
                TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.STATUS, "Local-only runtime. No account. No external control plane."),
                TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.STATUS, "Workspace root: ${rootDirectory.absolutePath}"),
                TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.STATUS, "Tap anywhere to type. Press enter or tap run. Shell directory changes survive between commands."),
                TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.STATUS, "Root-only commands such as sudo require a rooted device or a userspace distro."),
                TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.STATUS, "This screen starts in the Android local shell. Ubuntu commands such as apt do not work here yet.")
        )

        val historyLines = terminalHistoryStore.loadRecent(limit = 12).flatMap { record ->
            buildList {
                add(TerminalLine(nextLineId.getAndIncrement(), TerminalLineKind.COMMAND, "$ ${record.command}"))
                if (record.output.isNotBlank()) {
                    add(
                            TerminalLine(
                                    nextLineId.getAndIncrement(),
                                    if (record.exitCode == 0) TerminalLineKind.OUTPUT else TerminalLineKind.ERROR,
                                    record.output
                            )
                    )
                }
            }
        }

        return welcomeLines + historyLines
    }

    private fun buildServices(
            workspaceCount: Int,
            currentDirectory: String,
            runningCommand: Boolean,
            managedServiceCount: Int,
            runtimeServiceActive: Boolean
    ): List<ServiceSummary> {
        return listOf(
                ServiceSummary(
                        name = "Runtime service",
                        status = if (runtimeServiceActive) "Foreground" else "Inactive",
                        detail = if (runtimeServiceActive) {
                            "Keeps the local shell and managed services alive while the app is backgrounded."
                        } else {
                            "Grant notification permission to keep long-running work attached to the foreground runtime."
                        }
                ),
                ServiceSummary(
                        name = "Shell runtime",
                        status = if (runningCommand) "Busy" else "Ready",
                        detail = "Commands run through a persistent local shell session."
                ),
                ServiceSummary(
                        name = "Workspace store",
                        status = "$workspaceCount workspaces",
                        detail = currentDirectory
                ),
                ServiceSummary(
                        name = "Managed services",
                        status = "$managedServiceCount active",
                        detail = "Start long-running commands and control them from the Services tab."
                )
        )
    }

    private fun mapManagedService(snapshot: ManagedServiceSnapshot): ManagedServiceSummary {
        return ManagedServiceSummary(
                id = snapshot.id,
                name = snapshot.name,
                command = snapshot.command,
                status = when (snapshot.status) {
                    ManagedServiceStatus.STARTING -> "STARTING"
                    ManagedServiceStatus.RUNNING -> "RUNNING"
                    ManagedServiceStatus.STOPPED -> "STOPPED"
                    ManagedServiceStatus.FAILED -> "FAILED"
                },
                workingDirectory = snapshot.workingDirectory,
                outputPreview = snapshot.outputPreview,
                lastExitCode = snapshot.lastExitCode,
                startedAt = snapshot.startedAt
        )
    }

    private fun mapTerminalRuntime(snapshot: TerminalRuntimeSnapshot): TerminalRuntimeSummary {
        return TerminalRuntimeSummary(
                kind = snapshot.kind,
                displayName = snapshot.displayName,
                detail = snapshot.detail,
                homeDirectory = snapshot.homeDirectory,
                commandHint = snapshot.commandHint
        )
    }

    private fun mapUbuntuRuntime(snapshot: UbuntuRuntimeSnapshot): UbuntuRuntimeSummary {
        val canPrepare = snapshot.sourceUrl.isNotBlank() &&
                (snapshot.phase == UbuntuInstallPhase.NOT_INSTALLED || snapshot.phase == UbuntuInstallPhase.FAILED)
        val canInstall = snapshot.sourceUrl.isNotBlank() &&
                (snapshot.phase == UbuntuInstallPhase.SCAFFOLD_READY || snapshot.phase == UbuntuInstallPhase.FAILED)

        return UbuntuRuntimeSummary(
                releaseLabel = snapshot.releaseLabel,
                phase = snapshot.phase.name,
                detail = snapshot.detail,
                architecture = snapshot.architecture,
                rootfsPath = snapshot.rootfsPath,
                archivePath = snapshot.archivePath,
                sourceUrl = snapshot.sourceUrl,
                workspaceMountPath = snapshot.workspaceMountPath,
                backendReady = snapshot.backendReady,
                progressLabel = buildUbuntuProgressLabel(snapshot),
                canPrepare = canPrepare,
                canInstall = canInstall,
                canUse = snapshot.backendReady,
                installButtonLabel = when (snapshot.phase) {
                    UbuntuInstallPhase.FAILED -> "Repair Ubuntu"
                    UbuntuInstallPhase.READY -> "Installed"
                    UbuntuInstallPhase.DOWNLOADING_ROOTFS,
                    UbuntuInstallPhase.EXTRACTING_ROOTFS -> "Installing..."
                    else -> "Install Ubuntu"
                }
        )
    }

    private fun buildUbuntuProgressLabel(snapshot: UbuntuRuntimeSnapshot): String {
        return when (snapshot.phase) {
            UbuntuInstallPhase.DOWNLOADING_ROOTFS -> if (snapshot.totalBytes > 0L) {
                "${formatBytes(snapshot.downloadedBytes)} / ${formatBytes(snapshot.totalBytes)}"
            } else {
                "${formatBytes(snapshot.downloadedBytes)} downloaded"
            }

            UbuntuInstallPhase.EXTRACTING_ROOTFS -> {
                val archiveBytes = snapshot.totalBytes.coerceAtLeast(snapshot.downloadedBytes)
                if (archiveBytes > 0L) {
                    "Archive ready: ${formatBytes(archiveBytes)}"
                } else {
                    "Archive downloaded, extraction in progress"
                }
            }

            UbuntuInstallPhase.READY -> if (snapshot.totalBytes > 0L) {
                "Archive cached: ${formatBytes(snapshot.totalBytes)}"
            } else {
                ""
            }

            else -> ""
        }
    }

    private fun buildUbuntuRuntimeTerminalEvent(
            snapshot: UbuntuRuntimeSnapshot
    ): UbuntuRuntimeTerminalEvent? {
        if (!hasObservedUbuntuRuntime && snapshot.phase == UbuntuInstallPhase.NOT_INSTALLED) {
            return null
        }

        return when (snapshot.phase) {
            UbuntuInstallPhase.NOT_INSTALLED -> null

            UbuntuInstallPhase.SCAFFOLD_READY ->
                UbuntuRuntimeTerminalEvent(
                        key = "ubuntu-scaffold:${snapshot.detail}",
                        kind = TerminalLineKind.STATUS,
                        message = snapshot.detail
                )

            UbuntuInstallPhase.DOWNLOADING_ROOTFS -> buildUbuntuDownloadEvent(snapshot)

            UbuntuInstallPhase.EXTRACTING_ROOTFS -> buildUbuntuExtractionEvent(snapshot)

            UbuntuInstallPhase.READY ->
                UbuntuRuntimeTerminalEvent(
                        key = "ubuntu-ready:${snapshot.detail}:${snapshot.backendReady}",
                        kind = TerminalLineKind.STATUS,
                        message = snapshot.detail
                )

            UbuntuInstallPhase.FAILED ->
                UbuntuRuntimeTerminalEvent(
                        key = "ubuntu-failed:${snapshot.errorMessage ?: snapshot.detail}",
                        kind = TerminalLineKind.ERROR,
                        message = "Ubuntu install failed: ${snapshot.errorMessage ?: snapshot.detail}"
                )
        }
    }

    private fun buildUbuntuDownloadEvent(
            snapshot: UbuntuRuntimeSnapshot
    ): UbuntuRuntimeTerminalEvent {
        val bucket = if (snapshot.totalBytes > 0L) {
            ((snapshot.downloadedBytes * 10) / snapshot.totalBytes).toInt()
        } else {
            (snapshot.downloadedBytes / (8L * 1024L * 1024L)).toInt()
        }

        val message = if (bucket <= 0) {
            snapshot.detail
        } else if (snapshot.totalBytes > 0L) {
            "Ubuntu download progress: ${formatBytes(snapshot.downloadedBytes)} / ${formatBytes(snapshot.totalBytes)}"
        } else {
            "Ubuntu download progress: ${formatBytes(snapshot.downloadedBytes)} downloaded"
        }

        return UbuntuRuntimeTerminalEvent(
                key = "ubuntu-download:${bucket}:${snapshot.totalBytes}",
                kind = TerminalLineKind.STATUS,
                message = message
        )
    }

    private fun buildUbuntuExtractionEvent(
            snapshot: UbuntuRuntimeSnapshot
    ): UbuntuRuntimeTerminalEvent {
        val extractedEntries = Regex("(\\d+)").find(snapshot.detail)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        return if (extractedEntries == null) {
            UbuntuRuntimeTerminalEvent(
                    key = "ubuntu-extract:${snapshot.detail}",
                    kind = TerminalLineKind.STATUS,
                    message = snapshot.detail
            )
        } else {
            val bucket = extractedEntries / 400
            UbuntuRuntimeTerminalEvent(
                    key = "ubuntu-extract:${bucket}",
                    kind = TerminalLineKind.STATUS,
                    message = "Ubuntu extraction progress: $extractedEntries archive entries unpacked."
            )
        }
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
