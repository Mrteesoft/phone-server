package com.phoneserver.mobile.runtime

import java.io.File

class AndroidShellBackend(
        override val homeDirectory: File
) : TerminalBackend {

    private val session = PersistentTerminalSession(homeDirectory)

    override val kind: TerminalBackendKind = TerminalBackendKind.ANDROID_LOCAL
    override val displayName: String = "Android local shell"
    override val detail: String = "Runs commands through /system/bin/sh inside the app sandbox."
    override val commandHint: String = "Use pwd, ls, mkdir, cat, echo, touch, cp, mv, rm, or app-local scripts."

    override fun mapWorkspacePath(hostPath: String): String {
        return File(hostPath).canonicalOrSelf().absolutePath
    }

    override suspend fun execute(
            command: String,
            timeoutSeconds: Int,
            onOutputLine: (String) -> Unit
    ): TerminalCommandResult {
        return session.execute(command, timeoutSeconds, onOutputLine)
    }

    override suspend fun changeDirectory(
            targetDirectory: File,
            timeoutSeconds: Int
    ): TerminalCommandResult {
        return session.changeDirectory(targetDirectory, timeoutSeconds)
    }

    override suspend fun launchManagedProcess(
            command: String,
            workingDirectory: String
    ): ManagedServiceLaunch {
        val targetDirectory = File(workingDirectory).canonicalOrSelf()
        require(targetDirectory.exists() && targetDirectory.isDirectory) {
            "Working directory does not exist."
        }

        val process = ProcessBuilder(resolveShellPath(), "-c", command)
                .directory(targetDirectory)
                .redirectErrorStream(true)
                .start()

        return ManagedServiceLaunch(
                process = process,
                workingDirectory = targetDirectory.absolutePath
        )
    }

    override suspend fun close() {
        session.close()
    }

    private fun resolveShellPath(): String {
        val candidates = listOf("/system/bin/sh", "/system/xbin/sh", "sh")
        return candidates.firstOrNull { candidate -> candidate == "sh" || File(candidate).exists() }
                ?: "sh"
    }

    private fun File.canonicalOrSelf(): File = runCatching { canonicalFile }.getOrElse { this }
}
