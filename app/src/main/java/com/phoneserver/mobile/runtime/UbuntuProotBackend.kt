package com.phoneserver.mobile.runtime

import java.io.File
import java.io.IOException

class UbuntuProotBackend(
        private val runtimeSnapshotProvider: () -> UbuntuRuntimeSnapshot,
        private val pathMapper: WorkspacePathMapper
) : TerminalBackend {

    private var session: PtyTerminalSession? = null
    private var sessionKey: String = ""
    private var pendingColumns: Int = DEFAULT_COLUMNS
    private var pendingRows: Int = DEFAULT_ROWS

    override val kind: TerminalBackendKind = TerminalBackendKind.UBUNTU_2204
    override val displayName: String = "Ubuntu 22.04 userspace"
    override val detail: String
        get() = runtimeSnapshotProvider().detail
    override val homeDirectory: File
        get() = File(runtimeSnapshotProvider().homePath)
    override val commandHint: String = "Real Ubuntu userspace. Run apt, sudo, python3, git, curl, ssh, and login-shell commands directly."
    override val displayHomeDirectory: String
        get() = runtimeSnapshotProvider().guestHomePath
    override val defaultWorkingDirectory: String
        get() = runtimeSnapshotProvider().guestHomePath

    override suspend fun isAvailable(): Boolean = runtimeSnapshotProvider().backendReady

    override fun mapWorkspacePath(hostPath: String): String = pathMapper.toUbuntuPath(hostPath)

    override suspend fun execute(
            command: String,
            timeoutSeconds: Int,
            onOutputLine: (String) -> Unit
    ): TerminalCommandResult {
        val snapshot = runtimeSnapshotProvider()
        if (!snapshot.backendReady || snapshot.runtimeLauncherPath.isBlank()) {
            return unavailableResult(snapshot, command)
        }

        val launcher = File(snapshot.runtimeLauncherPath)
        if (!launcher.exists()) {
            return TerminalCommandResult(
                    output = "Ubuntu launcher script is missing from ${launcher.absolutePath}.",
                    currentDirectory = homeDirectory,
                    exitCode = 127
            )
        }

        val terminalSession = ensureSession(snapshot)
                ?: return TerminalCommandResult(
                        output = "Failed to start the Ubuntu PTY session.",
                        currentDirectory = homeDirectory,
                        exitCode = 127
                )

        return terminalSession.execute(command, timeoutSeconds, onOutputLine)
    }

    override suspend fun changeDirectory(
            targetDirectory: File,
            timeoutSeconds: Int
    ): TerminalCommandResult {
        val snapshot = runtimeSnapshotProvider()
        if (!snapshot.backendReady) {
            return unavailableResult(snapshot, "cd ${targetDirectory.absolutePath}")
        }

        val terminalSession = ensureSession(snapshot)
                ?: return TerminalCommandResult(
                        output = "Failed to start the Ubuntu PTY session.",
                        currentDirectory = homeDirectory,
                        exitCode = 127
                )

        val runtimeDirectory = normalizeWorkingDirectory(targetDirectory.absolutePath)
        return terminalSession.execute(
                command = "cd ${runtimeDirectory.shellQuoted()}",
                timeoutSeconds = timeoutSeconds
        )
    }

    override fun normalizeWorkingDirectory(path: String): String {
        return mapWorkspacePath(path)
    }

    override suspend fun launchManagedProcess(
            command: String,
            workingDirectory: String
    ): ManagedServiceLaunch {
        val snapshot = runtimeSnapshotProvider()
        if (!snapshot.backendReady || snapshot.runtimeLauncherPath.isBlank()) {
            throw IllegalStateException(buildUnavailableMessage(snapshot, command))
        }

        val launcher = File(snapshot.runtimeLauncherPath)
        if (!launcher.exists()) {
            throw IOException("Ubuntu launcher script is missing from ${launcher.absolutePath}.")
        }

        val runtimeDirectory = normalizeWorkingDirectory(workingDirectory)
        val hostWorkingDirectory = File(snapshot.workspaceMountPath)
                .takeIf { it.exists() && it.isDirectory }
                ?: File(snapshot.runtimeRootPath).takeIf { it.exists() && it.isDirectory }
                ?: launcher.parentFile

        val process = ProcessBuilder(
                resolveHostShellPath(),
                launcher.absolutePath,
                "/bin/bash",
                "--login",
                "-lc",
                "cd ${runtimeDirectory.shellQuoted()} && exec $command"
        ).directory(hostWorkingDirectory)
                .redirectErrorStream(true)
                .start()

        return ManagedServiceLaunch(
                process = process,
                workingDirectory = runtimeDirectory
        )
    }

    override suspend fun close() {
        session?.close()
        session = null
        sessionKey = ""
    }

    override suspend fun resize(columns: Int, rows: Int) {
        pendingColumns = columns.coerceAtLeast(MIN_COLUMNS)
        pendingRows = rows.coerceAtLeast(MIN_ROWS)
        session?.resize(pendingColumns, pendingRows)
    }

    override suspend fun interrupt(): Boolean {
        return session?.interruptForegroundProcess() ?: false
    }

    private suspend fun ensureSession(snapshot: UbuntuRuntimeSnapshot): PtyTerminalSession? {
        val launcherKey = "${snapshot.runtimeLauncherPath}:${snapshot.lastUpdatedAt}:${snapshot.guestHomePath}"
        val existingSession = session
        if (existingSession != null && sessionKey == launcherKey) {
            return existingSession
        }

        existingSession?.close()

        val launcher = File(snapshot.runtimeLauncherPath)
        if (!launcher.exists()) {
            session = null
            sessionKey = ""
            return null
        }

        val newSession = PtyTerminalSession(
                homeDirectory = homeDirectory
        ) {
            PtyLaunchConfiguration(
                    argv = listOf(resolveHostShellPath(), launcher.absolutePath),
                    environment = mapOf(
                            "TERM" to "xterm-256color",
                            "LANG" to "C.UTF-8"
                    ),
                    workingDirectory = homeDirectory
            )
        }
        newSession.resize(pendingColumns, pendingRows)

        session = newSession
        sessionKey = launcherKey
        return newSession
    }

    private fun unavailableResult(
            snapshot: UbuntuRuntimeSnapshot,
            command: String
    ): TerminalCommandResult {
        return TerminalCommandResult(
                output = buildUnavailableMessage(snapshot, command),
                currentDirectory = homeDirectory,
                exitCode = 127
        )
    }

    private fun buildUnavailableMessage(
            snapshot: UbuntuRuntimeSnapshot,
            command: String
    ): String {
        return when (snapshot.phase) {
            UbuntuInstallPhase.NOT_INSTALLED ->
                "Ubuntu 22.04 is not installed yet. Prepare the runtime first from the Services tab."

            UbuntuInstallPhase.SCAFFOLD_READY ->
                "Ubuntu scaffold is ready. Install Ubuntu Base from the Services tab first."

            UbuntuInstallPhase.DOWNLOADING_ROOTFS,
            UbuntuInstallPhase.EXTRACTING_ROOTFS,
            UbuntuInstallPhase.VERIFYING_BOOT ->
                "Ubuntu setup is still in progress. Wait for the rootfs preparation to finish before running `${command}`."

            UbuntuInstallPhase.READY ->
                "Ubuntu Base is extracted locally, but the bundled proot launcher is still unavailable."

            UbuntuInstallPhase.FAILED ->
                snapshot.errorMessage ?: "Ubuntu runtime setup failed."
        }
    }

    private fun resolveHostShellPath(): String {
        val candidates = listOf("/system/bin/sh", "/system/xbin/sh", "sh")
        return candidates.firstOrNull { candidate -> candidate == "sh" || File(candidate).exists() }
                ?: "sh"
    }

    private fun String.shellQuoted(): String = "'" + replace("'", "'\"'\"'") + "'"

    private companion object {
        const val DEFAULT_COLUMNS = 120
        const val DEFAULT_ROWS = 40
        const val MIN_COLUMNS = 40
        const val MIN_ROWS = 16
    }
}
