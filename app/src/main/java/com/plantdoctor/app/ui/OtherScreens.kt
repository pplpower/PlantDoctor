package com.plantdoctor.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.BuildConfig
import com.plantdoctor.app.R
import com.plantdoctor.app.Route
import com.plantdoctor.app.data.AnalysisRecord
import com.plantdoctor.app.data.HealthStatus

// ---------------- History ----------------

private enum class Filter(val label: Int, val match: (HealthStatus) -> Boolean) {
    ALL(R.string.filter_all, { true }),
    HEALTHY(R.string.health_healthy, { it == HealthStatus.HEALTHY }),
    ATTENTION(R.string.health_attention, { it == HealthStatus.ATTENTION }),
    SICK(R.string.filter_sick, { it == HealthStatus.SICK || it == HealthStatus.CRITICAL }),
}

@Composable
fun HistoryScreen(analyses: List<AnalysisRecord>, onOpen: (Route) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val q = query.trim().lowercase()
    val shown = analyses.filter { r ->
        filter.match(r.analysis.healthStatus) &&
            (q.isEmpty() || r.analysis.plantName.lowercase().contains(q) || r.analysis.latinName.lowercase().contains(q) ||
                r.analysis.problems.any { it.name.lowercase().contains(q) })
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Filter.entries.toList()) { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) })
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                Icons.Outlined.SearchOff,
                stringResource(if (analyses.isEmpty()) R.string.empty_history_title else R.string.history_empty_search),
                if (analyses.isEmpty()) stringResource(R.string.empty_history_text) else "",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { r ->
                    PhotoCard(
                        path = r.coverImage,
                        title = r.analysis.plantName.ifBlank { stringResource(R.string.unknown_plant) },
                        subtitle = formatDate(r.timestamp),
                        status = r.analysis.healthStatus,
                        modifier = Modifier.aspectRatio(0.8f).clickable { onOpen(Route.Result(r.id)) },
                    )
                }
            }
        }
    }
}

// ---------------- Chat ----------------

@Composable
fun ChatScreen(record: AnalysisRecord, busy: Boolean, onSend: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(record.chat.size, busy) {
        val count = record.chat.size + (if (busy) 1 else 0) + 1
        list.animateScrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhotoImage(record.coverImage, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), reqSize = 200)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(record.analysis.plantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(record.chat) { m -> Bubble(m.text, m.fromUser) }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.thinking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (record.chat.isEmpty() && !busy) {
            val suggestions = listOf(R.string.chat_suggest_1, R.string.chat_suggest_2, R.string.chat_suggest_3).map { stringResource(it) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { s -> SuggestionChip(onClick = { onSend(s) }, label = { Text(s) }) }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { onSend(input); input = "" },
                enabled = input.isNotBlank() && !busy,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
            }
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            color = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (fromUser) 20.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 20.dp,
                    )
                )
                .background(if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(onExport: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        InfoCard(stringResource(R.string.settings_language), Icons.Outlined.Language) {
            Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(10.dp))
            FilledTonalButton(onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                } else {
                    Intent(Settings.ACTION_LOCALE_SETTINGS)
                }
                runCatching { context.startActivity(intent) }
            }) { Text(stringResource(R.string.change_language)) }
        }
        InfoCard(stringResource(R.string.settings_backup), Icons.Outlined.Backup) {
            Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onExport) { Text(stringResource(R.string.export)) }
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_data)) }
            }
        }
        InfoCard(stringResource(R.string.settings_about), Icons.Outlined.Info) {
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
