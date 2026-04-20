package com.phoneserver.mobile.storage

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class WorkspaceRecord(
        val name: String,
        val path: String,
        val createdAt: String,
        val lastOpenedAt: String
)

class WorkspaceCatalogStore(context: Context) {

    private val storageFile = File(context.filesDir, "workspace-catalog.json")

    @Synchronized
    fun loadAll(): List<WorkspaceRecord> {
        if (!storageFile.exists()) {
            return emptyList()
        }

        val content = storageFile.readText().trim()
        if (content.isEmpty()) {
            return emptyList()
        }

        val jsonArray = runCatching { JSONArray(content) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                add(
                        WorkspaceRecord(
                                name = item.optString("name"),
                                path = item.optString("path"),
                                createdAt = item.optString("createdAt"),
                                lastOpenedAt = item.optString("lastOpenedAt")
                        )
                )
            }
        }
    }

    @Synchronized
    fun upsert(record: WorkspaceRecord) {
        val updatedRecords = loadAll()
                .filterNot { it.path == record.path }
                .plus(record)
                .sortedWith(compareByDescending<WorkspaceRecord> { it.lastOpenedAt }.thenBy { it.name.lowercase() })

        writeAll(updatedRecords)
    }

    @Synchronized
    fun markOpened(path: String, openedAt: String) {
        val records = loadAll().map { record ->
            if (record.path == path) {
                record.copy(lastOpenedAt = openedAt)
            } else {
                record
            }
        }
        writeAll(records)
    }

    @Synchronized
    fun pruneMissingPaths(validPaths: Set<String>) {
        val records = loadAll().filter { it.path in validPaths }
        writeAll(records)
    }

    private fun writeAll(records: List<WorkspaceRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                    JSONObject()
                            .put("name", record.name)
                            .put("path", record.path)
                            .put("createdAt", record.createdAt)
                            .put("lastOpenedAt", record.lastOpenedAt)
            )
        }

        storageFile.parentFile?.mkdirs()
        storageFile.writeText(array.toString())
    }
}
