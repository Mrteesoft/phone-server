package com.phoneserver.mobile.runtime

import android.system.Os
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class UbuntuRuntimeLayout(
        val runtimeRoot: File,
        val rootfsDirectory: File,
        val persistentHomeDirectory: File,
        val cacheDirectory: File,
        val workspaceMountDirectory: File,
        val prootRuntimeDirectory: File,
        val commandShimDirectory: File,
        val diagnosticsDirectory: File,
        val launcherScript: File,
        val runtimeNote: File,
        val bootLogFile: File,
        val guestHomePath: String = "/root",
        val guestWorkspacePath: String = "/workspace",
        val guestCommandShimPath: String = "/phoneserver/bin",
        val defaultUsername: String = "root",
        val guestHostname: String = "phone"
)

internal data class UbuntuBootVerification(
        val success: Boolean,
        val detail: String,
        val errorMessage: String? = null
)

internal class UbuntuRuntimeBootstrap(
        private val layout: UbuntuRuntimeLayout
) {

    fun ensureHostDirectories() {
        layout.runtimeRoot.mkdirs()
        layout.rootfsDirectory.mkdirs()
        layout.persistentHomeDirectory.mkdirs()
        layout.cacheDirectory.mkdirs()
        layout.workspaceMountDirectory.mkdirs()
        layout.prootRuntimeDirectory.mkdirs()
        layout.commandShimDirectory.mkdirs()
        layout.diagnosticsDirectory.mkdirs()
    }

    fun writePreparationFiles(
            source: UbuntuRootfsSource,
            runtime: BundledProotRuntime?
    ) {
        ensureHostDirectories()
        if (rootfsLooksInstalled()) {
            ensureGuestRuntimeDirectories()
            ensureGuestNetworkConfiguration()
            ensureGuestSystemIdentity()
            writePersistentHomeProfile()
        }

        writeCommandShims()
        writeLauncher(runtime)
        writeRuntimeNote(source, runtime)
    }

    fun verifyBoot(
            runtime: BundledProotRuntime?
    ): UbuntuBootVerification {
        if (!rootfsLooksInstalled()) {
            return UbuntuBootVerification(
                    success = false,
                    detail = "Ubuntu rootfs is missing from ${layout.rootfsDirectory.absolutePath}.",
                    errorMessage = "Ubuntu rootfs is missing."
            )
        }
        if (runtime == null) {
            return UbuntuBootVerification(
                    success = false,
                    detail = "Bundled proot assets are unavailable, so the Ubuntu shell cannot boot yet.",
                    errorMessage = "Bundled proot assets were not installed."
            )
        }
        if (!layout.launcherScript.exists()) {
            return UbuntuBootVerification(
                    success = false,
                    detail = "Ubuntu launcher script is missing from ${layout.launcherScript.absolutePath}.",
                    errorMessage = "Launcher script was not generated."
            )
        }

        trimBootLogIfNeeded()
        appendBootLog("Starting Ubuntu boot verification.")
        val process = ProcessBuilder(
                resolveHostShellPath(),
                layout.launcherScript.absolutePath,
                "/bin/bash",
                "--login",
                "-lc",
                buildVerificationCommand()
        ).directory(layout.runtimeRoot)
                .redirectErrorStream(true)
                .start()

        val finished = process.waitFor(20, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            process.destroyForcibly()
            appendBootLog("Boot verification timed out after 20 seconds.")
            return UbuntuBootVerification(
                    success = false,
                    detail = "Ubuntu boot verification timed out. See ${layout.bootLogFile.absolutePath} for diagnostics.",
                    errorMessage = "Ubuntu boot verification timed out."
            )
        }

        val output = process.inputStream.bufferedReader().use { reader ->
            reader.readText().trim()
        }

        val exitCode = process.exitValue()
        appendBootLog("Boot verification exit code: $exitCode")
        if (output.isNotBlank()) {
            appendBootLog("Boot verification output:\n$output")
        }

        if (exitCode != 0) {
            val errorLine = output.lineSequence().firstOrNull { it.isNotBlank() }
                    ?: "Ubuntu boot verification exited with $exitCode."
            return UbuntuBootVerification(
                    success = false,
                    detail = "Ubuntu rootfs is installed, but the launcher boot check failed. See ${layout.bootLogFile.absolutePath} for diagnostics.",
                    errorMessage = errorLine
            )
        }

        val values = output.lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        line.substring(0, separator) to line.substring(separator + 1)
                    }
                }
                .toMap()

        val user = values["USER"].orEmpty()
        val home = values["HOME"].orEmpty()
        val pwd = values["PWD"].orEmpty()
        val tmpWritable = values["TMP_OK"] == "yes"
        val varTmpWritable = values["VAR_TMP_OK"] == "yes"
        val aptAvailable = values["APT_OK"] == "yes"
        val sudoAvailable = values["SUDO_OK"] == "yes"

        if (user.isBlank() ||
                home.isBlank() ||
                pwd.isBlank() ||
                !tmpWritable ||
                !varTmpWritable ||
                !aptAvailable ||
                !sudoAvailable) {
            return UbuntuBootVerification(
                    success = false,
                    detail = "Ubuntu booted, but the runtime check found an incomplete environment. See ${layout.bootLogFile.absolutePath} for diagnostics.",
                    errorMessage = "Unexpected verification output: ${output.ifBlank { "empty output" }}"
            )
        }

        return UbuntuBootVerification(
                success = true,
                detail = "Ubuntu boot verified with /bin/bash --login. User=$user, HOME=$home, pwd=$pwd, /tmp and /var/tmp are writable. Diagnostics: ${layout.bootLogFile.absolutePath}"
        )
    }

    private fun rootfsLooksInstalled(): Boolean {
        return File(layout.rootfsDirectory, "usr").exists() &&
                File(layout.rootfsDirectory, "etc").exists() &&
                File(layout.rootfsDirectory, "bin").exists()
    }

    private fun ensureGuestRuntimeDirectories() {
        listOf(
                File(layout.rootfsDirectory, "tmp"),
                File(layout.rootfsDirectory, "var/tmp"),
                File(layout.rootfsDirectory, "workspace"),
                File(layout.rootfsDirectory, "phoneserver/bin"),
                File(layout.rootfsDirectory, "var/lib/apt/lists/partial"),
                File(layout.rootfsDirectory, "var/cache/apt/archives/partial"),
                File(layout.rootfsDirectory, layout.guestHomePath.removePrefix("/"))
        ).forEach { directory ->
            directory.mkdirs()
        }

        chmod(File(layout.rootfsDirectory, "tmp"), 0x3FF)
        chmod(File(layout.rootfsDirectory, "var/tmp"), 0x3FF)
        chmod(layout.persistentHomeDirectory, 0x1C0)
    }

    private fun ensureGuestNetworkConfiguration() {
        val etcDirectory = File(layout.rootfsDirectory, "etc").apply { mkdirs() }
        val resolvConf = File(etcDirectory, "resolv.conf")
        if (resolvConf.isDirectory) {
            resolvConf.deleteRecursively()
        }
        if (resolvConf.exists() && resolvConf.isFile) {
            resolvConf.delete()
        }
        resolvConf.writeText(
                buildString {
                    appendLine("nameserver 1.1.1.1")
                    appendLine("nameserver 8.8.8.8")
                }
        )
    }

    private fun ensureGuestSystemIdentity() {
        val etcDirectory = File(layout.rootfsDirectory, "etc").apply { mkdirs() }
        File(etcDirectory, "hostname").writeText("${layout.guestHostname}\n")
        File(etcDirectory, "hosts").writeText(
                buildString {
                    appendLine("127.0.0.1 localhost")
                    appendLine("127.0.1.1 ${layout.guestHostname}")
                    appendLine("::1 localhost ip6-localhost ip6-loopback")
                }
        )
    }

    private fun writePersistentHomeProfile() {
        layout.persistentHomeDirectory.mkdirs()

        File(layout.persistentHomeDirectory, ".bash_profile").writeText(
                buildString {
                    appendLine("# Phone Server Ubuntu login profile")
                    appendLine("if [ -f \"${'$'}HOME/.profile\" ]; then")
                    appendLine("  . \"${'$'}HOME/.profile\"")
                    appendLine("fi")
                }
        )

        File(layout.persistentHomeDirectory, ".profile").writeText(
                buildString {
                    appendLine("# Phone Server Ubuntu profile")
                    appendLine("export HOME=${layout.guestHomePath}")
                    appendLine("export USER=${layout.defaultUsername}")
                    appendLine("export LOGNAME=${layout.defaultUsername}")
                    appendLine("export SHELL=/bin/bash")
                    appendLine("export TERM=${'$'}{TERM:-xterm-256color}")
                    appendLine("export LANG=${'$'}{LANG:-C.UTF-8}")
                    appendLine("export LC_ALL=${'$'}{LC_ALL:-C.UTF-8}")
                    appendLine("export PATH=${layout.guestCommandShimPath}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                    appendLine("if [ -n \"${'$'}BASH_VERSION\" ] && [ -f \"${'$'}HOME/.bashrc\" ]; then")
                    appendLine("  . \"${'$'}HOME/.bashrc\"")
                    appendLine("fi")
                }
        )

        File(layout.persistentHomeDirectory, ".bashrc").writeText(
                buildString {
                    appendLine("# Phone Server Ubuntu shell customizations")
                    appendLine("export HISTFILE=\"${'$'}HOME/.bash_history\"")
                    appendLine("export HISTSIZE=2000")
                    appendLine("export HISTCONTROL=ignoredups")
                    appendLine("export PS1='${layout.defaultUsername}@${layout.guestHostname}:\\w\\$ '")
                    appendLine("alias ll='ls -alF'")
                    appendLine("alias la='ls -A'")
                    appendLine("alias l='ls -CF'")
                }
        )
    }

    private fun writeCommandShims() {
        layout.commandShimDirectory.mkdirs()

        val sudoShim = File(layout.commandShimDirectory, "sudo")
        sudoShim.writeText(
                buildString {
                    appendLine("#!/bin/sh")
                    appendLine("if [ \"$#\" -eq 0 ]; then")
                    appendLine("  echo \"usage: sudo <command> [args...]\"")
                    appendLine("  exit 1")
                    appendLine("fi")
                    appendLine("exec \"$@\"")
                }
        )
        sudoShim.setExecutable(true, true)
        sudoShim.setReadable(true, true)
    }

    private fun writeLauncher(runtime: BundledProotRuntime?) {
        val launcherContents = if (runtime != null && rootfsLooksInstalled()) {
            buildLauncherScript(runtime)
        } else {
            buildString {
                appendLine("#!/system/bin/sh")
                appendLine("echo \"Ubuntu runtime cannot launch yet. Rootfs or bundled proot assets are missing.\" >&2")
                appendLine("exit 127")
            }
        }

        layout.launcherScript.writeText(launcherContents)
        layout.launcherScript.setExecutable(true, true)
    }

    private fun writeRuntimeNote(
            source: UbuntuRootfsSource,
            runtime: BundledProotRuntime?
    ) {
        layout.runtimeNote.writeText(
                buildString {
                    appendLine("Phone Server Ubuntu runtime note")
                    appendLine("Generated at: ${Instant.now()}")
                    appendLine("Release: ${source.releaseLabel}")
                    appendLine("Image source: ${source.downloadUrl}")
                    appendLine("Source page: ${source.sourcePageUrl}")
                    appendLine("Expected SHA256: ${source.sha256}")
                    appendLine("Rootfs path: ${layout.rootfsDirectory.absolutePath}")
                    appendLine("Persistent home bind: ${layout.persistentHomeDirectory.absolutePath} -> ${layout.guestHomePath}")
                    appendLine("Workspace bind: ${layout.workspaceMountDirectory.absolutePath} -> ${layout.guestWorkspacePath}")
                    appendLine("Command shim bind: ${layout.commandShimDirectory.absolutePath} -> ${layout.guestCommandShimPath}")
                    appendLine("Launcher path: ${layout.launcherScript.absolutePath}")
                    appendLine("Diagnostics log: ${layout.bootLogFile.absolutePath}")
                    appendLine("Default login shell: /bin/bash --login")
                    appendLine(
                            if (runtime != null) {
                                "Bundled proot asset ABI: ${runtime.assetAbi}"
                            } else {
                                "Bundled proot assets were not found in app storage."
                            }
                    )
                }
        )
    }

    private fun buildLauncherScript(runtime: BundledProotRuntime): String {
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set -eu")
            appendLine("PROOT_BIN=${runtime.prootBinary.absolutePath.shellQuoted()}")
            appendLine("PROOT_LOADER_PATH=${runtime.loaderBinary.absolutePath.shellQuoted()}")
            appendLine("PROOT_LOADER32_PATH=${runtime.loader32Binary?.absolutePath.orEmpty().shellQuoted()}")
            appendLine("PROOT_LIB_DIR=${runtime.libraryDirectory.absolutePath.shellQuoted()}")
            appendLine("PROOT_TMP_DIR_PATH=${runtime.tempDirectory.absolutePath.shellQuoted()}")
            appendLine("HOST_LINKER=${runtime.hostLinkerPath.shellQuoted()}")
            appendLine("ROOTFS=${layout.rootfsDirectory.absolutePath.shellQuoted()}")
            appendLine("HOME_BIND=${layout.persistentHomeDirectory.absolutePath.shellQuoted()}")
            appendLine("WORKSPACE_BIND=${layout.workspaceMountDirectory.absolutePath.shellQuoted()}")
            appendLine("SHIM_BIND=${layout.commandShimDirectory.absolutePath.shellQuoted()}")
            appendLine("BOOT_LOG=${layout.bootLogFile.absolutePath.shellQuoted()}")
            appendLine("GUEST_HOME=${layout.guestHomePath.shellQuoted()}")
            appendLine("GUEST_WORKSPACE=${layout.guestWorkspacePath.shellQuoted()}")
            appendLine("GUEST_SHIMS=${layout.guestCommandShimPath.shellQuoted()}")
            appendLine("DEFAULT_USER=${layout.defaultUsername.shellQuoted()}")
            appendLine("mkdir -p \"${'$'}HOME_BIND\" \"${'$'}WORKSPACE_BIND\" \"${'$'}SHIM_BIND\" \"${'$'}PROOT_TMP_DIR_PATH\" \"$(dirname \"${'$'}BOOT_LOG\")\"")
            appendLine("mkdir -p \"${'$'}ROOTFS/tmp\" \"${'$'}ROOTFS/var/tmp\" \"${'$'}ROOTFS/workspace\" \"${'$'}ROOTFS/phoneserver/bin\"")
            appendLine("chmod 1777 \"${'$'}ROOTFS/tmp\" \"${'$'}ROOTFS/var/tmp\" 2>/dev/null || true")
            appendLine("touch \"${'$'}BOOT_LOG\" 2>/dev/null || true")
            appendLine("printf '%s launcher start rootfs=%s home=%s workspace=%s argc=%s\\n' \"$(date '+%Y-%m-%dT%H:%M:%S%z')\" \"${'$'}ROOTFS\" \"${'$'}GUEST_HOME\" \"${'$'}GUEST_WORKSPACE\" \"${'$'}#\" >> \"${'$'}BOOT_LOG\" 2>/dev/null || true")
            appendLine("export PROOT_TMP_DIR=\"${'$'}PROOT_TMP_DIR_PATH\"")
            appendLine("export PROOT_TMPDIR=\"${'$'}PROOT_TMP_DIR_PATH\"")
            appendLine("export TMPDIR=\"${'$'}PROOT_TMP_DIR_PATH\"")
            appendLine("export PROOT_LOADER=\"${'$'}PROOT_LOADER_PATH\"")
            appendLine("if [ -n \"${'$'}PROOT_LOADER32_PATH\" ]; then export PROOT_LOADER32=\"${'$'}PROOT_LOADER32_PATH\"; fi")
            appendLine("export TERM=\"${'$'}{TERM:-xterm-256color}\"")
            appendLine("export LANG=\"${'$'}{LANG:-C.UTF-8}\"")
            appendLine("export LC_ALL=\"${'$'}{LC_ALL:-C.UTF-8}\"")
            appendLine("exec_proot() {")
            appendLine("  exec \"${'$'}HOST_LINKER\" --library-path \"${'$'}PROOT_LIB_DIR\" \"${'$'}PROOT_BIN\" \\")
            appendLine("    --kill-on-exit \\")
            appendLine("    -0 \\")
            appendLine("    -r \"${'$'}ROOTFS\" \\")
            appendLine("    -b /dev:/dev \\")
            appendLine("    -b /proc:/proc \\")
            appendLine("    -b /sys:/sys \\")
            appendLine("    -b \"${'$'}HOME_BIND:${'$'}GUEST_HOME\" \\")
            appendLine("    -b \"${'$'}WORKSPACE_BIND:${'$'}GUEST_WORKSPACE\" \\")
            appendLine("    -b \"${'$'}SHIM_BIND:${'$'}GUEST_SHIMS\" \\")
            appendLine("    -w \"${'$'}GUEST_HOME\" \\")
            appendLine("    $(if [ -d /system ]; then printf '%s' '-b /system:/system '; fi)\\")
            appendLine("    $(if [ -d /vendor ]; then printf '%s' '-b /vendor:/vendor '; fi)\\")
            appendLine("    $(if [ -d /apex ]; then printf '%s' '-b /apex:/apex '; fi)\\")
            appendLine("    $(if [ -d /storage ]; then printf '%s' '-b /storage:/storage '; fi)\\")
            appendLine("    $(if [ -f /property_contexts ]; then printf '%s' '-b /property_contexts:/property_contexts '; fi)\\")
            appendLine("    $(if [ -e /linkerconfig/ld.config.txt ]; then printf '%s' '-b /linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt '; fi)\\")
            appendLine("    /usr/bin/env -i HOME=\"${'$'}GUEST_HOME\" USER=\"${'$'}DEFAULT_USER\" LOGNAME=\"${'$'}DEFAULT_USER\" SHELL=/bin/bash TERM=\"${'$'}TERM\" LANG=\"${'$'}LANG\" LC_ALL=\"${'$'}LC_ALL\" PATH=\"${layout.guestCommandShimPath}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\" \"${'$'}@\"")
            appendLine("}")
            appendLine("if [ \"${'$'}#\" -gt 0 ]; then")
            appendLine("  exec_proot \"${'$'}@\"")
            appendLine("fi")
            appendLine("exec_proot /bin/bash --login")
        }
    }

    private fun buildVerificationCommand(): String {
        return buildString {
            append("test -x /bin/bash && ")
            append("test -w /tmp && ")
            append("test -w /var/tmp && ")
            append("command -v apt >/dev/null 2>&1 && ")
            append("command -v sudo >/dev/null 2>&1 && ")
            append("printf 'USER=%s\\nHOME=%s\\nPWD=%s\\nTMP_OK=yes\\nVAR_TMP_OK=yes\\nAPT_OK=yes\\nSUDO_OK=yes\\n' ")
            append("\"$(whoami 2>/dev/null || echo unknown)\" \"${'$'}HOME\" \"$(pwd)\"")
        }
    }

    private fun trimBootLogIfNeeded() {
        val file = layout.bootLogFile
        if (!file.exists() || file.length() <= MAX_BOOT_LOG_BYTES) {
            return
        }
        file.writeText(file.readText().takeLast(MAX_BOOT_LOG_BYTES.toInt() / 2))
    }

    private fun appendBootLog(message: String) {
        layout.bootLogFile.parentFile?.mkdirs()
        layout.bootLogFile.appendText("[${Instant.now()}] $message\n")
    }

    private fun chmod(target: File, mode: Int) {
        runCatching { Os.chmod(target.absolutePath, mode) }
                .onFailure {
                    if (mode == 0x1C0) {
                        target.setReadable(true, true)
                        target.setWritable(true, true)
                        target.setExecutable(true, true)
                    } else {
                        target.setReadable(true, false)
                        target.setWritable(true, false)
                        target.setExecutable(true, false)
                    }
                }
    }

    private fun resolveHostShellPath(): String {
        val candidates = listOf("/system/bin/sh", "/system/xbin/sh", "sh")
        return candidates.firstOrNull { candidate -> candidate == "sh" || File(candidate).exists() }
                ?: "sh"
    }

    private fun String.shellQuoted(): String = "'" + replace("'", "'\"'\"'") + "'"

    private companion object {
        const val MAX_BOOT_LOG_BYTES = 512 * 1024L
    }
}
