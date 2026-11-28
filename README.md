# Plant Doctor / Зелёный доктор (Android)

Take a photo of a plant and the app (Google Gemini, free tier) tells you the species, diseases and pests, the overall condition and how to care for it.

**Download:** https://github.com/pplpower/PlantDoctor/releases/download/latest/PlantDoctor.apk

## Features (v2.0)

- Plant card with a health ring, expandable problems (severity, symptoms, cause, treatment) and care tiles.
- Up to 3 photos per scan plus an optional note.
- "My plants": each plant keeps a timeline of scans, watering and feeding intervals.
- Watering and feeding reminders (notifications, WorkManager).
- "Ask the doctor" follow-up chat about a scan.
- Share a result, search and filter history, works offline for saved scans.
- Export and import all data as a zip backup.
- Material You colors, dark theme, splash screen.
- Languages: English, Русский, Українська, Polski, 简体中文, Español, Português (Brasil), Deutsch, Français. The AI answers in the phone language.

## Build

The Gemini key is injected at build time and is never stored in the repo:
`./gradlew :app:assembleRelease -PgeminiApiKey=...` or the `GEMINI_API_KEY` environment variable.
GitHub Actions takes it from the `GEMINI_API_KEY` secret and publishes the APK from `main` to the `latest` release.

## Code layout

- `data/Gemini.kt` – requests to Gemini with model fallback and timeouts.
- `data/Store.kt`, `data/Models.kt` – JSON storage of scans and plants.
- `data/Backup.kt`, `data/Share.kt` – export/import and sharing.
- `reminders/Reminders.kt` – care notifications.
- `ui/` – Compose screens; `PlantViewModel.kt` – state and navigation.
- `res/values-*/strings.xml` – translations.
