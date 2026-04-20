package com.phoneserver.mobile.runtime

import java.io.File

class UbuntuProotBackend(
        private val runtimeSnapshotProvider: () -> UbuntuRuntimeSnapshot
) : TerminalBackend {

    private var session: PtyTerminalSession? = null
    private var sessionLauncherPath: String = ""

    override val kind: TerminalBackendKind = TerminalBackendKind.UBUNTU_2204
    override val displayName: String = "Ubuntu 22.04 userspace"
    override val detail: String
        get() = runtimeSnapshotProvider().detail
    override val homeDirectory: File
        get() = File(runtimeSnapshotProvider().homePath)
    override val commandHint: String = "Run apt, bash, python3, git, and other Ubuntu userspace commands."

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

        return terminalSession.changeDirectory(targetDirectory, timeoutSeconds)
    }

    override suspend fun close() {
        session?.close()
        session = null
        sessionLauncherPath = ""
    }

    private suspend fun ensureSession(snapshot: UbuntuRuntimeSnapshot): PtyTerminalSession? {
        val launcherPath = snapshot.runtimeLauncherPath
        val existingSession = session
        if (existingSession != null && sessionLauncherPath == launcherPath) {
            return existingSession
        }

        existingSession?.close()

        val launcher = File(launcherPath)
        if (!launcher.exists()) {
            session = null
            sessionLauncherPath = ""
            return null
        }

        val newSession = PtyTerminalSession(
                homeDirectory = homeDirectory
        ) {
            PtyLaunchConfiguration(
                    argv = listOf(launcher.absolutePath),
                    environment = mapOf("TERM" to "xterm-256color"),
                    workingDirectory = homeDirectory
            )
        }

        session = newSession
        sessionLauncherPath = launcherPath
        return newSession
    }

    private fun unavailableResult(
            snapshot: UbuntuRuntimeSnapshot,
            command: String
    ): TerminalCommandResult {
        val output = when (snapshot.phase) {
            UbuntuInstallPhase.NOT_INSTALLED ->
                "Ubuntu 22.04 is not installed yet. Prepare the runtime first from the Services tab."

            UbuntuInstallPhase.SCAFFOLD_READY ->
                "Ubuntu scaffold is ready. Install Ubuntu Base from the Services tab first."

            UbuntuInstallPhase.DOWNLOADING_ROOTFS,
            UbuntuInstallPhase.EXTRACTING_ROOTFS ->
                "Ubuntu setup is still in progress. Wait for the rootfs preparation to finish before running `${command}`."

            UbuntuInstallPhase.READY ->
                "Ubuntu Base is extracted locally, but the bundled proot launcher is still unavailable."

            UbuntuInstallPhase.FAILED ->
                snapshot.errorMessage ?: "Ubuntu runtime setup failed."
        }

        return TerminalCommandResult(
                output = output,
                currentDirectory = homeDirectory,
                exitCode = 127
        )
    }
}
