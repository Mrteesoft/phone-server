package com.phoneserver.mobile.runtime

import android.content.Context
import android.os.Build
import java.io.File

internal data class BundledProotRuntime(
        val assetAbi: String,
        val installRoot: File,
        val prootBinary: File,
        val loaderBinary: File,
        val loader32Binary: File?,
        val libraryDirectory: File,
        val tempDirectory: File
)

internal class BundledProotInstaller(
        context: Context,
        private val installRoot: File
) {

    private val appContext = context.applicationContext

    fun install(): BundledProotRuntime? {
        val assetAbi = resolveAssetAbi() ?: return null
        val assetPrefix = "runtime/proot/$assetAbi"

        if (!assetExists("$assetPrefix/proot") || !assetExists("$assetPrefix/loader")) {
            return null
        }

        val binDirectory = File(installRoot, "bin").apply { mkdirs() }
        val libDirectory = File(installRoot, "lib").apply { mkdirs() }
        val tmpDirectory = File(installRoot, "tmp").apply { mkdirs() }

        val prootBinary = File(binDirectory, "proot")
        val loaderBinary = File(binDirectory, "loader")
        val loader32Binary = File(binDirectory, "loader32").takeIf { assetExists("$assetPrefix/loader32") }
        val tallocLibrary = File(libDirectory, "libtalloc.so.2")
        val metadataFile = File(installRoot, "metadata.json")

        copyAsset(
                assetPath = "$assetPrefix/proot",
                target = prootBinary,
                transform = ::patchProotBinary
        )
        copyAsset("$assetPrefix/loader", loaderBinary)
        loader32Binary?.let { copyAsset("$assetPrefix/loader32", it) }
        copyAsset("$assetPrefix/lib/libtalloc.so.2", tallocLibrary)
        if (assetExists("$assetPrefix/metadata.json")) {
            copyAsset("$assetPrefix/metadata.json", metadataFile)
        }

        prootBinary.setExecutable(true, true)
        loaderBinary.setExecutable(true, true)
        loader32Binary?.setExecutable(true, true)
        tallocLibrary.setReadable(true, true)

        return BundledProotRuntime(
                assetAbi = assetAbi,
                installRoot = installRoot,
                prootBinary = prootBinary,
                loaderBinary = loaderBinary,
                loader32Binary = loader32Binary,
                libraryDirectory = libDirectory,
                tempDirectory = tmpDirectory
        )
    }

    private fun copyAsset(
            assetPath: String,
            target: File,
            transform: ((ByteArray) -> ByteArray)? = null
    ) {
        target.parentFile?.mkdirs()
        appContext.assets.open(assetPath).use { input ->
            val payload = input.readBytes()
            val output = transform?.invoke(payload) ?: payload
            target.outputStream().use { stream ->
                stream.write(output)
                stream.fd.sync()
            }
        }
    }

    private fun patchProotBinary(bytes: ByteArray): ByteArray {
        val patched = bytes.copyOf()
        val replacements = listOf(
                "/data/data/com.termux/files/usr/libexec/proot/loader32" to "\$ORIGIN/loader32",
                "/data/data/com.termux/files/usr/libexec/proot/loader" to "\$ORIGIN/loader",
                "/data/data/com.termux/files/usr/tmp/" to "\$ORIGIN/../tmp/",
                "/data/data/com.termux/files/usr/lib" to "\$ORIGIN/../lib"
        )

        replacements.forEach { (original, replacement) ->
            require(replacement.length <= original.length) {
                "Replacement path is longer than the original embedded path."
            }

            var index = indexOf(patched, original.encodeToByteArray())
            while (index >= 0) {
                original.indices.forEach { offset ->
                    patched[index + offset] = 0
                }
                replacement.encodeToByteArray().copyInto(
                        destination = patched,
                        destinationOffset = index
                )
                index = indexOf(
                        source = patched,
                        needle = original.encodeToByteArray(),
                        startIndex = index + original.length
                )
            }
        }

        return patched
    }

    private fun indexOf(
            source: ByteArray,
            needle: ByteArray,
            startIndex: Int = 0
    ): Int {
        if (needle.isEmpty() || source.size < needle.size) {
            return -1
        }

        for (index in startIndex..(source.size - needle.size)) {
            var matched = true
            for (offset in needle.indices) {
                if (source[index + offset] != needle[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return index
            }
        }

        return -1
    }

    private fun assetExists(assetPath: String): Boolean {
        return runCatching {
            appContext.assets.open(assetPath).use { }
            true
        }.getOrDefault(false)
    }

    private fun resolveAssetAbi(): String? {
        val supported = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        return Build.SUPPORTED_ABIS.firstOrNull { it in supported }
    }
}
