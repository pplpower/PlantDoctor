# Сервер анализа (Cloudflare Worker + бесплатный Gemini)

Приложение отправляет фото сюда, а Worker передаёт его в Gemini. Ключ Gemini хранится только в Cloudflare.

## Настройка (один раз, ~10 минут, всё бесплатно)

1. **Ключ Gemini.** Откройте https://aistudio.google.com/apikey, войдите в Google-аккаунт и нажмите «Create API key». Скопируйте ключ.
2. **Аккаунт Cloudflare.** Зарегистрируйтесь на https://dash.cloudflare.com/sign-up.
3. **Создайте Worker.** В панели Cloudflare: Compute (Workers) → Create → Start with Hello World → Deploy.
4. **Вставьте код.** Нажмите Edit code, удалите всё и вставьте содержимое файла `worker.js`. Нажмите Deploy.
5. **Добавьте ключ.** Вернитесь к Worker → Settings → Variables and Secrets → Add → тип Secret, имя `GEMINI_API_KEY`, значение — ключ из шага 1 → Deploy.
6. **Адрес.** На странице Worker скопируйте адрес вида `https://plant-doctor.<ваше-имя>.workers.dev` и пропишите его в `gradle.properties` (`plantApiUrl=...`).

Проверка: откройте адрес в браузере, должно появиться `{"ok":true,"service":"plant-doctor"}`.

Необязательно: переменная `GEMINI_MODEL` задаёт модель (по умолчанию `gemini-flash-latest`),
а секрет `APP_TOKEN` закрывает Worker от чужих запросов (тогда приложение должно слать заголовок `X-App-Token`).
