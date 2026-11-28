package com.plantdoctor.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.R
import com.plantdoctor.app.Route
import com.plantdoctor.app.UiState
import com.plantdoctor.app.data.Plant

@Composable
fun HomeScreen(
    state: UiState,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onOpen: (Route) -> Unit,
    onSeeAll: () -> Unit,
    onWatered: (Plant) -> Unit,
    onFed: (Plant) -> Unit,
) {
    val due = state.plants.filter { it.remindersOn && (isDue(it.nextWatering()) || isDue(it.nextFertilizing())) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ScanHero(onTakePhoto, onPickGallery) }

        if (due.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.due_today)) }
            items(due, key = { "due_" + it.id }) { plant ->
                DueRow(plant, onOpen = { onOpen(Route.PlantDetail(plant.id)) }, onWatered = { onWatered(plant) }, onFed = { onFed(plant) })
            }
        }

        if (state.analyses.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.LocalFlorist,
                    stringResource(R.string.empty_history_title),
                    stringResource(R.string.empty_history_text),
                )
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.recent), Modifier.weight(1f))
                    TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.analyses.take(8), key = { it.id }) { r ->
                        PhotoCard(
                            path = r.coverImage,
                            title = r.analysis.plantName.ifBlank { stringResource(R.string.unknown_plant) },
                            subtitle = formatDate(r.timestamp),
                            status = r.analysis.healthStatus,
                            modifier = Modifier
                                .width(150.dp)
                                .height(190.dp)
                                .clickable { onOpen(Route.Result(r.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHero(onTakePhoto: () -> Unit, onPickGallery: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring",
    )
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(c.primaryContainer, c.surfaceContainerLow)))
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = c.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = c.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(28.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(150.dp)
                        .scale(ring)
                        .clip(CircleShape)
                        .background(c.primary.copy(alpha = 0.15f))
                )
                Box(
                    Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .background(c.primary)
                        .clickable(onClick = onTakePhoto),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.take_photo),
                        tint = c.onPrimary,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPickGallery) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.from_gallery))
            }
        }
    }
}

@Composable
private fun DueRow(plant: Plant, onOpen: () -> Unit, onWatered: () -> Unit, onFed: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoImage(plant.coverImagePath, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), reqSize = 200)
            Spacer(Modifier.width(12.dp))
            Text(plant.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
            if (isDue(plant.nextWatering())) {
                FilledTonalButton(onClick = onWatered, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.mark_watered))
                }
            } else if (isDue(plant.nextFertilizing())) {
                FilledTonalButton(onClick = onFed, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.mark_fed))
                }
            }
        }
    }
}
