package com.plantdoctor.app.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Отправляет фото растения на сервер анализа (Cloudflare Worker с бесплатным Gemini)
 * и разбирает ответ. Ключ ИИ хранится на сервере, в приложении его нет.
 * Вызывать с фонового потока (Dispatchers.IO).
 */
class PlantAnalyzer(private val serverUrl: String) {

    fun analyze(jpegBase64: String, userNote: String?): PlantAnalysis {
        if (serverUrl.isBlank()) {
            throw AnalysisException("Сервер анализа не настроен в этой сборке приложения.")
        }
        val body = JSONObject().apply {
            put("image", jpegBase64)
            put("note", userNote?.trim().orEmpty())
        }.toString().toByteArray(Charsets.UTF_8)

        val conn = try {
            (URL(serverUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setFixedLengthStreamingMode(body.size)
            }
        } catch (e: Exception) {
            throw AnalysisException("Неверный адрес сервера анализа.", e)
        }

        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw AnalysisException("Сервер анализа вернул неожиданный ответ (код $code).", e)
            }
            if (!json.optBoolean("ok", false)) {
                val msg = json.optString("error").ifBlank { "Ошибка сервера анализа (код $code)." }
                throw AnalysisException(msg)
            }
            val analysis = json.optJSONObject("analysis")
                ?: throw AnalysisException("Сервер не прислал результат. Попробуйте ещё раз.")
            return AnalysisJson.fromJson(analysis)
        } catch (e: AnalysisException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AnalysisException("Сервер долго не отвечает. Попробуйте ещё раз.", e)
        } catch (e: IOException) {
            throw AnalysisException("Нет соединения с сервером. Проверьте интернет.", e)
        } finally {
            conn.disconnect()
        }
    }
}
