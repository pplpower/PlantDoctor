package com.plantdoctor.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Отправляет фото растения в Gemini API (бесплатный тариф) и разбирает ответ.
 * Ключ подставляется при сборке (BuildConfig.GEMINI_API_KEY), пользователю его вводить не нужно.
 * Вызывать с фонового потока (Dispatchers.IO).
 */
class PlantAnalyzer(private val apiKey: String) {

    fun analyze(jpegBase64: String, userNote: String?): PlantAnalysis {
        if (apiKey.isBlank()) {
            throw AnalysisException("В этой сборке приложения не задан ключ Gemini.")
        }
        // Инструкции идут в начале сообщения пользователя: с полем systemInstruction
        // бесплатные модели Gemini сейчас отвечают ошибкой 503.
        val userText = buildString {
            append(SYSTEM_PROMPT)
            append("\n\n")
            append("Проанализируй растение на фото и верни JSON по схеме.")
            if (!userNote.isNullOrBlank()) {
                append("\n\nКомментарий владельца: ")
                append(userNote.trim().take(1000))
            }
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray()
                    .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64)))
                    .put(JSONObject().put("text", userText)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
                put("maxOutputTokens", 16384)
            })
        }.toString().toByteArray(Charsets.UTF_8)

        var lastError = "Сервис анализа недоступен. Попробуйте позже."
        // Два прохода по списку моделей: бесплатные модели часто бывают перегружены (503).
        for ((pass, model) in (MODELS + MODELS).withIndex()) {
            if (pass == MODELS.size) Thread.sleep(3_000)
            val (code, text) = try {
                post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body)
            } catch (e: SocketTimeoutException) {
                throw AnalysisException("Сервер долго не отвечает. Попробуйте ещё раз.", e)
            } catch (e: IOException) {
                throw AnalysisException("Нет соединения с сервером. Проверьте интернет.", e)
            }

            when {
                code == 404 || code >= 500 -> {
                    lastError = "Сервис анализа временно недоступен. Попробуйте позже."
                    continue
                }
                code == 429 -> {
                    lastError = "Достигнут бесплатный лимит запросов. Попробуйте позже."
                    continue
                }
                code == 400 || code == 403 -> throw AnalysisException("Ключ Gemini не принят сервером (код $code).")
                code !in 200..299 -> throw AnalysisException("Ошибка сервиса анализа (код $code).")
            }

            val json = try { JSONObject(text) } catch (e: Exception) {
                throw AnalysisException("Неожиданный ответ сервиса анализа.", e)
            }
            if (json.optJSONObject("promptFeedback")?.has("blockReason") == true) {
                throw AnalysisException("Фото не удалось проанализировать. Попробуйте другое.")
            }
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            val answer = buildString {
                if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            try {
                return AnalysisJson.parse(answer)
            } catch (e: Exception) {
                lastError = "Не удалось разобрать ответ. Попробуйте ещё раз."
            }
        }
        throw AnalysisException(lastError)
    }

    private fun post(url: String, body: ByteArray): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
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
        /** Модели пробуются по порядку: если одна недоступна или упёрлась в лимит, берётся следующая. */
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-latest", "gemini-flash-lite-latest")

        val SYSTEM_PROMPT = """
Ты — опытный агроном и фитопатолог. Пользователь присылает фото растения (комнатного, садового или огородного).
Твоя задача: определить растение, оценить его состояние, найти болезни, вредителей и ошибки ухода, и дать практичные советы.

Правила:
- Отвечай только на русском языке.
- Будь честен в неуверенности: если по фото нельзя точно определить вид или болезнь, снизь confidence и скажи, что стоит проверить (например, осмотреть изнанку листьев или корни).
- Советы должны быть конкретными и выполнимыми дома: частота полива, количество света, препараты (по действующему веществу и типу, например «фунгицид на основе меди»), дозировки только общепринятые.
- Если на фото нет растения, верни is_plant = false и кратко объясни в description, что нужно сфотографировать.
- Если проблем нет, верни пустой список problems.

Верни ТОЛЬКО один JSON-объект без markdown и без пояснений вокруг, строго по схеме:
{
  "is_plant": true,
  "plant_name": "Русское название",
  "latin_name": "Латинское название",
  "confidence": 0-100,
  "description": "1-2 предложения о растении",
  "health_status": "healthy" | "attention" | "sick" | "critical",
  "health_score": 0-100,
  "health_summary": "2-3 предложения о состоянии и что видно на фото",
  "problems": [
    {
      "name": "Название проблемы (болезнь, вредитель или ошибка ухода)",
      "type": "болезнь" | "вредитель" | "уход" | "дефицит питания",
      "severity": "низкая" | "средняя" | "высокая",
      "symptoms": "Что именно видно на фото",
      "cause": "Причина",
      "treatment": ["Шаг 1", "Шаг 2"]
    }
  ],
  "urgent_actions": ["Что сделать в ближайшие дни, по порядку важности"],
  "care": {
    "watering": "Полив",
    "light": "Освещение",
    "temperature": "Температура",
    "humidity": "Влажность воздуха",
    "soil": "Грунт и пересадка",
    "fertilizing": "Подкормки",
    "pruning": "Обрезка и формирование"
  },
  "tips": ["Полезный совет", "Ещё совет"]
}
""".trimIndent()
    }
}
