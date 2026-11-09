package com.example.music

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.music.data.LoudnessNormalizer
import com.example.music.data.PlaybackTimers
import com.example.music.data.SettingsStore
import com.example.music.player.AudioArtworkLoader
import com.example.music.widget.MusicWidget
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var fadeJob: Job? = null
    private var sleepJob: Job? = null
    private var crossfadeJob: Job? = null
    private var measureJob: Job? = null
    /** Второй плеер: играет начало следующего трека одновременно с концом текущего. */
    private var overlapPlayer: ExoPlayer? = null
    private var overlapJob: Job? = null
    private var handoffJob: Job? = null
    /** mediaId трека, который уже звучит из второго плеера; null — перекрытия нет. */
    private var overlapMediaId: String? = null
    private var handoffPending = false
    /** Громкость, с которой второй плеер играет голову следующего трека. */
    private var overlapGain = 1f
    /** mediaId, для которого второй плеер уже подготовлен (но ещё не играет). */
    private var preparedOverlapId: String? = null
    /** Когда начался плавный старт нового трека (elapsedRealtime); 0 — не идёт. */
    private var fadeInStartAt = 0L
    // Громкость складывается из трёх множителей: кроссфейд/пауза, приглушение фокусом и нормализация.
    private var fadeVolume = 1f
    private var duckVolume = 1f
    private var normalizationGain = 1f
    private var normalizationTarget = 1f
    private var noisyRegistered = false
    /** URI, для которого уже загружена обложка виджета. */
    private var widgetCoverUri: Uri? = null
    /** Пауза случилась из-за потери фокуса (видео/звонок) — можно автопродолжить. */
    private var pausedByFocus = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val artworkLoader = AudioArtworkLoader(this)

    private val settings by lazy { getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** Настройки нормализации меняются из активити — слушаем prefs, чтобы применить сразу. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_VOLUME_BALANCE || key == SettingsStore.KEY_TARGET_DB) {
            refreshNormalization()
        }
    }

    /** Вытащили наушники — плавно затихаем и останавливаемся. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                fadeOutAndPause()
            }
        }
    }

    /** Другой звук забрал фокус (видео, звонок) — затихаем и встаём на паузу. */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                fadeJob?.cancel()
                duckVolume = 1f
                fadeVolume = 1f
                applyVolume()
                // Другой звук закончился — продолжаем, если остановились из-за него.
                if (pausedByFocus) {
                    pausedByFocus = false
                    if (settings.getBoolean(SettingsStore.KEY_RESUME_ON_FOCUS_GAIN, true)) {
                        mediaSession?.player?.play()
                    }
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckVolume = 0.15f
                applyVolume()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Фокус потерян насовсем — продолжать после него не надо.
                fadeOutAndPause(resumeWhenFocusReturns = false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Фокус надо сохранить, иначе система не пришлёт AUDIOFOCUS_GAIN.
                fadeOutAndPause(resumeWhenFocusReturns = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings.registerOnSharedPreferenceChangeListener(settingsListener)
        val player = ExoPlayer.Builder(this)
            // Аудиофокусом и отключением наушников управляем сами,
            // чтобы сделать плавное затухание и настройки.
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .build()
        player.setHandleAudioBecomingNoisy(false)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId <= 0) return
                // Аудио-сессия появилась — можно вешать системный эквалайзер.
                runCatching { equalizer?.release() }
                equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
                applySoundProfile(settings.getInt(SettingsStore.KEY_SOUND_PROFILE, 0))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // На стыке треков ExoPlayer на мгновение уходит в буферизацию:
                // это не пауза, перекрытие и хендовер рвать нельзя.
                if (!isPlaying && handoffPending) return
                if (isPlaying) {
                    fadeJob?.cancel()
                    pausedByFocus = false
                    if (settings.getBoolean(SettingsStore.KEY_PAUSE_ON_OTHER_AUDIO, true)) requestAudioFocus()
                    if (settings.getBoolean(SettingsStore.KEY_PAUSE_ON_HEADPHONES, true)) registerNoisy()
                    startSleepWatch()
                    startVolumeWatch()
                } else {
                    // Если пауза из-за потери фокуса — запрос не снимаем,
                    // иначе система не пришлёт AUDIOFOCUS_GAIN для автопродолжения.
                    if (!pausedByFocus) abandonAudioFocus()
                    unregisterNoisy()
                    fadeJob?.cancel()
                    sleepJob?.cancel()
                    crossfadeJob?.cancel()
                    stopOverlap()
                    handoffJob?.cancel()
                    fadeVolume = 1f
                    duckVolume = 1f
                    applyVolume()
                }
                updateWidget()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateWidget()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateWidget()
                val handedOver = overlapMediaId != null &&
                    mediaItem?.mediaId == overlapMediaId &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                if (handedOver) {
                    // Второй плеер уже доигрывает начало этого трека — переводим звук на основной.
                    handoffPending = true
                    startHandoff()
                } else {
                    stopOverlap()
                    // Новый трек начинается с нулевой громкости — дальше её поднимает startVolumeWatch.
                    if (mediaItem != null && settings.getInt(SettingsStore.KEY_CROSSFADE_MS, 0) > 0) {
                        fadeInStartAt = SystemClock.elapsedRealtime()
                        fadeVolume = 0f
                        applyVolume()
                    }
                }
                refreshNormalization(mediaItem)
                // «Стоп через N треков» считает только реально дослушанные треки,
                // ручные перескоки не должны приближать остановку.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    val left = PlaybackTimers.songsUntilStop(this@PlaybackService)
                    if (left > 0) {
                        PlaybackTimers.setSongsUntilStop(this@PlaybackService, left - 1)
                        if (left == 1) mediaSession?.player?.pause()
                    }
                }
            }
        })
        // Тап по уведомлению открывает приложение.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(artworkLoader)
            .build()
        updateWidget()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** Обновляем виджет: текст и состояние — сразу, обложку — когда декодируется. */
    private fun updateWidget() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        MusicWidget.setState(
            this,
            metadata?.title?.toString().orEmpty(),
            metadata?.artist?.toString().orEmpty(),
            player.isPlaying
        )
        val uri = metadata?.artworkUri ?: item?.localConfiguration?.uri
        if (uri != null && uri == widgetCoverUri) return
        widgetCoverUri = uri
        MusicWidget.setCover(this, null)
        if (uri == null) return
        val future = runCatching { artworkLoader.loadBitmap(uri) }.getOrNull() ?: return
        future.addListener({
            val bitmap = runCatching { future.get() }.getOrNull()
            if (uri == widgetCoverUri) MusicWidget.setCover(this, bitmap)
        }, MoreExecutors.directExecutor())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /** Один цикл на всё: кроссфейд, перекрытие треков, плавный старт и нормализация. */
    private fun startVolumeWatch() {
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            while (true) {
                val player = mediaSession?.player ?: break
                if (!player.isPlaying) break
                val fadeMs = settings.getInt(SettingsStore.KEY_CROSSFADE_MS, 0)
                if (!handoffPending) {
                    fadeVolume = if (fadeMs <= 0) {
                        1f
                    } else {
                        crossfadeVolume(
                            now = SystemClock.elapsedRealtime(),
                            fadeInStartAt = fadeInStartAt,
                            fadeMs = fadeMs,
                            durationMs = player.duration,
                            positionMs = player.currentPosition
                        )
                    }
                }
                if (fadeMs > 0 && !handoffPending) {
                    val remaining = player.duration - player.currentPosition
                    val nextIndex = if (player.hasNextMediaItem()) {
                        player.nextMediaItemIndex
                    } else {
                        androidx.media3.common.C.INDEX_UNSET
                    }
                    val nextItem = if (nextIndex != androidx.media3.common.C.INDEX_UNSET &&
                        nextIndex != player.currentMediaItemIndex
                    ) {
                        player.getMediaItemAt(nextIndex)
                    } else {
                        null
                    }
                    // Заранее готовим следующий трек во втором плеере — тогда переход
                    // начинается без задержки на подготовку.
                    if (nextItem != null && overlapMediaId == null &&
                        remaining in 0..(fadeMs + PREPARE_AHEAD_MS) &&
                        preparedOverlapId != nextItem.mediaId
                    ) {
                        prepareOverlap(nextItem)
                    }
                    if (nextItem != null && overlapMediaId == null &&
                        remaining in 0..fadeMs.toLong() &&
                        preparedOverlapId == nextItem.mediaId
                    ) {
                        startOverlap(fadeMs)
                    } else if (overlapMediaId != null && remaining > fadeMs + 500L) {
                        // Пользователь перемотал назад — перекрытие больше не нужно.
                        stopOverlap()
                    }
                }
                // Усиление меняем не рывком, а за ~0.5 с — иначе слышен скачок громкости.
                if (abs(normalizationGain - normalizationTarget) > 0.005f) {
                    normalizationGain += (normalizationTarget - normalizationGain) * 0.12f
                } else {
                    normalizationGain = normalizationTarget
                }
                applyVolume()
                delay(50)
            }
        }
    }

    /** Готовит второй плеер на позицию 0 следующего трека, пока играет текущий. */
    private fun prepareOverlap(item: MediaItem) {
        val incoming = overlapPlayer ?: createOverlapPlayer().also { overlapPlayer = it }
        if (runCatching {
                incoming.stop()
                incoming.clearMediaItems()
                incoming.setMediaItem(item)
                incoming.prepare()
                incoming.volume = 0f
            }.isFailure
        ) {
            preparedOverlapId = null
            return
        }
        preparedOverlapId = item.mediaId
    }

    /** Запускает второй плеер с началом следующего трека: получается настоящее наложение. */
    private fun startOverlap(fadeMs: Int) {
        val incoming = overlapPlayer ?: return
        val id = preparedOverlapId ?: return
        if (runCatching { incoming.play() }.isFailure) return
        overlapMediaId = id
        overlapJob?.cancel()
        overlapJob = serviceScope.launch {
            val nextGain = LoudnessNormalizer
                .cached(this@PlaybackService, id.toLongOrNull() ?: -1L)
                ?.let { LoudnessNormalizer.gainFor(it, targetLoudnessDb()) }
                ?: normalizationGain
            val scale = nextGain.coerceIn(0f, 1f)
            overlapGain = scale
            val steps = 20
            val stepMs = (fadeMs / steps).coerceAtLeast(1)
            for (i in 1..steps) {
                incoming.volume = (i.toFloat() / steps) * scale
                delay(stepMs.toLong())
            }
            incoming.volume = scale
        }
    }

    /** Основной плеер как раз перешёл на трек, который уже играет второй: сводим звук. */
    private fun startHandoff() {
        handoffJob?.cancel()
        val incoming = overlapPlayer
        val player = mediaSession?.player
        if (incoming == null || player == null) {
            stopOverlap()
            return
        }
        handoffJob = serviceScope.launch {
            overlapJob?.cancel()
            // Пока звучит второй плеер, ждём, когда основной будет готов на новом треке.
            var waited = 0
            while (player.playbackState != Player.STATE_READY && waited < 1000) {
                delay(25)
                waited += 25
            }
            // Подводим основной ровно к позиции второго и ждём, пока seek применится.
            val target = incoming.currentPosition
            player.seekTo(target)
            waited = 0
            while (waited < 600) {
                if (player.playbackState == Player.STATE_READY &&
                    abs(player.currentPosition - target) <= 200
                ) {
                    break
                }
                delay(25)
                waited += 25
            }
            if (!player.isPlaying) {
                stopOverlap()
                return@launch
            }
            // Продолжаем с той же громкости, что играл второй плеер, — без скачка.
            normalizationGain = overlapGain
            fadeVolume = 1f
            fadeInStartAt = 0L
            applyVolume()
            // Даём основному один аудиобуфер и только потом убираем второй.
            delay(60)
            stopOverlap()
        }
    }

    private fun stopOverlap() {
        overlapJob?.cancel()
        overlapJob = null
        overlapPlayer?.let { runCatching { it.stop(); it.clearMediaItems() } }
        overlapMediaId = null
        preparedOverlapId = null
        handoffPending = false
    }

    private fun createOverlapPlayer(): ExoPlayer {
        val attributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, /* handleAudioFocus = */ false)
            .build()
            .apply { setHandleAudioBecomingNoisy(false) }
    }

    /** Итоговая громкость = кроссфейд × приглушение фокусом × нормализация трека. */
    private fun applyVolume() {
        val player = mediaSession?.player ?: return
        val volume = (fadeVolume * duckVolume * normalizationGain).coerceIn(0f, 1f)
        if (abs(player.volume - volume) > 0.005f) player.volume = volume
    }

    /** Считаем нужное усиление для текущего трека (из кэша или измеряем в фоне). */
    private fun refreshNormalization(item: MediaItem? = mediaSession?.player?.currentMediaItem) {
        measureJob?.cancel()
        if (!settings.getBoolean(SettingsStore.KEY_VOLUME_BALANCE, false) || item == null) {
            normalizationTarget = 1f
            return
        }
        val songId = item.mediaId.toLongOrNull()
        val uri = item.localConfiguration?.uri
        if (songId == null || uri == null) {
            normalizationTarget = 1f
            return
        }
        val cached = LoudnessNormalizer.cached(this, songId)
        if (cached != null) {
            normalizationTarget = LoudnessNormalizer.gainFor(cached, targetLoudnessDb())
            return
        }
        normalizationTarget = 1f
        measureJob = serviceScope.launch {
            val measured = LoudnessNormalizer.measure(this@PlaybackService, uri) ?: return@launch
            LoudnessNormalizer.save(this@PlaybackService, songId, measured)
            if (mediaSession?.player?.currentMediaItem?.mediaId == item.mediaId) {
                normalizationTarget = LoudnessNormalizer.gainFor(measured, targetLoudnessDb())
            }
        }
    }

    private fun targetLoudnessDb(): Float =
        settings.getInt(SettingsStore.KEY_TARGET_DB, -18).toFloat()

    /** Следим за таймером сна, пока играет музыка: активити может быть уже уничтожена. */
    private fun startSleepWatch() {
        sleepJob?.cancel()
        sleepJob = serviceScope.launch {
            while (true) {
                val endAt = PlaybackTimers.sleepEndAt(this@PlaybackService)
                if (endAt > 0L && SystemClock.elapsedRealtime() >= endAt) {
                    PlaybackTimers.setSleepEndAt(this@PlaybackService, 0L)
                    mediaSession?.player?.pause()
                    break
                }
                delay(1000)
            }
        }
    }

    /** Плавно затихаем и ставим на паузу (видео, звонок, наушники вытащили). */
    private fun fadeOutAndPause(resumeWhenFocusReturns: Boolean = false) {
        val player = mediaSession?.player ?: return
        if (!player.isPlaying) return
        // Кроссфейд тоже крутит громкость — иначе два цикла будут спорить.
        crossfadeJob?.cancel()
        stopOverlap()
        handoffJob?.cancel()
        pausedByFocus = resumeWhenFocusReturns
        val fadeMs = settings.getInt(SettingsStore.KEY_FADE_OUT_MS, DEFAULT_FADE_OUT_MS)
        if (fadeMs <= 0) {
            player.pause()
            return
        }
        if (fadeJob?.isActive == true) return
        fadeJob = serviceScope.launch {
            val steps = 20
            val stepMs = fadeMs / steps
            for (i in 1..steps) {
                fadeVolume = 1f - (1f * i / steps)
                applyVolume()
                delay(stepMs.toLong())
            }
            fadeVolume = 1f
            player.pause()
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(focusListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioFocusRequest = request
            runCatching { audioManager.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(focusListener) }
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        noisyRegistered = true
        runCatching {
            ContextCompat.registerReceiver(
                this,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        noisyRegistered = false
        runCatching { unregisterReceiver(noisyReceiver) }
    }

    override fun onDestroy() {
        settings.unregisterOnSharedPreferenceChangeListener(settingsListener)
        mediaSession?.player?.currentMediaItem?.mediaMetadata?.let { metadata ->
            MusicWidget.setState(
                this,
                metadata.title?.toString().orEmpty(),
                metadata.artist?.toString().orEmpty(),
                false
            )
        }
        serviceScope.cancel()
        abandonAudioFocus()
        unregisterNoisy()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
            mediaSession = null
        }
        runCatching { equalizer?.release() }
        equalizer = null
        overlapPlayer?.release()
        overlapPlayer = null
        artworkLoader.shutdown()
        super.onDestroy()
    }

    companion object {
        /** Системный эквалайзер плеера; создаётся, когда у ExoPlayer появляется аудио-сессия. */
        @Volatile
        var equalizer: Equalizer? = null
            private set

        const val DEFAULT_FADE_OUT_MS = 800
        /** За сколько до перехода второй плеер начинает готовить следующий трек. */
        private const val PREPARE_AHEAD_MS = 8_000L

        /**
         * Профиль звучания через системный эквалайзер.
         * 0 — выключен, 1 — бас, 2 — вокал, 3 — рок, 4 — джаз.
         */
        fun applySoundProfile(profile: Int) {
            val eq = equalizer ?: return
            runCatching {
                if (profile == 0) {
                    eq.enabled = false
                    return
                }
                eq.enabled = true
                val range = eq.bandLevelRange
                val gains = when (profile) {
                    1 -> floatArrayOf(8f, 4f, 0f, 1f, 2f)
                    2 -> floatArrayOf(-2f, 2f, 6f, 4f, 0f)
                    3 -> floatArrayOf(4f, 1f, -1f, 3f, 5f)
                    else -> floatArrayOf(3f, 2f, 0f, 2f, 3f)
                }
                for (band in 0 until eq.numberOfBands.toInt()) {
                    val millibels = (gains.getOrElse(band) { 0f } * 100f).toInt()
                        .coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(band.toShort(), millibels.toShort())
                }
            }
        }
    }
}

/**
 * Громкость перехода: плавный старт нового трека, затем плавное затухание
 * в последние [fadeMs] трека. [fadeMs] <= 0 — переход выключен.
 */
internal fun crossfadeVolume(
    now: Long,
    fadeInStartAt: Long,
    fadeMs: Int,
    durationMs: Long,
    positionMs: Long
): Float {
    if (fadeMs <= 0) return 1f
    return when {
        now < fadeInStartAt + fadeMs -> (now - fadeInStartAt).toFloat() / fadeMs
        durationMs > 0 && durationMs - positionMs <= fadeMs ->
            (durationMs - positionMs).toFloat() / fadeMs
        else -> 1f
    }.coerceIn(0f, 1f)
}
