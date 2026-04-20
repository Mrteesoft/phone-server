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

    override suspend fun close() {
        session.close()
    }
}
