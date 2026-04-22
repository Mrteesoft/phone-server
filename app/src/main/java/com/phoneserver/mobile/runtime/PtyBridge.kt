package com.phoneserver.mobile.runtime

import java.io.IOException

internal data class PtyProcessHandle(
        val pid: Int,
        val masterFd: Int
)

internal object PtyBridge {

    init {
        System.loadLibrary("phoneserverpty")
    }

    fun start(
            argv: List<String>,
            environment: Map<String, String>,
            workingDirectory: String?,
            columns: Int = 120,
            rows: Int = 40
    ): PtyProcessHandle {
        val result = nativeStart(
                argv = argv.toTypedArray(),
                environment = environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
                workingDirectory = workingDirectory,
                columns = columns,
                rows = rows
        )
        if (result.size < 2) {
            throw IOException("Failed to start PTY process.")
        }
        return PtyProcessHandle(
                pid = result[0],
                masterFd = result[1]
        )
    }

    fun waitFor(pid: Int): Int = nativeWaitFor(pid)

    fun signal(pid: Int, signal: Int): Boolean = nativeSignal(pid, signal)

    fun resize(masterFd: Int, columns: Int, rows: Int): Boolean {
        return nativeResize(
                masterFd = masterFd,
                columns = columns,
                rows = rows
        )
    }

    private external fun nativeStart(
            argv: Array<String>,
            environment: Array<String>,
            workingDirectory: String?,
            columns: Int,
            rows: Int
    ): IntArray

    private external fun nativeWaitFor(pid: Int): Int

    private external fun nativeSignal(pid: Int, signal: Int): Boolean

    private external fun nativeResize(
            masterFd: Int,
            columns: Int,
            rows: Int
    ): Boolean
}
