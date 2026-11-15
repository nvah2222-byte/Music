package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.music.R

/**
 * Выбор показа групп одинаковых обложек + мини-пример выбранного варианта.
 * Используется и в настройках, и в диалоге из списка песен.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoverGroupPicker(
    mode: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val labels = listOf(
                R.string.cover_group_collapse,
                R.string.cover_group_headers,
                R.string.cover_group_screens,
                R.string.cover_group_compact
            )
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = mode == index,
                    onClick = { onChange(index) },
                    label = { Text(stringResource(label)) },
                    leadingIcon = if (mode == index) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
        val descriptions = listOf(
            R.string.cover_group_collapse_desc,
            R.string.cover_group_headers_desc,
            R.string.cover_group_screens_desc,
            R.string.cover_group_compact_desc
        )
        Text(
            text = stringResource(descriptions[mode.coerceIn(0, 3)]),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.cover_group_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(6.dp))
        CoverGroupPreview(mode)
    }
}

/** Наглядный пример: те же строки, что и в списке песен, только маленькие. */
@Composable
private fun CoverGroupPreview(mode: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        PreviewGroupHeader(
            title = "Валентин Стрыкало",
            count = 9,
            chevron = when (mode) {
                0 -> "⌄"
                2 -> "›"
                else -> ""
            }
        )
        when (mode) {
            0 -> {
                PreviewSong("Знаешь, Таня", "4:35", showArtist = true)
                PreviewSong("Кладбище Самолётов", "5:54", showArtist = true)
                PreviewGroupHeader("Птицу Емъ", 7, "⌄")
            }
            1 -> {
                PreviewSong("Знаешь, Таня", "4:35", true)
                PreviewSong("Кладбище Самолётов", "5:54", true)
                PreviewSong("Офисный Стиляга", "2:15", true)
            }
            2 -> {
                PreviewGroupHeader("Птицу Емъ", 7, "›")
                PreviewGroupHeader("ЯйцЫ Fаберже", 3, "›")
                PreviewGroupHeader("Hools", 1, "›")
            }
            else -> {
                PreviewSong("Знаешь, Таня", "4:35", showArtist = false)
                PreviewSong("Кладбище Самолётов", "5:54", false)
                PreviewSong("Офисный Стиляга", "2:15", false)
                PreviewSong("Сера - Винтовар", "2:34", false)
            }
        }
    }
}

@Composable
private fun PreviewGroupHeader(title: String, count: Int, chevron: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = pluralStringResource(R.plurals.tracks_count, count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            text = chevron,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun PreviewSong(title: String, duration: String, showArtist: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            if (showArtist) {
                Text(
                    text = "Валентин Стрыкало",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Text(
            text = duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
