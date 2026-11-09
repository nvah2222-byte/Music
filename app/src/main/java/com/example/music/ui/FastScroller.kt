package com.example.music.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Полоса букв для быстрого перехода: тап или свайп в любом месте полосы
 * мгновенно скроллит список к нужной букве.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    letters: List<String>,
    indexOfLetter: (String) -> Int?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val letterHeight = 18.dp
    val letterHeightPx = with(LocalDensity.current) { letterHeight.toPx() }

    fun scrollToLetterAt(y: Float) {
        if (containerHeightPx <= 0f || letters.isEmpty()) return
        val totalHeight = letters.size * letterHeightPx
        val startY = (containerHeightPx - totalHeight) / 2f
        val index = ((y - startY) / letterHeightPx).toInt()
        letters.getOrNull(index)?.let { letter ->
            indexOfLetter(letter)?.let { itemIndex ->
                scope.launch { listState.scrollToItem(itemIndex) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
            .pointerInput(letters, letterHeightPx) {
                detectDragGestures(
                    onDragStart = { offset -> scrollToLetterAt(offset.y) },
                    onDrag = { change, _ ->
                        change.consume()
                        scrollToLetterAt(change.position.y)
                    }
                )
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Box(
                modifier = Modifier.height(letterHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
