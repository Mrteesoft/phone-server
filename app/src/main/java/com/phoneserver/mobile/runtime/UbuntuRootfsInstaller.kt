package com.phoneserver.mobile.runtime

import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

class UbuntuRootfsInstaller(
        private val coordinator: UbuntuRuntimeCoordinator
) {

    private data class PendingHardLink(
            val target: File,
            val source: File,
            val mode: Int
    )

    suspend fun install(
            onSnapshotChanged: suspend (UbuntuRuntimeSnapshot) -> Unit
    ): UbuntuRuntimeSnapshot = withContext(Dispatchers.IO) {
        val prepared = coordinator.prepareScaffold()
        onSnapshotChanged(prepared)

        val source = coordinator.requireRootfsSource()
        val archiveFile = coordinator.archiveFileFor(source)

        if (!hasVerifiedArchive(archiveFile, source.sha256)) {
            val downloadStarted = coordinator.markDownloadStarting(source)
            onSnapshotChanged(downloadStarted)
            downloadArchive(source, archiveFile) { downloadedBytes, totalBytes ->
                onSnapshotChanged(
                        coordinator.updateDownloadProgress(
                                source = source,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                        )
                )
            }
        } else {
            onSnapshotChanged(
                    coordinator.updateDownloadProgress(
                            source = source,
                            downloadedBytes = archiveFile.length(),
                            totalBytes = archiveFile.length()
                    )
            )
        }

        verifyArchiveSha256(archiveFile, source.sha256)

        val extracting = coordinator.markExtracting(source, archiveFile.length())
        onSnapshotChanged(extracting)

        coordinator.resetRootfsDirectory()
        extractArchive(archiveFile, coordinator.rootfsDirectory()) { extractedEntries ->
            if (extractedEntries % EXTRACTION_PROGRESS_STEP == 0) {
                onSnapshotChanged(
                        coordinator.updateExtractionProgress(
                                source = source,
                                extractedEntries = extractedEntries,
                                archiveBytes = archiveFile.length()
                        )
                )
            }
        }

        val ready = coordinator.markReady(source)
        onSnapshotChanged(ready)
        ready
    }

    private suspend fun hasVerifiedArchive(
            archiveFile: File,
            expectedSha256: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!archiveFile.exists() || !archiveFile.isFile) {
            return@withContext false
        }

        try {
            calculateSha256(archiveFile) == expectedSha256.lowercase()
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun downloadArchive(
            source: UbuntuRootfsSource,
            archiveFile: File,
            onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        archiveFile.parentFile?.mkdirs()
        val tempFile = File(archiveFile.parentFile, "${source.fileName}.part")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val connection = (URL(source.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

        try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Ubuntu rootfs download failed with HTTP $responseCode.")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: 0L
            var downloadedBytes = 0L
            var lastReportedBytes = Long.MIN_VALUE
            onProgress(downloadedBytes, totalBytes)

            connection.inputStream.use { input ->
                BufferedInputStream(input).use { bufferedInput: BufferedInputStream ->
                    FileOutputStream(tempFile).use { output: FileOutputStream ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        while (true) {
                            val bytesRead = bufferedInput.read(buffer)
                            if (bytesRead < 0) {
                                break
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (downloadedBytes == totalBytes ||
                                    downloadedBytes - lastReportedBytes >= DOWNLOAD_PROGRESS_STEP ||
                                    lastReportedBytes == Long.MIN_VALUE) {
                                onProgress(downloadedBytes, totalBytes)
                                lastReportedBytes = downloadedBytes
                            }
                        }
                        onProgress(downloadedBytes, totalBytes)
                        output.fd.sync()
                    }
                }
            }

            if (archiveFile.exists()) {
                archiveFile.delete()
            }
            if (!tempFile.renameTo(archiveFile)) {
                throw IOException("Failed to move Ubuntu archive into cache storage.")
            }
        } finally {
            connection.disconnect()
            if (tempFile.exists() && archiveFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun verifyArchiveSha256(
            archiveFile: File,
            expectedSha256: String
    ) = withContext(Dispatchers.IO) {
        val actualSha256 = calculateSha256(archiveFile)
        if (actualSha256 != expectedSha256.lowercase()) {
            throw IOException(
                    "Ubuntu rootfs checksum mismatch. Expected $expectedSha256 but got $actualSha256."
            )
        }
    }

    private suspend fun extractArchive(
            archiveFile: File,
            rootfsDirectory: File,
            onEntryExtracted: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var extractedEntries = 0
        val pendingHardLinks = mutableListOf<PendingHardLink>()

        FileInputStream(archiveFile).use { fileInputStream: FileInputStream ->
            BufferedInputStream(fileInputStream).use { bufferedInputStream: BufferedInputStream ->
                GzipCompressorInputStream(bufferedInputStream).use { gzipInputStream: GzipCompressorInputStream ->
                    TarArchiveInputStream(gzipInputStream).use { tarInputStream: TarArchiveInputStream ->
                        var entry: TarArchiveEntry? = tarInputStream.nextEntry
                        while (entry != null) {
                            val target = safeResolve(rootfsDirectory, entry.name)
                            when {
                                entry.isDirectory -> {
                                    ensureDirectoryTarget(target)
                                }

                                entry.isSymbolicLink -> createSymbolicLink(target, entry.linkName)
                                entry.isLink -> pendingHardLinks += PendingHardLink(
                                        target = target,
                                        source = safeResolve(rootfsDirectory, entry.linkName),
                                        mode = entry.mode
                                )
                                else -> writeRegularFile(tarInputStream, target)
                            }

                            if (!entry.isLink) {
                                applyPermissions(target, entry.mode, entry.isDirectory)
                            }
                            extractedEntries += 1
                            onEntryExtracted(extractedEntries)
                            entry = tarInputStream.nextEntry
                        }
                    }
                }
            }
        }

        resolvePendingHardLinks(pendingHardLinks)
    }

    private fun writeRegularFile(
            tarInputStream: TarArchiveInputStream,
            target: File
    ) {
        prepareNonDirectoryTarget(target)
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(EXTRACTION_BUFFER_SIZE)
            while (true) {
                val bytesRead = tarInputStream.read(buffer)
                if (bytesRead < 0) {
                    break
                }
                output.write(buffer, 0, bytesRead)
            }
            output.fd.sync()
        }
    }

    private fun createSymbolicLink(
            target: File,
            linkName: String
    ) {
        prepareNonDirectoryTarget(target)
        runCatching { Os.symlink(linkName, target.absolutePath) }
                .getOrElse { error ->
                    throw IOException(
                            "Failed to create symbolic link ${target.absolutePath} -> $linkName.",
                            error
                    )
                }
    }

    private fun createHardLink(
            target: File,
            source: File
    ) {
        prepareNonDirectoryTarget(target)
        val sourcePath = source.toPath()
        if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Hard link source does not exist: ${source.absolutePath}.")
        }

        runCatching { Os.link(source.absolutePath, target.absolutePath) }
                .recoverCatching {
                    Files.copy(sourcePath, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                .getOrElse { error ->
                    throw IOException(
                            "Failed to create hard link ${target.absolutePath} -> ${source.absolutePath}.",
                            error
                    )
                }
    }

    private fun resolvePendingHardLinks(
            pendingHardLinks: List<PendingHardLink>
    ) {
        val remaining = pendingHardLinks.toMutableList()
        while (remaining.isNotEmpty()) {
            var resolvedInPass = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (!Files.exists(pending.source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    continue
                }

                createHardLink(target = pending.target, source = pending.source)
                applyPermissions(pending.target, pending.mode, false)
                iterator.remove()
                resolvedInPass = true
            }

            if (!resolvedInPass) {
                val unresolved = remaining.first()
                throw IOException(
                        "Hard link source was not extracted: ${unresolved.source.absolutePath}."
                )
            }
        }
    }

    private fun safeResolve(
            rootDirectory: File,
            entryName: String
    ): File {
        val rootPath = rootDirectory.toPath().normalize()
        val resolvedPath = rootPath.resolve(entryName).normalize()
        if (!resolvedPath.startsWith(rootPath)) {
            throw IOException("Refusing to extract outside the Ubuntu rootfs: $entryName")
        }
        return resolvedPath.toFile()
    }

    private fun ensureDirectoryTarget(target: File) {
        val path = target.toPath()
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            deleteRecursively(path)
        }
        Files.createDirectories(path)
    }

    private fun prepareNonDirectoryTarget(target: File) {
        target.parentFile?.toPath()?.let { Files.createDirectories(it) }
        val targetPath = target.toPath()
        if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            deleteRecursively(targetPath)
        }
    }

    private fun deleteRecursively(path: Path) {
        runCatching {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                return
            }

            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(path)) {
                Files.walkFileTree(
                        path,
                        object : SimpleFileVisitor<Path>() {
                            override fun visitFile(
                                    file: Path,
                                    attrs: BasicFileAttributes
                            ): FileVisitResult {
                                Files.delete(file)
                                return FileVisitResult.CONTINUE
                            }

                            override fun postVisitDirectory(
                                    dir: Path,
                                    exc: IOException?
                            ): FileVisitResult {
                                if (exc != null) {
                                    throw exc
                                }
                                Files.delete(dir)
                                return FileVisitResult.CONTINUE
                            }
                        }
                )
            } else {
                Files.delete(path)
            }
        }.getOrElse { error ->
            throw IOException(
                    "Failed to remove existing path before extracting Ubuntu rootfs: $path",
                    error
            )
        }
    }

    private fun applyPermissions(
            target: File,
            mode: Int,
            isDirectory: Boolean
    ) {
        val targetPath = target.toPath()
        if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(targetPath)) {
            return
        }

        val ownerRead = mode and 0b100_000_000 != 0
        val ownerWrite = mode and 0b010_000_000 != 0
        val ownerExecute = mode and 0b001_000_000 != 0

        target.setReadable(ownerRead || isDirectory, true)
        target.setWritable(ownerWrite, true)
        target.setExecutable(ownerExecute || isDirectory, true)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        const val DOWNLOAD_PROGRESS_STEP = 512 * 1024
        const val EXTRACTION_BUFFER_SIZE = 64 * 1024
        const val HASH_BUFFER_SIZE = 64 * 1024
        const val EXTRACTION_PROGRESS_STEP = 120
    }
}
