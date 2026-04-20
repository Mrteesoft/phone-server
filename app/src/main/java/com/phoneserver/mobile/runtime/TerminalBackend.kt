package com.phoneserver.mobile.runtime

import java.io.File

enum class TerminalBackendKind {
    ANDROID_LOCAL,
    UBUNTU_2204
}

data class TerminalRuntimeSnapshot(
        val kind: TerminalBackendKind,
        val displayName: String,
        val detail: String,
        val homeDirectory: String,
        val commandHint: String
)

data class RuntimeSwitchResult(
        val success: Boolean,
        val message: String
)

data class RuntimeActionResult(
        val success: Boolean,
        val message: String
)

interface TerminalBackend {
    val kind: TerminalBackendKind
    val displayName: String
    val detail: String
    val homeDirectory: File
    val commandHint: String

    suspend fun execute(
            command: String,
            timeoutSeconds: Int = 30,
            onOutputLine: (String) -> Unit = {}
    ): TerminalCommandResult

    suspend fun changeDirectory(
            targetDirectory: File,
            timeoutSeconds: Int = 10
    ): TerminalCommandResult

    suspend fun close()
}
