package com.plantdoctor.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.CaptureState
import com.plantdoctor.app.PlantViewModel
import com.plantdoctor.app.R
import com.plantdoctor.app.data.Plant
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    capture: CaptureState,
    plants: List<Plant>,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onNote: (String) -> Unit,
    onPlant: (String?) -> Unit,
    onAnalyze: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val main = capture.photos.firstOrNull()
        if (main != null) {
            Image(
                main.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            capture.photos.forEachIndexed { i, p ->
                Box(Modifier.size(84.dp)) {
                    Image(
                        p.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onRemovePhoto(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (capture.photos.size < PlantViewModel.MAX_PHOTOS) {
                AddTile(Icons.Outlined.AddAPhoto, stringResource(R.string.take_photo), onTakePhoto)
                AddTile(Icons.Outlined.PhotoLibrary, stringResource(R.string.from_gallery), onPickGallery)
            }
        }
        Text(
            stringResource(R.string.photos_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = capture.note,
            onValueChange = onNote,
            label = { Text(stringResource(R.string.note_label)) },
            placeholder = { Text(stringResource(R.string.note_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (plants.isNotEmpty()) PlantPicker(plants, capture.plantId, onPlant)
        Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.analyze), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AddTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun PlantPicker(plants: List<Plant>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = plants.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.no_plant)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${stringResource(R.string.for_plant)}: $name", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.no_plant)) }, onClick = { onSelect(null); open = false })
            plants.forEach { p ->
                DropdownMenuItem(text = { Text(p.name) }, onClick = { onSelect(p.id); open = false })
            }
        }
    }
}

private val steps = listOf(R.string.step_1, R.string.step_2, R.string.step_3, R.string.step_4, R.string.step_5)

@Composable
fun AnalyzingScreen(capture: CaptureState, onCancel: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            step = (step + 1) % steps.size
        }
    }
    val scan = rememberInfiniteTransition(label = "scan")
    val pos by scan.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "pos",
    )
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .clip(RoundedCornerShape(28.dp))
        ) {
            capture.photos.firstOrNull()?.let {
                Image(it.bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            // Scanning beam moving up and down over the photo.
            val beam = 90.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(beam)
                    .offset(y = (maxHeight - beam) * pos)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, primary.copy(alpha = 0.55f), Color.Transparent)))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = (maxHeight - beam) * pos + beam / 2)
                    .background(primary)
            )
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(targetState = step, label = "step") { s ->
            Text(stringResource(steps[s]), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.analyzing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}
