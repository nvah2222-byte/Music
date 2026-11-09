package com.example.music.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Нормализация громкости: измеряем средний уровень трека (RMS, дБFS)
 * и подбираем усиление, чтобы все песни звучали одинаково громко.
 * Результат измерений кэшируется по id трека.
 */
object LoudnessNormalizer {
    private const val PREFS_NAME = "loudness"
    private const val SAMPLE_SECONDS = 20

    /** Усиление, приводящее измеренный уровень к целевому; ограничено ±20 дБ. */
    fun gainFor(measuredDb: Float, targetDb: Float): Float =
        10.0.pow(((targetDb - measuredDb) / 20.0)).toFloat().coerceIn(0.1f, 4f)

    fun cached(context: Context, songId: Long): Float? {
        val value = prefs(context).getFloat(songId.toString(), Float.NaN)
        return if (value.isNaN()) null else value
    }

    fun save(context: Context, songId: Long, measuredDb: Float) {
        prefs(context).edit().putFloat(songId.toString(), measuredDb).apply()
    }

    /** Средний уровень трека в дБFS; null, если декодировать не удалось. */
    suspend fun measure(context: Context, uri: Uri): Float? = withContext(Dispatchers.IO) {
        runCatching { measureBlocking(context, uri) }.getOrNull()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun measureBlocking(context: Context, uri: Uri): Float? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = index
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val isFloat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT

            val endUs = SAMPLE_SECONDS * 1_000_000L
            val info = MediaCodec.BufferInfo()
            var sumSquares = 0.0
            var sampleCount = 0L
            var inputDone = false
            var outputDone = false
            // Декодер может залипнуть на битом файле — не крутим цикл дольше 15 секунд.
            val deadline = SystemClock.elapsedRealtime() + 15_000L

            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
                while (outputIndex >= 0) {
                    if (info.size > 0) {
                        val output = decoder.getOutputBuffer(outputIndex)!!
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        if (isFloat) {
                            val samples = output.asFloatBuffer()
                            while (samples.hasRemaining()) {
                                val value = samples.get()
                                sumSquares += value * value
                                sampleCount++
                            }
                        } else {
                            val samples = output.asShortBuffer()
                            while (samples.hasRemaining()) {
                                val value = samples.get() / 32768.0
                                sumSquares += value * value
                                sampleCount++
                            }
                        }
                        output.clear()
                    }
                    val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val longEnough = sampleCount > 0 && info.presentationTimeUs >= endUs
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (endOfStream || longEnough) {
                        outputDone = true
                        break
                    }
                    outputIndex = decoder.dequeueOutputBuffer(info, 0)
                }
            }

            if (sampleCount == 0L) return null
            val rms = sqrt(sumSquares / sampleCount)
            if (rms <= 0.0) return null
            return (20 * log10(rms)).toFloat()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
