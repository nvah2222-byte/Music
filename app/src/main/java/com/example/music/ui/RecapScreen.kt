package com.example.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.music.R
import com.example.music.Song

private const val DAY_MS = 24L * 3600_000L
private const val WEEK_MS = 7L * DAY_MS
private const val MONTH_MS = 30L * DAY_MS

/** Итоги за период: любимая песня/исполнитель/альбом, сколько раз слушал. */
@Composable
fun RecapScreen(
    songs: List<Song>,
    playEvents: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier
) {
    var period by remember { mutableIntStateOf(3) }
    val now = remember { System.currentTimeMillis() }
    val cutoff = when (period) {
        0 -> now - DAY_MS
        1 -> now - WEEK_MS
        2 -> now - MONTH_MS
        else -> 0L
    }
    val songsById = songs.associateBy { it.id }
    val counts = playEvents
        .filter { it.second >= cutoff }
        .groupingBy { it.first }
        .eachCount()
    val unknownArtist = stringResource(R.string.unknown_artist)

    fun isUnknown(name: String): Boolean =
        name.isBlank() ||
            name.equals(unknownArtist, ignoreCase = true) ||
            name.equals("<unknown>", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val periodLabels = listOf(
            stringResource(R.string.recap_day),
            stringResource(R.string.recap_week),
            stringResource(R.string.recap_month),
            stringResource(R.string.recap_all)
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            periodLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = period == index,
                    onClick = { period = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = periodLabels.size)
                ) {
                    Text(label)
                }
            }
        }

        if (counts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.recap_empty),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        val totalPlays = counts.values.sum()
        val minutes = counts.entries
            .sumOf { (songsById[it.key]?.durationMs ?: 0L) * it.value } / 60_000
        val topSong = counts.maxByOrNull { it.value }?.key?.let { songsById[it] }
        val topArtist = counts.entries
            .groupBy { songsById[it.key]?.artist ?: "" }
            .filterKeys { !isUnknown(it) }
            .mapValues { (_, list) -> list.sumOf { it.value } }
            .maxByOrNull { it.value }?.key
        val topAlbum = counts.entries
            .groupBy { songsById[it.key]?.album.orEmpty() }
            .filterKeys { !isUnknown(it) }
            .mapValues { (_, list) -> list.sumOf { it.value } }
            .maxByOrNull { it.value }?.key

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecapStatCard(
                value = totalPlays.toString(),
                label = stringResource(R.string.recap_total_plays),
                modifier = Modifier.weight(1f)
            )
            RecapStatCard(
                value = minutes.toString(),
                label = stringResource(R.string.recap_minutes),
                modifier = Modifier.weight(1f)
            )
        }
        RecapBigCard(
            label = stringResource(R.string.recap_top_song),
            value = topSong?.title ?: "—",
            sub = topSong?.artist ?: ""
        )
        RecapBigCard(
            label = stringResource(R.string.recap_top_artist),
            value = topArtist ?: "—",
            sub = ""
        )
        RecapBigCard(
            label = stringResource(R.string.recap_top_album),
            value = topAlbum ?: "—",
            sub = ""
        )
    }
}

@Composable
private fun RecapStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun RecapBigCard(label: String, value: String, sub: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
