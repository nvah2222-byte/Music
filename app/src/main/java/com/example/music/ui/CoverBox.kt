package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size

/** Розовый цвет сердечек избранного. */
val FavoritePink = Color(0xFFE91E63)

/**
 * Обложка с цветным плейсхолдером: пока картинка не загрузилась (или её нет),
 * показываем цвет, вычисленный из хэша песни, и нотку.
 */
@Composable
fun CoverBox(
    seed: Long,
    data: Any?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    requestSize: Int = 150,
    /** Запросить максимальное доступное разрешение (полноэкранный плеер). */
    original: Boolean = false,
    iconFraction: Float = 0.5f
) {
    val hue = ((seed % 360) + 360) % 360
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.hsv(hue.toFloat(), 0.55f, 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxSize(iconFraction)
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(data)
                .size(if (original) Size.ORIGINAL else Size(requestSize, requestSize))
                // Coil кэширует по URI без размера: без отдельного ключа плеер
                // получал маленькую обложку, уже загруженную для списка.
                .apply { if (original) memoryCacheKey("cover-original:$data") }
                .crossfade(150)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
