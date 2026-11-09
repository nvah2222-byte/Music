package com.example.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.R
import com.example.music.Song
import com.example.music.matchesQuery

/**
 * Поиск треков по названию И исполнителю с выбором галочками.
 * [excludeIds] — уже добавленные треки: они в результатах не показываются.
 * Используется для создания альбома и для пополнения готового.
 */
@Composable
fun SongPickerDialog(
    title: String,
    songs: List<Song>,
    excludeIds: Set<Long> = emptySet(),
    initialSelectedIds: Set<Long> = emptySet(),
    /** Имена уже существующих альбомов (в нижнем регистре) — чтобы не создать дубль. */
    existingNames: Set<String> = emptySet(),
    showNameField: Boolean = false,
    confirmLabel: String = stringResource(R.string.create),
    onDismiss: () -> Unit,
    onConfirm: (name: String, songIds: List<Long>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selected by remember(initialSelectedIds) { mutableStateOf(initialSelectedIds) }
    val nameTaken = showNameField && name.trim().lowercase() in existingNames

    val candidates = remember(songs, excludeIds, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            songs.filter { it.id !in excludeIds && it.matchesQuery(query) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.create_album_query)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showNameField) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.create_album_name)) },
                        singleLine = true,
                        isError = nameTaken,
                        supportingText = if (nameTaken) {
                            { Text(stringResource(R.string.create_album_name_taken)) }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = candidates.map { it.id }.toSet() }) {
                        Text(stringResource(R.string.select_all))
                    }
                    TextButton(onClick = { selected = emptySet() }) {
                        Text(stringResource(R.string.deselect_all))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.create_album_selected, selected.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (song.id in selected) {
                                        selected - song.id
                                    } else {
                                        selected + song.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = song.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + song.id else selected - song.id
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() && (!showNameField || (name.isNotBlank() && !nameTaken)),
                onClick = {
                    // Берём всё выбранное, а не только текущую выдачу поиска.
                    onConfirm(name.trim(), songs.filter { it.id in selected }.map { it.id })
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
