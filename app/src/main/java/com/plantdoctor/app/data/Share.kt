package com.plantdoctor.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.plantdoctor.app.R
import java.io.File

object Share {
    /** Opens the system share sheet with a text summary and the main photo. */
    fun analysis(context: Context, r: AnalysisRecord, statusLabel: String) {
        val a = r.analysis
        val text = buildString {
            append(a.plantName)
            if (a.latinName.isNotBlank()) append(" (").append(a.latinName).append(")")
            append("\n").append(context.getString(R.string.health)).append(": ")
                .append(statusLabel).append(" · ").append(a.healthScore).append("/100")
            if (a.healthSummary.isNotBlank()) append("\n\n").append(a.healthSummary)
            if (a.problems.isNotEmpty()) {
                append("\n\n").append(context.getString(R.string.problems)).append(":")
                a.problems.forEach { append("\n• ").append(it.name) }
            }
            if (a.urgentActions.isNotEmpty()) {
                append("\n\n").append(context.getString(R.string.urgent)).append(":")
                a.urgentActions.forEachIndexed { i, s -> append("\n").append(i + 1).append(". ").append(s) }
            }
            append("\n\n— ").append(context.getString(R.string.app_name))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            val image = File(r.coverImage)
            if (image.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }
}
