package com.plantdoctor.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Result of one AI analysis of a plant photo. */
data class PlantAnalysis(
    val isPlant: Boolean,
    val plantName: String,
    val latinName: String,
    val confidence: Int,
    val description: String,
    val healthStatus: HealthStatus,
    val healthScore: Int,
    val healthSummary: String,
    val problems: List<Problem>,
    val urgentActions: List<String>,
    val care: Care,
    val tips: List<String>,
    /** Suggested watering interval in days (0 = unknown). Used for reminders. */
    val wateringDays: Int = 0,
    /** Suggested fertilizing interval in days (0 = unknown / not needed now). */
    val fertilizingDays: Int = 0,
)

enum class HealthStatus(val key: String) {
    HEALTHY("healthy"),
    ATTENTION("attention"),
    SICK("sick"),
    CRITICAL("critical"),
    UNKNOWN("unknown");

    companion object {
        fun from(value: String?): HealthStatus =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

enum class Severity(val key: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    companion object {
        /** Accepts English keys and the Russian words used by version 1.x. */
        fun from(value: String?): Severity {
            val v = value?.trim()?.lowercase().orEmpty()
            return when {
                v.startsWith("high") || v.startsWith("выс") -> HIGH
                v.startsWith("med") || v.startsWith("сред") -> MEDIUM
                else -> LOW
            }
        }
    }
}

data class Problem(
    val name: String,
    val type: String,
    val severity: Severity,
    val symptoms: String,
    val cause: String,
    val treatment: List<String>,
)

data class Care(
    val watering: String,
    val light: String,
    val temperature: String,
    val humidity: String,
    val soil: String,
    val fertilizing: String,
    val pruning: String,
)

data class ChatMessage(val fromUser: Boolean, val text: String, val timestamp: Long)

/** A saved analysis: photos, result, optional link to a plant and follow-up chat. */
data class AnalysisRecord(
    val id: String,
    val timestamp: Long,
    val imagePaths: List<String>,
    val analysis: PlantAnalysis,
    val plantId: String? = null,
    val note: String = "",
    val chat: List<ChatMessage> = emptyList(),
) {
    val coverImage: String get() = imagePaths.firstOrNull().orEmpty()
}

/** A plant in "My plants". */
data class Plant(
    val id: String,
    val name: String,
    val createdAt: Long,
    val coverImagePath: String,
    val species: String = "",
    val wateringDays: Int = 0,
    val fertilizingDays: Int = 0,
    val lastWatered: Long = 0,
    val lastFertilized: Long = 0,
    val remindersOn: Boolean = false,
) {
    fun nextWatering(): Long =
        if (wateringDays > 0) (if (lastWatered > 0) lastWatered else createdAt) + wateringDays * DAY_MS else 0

    fun nextFertilizing(): Long =
        if (fertilizingDays > 0) (if (lastFertilized > 0) lastFertilized else createdAt) + fertilizingDays * DAY_MS else 0

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

// ---------------- JSON ----------------

object AnalysisJson {

    /** Extracts the JSON object from a model reply (tolerates text or ``` around it). */
    fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON in reply" }
        return text.substring(start, end + 1)
    }

    fun parse(text: String): PlantAnalysis = fromJson(JSONObject(extractJsonObject(text)))

    fun fromJson(o: JSONObject): PlantAnalysis {
        val careObj = o.optJSONObject("care") ?: JSONObject()
        return PlantAnalysis(
            isPlant = o.optBoolean("is_plant", true),
            plantName = o.str("plant_name"),
            latinName = o.str("latin_name"),
            confidence = o.optInt("confidence", 0).coerceIn(0, 100),
            description = o.str("description"),
            healthStatus = HealthStatus.from(o.optString("health_status")),
            healthScore = o.optInt("health_score", 0).coerceIn(0, 100),
            healthSummary = o.str("health_summary"),
            problems = o.optJSONArray("problems").objects().map { p ->
                Problem(
                    name = p.str("name"),
                    type = p.str("type"),
                    severity = Severity.from(p.optString("severity")),
                    symptoms = p.str("symptoms"),
                    cause = p.str("cause"),
                    treatment = p.optJSONArray("treatment").strings(),
                )
            },
            urgentActions = o.optJSONArray("urgent_actions").strings(),
            care = Care(
                watering = careObj.str("watering"),
                light = careObj.str("light"),
                temperature = careObj.str("temperature"),
                humidity = careObj.str("humidity"),
                soil = careObj.str("soil"),
                fertilizing = careObj.str("fertilizing"),
                pruning = careObj.str("pruning"),
            ),
            tips = o.optJSONArray("tips").strings(),
            wateringDays = o.optInt("watering_interval_days", 0).coerceIn(0, 365),
            fertilizingDays = o.optInt("fertilizing_interval_days", 0).coerceIn(0, 365),
        )
    }

    fun toJson(a: PlantAnalysis): JSONObject = JSONObject().apply {
        put("is_plant", a.isPlant)
        put("plant_name", a.plantName)
        put("latin_name", a.latinName)
        put("confidence", a.confidence)
        put("description", a.description)
        put("health_status", a.healthStatus.key)
        put("health_score", a.healthScore)
        put("health_summary", a.healthSummary)
        put("problems", JSONArray().apply {
            a.problems.forEach { p ->
                put(JSONObject().apply {
                    put("name", p.name)
                    put("type", p.type)
                    put("severity", p.severity.key)
                    put("symptoms", p.symptoms)
                    put("cause", p.cause)
                    put("treatment", JSONArray(p.treatment))
                })
            }
        })
        put("urgent_actions", JSONArray(a.urgentActions))
        put("care", JSONObject().apply {
            put("watering", a.care.watering)
            put("light", a.care.light)
            put("temperature", a.care.temperature)
            put("humidity", a.care.humidity)
            put("soil", a.care.soil)
            put("fertilizing", a.care.fertilizing)
            put("pruning", a.care.pruning)
        })
        put("tips", JSONArray(a.tips))
        put("watering_interval_days", a.wateringDays)
        put("fertilizing_interval_days", a.fertilizingDays)
    }

    fun recordToJson(r: AnalysisRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("timestamp", r.timestamp)
        put("images", JSONArray(r.imagePaths.map { java.io.File(it).name }))
        put("analysis", toJson(r.analysis))
        put("plant_id", r.plantId ?: JSONObject.NULL)
        put("note", r.note)
        put("chat", JSONArray().apply {
            r.chat.forEach { m ->
                put(JSONObject().put("user", m.fromUser).put("text", m.text).put("ts", m.timestamp))
            }
        })
    }

    fun recordFromJson(o: JSONObject, imageDir: java.io.File): AnalysisRecord = AnalysisRecord(
        id = o.getString("id"),
        timestamp = o.getLong("timestamp"),
        imagePaths = o.optJSONArray("images").strings().map { java.io.File(imageDir, it).absolutePath },
        analysis = fromJson(o.getJSONObject("analysis")),
        plantId = if (o.isNull("plant_id")) null else o.optString("plant_id").ifBlank { null },
        note = o.str("note"),
        chat = o.optJSONArray("chat").objects().map {
            ChatMessage(it.optBoolean("user"), it.str("text"), it.optLong("ts"))
        },
    )

    fun plantToJson(p: Plant): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("created_at", p.createdAt)
        put("cover", java.io.File(p.coverImagePath).name)
        put("species", p.species)
        put("watering_days", p.wateringDays)
        put("fertilizing_days", p.fertilizingDays)
        put("last_watered", p.lastWatered)
        put("last_fertilized", p.lastFertilized)
        put("reminders", p.remindersOn)
    }

    fun plantFromJson(o: JSONObject, imageDir: java.io.File): Plant = Plant(
        id = o.getString("id"),
        name = o.str("name"),
        createdAt = o.optLong("created_at"),
        coverImagePath = java.io.File(imageDir, o.optString("cover")).absolutePath,
        species = o.str("species"),
        wateringDays = o.optInt("watering_days"),
        fertilizingDays = o.optInt("fertilizing_days"),
        lastWatered = o.optLong("last_watered"),
        lastFertilized = o.optLong("last_fertilized"),
        remindersOn = o.optBoolean("reminders"),
    )

    private fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key, "").trim()

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            optString(i, "").trim().takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i) }
    }
}
