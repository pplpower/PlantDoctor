package com.plantdoctor.app

import android.graphics.Bitmap
import com.plantdoctor.app.data.ClaudeModel
import com.plantdoctor.app.data.HistoryEntry

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    /** Фото выбрано, можно добавить комментарий и запустить анализ. */
    data class Preview(val jpeg: ByteArray, val bitmap: Bitmap) : Screen
    data class Analyzing(val bitmap: Bitmap) : Screen
    data class Result(val entry: HistoryEntry) : Screen
}

data class UiState(
    val screen: Screen = Screen.Home,
    val history: List<HistoryEntry> = emptyList(),
    val apiKey: String = "",
    val model: ClaudeModel = ClaudeModel.OPUS,
    val error: String? = null,
)
