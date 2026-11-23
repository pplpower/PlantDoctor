package com.plantdoctor.app.data

import android.content.Context

/** Настройки: API-ключ и выбранная модель. Хранятся только на устройстве. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: ClaudeModel
        get() = ClaudeModel.fromId(prefs.getString(KEY_MODEL, null))
        set(value) = prefs.edit().putString(KEY_MODEL, value.id).apply()

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "model"
    }
}
