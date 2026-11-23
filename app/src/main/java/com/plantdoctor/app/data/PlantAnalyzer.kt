package com.plantdoctor.app.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import java.time.Duration

/** Доступные модели Claude. */
enum class ClaudeModel(val id: String, val title: String, val hint: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "Самый точный анализ"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Быстрее и примерно в 2 раза дешевле"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "Самый быстрый и дешёвый");

    companion object {
        fun fromId(id: String?): ClaudeModel = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Отправляет фото растения в Claude и разбирает ответ.
 * Вызывать с фонового потока (Dispatchers.IO).
 */
class PlantAnalyzer(private val apiKey: String, private val model: ClaudeModel) {

    fun analyze(jpegBase64: String, userNote: String?): PlantAnalysis {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofMinutes(3))
            .maxRetries(2)
            .build()

        try {
            val userText = buildString {
                append("Проанализируй растение на фото и верни JSON по схеме.")
                if (!userNote.isNullOrBlank()) {
                    append("\n\nКомментарий владельца: ")
                    append(userNote.trim())
                }
            }

            val image = ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .data(jpegBase64)
                        .build()
                )
                .build()

            val params = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .addUserMessageOfContentBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(image),
                        ContentBlockParam.ofText(userText),
                    )
                )
                .build()

            val message = client.messages().create(params)

            val stop = message.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL) {
                throw AnalysisException("Модель отказалась анализировать это изображение. Попробуйте другое фото.")
            }

            val text = message.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("")

            if (text.isBlank()) {
                throw AnalysisException("Пустой ответ от модели. Попробуйте ещё раз.")
            }
            if (stop == StopReason.MAX_TOKENS) {
                throw AnalysisException("Ответ оказался слишком длинным и обрезан. Попробуйте ещё раз.")
            }

            return try {
                AnalysisJson.parse(text)
            } catch (e: Exception) {
                throw AnalysisException("Не удалось разобрать ответ модели. Попробуйте ещё раз.", e)
            }
        } catch (e: AnalysisException) {
            throw e
        } catch (e: UnauthorizedException) {
            throw AnalysisException("Неверный API-ключ. Проверьте его в настройках.", e)
        } catch (e: RateLimitException) {
            throw AnalysisException("Слишком много запросов. Подождите минуту и попробуйте снова.", e)
        } catch (e: AnthropicServiceException) {
            throw AnalysisException("Ошибка сервиса Claude (${e.statusCode()}): ${e.message}", e)
        } catch (e: AnthropicIoException) {
            throw AnalysisException("Нет соединения с сервером. Проверьте интернет.", e)
        } finally {
            client.close()
        }
    }

    companion object {
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
