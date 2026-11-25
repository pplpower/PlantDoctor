// Cloudflare Worker: прокси между приложением «Зелёный доктор» и бесплатным Gemini API.
// Ключ Gemini хранится только здесь, в секрете GEMINI_API_KEY, и не попадает в APK.

const SYSTEM_PROMPT = `Ты — опытный агроном и фитопатолог. Пользователь присылает фото растения (комнатного, садового или огородного).
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
}`;

// Модели пробуются по порядку: если первая недоступна или перегружена, берётся следующая.
const DEFAULT_MODELS = ["gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-3.5-flash"];
const MAX_BODY_BYTES = 8 * 1024 * 1024;

export default {
  async fetch(request, env) {
    if (request.method === "GET") {
      return json({ ok: true, service: "plant-doctor" });
    }
    if (request.method !== "POST") {
      return json({ ok: false, error: "Метод не поддерживается" }, 405);
    }
    if (env.APP_TOKEN && request.headers.get("X-App-Token") !== env.APP_TOKEN) {
      return json({ ok: false, error: "Нет доступа" }, 403);
    }
    if (!env.GEMINI_API_KEY) {
      return json({ ok: false, error: "На сервере не задан ключ Gemini" }, 500);
    }
    const len = Number(request.headers.get("Content-Length") || 0);
    if (len > MAX_BODY_BYTES) {
      return json({ ok: false, error: "Фото слишком большое" }, 413);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ ok: false, error: "Неверный запрос" }, 400);
    }
    const image = typeof body.image === "string" ? body.image : "";
    const note = typeof body.note === "string" ? body.note.slice(0, 1000) : "";
    if (!image) {
      return json({ ok: false, error: "Нет фото" }, 400);
    }

    // Инструкции идут в начале сообщения: с systemInstruction бесплатные модели отвечают 503.
    let userText = SYSTEM_PROMPT + "\n\nПроанализируй растение на фото и верни JSON по схеме.";
    if (note.trim()) userText += "\n\nКомментарий владельца: " + note.trim();

    const payload = {
      contents: [{
        role: "user",
        parts: [
          { inline_data: { mime_type: "image/jpeg", data: image } },
          { text: userText },
        ],
      }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.4, maxOutputTokens: 16384 },
    };

    const models = (env.GEMINI_MODEL ? [env.GEMINI_MODEL] : []).concat(DEFAULT_MODELS);
    let lastError = "Сервис анализа недоступен";
    let lastStatus = 502;

    for (const model of [...new Set(models)]) {
      const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
      let res;
      try {
        res = await fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
          body: JSON.stringify(payload),
        });
      } catch (e) {
        lastError = "Нет связи с Gemini";
        continue;
      }

      if (res.status === 404 || res.status === 429 || res.status >= 500) {
        // Модель не найдена, лимит или перегрузка: пробуем следующую модель.
        lastStatus = res.status === 429 ? 429 : 502;
        lastError = res.status === 429
          ? "Достигнут бесплатный лимит запросов. Попробуйте через минуту."
          : "Сервис анализа временно недоступен. Попробуйте позже.";
        continue;
      }
      if (!res.ok) {
        const detail = await res.text();
        return json({ ok: false, error: `Ошибка Gemini (${res.status})`, detail: detail.slice(0, 500) }, 502);
      }

      const data = await res.json();
      if (data.promptFeedback && data.promptFeedback.blockReason) {
        return json({ ok: false, error: "Фото не удалось проанализировать. Попробуйте другое." }, 422);
      }
      const parts = (data.candidates && data.candidates[0] && data.candidates[0].content && data.candidates[0].content.parts) || [];
      const text = parts.map((p) => p.text || "").join("");
      const analysis = parseJson(text);
      if (!analysis) {
        lastError = "Не удалось разобрать ответ. Попробуйте ещё раз.";
        continue;
      }
      return json({ ok: true, model, analysis });
    }
    return json({ ok: false, error: lastError }, lastStatus);
  },
};

function parseJson(text) {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
