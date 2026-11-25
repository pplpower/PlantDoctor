package com.plantdoctor.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.Screen
import com.plantdoctor.app.UiState
import com.plantdoctor.app.data.HistoryEntry
import com.plantdoctor.app.data.ImageUtils
import com.plantdoctor.app.data.PlantAnalysis
import com.plantdoctor.app.data.Problem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    state: UiState,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onOpen: (Screen) -> Unit,
    onBack: () -> Unit,
    onAnalyze: (Screen.Preview, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        val msg = state.error
        if (msg != null) {
            snackbar.showSnackbar(msg)
            onDismissError()
        }
    }

    val screen = state.screen
    val title = when (screen) {
        Screen.Home -> "Зелёный доктор"
        is Screen.Preview -> "Новый анализ"
        is Screen.Analyzing -> "Анализ…"
        is Screen.Result -> screen.entry.analysis.plantName.ifBlank { "Результат" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (screen !is Screen.Home) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (screen) {
                Screen.Home -> HomeScreen(state, onTakePhoto, onPickGallery, onOpen)
                is Screen.Preview -> PreviewScreen(screen, onAnalyze, onBack)
                is Screen.Analyzing -> AnalyzingScreen(screen.bitmap)
                is Screen.Result -> ResultScreen(screen.entry, onDelete)
            }
        }
    }
}

// ---------------- Главный экран ----------------

