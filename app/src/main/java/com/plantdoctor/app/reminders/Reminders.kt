package com.plantdoctor.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plantdoctor.app.MainActivity
import com.plantdoctor.app.R
import com.plantdoctor.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object Reminders {
    private const val CHANNEL_ID = "care_reminders"
    private const val WORK_NAME = "care_reminders"

    /** Runs the reminder check a few times a day. Safe to call on every app start. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts notifications for plants that are due. Each plant is notified at most once per day per task. */
    fun checkAndNotify(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
        val now = System.currentTimeMillis()
        val store = Store(context.filesDir)

        for (plant in store.plants().filter { it.remindersOn }) {
            val tasks = buildList {
                if (plant.nextWatering() in 1..now) add("water" to context.getString(R.string.reminder_water, plant.name))
                if (plant.nextFertilizing() in 1..now) add("feed" to context.getString(R.string.reminder_feed, plant.name))
            }
            for ((task, text) in tasks) {
                val key = "${plant.id}_$task"
                if (prefs.getLong(key, -1) == today) continue
                prefs.edit().putLong(key, today).apply()

                val intent = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_PLANT_ID, plant.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending = PendingIntent.getActivity(
                    context, key.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(text)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(key.hashCode(), n)
                } catch (_: SecurityException) {
                }
            }
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { Reminders.checkAndNotify(applicationContext) }
        Result.success()
    }
}
