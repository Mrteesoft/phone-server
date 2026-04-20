package com.phoneserver.mobile.runtime

import android.content.Context
import com.phoneserver.mobile.storage.WorkspaceCatalogStore
import com.phoneserver.mobile.storage.WorkspaceRecord
import com.phoneserver.mobile.ui.WorkspaceSummary
import java.io.File
import java.time.Instant

class WorkspaceManager(context: Context) {

    private val rootDirectory = File(context.filesDir, "workspaces").apply { mkdirs() }
    private val catalogStore = WorkspaceCatalogStore(context)

    init {
        syncCatalogWithFilesystem()
    }

    fun rootDirectory(): File = rootDirectory

    fun listWorkspaces(): List<WorkspaceSummary> {
        syncCatalogWithFilesystem()

        val directoriesByPath = rootDirectory.listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .associateBy { it.absolutePath }

        return catalogStore.loadAll()
                .sortedWith(compareByDescending<WorkspaceRecord> { it.lastOpenedAt }.thenBy { it.name.lowercase() })
                .mapNotNull { record ->
                    val directory = directoriesByPath[record.path] ?: return@mapNotNull null
                    WorkspaceSummary(
                            name = record.name,
                            path = directory.absolutePath,
                            fileCount = directory.listFiles().orEmpty().size,
                            createdAt = record.createdAt
                    )
                }
    }

    fun createWorkspace(rawName: String): WorkspaceSummary {
        val normalizedName = normalizeWorkspaceName(rawName)
        require(normalizedName.isNotBlank()) { "Workspace name cannot be empty." }

        val directory = File(rootDirectory, normalizedName)
        require(!directory.exists()) { "A workspace named '$normalizedName' already exists." }
        require(directory.mkdirs()) { "Failed to create workspace directory." }

        File(directory, "README.txt").writeText(
                buildString {
                    appendLine("Phone Server workspace")
                    appendLine()
                    appendLine("This directory was created locally by the app.")
                    appendLine("Use the terminal tab to run commands inside it.")
                }
        )

        val now = Instant.now().toString()
        catalogStore.upsert(
                WorkspaceRecord(
                        name = directory.name,
                        path = directory.absolutePath,
                        createdAt = now,
                        lastOpenedAt = now
                )
        )

        return WorkspaceSummary(
                name = directory.name,
                path = directory.absolutePath,
                fileCount = directory.listFiles().orEmpty().size,
                createdAt = now
        )
    }

    fun markOpened(path: String) {
        catalogStore.markOpened(path, Instant.now().toString())
    }

    private fun normalizeWorkspaceName(value: String): String {
        return value.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9-_]+"), "-")
                .replace(Regex("-+"), "-")
                .trim('-')
    }

    private fun syncCatalogWithFilesystem() {
        val directories = rootDirectory.listFiles()
                .orEmpty()
                .filter { it.isDirectory }

        val validPaths = directories.map { it.absolutePath }.toSet()
        catalogStore.pruneMissingPaths(validPaths)

        val existingPaths = catalogStore.loadAll().map { it.path }.toSet()
        directories.filterNot { it.absolutePath in existingPaths }
                .forEach { directory ->
                    val timestamp = Instant.ofEpochMilli(directory.lastModified()).toString()
                    catalogStore.upsert(
                            WorkspaceRecord(
                                    name = directory.name,
                                    path = directory.absolutePath,
                                    createdAt = timestamp,
                                    lastOpenedAt = timestamp
                            )
                    )
                }
    }
}
