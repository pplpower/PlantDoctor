package com.plantdoctor.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

/** Why an operation failed. The UI maps each reason to a translated message. */
enum class ErrorReason { NO_KEY, NETWORK, TIMEOUT, BUSY, QUOTA, KEY_REJECTED, BAD_RESPONSE, BLOCKED, IMAGE_OPEN, NOT_IMAGE, GENERIC }

class AppException(val reason: ErrorReason, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * Talks to the Gemini API (free tier). The key is injected at build time.
 * Free models are often overloaded or slow, so several models are tried within a time budget.
 * Call from a background thread.
 */
class Gemini(private val apiKey: String) {

    /** Analyses 1–3 photos of a plant. Answers in [language] (an English language name, e.g. "Polish"). */
    fun analyze(jpegsBase64: List<String>, note: String?, language: String): PlantAnalysis {
        val parts = JSONArray()
        jpegsBase64.forEach { parts.put(imagePart(it)) }
        val text = buildString {
            append(analysisPrompt(language))
            append("\n\n")
            if (jpegsBase64.size > 1) append("There are ${jpegsBase64.size} photos of the same plant.\n")
            append("Analyse the plant and return the JSON.")
            if (!note.isNullOrBlank()) append("\n\nOwner's note: ").append(note.trim().take(1000))
        }
        parts.put(JSONObject().put("text", text))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val reply = generate(contents, json = true)
        return try {
            AnalysisJson.parse(reply)
        } catch (e: Exception) {
            throw AppException(ErrorReason.BAD_RESPONSE, e)
        }
    }

    /** Answers a follow-up question about an analysis, keeping the previous chat as context. */
    fun ask(
        jpegsBase64: List<String>,
        analysis: PlantAnalysis,
        history: List<ChatMessage>,
        question: String,
        language: String,
    ): String {
        val contents = JSONArray()
        val first = JSONArray()
        jpegsBase64.take(2).forEach { first.put(imagePart(it)) }
        first.put(JSONObject().put("text", buildString {
            append("You are an experienced gardener and plant pathologist helping the owner of this plant. ")
            append("Earlier you produced this analysis of the photos:\n")
            append(AnalysisJson.toJson(analysis).toString())
            append("\n\nAnswer the owner's questions about this plant briefly and practically, in plain text without markdown, ")
            append("in $language. If you are unsure, say what to check.")
        }))
        contents.put(JSONObject().put("role", "user").put("parts", first))
        contents.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "OK."))))
        history.takeLast(20).forEach { m ->
            contents.put(JSONObject().put("role", if (m.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question.take(2000)))))
        return generate(contents, json = false).trim().ifBlank { throw AppException(ErrorReason.BAD_RESPONSE) }
    }

    private fun imagePart(b64: String) =
        JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64))

    /** Sends a request, trying models in order within the time budget. Returns the reply text. */
    private fun generate(contents: JSONArray, json: Boolean): String {
        if (apiKey.isBlank()) throw AppException(ErrorReason.NO_KEY)
        // Note: instructions go into the user message: with systemInstruction the free models answer 503.
        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                if (json) put("responseMimeType", "application/json")
                put("temperature", 0.4)
                put("maxOutputTokens", 16384)
            })
        }.toString().toByteArray(Charsets.UTF_8)

        var lastReason = ErrorReason.BUSY
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        for ((pass, model) in (MODELS + MODELS).withIndex()) {
            if (pass == MODELS.size) Thread.sleep(2_000)
            val remaining = deadline - System.currentTimeMillis()
            if (remaining < 10_000) break
            val (code, text) = try {
                post(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    body,
                    remaining.coerceAtMost(PER_REQUEST_TIMEOUT_MS).toInt(),
                )
            } catch (e: SocketTimeoutException) {
                lastReason = ErrorReason.TIMEOUT
                continue
            } catch (e: IOException) {
                throw AppException(ErrorReason.NETWORK, e)
            }

            when {
                code == 404 || code >= 500 -> { lastReason = ErrorReason.BUSY; continue }
                code == 429 -> { lastReason = ErrorReason.QUOTA; continue }
                code == 400 || code == 401 || code == 403 -> throw AppException(ErrorReason.KEY_REJECTED)
                code !in 200..299 -> throw AppException(ErrorReason.GENERIC)
            }

            val obj = try { JSONObject(text) } catch (e: Exception) { throw AppException(ErrorReason.BAD_RESPONSE, e) }
            if (obj.optJSONObject("promptFeedback")?.has("blockReason") == true) throw AppException(ErrorReason.BLOCKED)
            val parts = obj.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val answer = buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    if (!p.optBoolean("thought", false)) append(p.optString("text"))
                }
            }
            if (answer.isBlank() || (json && !answer.contains('{'))) { lastReason = ErrorReason.BAD_RESPONSE; continue }
            return answer
        }
        throw AppException(lastReason)
    }

    private fun post(url: String, body: ByteArray, readTimeoutMs: Int): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            setFixedLengthStreamingMode(body.size)
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Fast lite models first (usually 3–5 s); heavier ones are often overloaded on the free tier. */
        val MODELS = listOf("gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-3.5-flash")
        const val TOTAL_BUDGET_MS = 100_000L
        const val PER_REQUEST_TIMEOUT_MS = 40_000L

        /** English name of the phone's language, for the model ("Russian", "Chinese (China)", ...). */
        fun deviceLanguage(locale: Locale = Locale.getDefault()): String {
            val lang = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
            return if (locale.language == "zh" || locale.language == "pt") {
                val country = locale.getDisplayCountry(Locale.ENGLISH)
                if (country.isNotBlank()) "$lang ($country)" else lang
            } else lang
        }

        fun analysisPrompt(language: String) = """
You are an experienced agronomist and plant pathologist. The user sends photos of a plant (houseplant, garden or vegetable plant).
Identify the plant, assess its condition, find diseases, pests and care mistakes, and give practical advice.

Rules:
- Write every text value in $language. Keep JSON keys and enum values exactly as in the schema (in English).
- Be honest about uncertainty: if the species or disease cannot be determined from the photo, lower "confidence" and say what to check (for example the underside of leaves or the roots).
- Advice must be specific and doable at home: watering frequency, amount of light, products by active ingredient and type (for example "copper-based fungicide"), only commonly accepted doses.
- If there is no plant in the photo, set is_plant to false and briefly explain in "description" what to photograph.
- If there are no problems, return an empty "problems" list.
- watering_interval_days: typical number of days between waterings for this plant in its current season indoors (integer, 0 if not applicable). fertilizing_interval_days: days between feedings now (integer, 0 if it should not be fed now).

Return ONLY one JSON object, without markdown and without any text around it, strictly following this schema:
{
  "is_plant": true,
  "plant_name": "Common name",
  "latin_name": "Latin name",
  "confidence": 0-100,
  "description": "1-2 sentences about the plant",
  "health_status": "healthy" | "attention" | "sick" | "critical",
  "health_score": 0-100,
  "health_summary": "2-3 sentences about the condition and what is visible in the photo",
  "problems": [
    {
      "name": "Problem name (disease, pest or care mistake)",
      "type": "Short category, e.g. disease / pest / care / nutrient deficiency",
      "severity": "low" | "medium" | "high",
      "symptoms": "What exactly is visible in the photo",
      "cause": "Cause",
      "treatment": ["Step 1", "Step 2"]
    }
  ],
  "urgent_actions": ["What to do in the next few days, most important first"],
  "care": {
    "watering": "Watering",
    "light": "Light",
    "temperature": "Temperature",
    "humidity": "Air humidity",
    "soil": "Soil and repotting",
    "fertilizing": "Feeding",
    "pruning": "Pruning and shaping"
  },
  "watering_interval_days": 7,
  "fertilizing_interval_days": 14,
  "tips": ["Useful tip", "Another tip"]
}
""".trimIndent()
    }
}
