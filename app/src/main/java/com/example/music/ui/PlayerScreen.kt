package com.example.music.ui

import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.music.PlaybackService
import com.example.music.R
import com.example.music.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** Полноэкранный плеер: обложка, управление, случайный порядок, повтор и таймер сна. */
@Composable
fun PlayerScreen(
    player: MediaController?,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    favoriteUris: Set<Uri> = emptySet(),
    onToggleFavorite: ((Uri) -> Unit)? = null,
    songsUntilStop: Int? = null,
    onSongsUntilStopChange: (Int?) -> Unit = {},
    sleepTimerEndAt: Long? = null,
    onSleepTimerChange: (Long?) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    coverRotation: Boolean = true,
    rotateBackground: Boolean = false
) {
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(player?.shuffleModeEnabled == true) }
    var repeatMode by remember { mutableIntStateOf(player?.repeatMode ?: Player.REPEAT_MODE_OFF) }
    var currentItem by remember { mutableStateOf(player?.currentMediaItem) }
    var playbackSpeed by remember { mutableFloatStateOf(player?.playbackParameters?.speed ?: 1f) }
    var isSeeking by remember { mutableStateOf(false) }

    // --- Эквалайзер ---
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var eqPresets by remember { mutableStateOf<List<Pair<Short, String>>>(emptyList()) }
    var eqMenuExpanded by remember { mutableStateOf(false) }

    // --- Приколы: вибрация при смене трека и пасхалка ---
    val haptic = LocalHapticFeedback.current
    var trackTick by remember { mutableIntStateOf(0) }
    var titleTaps by remember { mutableIntStateOf(0) }
    var easterEggJoke by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val jokes = remember { context.resources.getStringArray(R.array.jokes) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = player?.duration?.coerceAtLeast(0L) ?: 0L
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled = enabled
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentItem = mediaItem
                duration = player?.duration?.coerceAtLeast(0L) ?: 0L
                trackTick++
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }
        }
        player?.addListener(listener)
        if (player != null) {
            // Экран мог открыться посреди трека: сразу подтягиваем текущее состояние,
            // иначе длительность и полоска остаются нулевыми до следующего события плеера.
            isPlaying = player.isPlaying
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            currentItem = player.currentMediaItem
            shuffleEnabled = player.shuffleModeEnabled
            repeatMode = player.repeatMode
            playbackSpeed = player.playbackParameters.speed
        }
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            // Пока пользователь тянет ползунок, не перезаписываем позицию.
            if (!isSeeking) {
                currentPosition = player?.currentPosition ?: 0L
            }
            // Длительность может стать известна позже готовности плеера — держим её в актуальном виде.
            val total = player?.duration?.coerceAtLeast(0L) ?: 0L
            if (total > 0) duration = total
            delay(100)
        }
    }

    // --- Остановка через N песен ---
    var stopMenuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    // Вибрация при смене трека.
    LaunchedEffect(trackTick) {
        if (trackTick > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Эквалайзер живёт в PlaybackService (у него есть аудио-сессия плеера).
    // Пока идёт воспроизведение, забираем его оттуда и читаем пресеты.
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var attempts = 0
        while (equalizer == null && attempts < 10) {
            equalizer = PlaybackService.equalizer
            if (equalizer != null) break
            delay(300)
            attempts++
        }
        val eq = equalizer ?: return@LaunchedEffect
        if (eqPresets.isEmpty()) {
            eqPresets = (0 until eq.numberOfPresets.toInt()).map { index ->
                index.toShort() to (eq.getPresetName(index.toShort()) ?: "Preset $index")
            }
        }
    }

    DisposableEffect(Unit) {
        // Эквалайзер освобождает сервис, здесь только забываем ссылку.
        onDispose { equalizer = null }
    }

    BackHandler(onBack = onClose)

    val metadata = currentItem?.mediaMetadata
    val currentUri = currentItem?.localConfiguration?.uri
    val title = metadata?.title?.toString().orEmpty()
        .ifEmpty { stringResource(R.string.unknown_title) }
    val artist = metadata?.artist?.toString().orEmpty()
        .ifEmpty { stringResource(R.string.unknown_artist) }
    val artworkUri = metadata?.artworkUri

    // Угол вращения обложки; тот же угол используется размытым фоном.
    var coverAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, coverRotation) {
        if (!isPlaying || !coverRotation) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    // Полный оборот за 15 секунд.
                    coverAngle = (coverAngle + (now - lastNanos) / 1_000_000_000f * 24f) % 360f
                }
                lastNanos = now
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Фон — размытая обложка текущего трека.
        if (artworkUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(64, 64)
                    .memoryCacheKey("cover-blur:$artworkUri")
                    .crossfade(300)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (coverRotation && rotateBackground) coverAngle else 0f
                        // С запасом, чтобы при повороте не оголялись углы экрана.
                        scaleX = 1.6f
                        scaleY = 1.6f
                    }
                    .blur(50.dp)
                    .alpha(0.8f)
            )
        }
        // Затемнение к низу: обложка видна, а текст и кнопки читаются в любой теме.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                        0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.now_playing),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        // Пасхалка: пять тапов по заголовку — шутка.
                        titleTaps++
                        if (titleTaps >= 5) {
                            easterEggJoke = jokes.random()
                            titleTaps = 0
                        }
                    },
                textAlign = TextAlign.Center
            )
            Row {
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue_open))
                }
                Box {
                    TextButton(onClick = { speedMenuExpanded = true }) {
                        Text(speedLabel(playbackSpeed))
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { speedMenuExpanded = false }
                    ) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(speed)) },
                                onClick = {
                                    speedMenuExpanded = false
                                    player?.setPlaybackSpeed(speed)
                                }
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { stopMenuExpanded = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            stringResource(R.string.stop_timer),
                            tint = if (songsUntilStop != null || sleepTimerEndAt != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = stopMenuExpanded,
                        onDismissRequest = { stopMenuExpanded = false }
                    ) {
                        listOf(1, 3, 5, 10, 20).forEach { count ->
                            DropdownMenuItem(
                                text = {
                                    Text(pluralStringResource(R.plurals.songs_count, count, count))
                                },
                                onClick = {
                                    stopMenuExpanded = false
                                    onSongsUntilStopChange(count)
                                }
                            )
                        }
                        listOf(15, 30, 60, 90).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sleep_minutes, minutes)) },
                                onClick = {
                                    stopMenuExpanded = false
                                    onSleepTimerChange(
                                        SystemClock.elapsedRealtime() + minutes * 60_000L
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stop_cancel)) },
                            onClick = {
                                stopMenuExpanded = false
                                onSongsUntilStopChange(null)
                                onSleepTimerChange(null)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // «Винил»: обложка медленно крутится, пока играет музыка, и замирает на паузе.
        val swipeThreshold = with(LocalDensity.current) { 120.dp.toPx() }
        CoverBox(
            seed = (artworkUri?.hashCode() ?: 0).toLong(),
            data = artworkUri,
            imageLoader = imageLoader,
            requestSize = 1024,
            original = true,
            cornerRadius = 16.dp,
            iconFraction = 0.35f,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .graphicsLayer { rotationZ = if (coverRotation) coverAngle else 0f }
                .pointerInput(player) {
                    var sum = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            sum += dragAmount
                        },
                        onDragEnd = {
                            when {
                                sum < -250f -> player?.seekToNext()
                                sum > 250f -> player?.seekToPrevious()
                            }
                            sum = 0f
                        }
                    )
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            total += dragAmount
                        },
                        onDragEnd = {
                            when {
                                total < -swipeThreshold -> onOpenQueue()
                                total > swipeThreshold -> onClose()
                            }
                            total = 0f
                        }
                    )
                }
                .pointerInput(duration, player) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val step = 10_000L
                            val target = if (offset.x < size.width / 2f) {
                                player?.currentPosition?.minus(step)
                            } else {
                                player?.currentPosition?.plus(step)
                            }
                            target?.let { player?.seekTo(it.coerceIn(0L, duration.coerceAtLeast(0L))) }
                        }
                    )
                }
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onToggleFavorite != null && currentUri != null) {
            val isFavorite = currentUri in favoriteUris
            IconButton(onClick = { onToggleFavorite(currentUri) }) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(
                        if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
                    ),
                    tint = if (isFavorite) FavoritePink else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Пока тянем ползунок, показываем позицию пальца, а не позицию плеера.
        var dragValue by remember { mutableStateOf<Float?>(null) }
        val sliderValue = dragValue ?: currentPosition.coerceIn(0L, duration).toFloat()
        Slider(
            value = sliderValue,
            onValueChange = {
                dragValue = it
                isSeeking = true
            },
            onValueChangeFinished = {
                dragValue?.let { player?.seekTo(it.toLong()) }
                dragValue = null
                isSeeking = false
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(sliderValue.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
        }

        // Основные кнопки — по центру; эквалайзер вынесен в правый нижний угол.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShuffleButton(enabled = shuffleEnabled) {
                player?.shuffleModeEnabled = !shuffleEnabled
            }
            IconButton(onClick = { player?.seekToPrevious() }) {
                Icon(
                    Icons.Default.SkipPrevious,
                    stringResource(R.string.previous),
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { if (isPlaying) player?.pause() else player?.play() }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(72.dp)
                )
            }
            IconButton(onClick = { player?.seekToNext() }) {
                Icon(
                    Icons.Default.SkipNext,
                    stringResource(R.string.next),
                    modifier = Modifier.size(36.dp)
                )
            }
            RepeatButton(repeatMode = repeatMode) {
                player?.repeatMode = nextRepeatMode(repeatMode)
            }
        }

        songsUntilStop?.let { left ->
            Text(
                text = stringResource(
                    R.string.stop_after,
                    pluralStringResource(R.plurals.songs_count, left, left)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        sleepTimerEndAt?.let { endAt ->
            val left = ceil((endAt - SystemClock.elapsedRealtime()) / 60_000.0)
                .toInt().coerceAtLeast(1)
            Text(
                text = stringResource(R.string.stop_after_minutes, left),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.weight(1f))
        }

        // Эквалайзер — в правом нижнем углу, чтобы не мешать основным кнопкам.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            IconButton(
                onClick = { eqMenuExpanded = true },
                enabled = equalizer != null
            ) {
                Icon(
                    Icons.Default.Equalizer,
                    stringResource(R.string.equalizer),
                    tint = if (equalizer != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
            DropdownMenu(
                expanded = eqMenuExpanded,
                onDismissRequest = { eqMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.equalizer_disable)) },
                    onClick = {
                        eqMenuExpanded = false
                        runCatching { equalizer?.enabled = false }
                    }
                )
                eqPresets.forEach { (preset, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            eqMenuExpanded = false
                            runCatching {
                                equalizer?.enabled = true
                                equalizer?.usePreset(preset)
                            }
                        }
                    )
                }
            }
        }
    }

    // Пасхалка: диалог с шуткой после пяти тапов по заголовку.
    easterEggJoke?.let { joke ->
        AlertDialog(
            onDismissRequest = { easterEggJoke = null },
            title = { Text(stringResource(R.string.surprise)) },
            text = { Text(joke) },
            confirmButton = {
                TextButton(onClick = { easterEggJoke = jokes.random() }) {
                    Text(stringResource(R.string.surprise_another))
                }
            },
            dismissButton = {
                TextButton(onClick = { easterEggJoke = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Повтор: выкл → все треки → один трек → выкл. */
fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
    else -> Player.REPEAT_MODE_ALL
}

/** Подпись скорости воспроизведения: "1x", "1.25x". */
private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

@Composable
fun ShuffleButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Shuffle,
            stringResource(R.string.shuffle),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun RepeatButton(repeatMode: Int, onClick: () -> Unit) {
    val (icon, description) = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat to R.string.repeat_all
        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to R.string.repeat_one
        else -> Icons.Default.Repeat to R.string.repeat_off
    }
    val active = repeatMode != Player.REPEAT_MODE_OFF
    IconButton(onClick = onClick) {
        Icon(
            icon,
            stringResource(description),
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
