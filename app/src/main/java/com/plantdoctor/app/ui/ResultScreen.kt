package com.plantdoctor.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.R
import com.plantdoctor.app.data.AnalysisRecord
import com.plantdoctor.app.data.Plant
import com.plantdoctor.app.data.PlantAnalysis
import com.plantdoctor.app.data.Problem
import com.plantdoctor.app.data.Severity

@Composable
fun ResultScreen(
    record: AnalysisRecord,
    plants: List<Plant>,
    onAsk: () -> Unit,
    onShare: () -> Unit,
    onCreatePlant: (String) -> Unit,
    onLinkPlant: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val a = record.analysis
    var confirmDelete by remember { mutableStateOf(false) }
    var savePlant by remember { mutableStateOf(false) }
    val linked = plants.firstOrNull { it.id == record.plantId }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Hero(record) }

        if (!a.isPlant) {
            item {
                InfoCard(stringResource(R.string.not_plant_title), Icons.Outlined.PriorityHigh, Modifier.padding(horizontal = 16.dp)) {
                    Text(a.description)
                }
            }
        } else {
            item { HealthCard(a, Modifier.padding(horizontal = 16.dp)) }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        if (linked != null) {
                            AssistChip(
                                onClick = { onOpenPlant(linked.id) },
                                label = { Text(stringResource(R.string.in_plants, linked.name), maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.Yard, null, Modifier.size(18.dp)) },
                            )
                        } else {
                            AssistChip(
                                onClick = { savePlant = true },
                                label = { Text(stringResource(R.string.add_to_plants)) },
                                leadingIcon = { Icon(Icons.Outlined.Yard, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                    item {
                        AssistChip(
                            onClick = onAsk,
                            label = { Text(stringResource(R.string.ask)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(18.dp)) },
                        )
                    }
                    item {
                        AssistChip(
                            onClick = onShare,
                            label = { Text(stringResource(R.string.share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            if (a.urgentActions.isNotEmpty()) {
                item {
                    InfoCard(
                        stringResource(R.string.urgent), Icons.Outlined.PriorityHigh, Modifier.padding(horizontal = 16.dp),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        a.urgentActions.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
                    }
                }
            }

            if (a.problems.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.problems), Modifier.padding(horizontal = 16.dp)) }
                itemsIndexed(a.problems) { i, p -> ProblemCard(p, initiallyOpen = i == 0, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            item { SectionTitle(stringResource(R.string.care), Modifier.padding(horizontal = 16.dp)) }
            val tiles = careTiles(a)
            items(tiles.chunked(2)) { row ->
                Row(
                    Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { t -> CareTile(t, Modifier.weight(1f).fillMaxHeight()) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (a.tips.isNotEmpty()) {
                item {
                    InfoCard(stringResource(R.string.tips), Icons.Outlined.Lightbulb, Modifier.padding(horizontal = 16.dp)) {
                        a.tips.forEach { BulletLine(it) }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.ai_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Outlined.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_analysis_q)) },
            text = { Text(stringResource(R.string.delete_analysis_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (savePlant) {
        SavePlantDialog(
            defaultName = a.plantName,
            plants = plants,
            onDismiss = { savePlant = false },
            onCreate = { savePlant = false; onCreatePlant(it) },
            onLink = { savePlant = false; onLinkPlant(it) },
        )
    }
}

@Composable
private fun Hero(record: AnalysisRecord) {
    val a = record.analysis
    Box(Modifier.fillMaxWidth().height(340.dp)) {
        PhotoImage(record.coverImage, Modifier.fillMaxSize(), reqSize = 1400)
        Box(Modifier.fillMaxSize().background(PhotoScrim))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            if (a.isPlant) StatusPill(a.healthStatus, onPhoto = true)
            Spacer(Modifier.height(8.dp))
            Text(
                a.plantName.ifBlank { stringResource(R.string.unknown_plant) },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (a.latinName.isNotBlank()) {
                Text(a.latinName, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.85f))
            }
            Text(
                formatDateTime(record.timestamp) +
                    if (a.confidence > 0) " · " + stringResource(R.string.confidence, a.confidence) else "",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HealthCard(a: PlantAnalysis, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HealthRing(a.healthScore, a.healthStatus)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.health), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(a.healthStatus.label()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = a.healthStatus.color(),
                )
                if (a.healthSummary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(a.healthSummary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (a.description.isNotBlank()) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Text(
                a.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun ProblemCard(p: Problem, initiallyOpen: Boolean, modifier: Modifier) {
    var open by rememberSaveable(p.name) { mutableStateOf(initiallyOpen) }
    val c = p.severity.color()
    Card(
        modifier.fillMaxWidth().animateContentSize().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(c))
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BugReport, null, tint = c)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf(p.type, stringResource(R.string.severity, stringResource(p.severity.label())))
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = c,
                        )
                    }
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                if (open) {
                    if (p.symptoms.isNotBlank()) LabeledText(stringResource(R.string.symptoms), p.symptoms)
                    if (p.cause.isNotBlank()) LabeledText(stringResource(R.string.cause), p.cause)
                    if (p.treatment.isNotEmpty()) {
                        Text(
                            stringResource(R.string.treatment), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                        p.treatment.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
                    }
                }
            }
        }
    }
}

fun Severity.label(): Int = when (this) {
    Severity.LOW -> R.string.severity_low
    Severity.MEDIUM -> R.string.severity_medium
    Severity.HIGH -> R.string.severity_high
}

private class Tile(val icon: ImageVector, val title: Int, val text: String)

private fun careTiles(a: PlantAnalysis) = listOf(
    Tile(Icons.Outlined.WaterDrop, R.string.care_watering, a.care.watering),
    Tile(Icons.Outlined.WbSunny, R.string.care_light, a.care.light),
    Tile(Icons.Outlined.Thermostat, R.string.care_temperature, a.care.temperature),
    Tile(Icons.Outlined.Cloud, R.string.care_humidity, a.care.humidity),
    Tile(Icons.Outlined.Grass, R.string.care_soil, a.care.soil),
    Tile(Icons.Outlined.Science, R.string.care_fertilizing, a.care.fertilizing),
    Tile(Icons.Outlined.ContentCut, R.string.care_pruning, a.care.pruning),
).filter { it.text.isNotBlank() }

@Composable
private fun CareTile(t: Tile, modifier: Modifier) {
    var open by rememberSaveable(t.title) { mutableStateOf(false) }
    Card(
        modifier.animateContentSize().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp).heightIn(min = 110.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(t.title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                t.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledText(label: String, text: String) {
    Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    Text(text)
}

@Composable
fun NumberedLine(n: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$n.", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
        Text(text)
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", modifier = Modifier.width(16.dp))
        Text(text)
    }
}

@Composable
private fun SavePlantDialog(
    defaultName: String,
    plants: List<Plant>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LocalFlorist, null) },
        title = { Text(stringResource(R.string.add_to_plants)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.plant_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onCreate(name) }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.new_plant))
                }
                if (plants.isNotEmpty()) {
                    Text(stringResource(R.string.or_existing), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    plants.take(6).forEach { p ->
                        FilledTonalButton(onClick = { onLink(p.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
