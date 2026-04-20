package com.phoneserver.mobile.storage

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class TerminalHistoryRecord(
        val command: String,
        val output: String,
        val exitCode: Int,
        val currentDirectory: String,
        val recordedAt: String
)

class TerminalHistoryStore(context: Context) {

    private val storageFile = File(context.filesDir, "terminal-history.json")

    @Synchronized
    fun loadRecent(limit: Int = 40): List<TerminalHistoryRecord> {
        if (!storageFile.exists()) {
            return emptyList()
        }

        val content = storageFile.readText().trim()
        if (content.isEmpty()) {
            return emptyList()
        }

        val jsonArray = runCatching { JSONArray(content) }.getOrElse { return emptyList() }
        val records = buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                add(
                        TerminalHistoryRecord(
                                command = item.optString("command"),
                                output = item.optString("output"),
                                exitCode = item.optInt("exitCode"),
                                currentDirectory = item.optString("currentDirectory"),
                                recordedAt = item.optString("recordedAt")
                        )
                )
            }
        }

        return records.takeLast(limit)
    }

    @Synchronized
    fun append(record: TerminalHistoryRecord, maxEntries: Int = 200) {
        val records = loadRecent(limit = maxEntries).plus(record).takeLast(maxEntries)
        val array = JSONArray()
        records.forEach { entry ->
            array.put(
                    JSONObject()
                            .put("command", entry.command)
                            .put("output", entry.output)
                            .put("exitCode", entry.exitCode)
                            .put("currentDirectory", entry.currentDirectory)
                            .put("recordedAt", entry.recordedAt)
            )
        }

        storageFile.parentFile?.mkdirs()
        storageFile.writeText(array.toString())
    }
}
