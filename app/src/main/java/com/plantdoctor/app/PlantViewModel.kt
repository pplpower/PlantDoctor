package com.plantdoctor.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plantdoctor.app.data.AnalysisRecord
import com.plantdoctor.app.data.AppException
import com.plantdoctor.app.data.Backup
import com.plantdoctor.app.data.ChatMessage
import com.plantdoctor.app.data.ErrorReason
import com.plantdoctor.app.data.Gemini
import com.plantdoctor.app.data.ImageUtils
import com.plantdoctor.app.data.Plant
import com.plantdoctor.app.data.Store
import com.plantdoctor.app.reminders.Reminders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Screens. Tabs (Home, Plants, History) sit at the bottom of the back stack. */
sealed interface Route {
    data object Home : Route
    data object Plants : Route
    data object History : Route
    data object Settings : Route
    data object Capture : Route
    data object Analyzing : Route
    data class Result(val analysisId: String) : Route
    data class Chat(val analysisId: String) : Route
    data class PlantDetail(val plantId: String) : Route
}

class CapturedPhoto(val jpeg: ByteArray, val bitmap: Bitmap)

data class CaptureState(
    val photos: List<CapturedPhoto> = emptyList(),
    val note: String = "",
    /** If set, the new analysis is added to this plant. */
    val plantId: String? = null,
)

data class UiState(
    val stack: List<Route> = listOf(Route.Home),
    val analyses: List<AnalysisRecord> = emptyList(),
    val plants: List<Plant> = emptyList(),
    val capture: CaptureState = CaptureState(),
    val chatBusy: Boolean = false,
    val error: ErrorReason? = null,
    @StringRes val message: Int? = null,
) {
    val route: Route get() = stack.last()
    fun analysis(id: String) = analyses.firstOrNull { it.id == id }
    fun plant(id: String) = plants.firstOrNull { it.id == id }
}

class PlantViewModel(app: Application) : AndroidViewModel(app) {

