package com.phoneserver.mobile.runtime

import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class PtyLaunchConfiguration(
        val argv: List<String>,
        val environment: Map<String, String>,
        val workingDirectory: File
)

private data class PtyShellSession(
        val process: PtyProcessHandle,
        val descriptor: ParcelFileDescriptor,
        val writer: BufferedWriter,
        val reader: BufferedReader
)

internal class PtyTerminalSession(
        private val homeDirectory: File,
        private val launchConfigurationProvider: () -> PtyLaunchConfiguration
) {

    private val sessionMutex = Mutex()
    private var shellSession: PtyShellSession? = null
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
                            output = "Failed to start PTY shell session.",
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
            session: PtyShellSession,
            command: String,
            timeoutSeconds: Int,
            onOutputLine: (String) -> Unit
    ): TerminalCommandResult {
        val marker = UUID.randomUUID().toString()
        val echoedLines = ArrayDeque(buildEnvelopeLines(command, marker))

        return try {
            writeCommandEnvelope(session.writer, echoedLines)

            val output = StringBuilder()
            var metaStarted = false
            var exitCodeLine: String? = null
            var currentDirectoryLine: String? = null

            withTimeout(timeoutSeconds * 1_000L) {
                while (true) {
                    val line = session.reader.readLine()
                            ?: throw EOFException("PTY shell session closed unexpectedly.")

                    if (!metaStarted) {
                        if (echoedLines.isNotEmpty() && line == echoedLines.first()) {
                            echoedLines.removeFirst()
                            continue
                        }

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
                    output = "Command timed out after ${timeoutSeconds}s. PTY session was reset.",
                    currentDirectory = lastKnownDirectory,
                    exitCode = 124
            )
        } catch (exception: Exception) {
            destroySession()
            TerminalCommandResult(
                    output = exception.message ?: "PTY shell session failed.",
                    currentDirectory = lastKnownDirectory,
                    exitCode = 127
            )
        }
    }

    private fun ensureShellSession(): PtyShellSession? {
        val existingSession = shellSession
        if (existingSession != null) {
            return existingSession
        }

        destroySession()

        return try {
            val configuration = launchConfigurationProvider()
            val workingDirectory = configuration.workingDirectory
                    .takeIf { it.exists() && it.isDirectory }
                    ?.canonicalOrSelf()
                    ?: homeDirectory.canonicalOrSelf()

            val process = PtyBridge.start(
                    argv = configuration.argv,
                    environment = configuration.environment,
                    workingDirectory = workingDirectory.absolutePath
            )
            val descriptor = ParcelFileDescriptor.adoptFd(process.masterFd)
            val inputStream = FileInputStream(descriptor.fileDescriptor)
            val outputStream = FileOutputStream(descriptor.fileDescriptor)

            val session = PtyShellSession(
                    process = process,
                    descriptor = descriptor,
                    writer = BufferedWriter(OutputStreamWriter(outputStream)),
                    reader = BufferedReader(InputStreamReader(inputStream))
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
        runCatching { session.descriptor.close() }
        runCatching { PtyBridge.signal(session.process.pid, 15) }
        runCatching { PtyBridge.signal(session.process.pid, 9) }
        runCatching { PtyBridge.waitFor(session.process.pid) }

        shellSession = null
    }

    private fun buildEnvelopeLines(
            command: String,
            marker: String
    ): List<String> {
        return listOf(
                command,
                "__phoneserver_status=$?",
                "printf '\\n%s\\n' '${metaBeginMarker(marker)}'",
                "printf '%s\\n' \"\$__phoneserver_status\"",
                "pwd",
                "printf '%s\\n' '${metaEndMarker(marker)}'"
        )
    }

    private fun writeCommandEnvelope(
            writer: BufferedWriter,
            envelopeLines: Iterable<String>
    ) {
        envelopeLines.forEach { line ->
            writer.write(line)
            writer.newLine()
        }
        writer.flush()
    }

    private fun metaBeginMarker(marker: String): String = "__PHONESERVER_META_BEGIN__${marker}"

    private fun metaEndMarker(marker: String): String = "__PHONESERVER_META_END__${marker}"

    private fun File.canonicalOrSelf(): File = runCatching { canonicalFile }.getOrElse { this }

    private fun String.shellQuoted(): String = "'" + replace("'", "'\"'\"'") + "'"
}
