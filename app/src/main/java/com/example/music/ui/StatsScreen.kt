package com.example.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.R
import com.example.music.Song

/** Экран статистики: счётчики, топ исполнителей, самые заигранные и история. */
@Composable
fun StatsScreen(
    songs: List<Song>,
    favoritesCount: Int,
    playCounts: Map<Long, Int>,
    historyIds: List<Long>,
    modifier: Modifier = Modifier,
    onPlaySong: (Song) -> Unit = {}
) {
    if (songs.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val totalMs = remember(songs) { songs.sumOf { it.durationMs } }
    val artistCount = remember(songs) { songs.map { it.artist }.distinct().size }
    val topArtists = remember(songs) {
        songs.groupingBy { it.artist }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val topPlayed = remember(playCounts) {
        playCounts.toList().sortedByDescending { it.second }.take(5)
    }
    val recent = remember(historyIds, songsById) {
        historyIds.mapNotNull { songsById[it] }.take(5)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = songs.size.toString(),
                    label = stringResource(R.string.stats_tracks),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = favoritesCount.toString(),
                    label = stringResource(R.string.stats_favorites),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = formatTotalTime(totalMs),
                    label = stringResource(R.string.stats_total_time),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = artistCount.toString(),
                    label = stringResource(R.string.stats_artists),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (topPlayed.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stats_most_played),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(topPlayed) { (id, count) ->
                songsById[id]?.let { song ->
                    SongCard(
                        song = song,
                        trailing = pluralStringResource(R.plurals.plays_count, count, count),
                        onClick = { onPlaySong(song) }
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stats_recent),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(recent, key = { it.id }) { song ->
                SongCard(song = song, trailing = null, onClick = { onPlaySong(song) })
            }
        }

        item {
            Text(
                text = stringResource(R.string.stats_top_artists),
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(topArtists) { (artist, count) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = artist,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = pluralStringResource(R.plurals.tracks_count, count, count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
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

/** Карточка песни с необязательным текстом справа; тап — включить. */
@Composable
private fun SongCard(song: Song, trailing: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.artist,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (trailing != null) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** "3 ч 25 мин" — общее время в часах и минутах. */
private fun formatTotalTime(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "$hours ч $minutes мин"
}
