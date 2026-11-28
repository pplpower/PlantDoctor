package com.plantdoctor.app.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdoctor.app.PlantViewModel
import com.plantdoctor.app.R
import com.plantdoctor.app.Route
import com.plantdoctor.app.data.Share
import com.plantdoctor.app.reminders.Reminders
import java.io.File

private data class Tab(val route: Route, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, R.string.tab_scan, Icons.Outlined.PhotoCamera, Icons.Filled.PhotoCamera),
    Tab(Route.Plants, R.string.tab_plants, Icons.Outlined.Yard, Icons.Filled.Yard),
    Tab(Route.History, R.string.tab_history, Icons.Outlined.History, Icons.Filled.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: PlantViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // ----- launchers -----
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) vm.addPhoto(Uri.parse(uri))
        pendingPhoto = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PlantViewModel.MAX_PHOTOS)) { uris ->
        uris.forEach { vm.addPhoto(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.showMessage(R.string.notif_permission_denied)
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri.toString()
        runCatching { camera.launch(uri) }.onFailure { pendingPhoto = null }
    }
    fun pickGallery() {
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun newScan(plantId: String?) {
        vm.startCapture(plantId)
        takePhoto()
    }
    fun enableReminders() {
        Reminders.schedule(context)
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ----- messages -----
    val errorText = state.error?.let { stringResource(it.message()) }
    LaunchedEffect(state.error) {
        if (errorText != null) { snackbar.showSnackbar(errorText); vm.dismissError() }
    }
    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) { snackbar.showSnackbar(messageText); vm.dismissMessage() }
    }

    val route = state.route
    val isTab = tabs.any { it.route == route } && state.stack.size == 1
    BackHandler(enabled = !(route == Route.Home && state.stack.size == 1)) { vm.back() }

    val title = when (route) {
        Route.Home -> stringResource(R.string.app_name)
        Route.Plants -> stringResource(R.string.tab_plants)
        Route.History -> stringResource(R.string.tab_history)
        Route.Settings -> stringResource(R.string.settings)
        Route.Capture -> stringResource(R.string.capture_title)
        Route.Analyzing -> stringResource(R.string.analyzing_title)
        is Route.Result -> ""
        is Route.Chat -> stringResource(R.string.chat_title)
        is Route.PlantDetail -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (!isTab) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (route == Route.Home) {
                        IconButton(onClick = { vm.navigate(Route.Settings) }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    tabs.forEach { t ->
                        val selected = route == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { vm.selectTab(t.route) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { r ->
                when (r) {
                    Route.Home -> HomeScreen(
                        state = state,
                        onTakePhoto = { newScan(null) },
                        onPickGallery = { vm.startCapture(null); pickGallery() },
                        onOpen = vm::navigate,
                        onSeeAll = { vm.selectTab(Route.History) },
                        onWatered = vm::markWatered,
                        onFed = vm::markFed,
                    )
                    Route.Plants -> PlantsScreen(state.plants, state.analyses, vm::navigate)
                    Route.History -> HistoryScreen(state.analyses, vm::navigate)
                    Route.Settings -> SettingsScreen(
                        onExport = { exportLauncher.launch("plant-doctor-backup.zip") },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    )
                    Route.Capture -> CaptureScreen(
                        capture = state.capture,
                        plants = state.plants,
                        onTakePhoto = ::takePhoto,
                        onPickGallery = ::pickGallery,
                        onRemovePhoto = vm::removePhoto,
                        onNote = vm::setNote,
                        onPlant = vm::setCapturePlant,
                        onAnalyze = vm::analyze,
                    )
                    Route.Analyzing -> AnalyzingScreen(state.capture, onCancel = { vm.back() })
                    is Route.Result -> {
                        val record = state.analysis(r.analysisId)
                        if (record != null) {
                            val statusLabel = stringResource(record.analysis.healthStatus.label())
                            ResultScreen(
                                record = record,
                                plants = state.plants,
                                onAsk = { vm.navigate(Route.Chat(record.id)) },
                                onShare = { runCatching { Share.analysis(context, record, statusLabel) } },
                                onCreatePlant = { name -> vm.createPlant(record.id, name); enableRemindersIfNeeded(record.analysis.wateringDays) { enableReminders() } },
                                onLinkPlant = { vm.linkToPlant(record.id, it) },
                                onOpenPlant = { vm.navigate(Route.PlantDetail(it)) },
                                onDelete = { vm.deleteAnalysis(record.id) },
                            )
                        }
                    }
                    is Route.Chat -> {
                        val record = state.analysis(r.analysisId)
                        if (record != null) ChatScreen(record, state.chatBusy) { vm.ask(record.id, it) }
                    }
                    is Route.PlantDetail -> {
                        val plant = state.plant(r.plantId)
                        if (plant != null) {
                            PlantDetailScreen(
                                plant = plant,
                                analyses = state.analyses,
                                onUpdate = vm::updatePlant,
                                onWatered = { vm.markWatered(plant) },
                                onFed = { vm.markFed(plant) },
                                onEnableReminders = ::enableReminders,
                                onNewAnalysis = { newScan(plant.id) },
                                onOpen = vm::navigate,
                                onDelete = { vm.deletePlant(plant.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private inline fun enableRemindersIfNeeded(wateringDays: Int, enable: () -> Unit) {
    if (wateringDays > 0) enable()
}
