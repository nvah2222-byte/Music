package com.example.music.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.music.R
import com.example.music.Song

/** Альбом: автособранный по исполнителю или созданный пользователем вручную. */
data class Album(
    val artist: String,
    val songs: List<Song>,
    val isCustom: Boolean = false,
    val coverUri: String? = null
)

/** Список альбомов: созданные вручную + собранные по исполнителям. */
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    customAlbums: List<Album>,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    onOpenAlbum: (Album) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateAlbum: () -> Unit,
    onCreateFromArchive: () -> Unit = {},
    onChangeAlbumCover: (Album) -> Unit = {},
    onDeleteAlbum: (Album) -> Unit = {},
    onShareAlbum: (Album) -> Unit = {}
) {
    val allAlbums = customAlbums + albums
    if (allAlbums.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.albums_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.albums_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCreateAlbum) {
                Text(stringResource(R.string.create_album_title))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCreateFromArchive) {
                Text(stringResource(R.string.create_album_from_archive))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item(key = "create") {
            Surface(
                onClick = onCreateAlbum,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.create_album_title),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item(key = "archive") {
            Surface(
                onClick = onCreateFromArchive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.create_album_from_archive),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        itemsIndexed(
            allAlbums,
            // Индекс в ключе: старые бэкапы могли сохранить два альбома с одним именем,
            // а дубли ключей в LazyColumn — это краш.
            key = { index, album -> (if (album.isCustom) "custom:" else "artist:") + album.artist + "#" + index }
        ) { _, album ->
            AlbumRow(
                album = album,
                imageLoader = imageLoader,
                onClick = { onOpenAlbum(album) },
                onChangeCover = if (album.isCustom) ({ onChangeAlbumCover(album) }) else null,
                onDelete = if (album.isCustom) ({ onDeleteAlbum(album) }) else null,
                onShare = { onShareAlbum(album) }
            )
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onChangeCover: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val firstSong = album.songs.firstOrNull()
    Box {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (album.coverUri != null) {
                    CoverBox(
                        seed = album.coverUri.hashCode().toLong(),
                        data = Uri.parse(album.coverUri),
                        imageLoader = imageLoader,
                        requestSize = 128,
                        cornerRadius = 8.dp,
                        modifier = Modifier.size(52.dp)
                    )
                } else if (album.isCustom || firstSong == null) {
                    val hue = ((album.artist.hashCode() % 360) + 360) % 360
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.hsv(hue.toFloat(), 0.55f, 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                } else {
                    CoverBox(
                        seed = firstSong.id,
                        data = firstSong.customCover ?: firstSong.uri,
                        imageLoader = imageLoader,
                        requestSize = 128,
                        cornerRadius = 8.dp,
                        modifier = Modifier.size(52.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = album.artist,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pluralStringResource(R.plurals.tracks_count, album.songs.size, album.songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (onShare != null || onChangeCover != null || onDelete != null) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.menu))
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (onShare != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_album)) },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    }
                )
            }
            if (onChangeCover != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.change_cover)) },
                    leadingIcon = { Icon(Icons.Default.Image, null) },
                    onClick = {
                        menuExpanded = false
                        onChangeCover()
                    }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_album), color = Color.Red) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/** Песни альбома: один тап — играть. */
@Composable
fun AlbumDetailScreen(
    album: Album,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    favorites: Set<Long> = emptySet(),
    onPlay: (Song) -> Unit,
    onToggleFavorite: ((Long) -> Unit)? = null,
    onAddTracks: (() -> Unit)? = null,
    onShare: ((Song) -> Unit)? = null,
    onDelete: ((Song) -> Unit)? = null,
    onChangeCover: ((Song) -> Unit)? = null
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (onAddTracks != null) {
            item(key = "add_tracks") {
                Surface(onClick = onAddTracks, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.add_tracks),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        itemsIndexed(album.songs, key = { _, song -> song.id }) { _, song ->
            SimpleSongRow(
                song = song,
                imageLoader = imageLoader,
                onClick = { onPlay(song) },
                isFavorite = song.id in favorites,
                onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(song.id) } },
                onShare = onShare?.let { share -> { share(song) } },
                onDelete = onDelete?.let { delete -> { delete(song) } },
                onChangeCover = onChangeCover?.let { cover -> { cover(song) } }
            )
        }
    }
}

/** Простая строка песни с полным меню: один тап — играть, долгий — меню. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SimpleSongRow(
    song: Song,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onChangeCover: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasMenu = onShare != null || onDelete != null || onChangeCover != null

    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (hasMenu) ({ menuExpanded = true }) else null
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverBox(
                seed = song.id,
                data = song.customCover ?: song.uri,
                imageLoader = imageLoader,
                requestSize = 128,
                cornerRadius = 6.dp,
                modifier = Modifier.size(44.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(song.durationText, style = MaterialTheme.typography.labelSmall)
            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(
                            if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
                        ),
                        tint = if (isFavorite) FavoritePink else Color.Gray
                    )
                }
            }
        }

        if (hasMenu) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (onChangeCover != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.change_cover)) },
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                        onClick = {
                            menuExpanded = false
                            onChangeCover()
                        }
                    )
                }
                if (onShare != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                }
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_file), color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
