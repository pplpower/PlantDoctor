package com.plantdoctor.app.data

import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Хранит историю анализов в папке приложения: фото + JSON с результатом. */
class HistoryStore(baseDir: File) {

    private val dir = File(baseDir, "history").apply { mkdirs() }

    fun save(jpegBytes: ByteArray, analysis: PlantAnalysis): HistoryEntry {
        val id = UUID.randomUUID().toString()
        val image = File(dir, "$id.jpg")
        image.writeBytes(jpegBytes)
        val entry = HistoryEntry(id, System.currentTimeMillis(), image.absolutePath, analysis)
        val json = JSONObject().apply {
            put("id", entry.id)
            put("timestamp", entry.timestamp)
            put("image", image.name)
            put("analysis", AnalysisJson.toJson(analysis))
        }
        File(dir, "$id.json").writeText(json.toString())
        return entry
    }

    fun list(): List<HistoryEntry> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f ->
                runCatching {
                    val o = JSONObject(f.readText())
                    HistoryEntry(
                        id = o.getString("id"),
                        timestamp = o.getLong("timestamp"),
                        imagePath = File(dir, o.getString("image")).absolutePath,
                        analysis = AnalysisJson.fromJson(o.getJSONObject("analysis")),
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.timestamp }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        File(dir, "$id.jpg").delete()
    }
}
