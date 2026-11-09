package com.example.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.music.R
import com.example.music.Song
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Геометрия колеса: центр, радиус и остриё стрелки. */
private data class WheelGeometry(val center: Offset, val radius: Float, val tip: Offset)

/**
 * Колесо фортуны со всеми песнями библиотеки. Крутишь пальцем — колесо
 * замедляется само, и что остановилось под стрелкой, то и играет.
 * [variant]: 0 — огромное (видна только нижняя дуга), 1 — классическое (целиком).
 */
@Composable
fun FortuneWheelOverlay(
    candidates: List<Song>,
    onDismiss: () -> Unit,
    onWin: (Song) -> Unit,
    speedMultiplier: Float = 1f,
    friction: Float = 0.975f,
    variant: Int = 0
) {
    var wheelRotation by remember { mutableFloatStateOf(0f) }
    var spinning by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<Song?>(null) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Полосы — разные цвета: оттенки темы, развёрнутые по кругу.
    val scheme = MaterialTheme.colorScheme
    val palette = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(scheme.primary.toArgb(), hsv)
        (0 until 12).map { index ->
            Color.hsv(
                (hsv[0] + index * 30f) % 360f,
                hsv[1].coerceAtLeast(0.4f),
                hsv[2].coerceAtLeast(0.5f)
            )
        }
    }

    fun geometry(): WheelGeometry {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        val big = variant == 0
        return if (big) {
            // Огромное колесо: центр глубоко внизу за экраном, видна верхняя дуга.
            val radius = w * 2.2f
            val center = Offset(w / 2f, h * 1.55f)
            val arcTop = center.y - radius
            WheelGeometry(center, radius, Offset(w / 2f, arcTop))
        } else {
            // Классическое: колесо целиком, стрелка снизу.
            val radius = min(w, h) * 0.36f
            val center = Offset(w / 2f, h / 2f - radius * 0.1f)
            WheelGeometry(center, radius, Offset(center.x, center.y + radius))
        }
    }

    fun winnerIndex(): Int {
        if (canvasSize.width <= 0) return 0
        val geo = geometry()
        val angle = atan2(
            (geo.tip.y - geo.center.y).toDouble(),
            (geo.tip.x - geo.center.x).toDouble()
        ) * 180.0 / Math.PI - wheelRotation
        val normalized = ((angle.toFloat() % 360f) + 360f) % 360f
        val sweep = 360f / candidates.size
        return (
            ((normalized + 90f) / sweep).toInt() % candidates.size +
                candidates.size
            ) % candidates.size
    }

    fun pickWinner() {
        spinning = false
        val song = candidates[winnerIndex()]
        winner = song
        scope.launch {
            delay(1400)
            onWin(song)
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .pointerInput(candidates, speedMultiplier, friction) {
                var lastTime = 0L
                var lastVelocity = 0f
                detectDragGestures(
                    onDragStart = {
                        spinning = true
                        winner = null
                        flingJob?.cancel()
                        lastTime = 0L
                        lastVelocity = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        wheelRotation += (dragAmount.x + dragAmount.y) * 0.5f
                        val now = System.nanoTime()
                        if (lastTime != 0L) {
                            val dt = (now - lastTime) / 1e9f
                            if (dt > 0.0005f) {
                                val delta = (dragAmount.x + dragAmount.y) * 0.5f
                                lastVelocity = lastVelocity * 0.7f + (delta / dt) * 0.3f
                            }
                        }
                        lastTime = now
                    },
                    onDragEnd = {
                        lastTime = 0L
                        val velocity = lastVelocity * speedMultiplier
                        lastVelocity = 0f
                        if (abs(velocity) > 40f) {
                            flingJob = scope.launch {
                                var speed = velocity
                                while (abs(speed) > 1f) {
                                    wheelRotation += speed * 0.016f
                                    speed *= friction
                                    delay(16)
                                }
                                pickWinner()
                            }
                        } else {
                            pickWinner()
                        }
                    },
                    onDragCancel = {
                        lastTime = 0L
                        lastVelocity = 0f
                        pickWinner()
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
        ) {
            val geo = geometry()
            val center = geo.center
            val radius = geo.radius
            val sweep = 360f / candidates.size
            val big = variant == 0

            rotate(degrees = wheelRotation, pivot = center) {
                candidates.forEachIndexed { index, _ ->
                    val startAngle = index * sweep - 90f
                    drawArc(
                        color = palette[index % palette.size],
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    rotate(degrees = startAngle, pivot = center) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.3f),
                            start = center,
                            end = Offset(center.x + radius, center.y),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            if (big) {
                // Названия видимых секторов: от края колеса к центру.
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14.dp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val titleRadius = radius - 46.dp.toPx()
                candidates.forEachIndexed { index, song ->
                    val angle = index * sweep - 90f + sweep / 2f + wheelRotation
                    val rad = angle * Math.PI / 180.0
                    val x = center.x + titleRadius * cos(rad).toFloat()
                    val y = center.y + titleRadius * sin(rad).toFloat()
                    if (y in 40f..(size.height - 20f) && x in 20f..(size.width - 20f)) {
                        rotate(degrees = angle, pivot = center) {
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    song.title.take(20),
                                    titleRadius,
                                    0f,
                                    textPaint
                                )
                            }
                        }
                    }
                }
                // Стрелка сверху, остриём вниз к дуге колеса.
                val tip = geo.tip
                val arrowHeight = 34.dp.toPx()
                val half = 16.dp.toPx()
                val pointer = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x + half, tip.y - arrowHeight)
                    lineTo(tip.x - half, tip.y - arrowHeight)
                    close()
                }
                drawPath(pointer, Color.White)
            } else {
                // Классическое: ступица и стрелка снизу остриём вверх.
                drawCircle(Color.White, radius = 14.dp.toPx(), center = center)
                val tip = geo.tip
                val arrowHeight = 34.dp.toPx()
                val half = 16.dp.toPx()
                val pointer = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x + half, tip.y + arrowHeight)
                    lineTo(tip.x - half, tip.y + arrowHeight)
                    close()
                }
                drawPath(pointer, Color.White)
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (winner != null) {
                        Text(
                            text = stringResource(R.string.wheel_winner),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = winner?.title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = winner?.artist.orEmpty(),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (spinning) R.string.wheel_spinning else R.string.wheel_hint
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}
