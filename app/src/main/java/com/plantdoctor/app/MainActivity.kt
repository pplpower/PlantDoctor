package com.plantdoctor.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.plantdoctor.app.reminders.Reminders
import com.plantdoctor.app.ui.AppRoot
import com.plantdoctor.app.ui.PlantTheme

class MainActivity : ComponentActivity() {
    private val vm: PlantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.ensureChannel(this)
        Reminders.schedule(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PlantTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val plantId = intent?.getStringExtra(EXTRA_PLANT_ID) ?: return
        vm.openPlantFromNotification(plantId)
    }

    companion object {
        const val EXTRA_PLANT_ID = "plant_id"
    }
}
