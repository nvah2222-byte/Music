package com.example.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Скорость бегущей строки названий, dp/с; 0 — обычный текст с «…». */
val LocalMarqueeSpeed = compositionLocalOf { 0 }

/**
 * Название песни, которое не обрезается, а плавно проезжает слева направо и обратно.
 * Пока строка короткая — обычный текст; длинная — «бегущая строка» с паузами на краях.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    speedDpPerSec: Int = LocalMarqueeSpeed.current
) {
    if (speedDpPerSec <= 0) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(text, speedDpPerSec) {
        // Ждём раскладку: до неё длина прокрутки ещё нулевая.
        var waited = 0
        while (scrollState.maxValue == 0 && waited < 2000) {
            delay(100)
            waited += 100
        }
        if (scrollState.maxValue == 0) return@LaunchedEffect
        val speedPx = with(density) { speedDpPerSec.toFloat().dp.toPx() }
        val duration = ((scrollState.maxValue / speedPx) * 1000f).toInt().coerceAtLeast(400)
        while (true) {
            delay(1200)
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(duration, easing = LinearEasing)
            )
            delay(1200)
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(duration, easing = LinearEasing)
            )
        }
    }
    Row(modifier = modifier.horizontalScroll(scrollState, enabled = false)) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}