    private val store by lazy { Store(app.filesDir) }
    private val gemini = Gemini(BuildConfig.GEMINI_API_KEY)
    private var analysisJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val (a, p) = withContext(Dispatchers.IO) { store.analyses() to store.plants() }
            _state.update { it.copy(analyses = a, plants = p) }
        }
    }

    private fun language(): String {
        val locales = getApplication<Application>().resources.configuration.locales
        return Gemini.deviceLanguage(if (locales.isEmpty) java.util.Locale.getDefault() else locales[0])
    }

    private fun fail(e: Throwable) {
        val reason = (e as? AppException)?.reason ?: ErrorReason.GENERIC
        _state.update { it.copy(error = reason) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun showMessage(@StringRes id: Int) = _state.update { it.copy(message = id) }

    // ---------- navigation ----------

    fun navigate(route: Route) = _state.update { it.copy(stack = it.stack + route) }

    /** Switches to a bottom tab and clears everything above it. */
    fun selectTab(tab: Route) = _state.update { it.copy(stack = listOf(tab)) }

    /** Replaces the current screen. */
    private fun replace(route: Route) = _state.update { it.copy(stack = it.stack.dropLast(1) + route) }

    /** Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        val s = _state.value
        if (s.stack.size <= 1) {
            if (s.route != Route.Home) { selectTab(Route.Home); return true }
            return false
        }
        if (s.route == Route.Analyzing) analysisJob?.cancel()
        _state.update { it.copy(stack = it.stack.dropLast(1)) }
        return true
    }

    fun openPlantFromNotification(plantId: String) =
        _state.update { it.copy(stack = listOf(Route.Plants, Route.PlantDetail(plantId))) }

    // ---------- capture & analysis ----------

    /** Starts a new capture; [plantId] attaches the result to an existing plant. */
    fun startCapture(plantId: String?) =
        _state.update { it.copy(capture = CaptureState(plantId = plantId)) }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) { ImageUtils.loadAndCompress(getApplication(), uri) }
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                _state.update {
                    val photos = (it.capture.photos + CapturedPhoto(jpeg, bmp)).takeLast(MAX_PHOTOS)
                    val stack = if (it.route == Route.Capture) it.stack else it.stack + Route.Capture
                    it.copy(capture = it.capture.copy(photos = photos), stack = stack)
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun removePhoto(index: Int) = _state.update {
        val photos = it.capture.photos.filterIndexed { i, _ -> i != index }
        val s = it.copy(capture = it.capture.copy(photos = photos))
        if (photos.isEmpty()) s.copy(stack = s.stack.dropLast(1)) else s
    }

    fun setNote(note: String) = _state.update { it.copy(capture = it.capture.copy(note = note)) }

    fun setCapturePlant(plantId: String?) = _state.update { it.copy(capture = it.capture.copy(plantId = plantId)) }

    fun analyze() {
        val capture = _state.value.capture
        if (capture.photos.isEmpty()) return
        navigate(Route.Analyzing)
        analysisJob = viewModelScope.launch {
            try {
                val record = withContext(Dispatchers.IO) {
                    val images = capture.photos.map { Base64.encodeToString(it.jpeg, Base64.NO_WRAP) }
                    val analysis = gemini.analyze(images, capture.note, language())
                    val saved = store.saveNewAnalysis(capture.photos.map { it.jpeg }, analysis, capture.note, null)
                    if (capture.plantId != null && store.plant(capture.plantId) != null) {
                        store.linkAnalysis(saved, capture.plantId)
                        store.analysis(saved.id) ?: saved
                    } else saved
                }
                val (a, p) = withContext(Dispatchers.IO) { store.analyses() to store.plants() }
                _state.update {
                    // Analyzing and Capture are replaced by the result.
                    val base = it.stack.filter { r -> r != Route.Analyzing && r != Route.Capture }
                    it.copy(analyses = a, plants = p, stack = base + Route.Result(record.id), capture = CaptureState())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(stack = it.stack.filter { r -> r != Route.Analyzing }) }
                fail(e)
            }
        }
    }

    fun deleteAnalysis(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteAnalysis(id) }
            _state.update { s ->
                s.copy(stack = s.stack.filter { it != Route.Result(id) && it != Route.Chat(id) }.ifEmpty { listOf(Route.Home) })
            }
            reload()
        }
    }

    // ---------- chat ----------

    fun ask(analysisId: String, question: String) {
        val q = question.trim()
        val record = _state.value.analysis(analysisId) ?: return
        if (q.isEmpty() || _state.value.chatBusy) return
        val withQuestion = record.copy(chat = record.chat + ChatMessage(true, q, System.currentTimeMillis()))
        updateRecord(withQuestion)
        _state.update { it.copy(chatBusy = true) }
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    val images = record.imagePaths.mapNotNull { p ->
                        File(p).takeIf { it.exists() }?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                    }
                    gemini.ask(images, record.analysis, record.chat, q, language())
                }
                updateRecord(withQuestion.copy(chat = withQuestion.chat + ChatMessage(false, answer, System.currentTimeMillis())))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            } finally {
                _state.update { it.copy(chatBusy = false) }
            }
        }
    }

    private fun updateRecord(r: AnalysisRecord) {
        _state.update { s -> s.copy(analyses = s.analyses.map { if (it.id == r.id) r else it }) }
        viewModelScope.launch(Dispatchers.IO) { store.writeAnalysis(r) }
    }

    // ---------- plants ----------

    fun createPlant(analysisId: String, name: String) {
        val record = _state.value.analysis(analysisId) ?: return
        viewModelScope.launch {
            val plant = withContext(Dispatchers.IO) { store.createPlantFrom(record, name) }
            reloadNow()
            _state.update { it.copy(message = R.string.msg_plant_saved) }
            if (plant.remindersOn) Reminders.schedule(getApplication())
        }
    }

    fun linkToPlant(analysisId: String, plantId: String) {
        val record = _state.value.analysis(analysisId) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.linkAnalysis(record, plantId) }
            reloadNow()
            _state.update { it.copy(message = R.string.msg_plant_saved) }
        }
    }

    fun updatePlant(plant: Plant) {
        _state.update { s -> s.copy(plants = s.plants.map { if (it.id == plant.id) plant else it }) }
        viewModelScope.launch(Dispatchers.IO) { store.writePlant(plant) }
        if (plant.remindersOn) Reminders.schedule(getApplication())
    }

    fun markWatered(plant: Plant) = updatePlant(plant.copy(lastWatered = System.currentTimeMillis()))
    fun markFed(plant: Plant) = updatePlant(plant.copy(lastFertilized = System.currentTimeMillis()))

    fun deletePlant(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deletePlant(id) }
            _state.update { s -> s.copy(stack = s.stack.filter { it != Route.PlantDetail(id) }.ifEmpty { listOf(Route.Plants) }) }
            reload()
        }
    }

    private suspend fun reloadNow() {
        val (a, p) = withContext(Dispatchers.IO) { store.analyses() to store.plants() }
        _state.update { it.copy(analyses = a, plants = p) }
    }

    // ---------- backup ----------

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw AppException(ErrorReason.GENERIC)
                    out.use { Backup.export(store, it) }
                }
                _state.update { it.copy(message = R.string.msg_exported) }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw AppException(ErrorReason.GENERIC)
                    input.use { Backup.import(store, it) }
                }
                reloadNow()
                _state.update { it.copy(message = R.string.msg_imported) }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    companion object {
        const val MAX_PHOTOS = 3
    }
}
