package com.plantdoctor.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Результат анализа фотографии растения. */
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
)

enum class HealthStatus(val key: String, val label: String) {
    HEALTHY("healthy", "Здорово"),
    ATTENTION("attention", "Нужно внимание"),
    SICK("sick", "Болеет"),
    CRITICAL("critical", "Критическое состояние"),
    UNKNOWN("unknown", "Не определено");

    companion object {
        fun from(value: String?): HealthStatus =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

data class Problem(
    val name: String,
    val type: String,
    val severity: String,
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

/** Запись в истории: путь к фото, время и результат. */
data class HistoryEntry(
    val id: String,
    val timestamp: Long,
    val imagePath: String,
    val analysis: PlantAnalysis,
)

// ---------- JSON <-> модели ----------

object AnalysisJson {

    /** Достаёт JSON-объект из ответа модели (на случай, если вокруг есть текст или ```). */
    fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "В ответе нет JSON" }
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
                    severity = p.str("severity"),
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
                    put("severity", p.severity)
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
    }

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
