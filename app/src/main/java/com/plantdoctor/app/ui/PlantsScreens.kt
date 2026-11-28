package com.plantdoctor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.R
import com.plantdoctor.app.Route
import com.plantdoctor.app.data.AnalysisRecord
import com.plantdoctor.app.data.Plant

@Composable
fun PlantsScreen(plants: List<Plant>, analyses: List<AnalysisRecord>, onOpen: (Route) -> Unit) {
    if (plants.isEmpty()) {
        EmptyState(
            Icons.Outlined.Yard,
            stringResource(R.string.empty_plants_title),
            stringResource(R.string.empty_plants_text),
            Modifier.padding(top = 48.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(plants, key = { it.id }) { p ->
            val latest = analyses.firstOrNull { it.plantId == p.id }
            val sub = if (p.remindersOn && p.nextWatering() > 0) {
                "💧 " + dueText(p.nextWatering())
            } else p.species
            PhotoCard(
                path = p.coverImagePath,
                title = p.name,
                subtitle = sub,
                status = latest?.analysis?.healthStatus,
                modifier = Modifier.aspectRatio(0.8f).clickable { onOpen(Route.PlantDetail(p.id)) },
            )
        }
    }
}

@Composable
fun PlantDetailScreen(
    plant: Plant,
    analyses: List<AnalysisRecord>,
    onUpdate: (Plant) -> Unit,
    onWatered: () -> Unit,
    onFed: () -> Unit,
    onEnableReminders: () -> Unit,
    onNewAnalysis: () -> Unit,
    onOpen: (Route) -> Unit,
    onDelete: () -> Unit,
) {
    val own = analyses.filter { it.plantId == plant.id }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                PhotoImage(plant.coverImagePath, Modifier.fillMaxSize(), reqSize = 1400)
                Box(Modifier.fillMaxSize().background(PhotoScrim))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    own.firstOrNull()?.let { StatusPill(it.analysis.healthStatus, onPhoto = true) }
                    Spacer(Modifier.height(6.dp))
                    Text(plant.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    if (plant.species.isNotBlank()) Text(plant.species, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TaskCard(
                    Icons.Outlined.WaterDrop, stringResource(R.string.care_watering),
                    if (plant.wateringDays > 0) dueText(plant.nextWatering()) else "—",
                    stringResource(R.string.mark_watered), onWatered, Modifier.weight(1f),
                )
                TaskCard(
                    Icons.Outlined.Science, stringResource(R.string.care_fertilizing),
                    if (plant.fertilizingDays > 0) dueText(plant.nextFertilizing()) else "—",
                    stringResource(R.string.mark_fed), onFed, Modifier.weight(1f),
                )
            }
        }

        item {
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reminders_on), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = plant.remindersOn,
                            onCheckedChange = { on ->
                                onUpdate(plant.copy(remindersOn = on))
                                if (on) onEnableReminders()
                            },
                        )
                    }
                    IntervalRow(stringResource(R.string.care_watering), plant.wateringDays) { onUpdate(plant.copy(wateringDays = it)) }
                    IntervalRow(stringResource(R.string.care_fertilizing), plant.fertilizingDays) { onUpdate(plant.copy(fertilizingDays = it)) }
                }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewAnalysis, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_analysis), maxLines = 1)
                }
                IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.rename)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
            }
        }

        if (own.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.analyses_of_plant), Modifier.padding(horizontal = 16.dp)) }
            items(own, key = { it.id }) { r -> AnalysisRow(r, Modifier.padding(horizontal = 16.dp)) { onOpen(Route.Result(r.id)) } }
        }
    }

    if (rename) {
        var name by rememberSaveable { mutableStateOf(plant.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.plant_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { rename = false; onUpdate(plant.copy(name = name.trim())) }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_plant_q)) },
            text = { Text(stringResource(R.string.delete_plant_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TaskCard(icon: ImageVector, title: String, due: String, action: String, onAction: () -> Unit, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(due, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(action, maxLines = 1)
            }
        }
    }
}

@Composable
private fun IntervalRow(title: String, days: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((days - 1).coerceAtLeast(0)) }) { Icon(Icons.Filled.Remove, "−") }
        Text(
            if (days > 0) stringResource(R.string.every, pluralStringResource(R.plurals.days, days, days)) else stringResource(R.string.off),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = { onChange((days + 1).coerceAtMost(90)) }) { Icon(Icons.Filled.Add, "+") }
    }
}

@Composable
fun AnalysisRow(r: AnalysisRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoImage(r.coverImage, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)), reqSize = 200)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDateTime(r.timestamp), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                StatusPill(r.analysis.healthStatus)
            }
            Text("${r.analysis.healthScore}", style = MaterialTheme.typography.titleLarge, color = r.analysis.healthStatus.color())
        }
    }
}
