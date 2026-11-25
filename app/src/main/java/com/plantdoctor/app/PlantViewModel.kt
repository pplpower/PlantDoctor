package com.plantdoctor.app

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plantdoctor.app.data.AnalysisException
import com.plantdoctor.app.data.HistoryEntry
import com.plantdoctor.app.data.HistoryStore
import com.plantdoctor.app.data.ImageUtils
import com.plantdoctor.app.data.PlantAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantViewModel(app: Application) : AndroidViewModel(app) {

    private val history = HistoryStore(app.filesDir)
    private var analysisJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshHistory()
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { history.list() }
            _state.update { it.copy(history = list) }
        }
    }

    fun open(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Назад. Возвращает false, если уже на главном экране. */
    fun back(): Boolean {
        val current = _state.value.screen
        if (current is Screen.Home) return false
        if (current is Screen.Analyzing) analysisJob?.cancel()
        open(Screen.Home)
        return true
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) { ImageUtils.loadAndCompress(getApplication(), uri) }
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                open(Screen.Preview(jpeg, bmp))
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не удалось загрузить фото") }
            }
        }
    }

    fun analyze(preview: Screen.Preview, note: String) {
        open(Screen.Analyzing(preview.bitmap))
        analysisJob = viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) {
                    val b64 = Base64.encodeToString(preview.jpeg, Base64.NO_WRAP)
                    val analysis = PlantAnalyzer(BuildConfig.PLANT_API_URL).analyze(b64, note)
                    history.save(preview.jpeg, analysis)
                }
                refreshHistory()
                open(Screen.Result(entry))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AnalysisException) {
                _state.update { it.copy(screen = preview, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(screen = preview, error = "Ошибка: ${e.message}") }
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.delete(id) }
            refreshHistory()
            if ((_state.value.screen as? Screen.Result)?.entry?.id == id) open(Screen.Home)
        }
    }
}
