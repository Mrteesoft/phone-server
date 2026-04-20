package com.phoneserver.mobile.runtime

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.EOFException
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class TerminalCommandResult(
        val output: String,
        val currentDirectory: File,
        val exitCode: Int,
        val cleared: Boolean = false
)

private data class ShellSession(
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader
)

class PersistentTerminalSession(private val homeDirectory: File) {

    private val sessionMutex = Mutex()
    private var shellSession: ShellSession? = null
    private var lastKnownDirectory: File = homeDirectory.canonicalOrSelf()

    suspend fun execute(
            command: String,
            timeoutSeconds: Int = 30,
            onOutputLine: (String) -> Unit = {}
    ): TerminalCommandResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext TerminalCommandResult("", lastKnownDirectory, 0)
        }
        if (trimmed == "clear") {
            return@withContext TerminalCommandResult("", lastKnownDirectory, 0, cleared = true)
        }

        sessionMutex.withLock {
            val session = ensureShellSession()
                    ?: return@withLock TerminalCommandResult(
                            output = "Failed to start local shell session.",
                            currentDirectory = lastKnownDirectory,
                            exitCode = 127
                    )

            runWrappedCommand(
                    session = session,
                    command = trimmed,
                    timeoutSeconds = timeoutSeconds,
                    onOutputLine = onOutputLine
            )
        }
    }

    suspend fun changeDirectory(
            targetDirectory: File,
            timeoutSeconds: Int = 10
    ): TerminalCommandResult = withContext(Dispatchers.IO) {
        val canonicalTarget = targetDirectory.canonicalOrSelf()
        if (!canonicalTarget.exists()) {
            return@withContext TerminalCommandResult(
                    output = "cd: no such directory: ${canonicalTarget.absolutePath}",
                    currentDirectory = lastKnownDirectory,
                    exitCode = 1
            )
        }
        if (!canonicalTarget.isDirectory) {
            return@withContext TerminalCommandResult(
                    output = "cd: not a directory: ${canonicalTarget.absolutePath}",
                    currentDirectory = lastKnownDirectory,
                    exitCode = 1
            )
        }

        execute(
                command = "cd ${canonicalTarget.absolutePath.shellQuoted()}",
                timeoutSeconds = timeoutSeconds
        )
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            destroySession()
        }
    }

    private suspend fun runWrappedCommand(
            session: ShellSession,
            command: String,
            timeoutSeconds: Int,
            onOutputLine: (String) -> Unit
    ): TerminalCommandResult {
        val marker = UUID.randomUUID().toString()

        return try {
            writeCommandEnvelope(session.writer, command, marker)

            val output = StringBuilder()
            var metaStarted = false
            var exitCodeLine: String? = null
            var currentDirectoryLine: String? = null

            withTimeout(timeoutSeconds * 1_000L) {
                while (true) {
                    val line = session.reader.readLine()
                            ?: throw EOFException("Local shell session closed unexpectedly.")

                    if (!metaStarted) {
                        if (line == metaBeginMarker(marker)) {
                            metaStarted = true
                            continue
                        }

                        if (output.isNotEmpty()) {
                            output.append('\n')
                        }
                        output.append(line)
                        onOutputLine(line)
                        continue
                    }

                    if (exitCodeLine == null) {
                        exitCodeLine = line
                        continue
                    }

                    if (currentDirectoryLine == null) {
                        currentDirectoryLine = line
                        continue
                    }

                    if (line == metaEndMarker(marker)) {
                        break
                    }
                }
            }

            val resolvedDirectory = currentDirectoryLine
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.canonicalOrSelf()
                    ?: lastKnownDirectory

            lastKnownDirectory = resolvedDirectory

            TerminalCommandResult(
                    output = output.toString(),
                    currentDirectory = resolvedDirectory,
                    exitCode = exitCodeLine?.toIntOrNull() ?: 1
            )
        } catch (exception: TimeoutCancellationException) {
            destroySession()
            TerminalCommandResult(
                    output = "Command timed out after ${timeoutSeconds}s. Shell session was reset.",
                    currentDirectory = lastKnownDirectory,
                    exitCode = 124
            )
        } catch (exception: Exception) {
            destroySession()
            TerminalCommandResult(
                    output = exception.message ?: "Shell session failed.",
                    currentDirectory = lastKnownDirectory,
                    exitCode = 127
            )
        }
    }

    private fun ensureShellSession(): ShellSession? {
        val existingSession = shellSession
        if (existingSession != null && existingSession.process.isAlive) {
            return existingSession
        }

        destroySession()

        return try {
            val workingDirectory = lastKnownDirectory.takeIf { it.exists() && it.isDirectory }
                    ?: homeDirectory.canonicalOrSelf()
            val process = ProcessBuilder(resolveShellPath())
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()

            val session = ShellSession(
                    process = process,
                    writer = BufferedWriter(OutputStreamWriter(process.outputStream)),
                    reader = BufferedReader(InputStreamReader(process.inputStream))
            )
            shellSession = session
            lastKnownDirectory = workingDirectory
            session
        } catch (_: Exception) {
            null
        }
    }

    private fun destroySession() {
        val session = shellSession ?: return

        runCatching { session.writer.write("exit\n") }
        runCatching { session.writer.flush() }
        runCatching { session.writer.close() }
        runCatching { session.reader.close() }
        runCatching { session.process.destroy() }
        runCatching { session.process.destroyForcibly() }

        shellSession = null
    }

    private fun writeCommandEnvelope(
            writer: BufferedWriter,
            command: String,
            marker: String
    ) {
        writer.write(command)
        writer.newLine()
        writer.write("__phoneserver_status=$?")
        writer.newLine()
        writer.write("printf '\\n%s\\n' '${metaBeginMarker(marker)}'")
        writer.newLine()
        writer.write("printf '%s\\n' \"\$__phoneserver_status\"")
        writer.newLine()
        writer.write("pwd")
        writer.newLine()
        writer.write("printf '%s\\n' '${metaEndMarker(marker)}'")
        writer.newLine()
        writer.flush()
    }

    private fun resolveShellPath(): String {
        val candidates = listOf("/system/bin/sh", "/system/xbin/sh", "sh")
        return candidates.firstOrNull { candidate -> candidate == "sh" || File(candidate).exists() }
                ?: "sh"
    }

    private fun metaBeginMarker(marker: String): String = "__PHONESERVER_META_BEGIN__${marker}"

    private fun metaEndMarker(marker: String): String = "__PHONESERVER_META_END__${marker}"

    private fun File.canonicalOrSelf(): File = runCatching { canonicalFile }.getOrElse { this }

    private fun String.shellQuoted(): String = "'" + replace("'", "'\"'\"'") + "'"
}