@Composable
private fun HomeScreen(
    state: UiState,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onOpen: (Screen) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Icon(
                        Icons.Filled.LocalFlorist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Сфотографируйте растение",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Определю вид, найду болезни и вредителей, оценю состояние и подскажу, как ухаживать.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сделать фото")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onPickGallery, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать из галереи")
                    }
                }
            }
        }

        item {
            Text(
                if (state.history.isEmpty()) "История пока пуста" else "История",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(state.history, key = { it.id }) { entry ->
            HistoryRow(entry) { onOpen(Screen.Result(entry)) }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val a = entry.analysis
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(entry.imagePath, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.plantName.ifBlank { "Неизвестное растение" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(formatDate(entry.timestamp), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                StatusBadge(a)
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String, modifier: Modifier, reqSize: Int = 400) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { ImageUtils.decodeThumbnail(path, reqSize)?.asImageBitmap() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StatusBadge(a: PlantAnalysis) {
    val c = a.healthStatus.color()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(c)
        )
        Spacer(Modifier.width(6.dp))
        Text(a.healthStatus.label, style = MaterialTheme.typography.labelMedium, color = c)
    }
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru")).format(Date(ts))

// ---------------- Предпросмотр ----------------

@Composable
private fun PreviewScreen(
    preview: Screen.Preview,
    onAnalyze: (Screen.Preview, String) -> Unit,
    onBack: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            preview.bitmap.asImageBitmap(),
            contentDescription = "Фото растения",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Комментарий (необязательно)") },
            placeholder = { Text("Например: листья желтеют уже неделю, стоит у окна") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(onClick = { onAnalyze(preview, note) }, modifier = Modifier.fillMaxWidth()) {
            Text("Анализировать")
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Выбрать другое фото")
        }
        Text(
            "Совет: снимайте при дневном свете, чтобы в кадре были листья крупным планом. Если есть пятна или вредители — сделайте фото поближе.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Анализ ----------------

private val analyzingSteps = listOf(
    "Определяю вид растения…",
    "Осматриваю листья и стебли…",
    "Ищу признаки болезней и вредителей…",
    "Оцениваю общее состояние…",
    "Готовлю советы по уходу…",
)

@Composable
private fun AnalyzingScreen(bitmap: Bitmap) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            step = (step + 1) % analyzingSteps.size
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.5f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            CircularProgressIndicator(modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(targetState = step, label = "step") { s ->
            Text(analyzingSteps[s], style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Обычно это занимает до минуты",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Результат ----------------

@Composable
private fun ResultScreen(entry: HistoryEntry, onDelete: (String) -> Unit) {
    val a = entry.analysis
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Thumbnail(
                entry.imagePath,
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp)),
                reqSize = 1200,
            )
        }

        if (!a.isPlant) {
            item {
                InfoCard("На фото не найдено растение", Icons.Filled.PriorityHigh) {
                    Text(a.description.ifBlank { "Попробуйте сфотографировать растение крупнее." })
                }
            }
            item { DeleteButton { confirmDelete = true } }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(a.plantName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (a.latinName.isNotBlank()) {
                            Text(a.latinName, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.confidence > 0) {
                            Text(
                                "Уверенность в определении: ${a.confidence}%",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (a.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(a.description)
                        }
                    }
                }
            }

            item { HealthCard(a) }

            if (a.urgentActions.isNotEmpty()) {
                item {
                    InfoCard("Что сделать в первую очередь", Icons.Filled.PriorityHigh, highlight = true) {
                        a.urgentActions.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
                    }
                }
            }

            if (a.problems.isNotEmpty()) {
                item {
                    Text("Найденные проблемы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                }
                items(a.problems) { p -> ProblemCard(p) }
            }

            item { CareCard(a) }

            if (a.tips.isNotEmpty()) {
                item {
                    InfoCard("Полезные советы", Icons.Filled.Lightbulb) {
                        a.tips.forEach { BulletLine(it) }
                    }
                }
            }

            item {
                Text(
                    "Анализ выполнен ИИ и может ошибаться. При серьёзных проблемах проконсультируйтесь со специалистом.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { DeleteButton { confirmDelete = true } }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить анализ?") },
            text = { Text("Запись и фото будут удалены из истории.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(entry.id) }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Delete, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Удалить из истории")
    }
}

@Composable
private fun HealthCard(a: PlantAnalysis) {
    val c = a.healthStatus.color()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Состояние", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.healthStatus.label,
                    color = c,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("${a.healthScore}/100", style = MaterialTheme.typography.titleMedium, color = c)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { a.healthScore / 100f },
                color = c,
                trackColor = c.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            if (a.healthSummary.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(a.healthSummary)
            }
        }
    }
}

@Composable
private fun ProblemCard(p: Problem) {
    val c = severityColor(p.severity)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, contentDescription = null, tint = c)
                Spacer(Modifier.width(8.dp))
                Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.type.isNotBlank()) Chip(p.type, MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.severity.isNotBlank()) Chip("Серьёзность: ${p.severity}", c)
            }
            if (p.symptoms.isNotBlank()) LabeledText("Признаки", p.symptoms)
            if (p.cause.isNotBlank()) LabeledText("Причина", p.cause)
            if (p.treatment.isNotEmpty()) {
                Text("Лечение", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                p.treatment.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
            }
        }
    }
}

@Composable
private fun CareCard(a: PlantAnalysis) {
    val rows = listOf(
        Triple(Icons.Filled.WaterDrop, "Полив", a.care.watering),
        Triple(Icons.Filled.WbSunny, "Свет", a.care.light),
        Triple(Icons.Filled.Thermostat, "Температура", a.care.temperature),
        Triple(Icons.Filled.Cloud, "Влажность", a.care.humidity),
        Triple(Icons.Filled.Grass, "Грунт и пересадка", a.care.soil),
        Triple(Icons.Filled.Science, "Подкормки", a.care.fertilizing),
        Triple(Icons.Filled.ContentCut, "Обрезка", a.care.pruning),
    ).filter { it.third.isNotBlank() }
    if (rows.isEmpty()) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Как ухаживать", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (icon, title, text) ->
                Row {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(text)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun LabeledText(label: String, text: String) {
    Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    Text(text)
}

@Composable
private fun NumberedLine(n: Int, text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$n.", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
        Text(text)
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", modifier = Modifier.width(16.dp))
        Text(text)
    }
}
