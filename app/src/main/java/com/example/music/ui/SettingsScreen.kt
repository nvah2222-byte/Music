package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.music.R
import com.example.music.data.AppSettings
import com.example.music.ui.theme.DefaultAccentColor
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    appVersion: String,
    updatedFrom: String,
    modifier: Modifier = Modifier,
    onAutoAlbumsChange: (Boolean) -> Unit,
    onMinSongsChange: (Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDarkModeChange: (Int) -> Unit,
    onAutoDeleteDuplicatesChange: (Boolean) -> Unit,
    onAutoDeleteDownloadsDuplicatesChange: (Boolean) -> Unit,
    onPauseOnOtherAudioChange: (Boolean) -> Unit,
    onPauseOnHeadphonesUnplugChange: (Boolean) -> Unit,
    onFadeOutMsChange: (Int) -> Unit,
    onFunRandomModeChange: (Boolean) -> Unit,
    onWheelVariantChange: (Int) -> Unit,
    onResumeOnFocusGainChange: (Boolean) -> Unit,
    onWheelSpeedPercentChange: (Int) -> Unit,
    onResumeLongTracksChange: (Boolean) -> Unit,
    onDrawerEditableChange: (Boolean) -> Unit,
    onAccentHueChange: (Int) -> Unit,
    onCrossfadeMsChange: (Int) -> Unit,
    onCoverRotationChange: (Boolean) -> Unit,
    onBackgroundRotationChange: (Boolean) -> Unit,
    onSoundProfileChange: (Int) -> Unit,
    onVolumeBalanceChange: (Boolean) -> Unit,
    onTargetLoudnessDbChange: (Int) -> Unit,
    onCheckUpdatesChange: (Boolean) -> Unit,
    onUpdateRepoChange: (String) -> Unit,
    onCheckNow: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var currentJoke by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val jokes = remember { context.resources.getStringArray(R.array.jokes) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // --- Библиотека ---
        SectionTitle(stringResource(R.string.settings_section_library))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_auto_albums),
                description = stringResource(R.string.settings_auto_albums_desc, settings.minSongsForAlbum),
                checked = settings.autoAlbums,
                onCheckedChange = onAutoAlbumsChange
            )
            if (settings.autoAlbums) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_min_songs, settings.minSongsForAlbum),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.minSongsForAlbum.toFloat(),
                    onValueChange = { onMinSongsChange(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_auto_delete),
                description = stringResource(R.string.settings_auto_delete_desc),
                checked = settings.autoDeleteDuplicates,
                onCheckedChange = onAutoDeleteDuplicatesChange
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_auto_delete_downloads),
                description = stringResource(R.string.settings_auto_delete_downloads_desc),
                checked = settings.autoDeleteDownloadsDuplicates,
                onCheckedChange = onAutoDeleteDownloadsDuplicatesChange
            )
        }

        // --- Оформление ---
        SectionTitle(stringResource(R.string.settings_section_appearance))

        SettingCard {
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            val themes = listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark)
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themes.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = settings.darkMode == index,
                        onClick = { onDarkModeChange(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size)
                    ) {
                        Text(label)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                description = stringResource(R.string.settings_dynamic_color_desc),
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColorChange
            )
        }

        // Акцентный цвет: работает, когда динамический цвет выключен.
        if (!settings.dynamicColor) {
            Spacer(Modifier.height(12.dp))
            SettingCard {
                Text(
                    text = stringResource(R.string.settings_accent_color),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccentDot(
                        color = DefaultAccentColor,
                        selected = settings.accentHue < 0,
                        description = stringResource(R.string.settings_accent_default),
                        onClick = { onAccentHueChange(-1) }
                    )
                    ACCENT_HUES.forEach { hue ->
                        AccentDot(
                            color = Color.hsv(hue.toFloat(), 0.65f, 0.6f),
                            selected = settings.accentHue == hue,
                            description = stringResource(R.string.settings_accent_hue, hue),
                            onClick = { onAccentHueChange(hue) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_drawer_editable),
                description = stringResource(R.string.settings_drawer_editable_desc),
                checked = settings.drawerEditable,
                onCheckedChange = onDrawerEditableChange
            )
        }

        // --- Плеер ---
        SectionTitle(stringResource(R.string.settings_section_player))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_cover_rotation),
                description = stringResource(R.string.settings_cover_rotation_desc),
                checked = settings.coverRotation,
                onCheckedChange = onCoverRotationChange
            )
            if (settings.coverRotation) {
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_background_rotation),
                    description = stringResource(R.string.settings_background_rotation_desc),
                    checked = settings.backgroundRotation,
                    onCheckedChange = onBackgroundRotationChange
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Рофляничек: колесо фортуны вместо обычного случайного трека.
        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_fun_random),
                description = stringResource(R.string.settings_fun_random_desc),
                checked = settings.funRandomMode,
                onCheckedChange = onFunRandomModeChange
            )
            if (settings.funRandomMode) {
                Spacer(Modifier.height(8.dp))
                val variants = listOf(
                    stringResource(R.string.wheel_variant_big),
                    stringResource(R.string.wheel_variant_classic)
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    variants.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = settings.wheelVariant == index,
                            onClick = { onWheelVariantChange(index) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = variants.size
                            )
                        ) {
                            Text(label)
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_wheel_speed, settings.wheelSpeedPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Slider(
                    value = settings.wheelSpeedPercent.toFloat(),
                    onValueChange = { onWheelSpeedPercentChange((it / 10f).roundToInt() * 10) },
                    valueRange = 50f..300f,
                    steps = 24
                )
            }
        }

        // --- Звук ---
        SectionTitle(stringResource(R.string.settings_section_sound))

        SettingCard {
            Text(
                text = stringResource(R.string.settings_sound_profile),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_sound_profile_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SOUND_PROFILES.forEachIndexed { index, labelRes ->
                    FilterChip(
                        selected = settings.soundProfile == index,
                        onClick = { onSoundProfileChange(index) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_volume_balance),
                description = stringResource(R.string.settings_volume_balance_desc),
                checked = settings.volumeBalance,
                onCheckedChange = onVolumeBalanceChange
            )
            if (settings.volumeBalance) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_target_db, settings.targetLoudnessDb),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.targetLoudnessDb.toFloat(),
                    onValueChange = { onTargetLoudnessDbChange(it.roundToInt()) },
                    valueRange = -30f..-6f,
                    steps = 23
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingCard {
            Text(
                text = stringResource(R.string.settings_crossfade),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (settings.crossfadeMs <= 0) {
                    stringResource(R.string.settings_crossfade_off)
                } else {
                    stringResource(R.string.settings_crossfade_value, settings.crossfadeMs)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Slider(
                value = settings.crossfadeMs.toFloat(),
                onValueChange = { onCrossfadeMsChange((it / 200f).roundToInt() * 200) },
                valueRange = 0f..6000f,
                steps = 29
            )
            Text(
                text = stringResource(R.string.settings_fade_out),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_fade_out_value, settings.fadeOutMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Slider(
                value = settings.fadeOutMs.toFloat(),
                onValueChange = { onFadeOutMsChange((it / 100f).roundToInt() * 100) },
                valueRange = 0f..2000f,
                steps = 19
            )
        }

        // --- Воспроизведение ---
        SectionTitle(stringResource(R.string.settings_section_playback))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_pause_other_audio),
                description = stringResource(R.string.settings_pause_other_audio_desc),
                checked = settings.pauseOnOtherAudio,
                onCheckedChange = onPauseOnOtherAudioChange
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_pause_headphones),
                description = stringResource(R.string.settings_pause_headphones_desc),
                checked = settings.pauseOnHeadphonesUnplug,
                onCheckedChange = onPauseOnHeadphonesUnplugChange
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_resume_on_gain),
                description = stringResource(R.string.settings_resume_on_gain_desc),
                checked = settings.resumeOnFocusGain,
                onCheckedChange = onResumeOnFocusGainChange
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_resume_long),
                description = stringResource(R.string.settings_resume_long_desc),
                checked = settings.resumeLongTracks,
                onCheckedChange = onResumeLongTracksChange
            )
        }

        // --- Резервная копия ---
        SectionTitle(stringResource(R.string.settings_section_backup))

        SettingCard {
            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Row {
                TextButton(onClick = onExportBackup) {
                    Text(stringResource(R.string.backup_export))
                }
                TextButton(onClick = onImportBackup) {
                    Text(stringResource(R.string.backup_import))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Обновления ---
        SectionTitle(stringResource(R.string.settings_section_updates))

        SettingCard {
            SwitchRow(
                title = stringResource(R.string.settings_check_updates),
                description = stringResource(R.string.settings_check_updates_desc),
                checked = settings.checkUpdates,
                onCheckedChange = onCheckUpdatesChange
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.updateRepo,
                onValueChange = onUpdateRepoChange,
                label = { Text(stringResource(R.string.settings_update_repo)) },
                placeholder = { Text("owner/repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_app_version, appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (updatedFrom.isNotBlank()) {
                Text(
                    text = stringResource(R.string.settings_update_installed, updatedFrom, appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onCheckNow) {
                Text(stringResource(R.string.settings_check_now))
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { currentJoke = jokes.random() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎁 " + stringResource(R.string.surprise))
        }
    }

    currentJoke?.let { joke ->
        AlertDialog(
            onDismissRequest = { currentJoke = null },
            title = { Text(stringResource(R.string.surprise)) },
            text = { Text(joke) },
            confirmButton = {
                TextButton(onClick = { currentJoke = jokes.random() }) {
                    Text(stringResource(R.string.surprise_another))
                }
            },
            dismissButton = {
                TextButton(onClick = { currentJoke = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Профили звучания: индекс совпадает с AppSettings.soundProfile. */
private val SOUND_PROFILES = listOf(
    R.string.sound_profile_original,
    R.string.sound_profile_bass,
    R.string.sound_profile_vocal,
    R.string.sound_profile_rock,
    R.string.sound_profile_jazz
)

/** Оттенки акцента для быстрого выбора (стандартный фиолетовый — отдельно, -1). */
private val ACCENT_HUES = listOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330)

@Composable
private fun AccentDot(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
    )
}
