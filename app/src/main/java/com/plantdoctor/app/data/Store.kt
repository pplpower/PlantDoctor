package com.plantdoctor.app.data

import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * File storage for analyses, plants and photos, all inside the app's private folder.
 * Layout: data/images (jpg), data/analyses (one json per analysis), data/plants (one json per plant).
 * Call from a background thread.
 */
class Store(private val filesDir: File) {

    val root = File(filesDir, "data")
    private val imageDir = File(root, "images")
    private val analysisDir = File(root, "analyses")
    private val plantDir = File(root, "plants")

    init {
        imageDir.mkdirs(); analysisDir.mkdirs(); plantDir.mkdirs()
        migrateV1()
    }

    // ---------- analyses ----------

    @Synchronized
    fun saveNewAnalysis(images: List<ByteArray>, analysis: PlantAnalysis, note: String, plantId: String?): AnalysisRecord {
        val id = UUID.randomUUID().toString()
        val paths = images.mapIndexed { i, bytes ->
            File(imageDir, "${id}_$i.jpg").apply { writeBytes(bytes) }.absolutePath
        }
        val record = AnalysisRecord(id, System.currentTimeMillis(), paths, analysis, plantId, note)
        writeAnalysis(record)
        return record
    }

    @Synchronized
    fun writeAnalysis(r: AnalysisRecord) {
        File(analysisDir, "${r.id}.json").writeText(AnalysisJson.recordToJson(r).toString())
    }

    @Synchronized
    fun analyses(): List<AnalysisRecord> =
        (analysisDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> runCatching { AnalysisJson.recordFromJson(JSONObject(f.readText()), imageDir) }.getOrNull() }
            .sortedByDescending { it.timestamp }

    @Synchronized
    fun analysis(id: String): AnalysisRecord? =
        File(analysisDir, "$id.json").takeIf { it.exists() }
            ?.let { runCatching { AnalysisJson.recordFromJson(JSONObject(it.readText()), imageDir) }.getOrNull() }

    @Synchronized
    fun deleteAnalysis(id: String) {
        val r = analysis(id) ?: return
        val usedByPlants = plants().map { it.coverImagePath }.toSet()
        r.imagePaths.filter { it !in usedByPlants }.forEach { File(it).delete() }
        File(analysisDir, "$id.json").delete()
    }

    // ---------- plants ----------

    @Synchronized
    fun plants(): List<Plant> =
        (plantDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> runCatching { AnalysisJson.plantFromJson(JSONObject(f.readText()), imageDir) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    @Synchronized
    fun plant(id: String): Plant? =
        File(plantDir, "$id.json").takeIf { it.exists() }
            ?.let { runCatching { AnalysisJson.plantFromJson(JSONObject(it.readText()), imageDir) }.getOrNull() }

    @Synchronized
    fun writePlant(p: Plant) {
        File(plantDir, "${p.id}.json").writeText(AnalysisJson.plantToJson(p).toString())
    }

    /** Creates a plant from an analysis and links the analysis to it. */
    @Synchronized
    fun createPlantFrom(record: AnalysisRecord, name: String): Plant {
        val a = record.analysis
        val plant = Plant(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { a.plantName },
            createdAt = System.currentTimeMillis(),
            coverImagePath = record.coverImage,
            species = listOf(a.plantName, a.latinName).filter { it.isNotBlank() }.joinToString(" · "),
            wateringDays = a.wateringDays,
            fertilizingDays = a.fertilizingDays,
            lastWatered = System.currentTimeMillis(),
            lastFertilized = System.currentTimeMillis(),
            remindersOn = a.wateringDays > 0,
        )
        writePlant(plant)
        writeAnalysis(record.copy(plantId = plant.id))
        return plant
    }

    @Synchronized
    fun linkAnalysis(record: AnalysisRecord, plantId: String) {
        writeAnalysis(record.copy(plantId = plantId))
        plant(plantId)?.let { p ->
            // The newest photo becomes the plant cover.
            writePlant(p.copy(coverImagePath = record.coverImage.ifBlank { p.coverImagePath }))
        }
    }

    @Synchronized
    fun deletePlant(id: String) {
        analyses().filter { it.plantId == id }.forEach { writeAnalysis(it.copy(plantId = null)) }
        File(plantDir, "$id.json").delete()
    }

    // ---------- migration from version 1.x (filesDir/history) ----------

    private fun migrateV1() {
        val old = File(filesDir, "history")
        val files = old.listFiles { f -> f.extension == "json" } ?: return
        for (f in files) {
            runCatching {
                val o = JSONObject(f.readText())
                val id = o.getString("id")
                val oldImage = File(old, o.getString("image"))
                val newImage = File(imageDir, "${id}_0.jpg")
                if (oldImage.exists()) oldImage.copyTo(newImage, overwrite = true)
                val record = AnalysisRecord(
                    id = id,
                    timestamp = o.getLong("timestamp"),
                    imagePaths = if (newImage.exists()) listOf(newImage.absolutePath) else emptyList(),
                    analysis = AnalysisJson.fromJson(o.getJSONObject("analysis")),
                )
                writeAnalysis(record)
            }
        }
        old.deleteRecursively()
    }
}
