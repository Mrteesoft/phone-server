package com.phoneserver.mobile.runtime

import java.io.File

class WorkspacePathMapper(
        workspaceHostRoot: File,
        private val ubuntuWorkspaceRoot: String = "/workspace",
        private val ubuntuHomePath: String = "/root"
) {

    private val canonicalWorkspaceHostRoot = workspaceHostRoot.canonicalOrSelf()

    fun toAndroidHostPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return canonicalWorkspaceHostRoot.absolutePath
        }

        if (trimmed == ubuntuHomePath || trimmed.startsWith("$ubuntuHomePath/")) {
            return canonicalWorkspaceHostRoot.absolutePath
        }

        if (trimmed == ubuntuWorkspaceRoot || trimmed.startsWith("$ubuntuWorkspaceRoot/")) {
            val relativePath = trimmed.removePrefix(ubuntuWorkspaceRoot).trimStart('/')
            return if (relativePath.isEmpty()) {
                canonicalWorkspaceHostRoot.absolutePath
            } else {
                File(
                        canonicalWorkspaceHostRoot,
                        relativePath.replace('/', File.separatorChar)
                ).absolutePath
            }
        }

        return File(trimmed).canonicalOrSelf().absolutePath
    }

    fun toUbuntuPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return ubuntuHomePath
        }

        if (trimmed == ubuntuHomePath || trimmed.startsWith("$ubuntuHomePath/") ||
                trimmed == ubuntuWorkspaceRoot || trimmed.startsWith("$ubuntuWorkspaceRoot/")) {
            return trimmed
        }

        val candidate = File(trimmed).canonicalOrSelf()
        val workspaceRootPath = canonicalWorkspaceHostRoot.toPath().normalize()
        val targetPath = candidate.toPath().normalize()
        if (targetPath.startsWith(workspaceRootPath)) {
            val relativePath = workspaceRootPath.relativize(targetPath)
                    .joinToString(separator = "/") { segment -> segment.toString() }
            return if (relativePath.isEmpty()) {
                ubuntuWorkspaceRoot
            } else {
                "$ubuntuWorkspaceRoot/$relativePath"
            }
        }

        return ubuntuHomePath
    }

    fun toRuntimePath(
            kind: TerminalBackendKind,
            hostPath: String
    ): String {
        return when (kind) {
            TerminalBackendKind.ANDROID_LOCAL -> toAndroidHostPath(hostPath)
            TerminalBackendKind.UBUNTU_2204 -> toUbuntuPath(hostPath)
        }
    }

    fun translateDisplayedPath(
            path: String,
            sourceKind: TerminalBackendKind,
            targetKind: TerminalBackendKind
    ): String {
        if (sourceKind == targetKind) {
            return toRuntimePath(targetKind, path)
        }

        val hostPath = when (sourceKind) {
            TerminalBackendKind.ANDROID_LOCAL -> toAndroidHostPath(path)
            TerminalBackendKind.UBUNTU_2204 -> toAndroidHostPath(path)
        }

        return toRuntimePath(targetKind, hostPath)
    }

    private fun File.canonicalOrSelf(): File = runCatching { canonicalFile }.getOrElse { this }
}
