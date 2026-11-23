package com.plantdoctor.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plantdoctor.app.ui.AppScaffold
import com.plantdoctor.app.ui.PlantTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantTheme {
                PlantApp()
            }
        }
    }
}

@Composable
fun PlantApp(vm: PlantViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // URI файла, в который камера сохранит снимок (переживает поворот экрана).
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) vm.onImagePicked(Uri.parse(uri))
        pendingPhoto = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onImagePicked(uri)
    }

    val takePhoto = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri.toString()
        try {
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            pendingPhoto = null
        }
    }
    val pickFromGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BackHandler(enabled = state.screen !is Screen.Home) { vm.back() }

    AppScaffold(
        state = state,
        onTakePhoto = takePhoto,
        onPickGallery = pickFromGallery,
        onOpen = vm::open,
        onBack = { vm.back() },
        onAnalyze = vm::analyze,
        onSaveSettings = vm::saveSettings,
        onDelete = vm::deleteEntry,
        onDismissError = vm::dismissError,
    )
}
