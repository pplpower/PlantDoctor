package com.plantdoctor.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.R
import com.plantdoctor.app.data.ErrorReason
import com.plantdoctor.app.data.HealthStatus
import com.plantdoctor.app.data.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Loads a photo from app storage off the main thread. */
@Composable
fun PhotoImage(path: String, modifier: Modifier = Modifier, reqSize: Int = 500) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path, reqSize) {
        value = withContext(Dispatchers.IO) { ImageUtils.decodeThumbnail(path, reqSize)?.asImageBitmap() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Outlined.Eco, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
    }
}

/** Dark gradient at the bottom of a photo so white text on top stays readable. */
val PhotoScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.75f),
)

/** Circular health score that fills up when shown. */
@Composable
fun HealthRing(score: Int, status: HealthStatus, size: Dp = 112.dp, stroke: Dp = 10.dp) {
    val color = status.color()
    val progress = remember(score) { Animatable(0f) }
    LaunchedEffect(score) { progress.animateTo(score / 100f, tween(900)) }
    val track = color.copy(alpha = 0.18f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            drawArc(track, -90f, 360f, false, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * progress.value, false, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text("/100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@StringRes
fun HealthStatus.label(): Int = when (this) {
    HealthStatus.HEALTHY -> R.string.health_healthy
    HealthStatus.ATTENTION -> R.string.health_attention
    HealthStatus.SICK -> R.string.health_sick
    HealthStatus.CRITICAL -> R.string.health_critical
    HealthStatus.UNKNOWN -> R.string.health_unknown
}

@Composable
fun StatusPill(status: HealthStatus, onPhoto: Boolean = false) {
    val c = status.color()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (onPhoto) Color.Black.copy(alpha = 0.45f) else c.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(status.label()),
            style = MaterialTheme.typography.labelSmall,
            color = if (onPhoto) Color.White else c,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun formatDate(ts: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))

fun formatDateTime(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

private fun dayIndex(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) * 400L + c.get(Calendar.DAY_OF_YEAR)
}

/** True if the date is today or already in the past. */
fun isDue(ts: Long): Boolean = ts > 0 && dayIndex(ts) <= dayIndex(System.currentTimeMillis())

/** "today", "overdue" or a date. */
@Composable
fun dueText(ts: Long): String {
    val today = dayIndex(System.currentTimeMillis())
    val d = dayIndex(ts)
    return when {
        d < today -> stringResource(R.string.overdue)
        d == today -> stringResource(R.string.today)
        else -> formatDate(ts)
    }
}

@StringRes
fun ErrorReason.message(): Int = when (this) {
    ErrorReason.NO_KEY -> R.string.err_no_key
    ErrorReason.NETWORK -> R.string.err_network
    ErrorReason.TIMEOUT -> R.string.err_timeout
    ErrorReason.BUSY -> R.string.err_busy
    ErrorReason.QUOTA -> R.string.err_quota
    ErrorReason.KEY_REJECTED -> R.string.err_key_rejected
    ErrorReason.BAD_RESPONSE -> R.string.err_bad_response
    ErrorReason.BLOCKED -> R.string.err_blocked
    ErrorReason.IMAGE_OPEN -> R.string.err_image_open
    ErrorReason.NOT_IMAGE -> R.string.err_not_image
    ErrorReason.GENERIC -> R.string.err_generic
}

/** Rounded photo card used in grids. */
@Composable
fun PhotoCard(
    path: String,
    title: String,
    subtitle: String?,
    status: HealthStatus?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        PhotoImage(path, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(PhotoScrim))
        if (status != null) {
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { StatusPill(status, onPhoto = true) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
