package com.example.music.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.ImageLoader
import com.example.music.R
import com.example.music.PlaybackService
import com.example.music.Song
import com.example.music.data.AlbumArchiveFormat
import com.example.music.data.AlbumArchiver
import com.example.music.data.ArchiveImporter
import com.example.music.data.CustomAlbum
import com.example.music.data.PlaybackTimers
import com.example.music.data.SettingsStore
import com.example.music.data.SongRepository
import com.example.music.data.UpdateCheck
import com.example.music.data.UpdateChecker
import com.example.music.data.UpdateInfo
import com.example.music.formatDuration
import com.example.music.matchesQuery
import com.example.music.player.AudioCoverFetcher
import com.example.music.toMediaItems
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Текущий экран приложения. Serializable — чтобы переживать поворот экрана. */
sealed interface Screen : java.io.Serializable {
    data object Songs : Screen
    data object Favorites : Screen
    data object Added : Screen
    data object Folders : Screen
    data class FolderDetail(val path: String) : Screen
    data object Albums : Screen
    data class AlbumDetail(val artist: String) : Screen
    data object Stats : Screen
    data object Recap : Screen
    data object Settings : Screen
    data object Player : Screen
}

/** Пункт боковой шторки; key хранится в настройках, чтобы порядок переживал перезапуск. */
private enum class DrawerEntry(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    Songs("songs", R.string.menu_songs, Icons.Default.MusicNote),
    Favorites("favorites", R.string.menu_favorites, Icons.Default.Favorite),
    Added("added", R.string.menu_added, Icons.Default.AddCircle),
    Random("random", R.string.play_random, Icons.Default.Casino),
    Albums("albums", R.string.menu_albums, Icons.Default.Album),
    Folders("folders", R.string.menu_folders, Icons.Default.Folder),
    Stats("stats", R.string.menu_stats, Icons.Default.BarChart),
    Recap("recap", R.string.menu_recap, Icons.Default.EmojiEvents),
    Settings("settings", R.string.menu_settings, Icons.Default.Settings);

    fun isSelected(screen: Screen): Boolean = when (this) {
        Songs -> screen is Screen.Songs
        Favorites -> screen is Screen.Favorites
        Added -> screen is Screen.Added
        Random -> false
        Albums -> screen is Screen.Albums || screen is Screen.AlbumDetail
        Folders -> screen is Screen.Folders || screen is Screen.FolderDetail
        Stats -> screen is Screen.Stats
        Recap -> screen is Screen.Recap
        Settings -> screen is Screen.Settings
    }

    companion object {
        /** Сохранённый порядок + недостающие пункты; неизвестные ключи отбрасываем. */
        fun ordered(saved: List<String>): List<DrawerEntry> {
            val known = saved.mapNotNull { key -> entries.firstOrNull { it.key == key } }.distinct()
            return known + entries.filterNot { it in known }
        }
    }
}

private fun requiredStoragePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/** С какого возраста трека запоминаем позицию (аудиокниги, лекции). */
private const val RESUME_MIN_MS = 10 * 60 * 1000L
private const val BACKUP_FORMAT = "music-backup"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(player: MediaController?, settingsStore: SettingsStore) {
    val context = LocalContext.current
    val repository = remember { SongRepository(context.applicationContext) }
    val songs = remember { mutableStateListOf<Song>() }
    var favorites by remember { mutableStateOf(emptySet<Long>()) }
    var playCounts by remember { mutableStateOf(emptyMap<Long, Int>()) }
    var historyIds by remember { mutableStateOf(emptyList<Long>()) }
    var pendingDuplicates by remember { mutableStateOf(emptyList<Song>()) }
    var pendingDownloadsDuplicates by remember { mutableStateOf(emptyList<Song>()) }
    var customAlbums by remember { mutableStateOf(emptyList<CustomAlbum>()) }
    var showCreateAlbum by remember { mutableStateOf(false) }
    var addToAlbumTarget by remember { mutableStateOf<CustomAlbum?>(null) }
    var playEvents by remember { mutableStateOf(emptyList<Pair<Long, Long>>()) }
    var showWheel by remember { mutableStateOf(false) }
    var wheelCandidates by remember { mutableStateOf(emptyList<Song>()) }
    var pendingAlbumCoverChange by remember { mutableStateOf<CustomAlbum?>(null) }
    var pendingDeleteAlbum by remember { mutableStateOf<CustomAlbum?>(null) }
    var coverPresence by remember { mutableStateOf(repository.loadCoverPresence()) }
    // После импорта копии перечитываем библиотеку.
    var libraryVersion by remember { mutableIntStateOf(0) }
    val settings by settingsStore.settings.collectAsState()

    // Профиль звучания применяем сразу, не дожидаясь новой аудио-сессии.
    LaunchedEffect(settings.soundProfile) {
        PlaybackService.applySoundProfile(settings.soundProfile)
    }

    // Скрытые папки убираем только из показываемой библиотеки,
    // чтобы не потерять их порядок, обложки и добавленные вручную файлы.
    val hiddenFolders = settings.hiddenFolders
    val librarySongs = songs.filterNot { song ->
        song.relativePath.isNotBlank() &&
            song.relativePath.trimEnd('/') in hiddenFolders
    }

    // --- Разрешения ---
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredStoragePermission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasStoragePermission = result[requiredStoragePermission()] == true
    }
    // Запрос показываем один раз за запуск: при повороте экрана не дёргаем систему снова.
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    // Открыт наш выбор файлов: возврат из него не должен запускать перечитывание библиотеки,
    // иначе оно может перетереть ещё не сохранённые добавленные треки.
    var songPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasStoragePermission && !permissionRequested) {
            permissionRequested = true
            val permissions = buildList {
                add(requiredStoragePermission())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
    // Вернулись из настроек — проверяем разрешение ещё раз, не перезапуская приложение.
    // Заодно перечитываем библиотеку: файлы могли скачать/удалить, пока приложение было свёрнуто.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> wasPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    hasStoragePermission = ContextCompat.checkSelfPermission(
                        context,
                        requiredStoragePermission()
                    ) == PackageManager.PERMISSION_GRANTED
                    if (wasPaused) {
                        wasPaused = false
                        // Возврат из выбора файлов — не перечитываем библиотеку, иначе
                        // можно перетереть ещё не сохранённые добавленные треки.
                        if (songPickerOpen) songPickerOpen = false else libraryVersion++
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(hasStoragePermission, libraryVersion) {
        if (hasStoragePermission) {
            val loaded = withContext(Dispatchers.IO) { repository.loadAll() }
            songs.clear()
            songs.addAll(loaded)
            favorites = repository.loadFavorites()
            playCounts = repository.loadPlayCounts()
            historyIds = repository.loadHistory()
            customAlbums = repository.loadCustomAlbums()
            playEvents = repository.loadPlayEvents()
        }
    }

    // Автоудаление дублей: ищем копии с обложкой, у которых есть пара без обложки.
    // Скрытые папки не трогаем — пользователь их специально убрал.
    LaunchedEffect(hasStoragePermission, settings.autoDeleteDuplicates, librarySongs.size) {
        if (hasStoragePermission && settings.autoDeleteDuplicates && librarySongs.isNotEmpty()) {
            val duplicates = withContext(Dispatchers.IO) {
                repository.findCoveredDuplicates(librarySongs.toList())
            }
            if (duplicates.isNotEmpty()) pendingDuplicates = duplicates
        }
    }

    // Наличие обложек — для сортировки «По обложке». Дорогое чтение файлов кэшируем в prefs.
    LaunchedEffect(librarySongs.size, settings.trackSort) {
        if (librarySongs.isNotEmpty() && settings.trackSort == 5) {
            val (cached, missing) = withContext(Dispatchers.IO) {
                val saved = repository.loadCoverPresence()
                saved to librarySongs.filter { it.id !in saved }
            }
            val fresh = if (missing.isEmpty()) {
                emptyMap()
            } else {
                withContext(Dispatchers.IO) {
                    missing.associate { it.id to repository.hasRealCover(it) }.also { repository.saveCoverPresence(cached + it) }
                }
            }
            coverPresence = cached + fresh
        }
    }

    // Автоудаление дублей из системных «Загрузок».
    LaunchedEffect(hasStoragePermission, settings.autoDeleteDownloadsDuplicates, librarySongs.size) {
        if (hasStoragePermission && settings.autoDeleteDownloadsDuplicates && librarySongs.isNotEmpty()) {
            val duplicates = withContext(Dispatchers.IO) {
                repository.findDownloadsDuplicates(librarySongs.toList())
            }
            if (duplicates.isNotEmpty()) pendingDownloadsDuplicates = duplicates
        }
    }

    // --- Состояние воспроизведения ---
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var isSeeking by remember { mutableStateOf(false) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    // 0 — таймер выключен; иначе сколько треков осталось до паузы.
    var songsUntilStop by remember { mutableIntStateOf(PlaybackTimers.songsUntilStop(context)) }
    // Время (elapsedRealtime) окончания таймера по часам; 0 — выключен.
    var sleepEndAt by remember { mutableLongStateOf(PlaybackTimers.sleepEndAt(context)) }
    var lastMediaId by remember { mutableStateOf<Long?>(null) }
    // Какой трек уже записан в статистику вручную: SEEK-переход к нему не должен считаться второй раз.
    val lastRecordedId = remember { mutableStateOf<Long?>(null) }
    val currentSong = songs.firstOrNull { it.uri == currentUri }

    /** Пишем в статистику: счётчик запусков, историю и события по времени. */
    fun recordPlay(id: Long) {
        lastRecordedId.value = id
        repository.incrementPlayCount(id)
        repository.addToHistory(id)
        repository.recordPlayEvent(id)
        playCounts = repository.loadPlayCounts()
        historyIds = repository.loadHistory()
        playEvents = repository.loadPlayEvents()
    }

    /** Сохраняем место только для длинных треков: обычные песни начинаем с начала. */
    fun saveCurrentPosition() {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val total = p.duration
        if (total < RESUME_MIN_MS) return
        val position = p.currentPosition
        repository.savePosition(id, if (position >= total - 15_000L) 0L else position)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) saveCurrentPosition()
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = player?.duration?.coerceAtLeast(0L) ?: 0L
                // Очередь доигрывает до последнего трека и останавливается.
                // Автоповтор — только если пользователь сам включил повтор кнопкой.
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled = enabled
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentUri = mediaItem?.localConfiguration?.uri
                val newId = mediaItem?.mediaId?.toLongOrNull()
                val autoAdvance = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                if (autoAdvance) {
                    // Дослушанный трек не должен продолжаться с конца при повторном запуске.
                    lastMediaId?.let { repository.clearPosition(it) }
                    newId?.let { recordPlay(it) }
                } else if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                    newId != null && newId != lastRecordedId.value
                ) {
                    // «Дальше/назад» и выбор из очереди тоже запускают трек — считаем в статистике.
                    recordPlay(newId)
                }
                lastMediaId = newId
                // Отсчёт «стоп через N» ведёт PlaybackService, здесь только обновляем показ.
                songsUntilStop = PlaybackTimers.songsUntilStop(context)
            }
        }
        player?.addListener(listener)
        if (player != null) {
            // После поворота экрана подтягиваем текущее состояние, а не ждём следующего события.
            isPlaying = player.isPlaying
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            currentUri = player.currentMediaItem?.localConfiguration?.uri
            lastMediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
            shuffleEnabled = player.shuffleModeEnabled
            repeatMode = player.repeatMode
        }
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        var lastSavedAt = SystemClock.elapsedRealtime()
        while (isPlaying) {
            // Пока пользователь тянет ползунок, не перезаписываем позицию.
            if (!isSeeking) {
                currentPosition = player?.currentPosition ?: 0L
            }
            if (SystemClock.elapsedRealtime() - lastSavedAt >= 5000L) {
                lastSavedAt = SystemClock.elapsedRealtime()
                saveCurrentPosition()
            }
            delay(100)
        }
    }

    // Таймер сна по времени: пауза без остановки сервиса.
    LaunchedEffect(sleepEndAt) {
        if (sleepEndAt <= 0L) return@LaunchedEffect
        while (true) {
            val left = sleepEndAt - SystemClock.elapsedRealtime()
            if (left <= 0L) {
                player?.pause()
                sleepEndAt = 0L
                PlaybackTimers.setSleepEndAt(context, 0L)
                break
            }
            delay(left.coerceAtMost(1000L))
        }
    }

    // --- Обложки ---
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(AudioCoverFetcher.Factory(context)) }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }

    // --- Диалоги и меню ---
    var showMenuIndex by remember { mutableIntStateOf(-1) }
    var isImportingArchive by remember { mutableStateOf(false) }
    var shareAlbumTarget by remember { mutableStateOf<Album?>(null) }
    var isPackingAlbum by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissedUpdateTag by rememberSaveable { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var pendingCoverChangeId by remember { mutableLongStateOf(-1L) }
    var pendingDeleteId by remember { mutableLongStateOf(-1L) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteSelected by remember { mutableStateOf(false) }
    var albumPickerForSelection by remember { mutableStateOf(false) }
    var createAlbumPreselected by remember { mutableStateOf(emptySet<Long>()) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val id = pendingCoverChangeId
        pendingCoverChangeId = -1L
        val index = songs.indexOfFirst { it.id == id }
        if (uri != null && index >= 0) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            songs[index] = songs[index].copy(customCover = uri)
            coverPresence = coverPresence + (id to true)
            repository.saveCoverPresence(coverPresence)
            repository.save(songs)
        }
    }

    val albumCoverPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val album = pendingAlbumCoverChange
        pendingAlbumCoverChange = null
        val index = customAlbums.indexOf(album)
        if (uri != null && album != null && index >= 0) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val updated = album.copy(coverUri = uri.toString())
            customAlbums = customAlbums.toMutableList().also { it[index] = updated }
            repository.saveCustomAlbums(customAlbums)
        }
    }

    val scope = rememberCoroutineScope()
    val appVersion = remember { UpdateChecker.currentVersion(context) }

    /** Проверка обновлений на GitHub (при запуске — молча, вручную — с сообщением). */
    fun runUpdateCheck(manual: Boolean) {
        val repo = settings.updateRepo.trim()
        if (repo.isBlank()) {
            if (manual) {
                Toast.makeText(context, R.string.update_repo_missing, Toast.LENGTH_LONG).show()
            }
            return
        }
        scope.launch {
            when (val result = UpdateChecker.check(context, repo)) {
                is UpdateCheck.Available -> {
                    if (result.info.tag != dismissedUpdateTag) {
                        updateInfo = result.info
                    } else if (manual) {
                        Toast.makeText(context, R.string.update_none, Toast.LENGTH_SHORT).show()
                    }
                }
                UpdateCheck.UpToDate ->
                    if (manual) Toast.makeText(context, R.string.update_none, Toast.LENGTH_SHORT).show()
                UpdateCheck.Failed ->
                    if (manual) Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val previous = settings.lastSeenVersion
        if (previous.isNotBlank() && previous != appVersion) {
            Toast.makeText(context, context.getString(R.string.update_installed, appVersion), Toast.LENGTH_LONG).show()
        }
        if (previous != appVersion) settingsStore.setLastSeenVersion(appVersion)
        if (settings.checkUpdates && settings.updateRepo.isNotBlank()) runUpdateCheck(manual = false)
    }
    val songPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        songPickerOpen = false
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val song = withContext(Dispatchers.IO) {
                    repository.readMetadata(uri, repository.newAddedId())
                }
                if (song != null && songs.none { it.uri == uri }) songs.add(song)
            }
            repository.save(songs)
        }
    }

    // --- Импорт альбома из архива (zip/rar/7z) ---
    val archivePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingArchive = true
        scope.launch {
            val result = runCatching {
                val imported = withContext(Dispatchers.IO) {
                    ArchiveImporter.import(context, uri)
                }
                val newSongs = withContext(Dispatchers.IO) {
                    imported.songs.mapNotNull { file ->
                        repository.readMetadata(Uri.fromFile(file), repository.newAddedId())
                    }
                }
                if (newSongs.isEmpty()) error("Не удалось прочитать треки из архива")
                songs.addAll(newSongs)
                repository.save(songs)
                val name = uniqueAlbumName(imported.albumName, customAlbums.map { it.name })
                customAlbums = customAlbums + CustomAlbum(
                    name = name,
                    songIds = newSongs.map { it.id },
                    coverUri = imported.cover?.let { Uri.fromFile(it).toString() }
                )
                repository.saveCustomAlbums(customAlbums)
                newSongs.size
            }
            isImportingArchive = false
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { count -> context.getString(R.string.archive_import_done, count) },
                    onFailure = { error ->
                        error.message ?: context.getString(R.string.archive_import_failed)
                    }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Упаковывает альбом в выбранный формат и открывает системное «Поделиться». */
    fun shareAlbumArchive(album: Album, format: AlbumArchiveFormat) {
        isPackingAlbum = true
        scope.launch {
            val result = runCatching {
                val file = AlbumArchiver.create(
                    context = context,
                    albumName = album.artist,
                    songs = album.songs,
                    coverUri = album.coverUri?.let(Uri::parse),
                    format = format
                )
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = when (format) {
                        AlbumArchiveFormat.Zip -> "application/zip"
                        AlbumArchiveFormat.SevenZip -> "application/x-7z-compressed"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(album.artist, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, album.artist))
            }
            isPackingAlbum = false
            result.onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: context.getString(R.string.share_archive_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // --- Резервная копия: настройки и библиотека одним JSON-файлом ---
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val json = JSONObject()
                .put("format", BACKUP_FORMAT)
                .put("version", 1)
                .put("library", repository.exportPrefs())
                .put("settings", settingsStore.exportPrefs())
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toString(2).toByteArray())
            } ?: error("Не удалось открыть файл")
        }
        Toast.makeText(
            context,
            if (result.isSuccess) R.string.backup_export_done else R.string.backup_export_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Не удалось открыть файл")
            val json = JSONObject(text)
            require(json.optString("format") == BACKUP_FORMAT)
            val libraryJson = json.optJSONObject("library") ?: error("Нет данных библиотеки")
            val settingsJson = json.optJSONObject("settings") ?: error("Нет данных настроек")
            repository.importPrefs(libraryJson)
            settingsStore.importPrefs(settingsJson)
            libraryVersion++
        }
        Toast.makeText(
            context,
            if (result.isSuccess) R.string.backup_import_done else R.string.backup_import_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Место, с которого продолжать длинный трек. */
    fun resumePosition(song: Song): Long {
        if (!settings.resumeLongTracks || song.durationMs < RESUME_MIN_MS) return 0L
        val max = (song.durationMs - 10_000L).coerceAtLeast(0L)
        return repository.loadPosition(song.id).coerceIn(0L, max)
    }

    /** Случайная песня + «радио»: дальше треки тоже идут в случайном порядке. */
    fun playRandomFlow(selected: Song) {
        val current = player ?: return
        current.shuffleModeEnabled = false
        current.setMediaItems(librarySongs.toMediaItems())
        val index = librarySongs.indexOf(selected)
        if (index >= 0) current.seekTo(index, resumePosition(selected))
        current.shuffleModeEnabled = true
        current.prepare()
        current.play()
        recordPlay(selected.id)
    }

    fun startRandomPlayback() {
        if (librarySongs.size < 2) return
        if (settings.funRandomMode) {
            // 48 секторов хватает: рисовать и вращать тысячу дуг — лишняя нагрузка.
            wheelCandidates = librarySongs.shuffled().take(48)
            showWheel = true
        } else {
            playRandomFlow(librarySongs.random())
        }
    }

    fun playSong(playlist: List<Song>, selected: Song) {
        val current = player ?: return
        // Ручной выбор всегда играет список по порядку. Иначе при включённом shuffle
        // в маленьком альбоме выбранная песня могла оказаться последней в перемешанном
        // порядке — и следующей за ней просто не было.
        if (current.shuffleModeEnabled) current.shuffleModeEnabled = false
        val index = playlist.indexOf(selected)
        current.setMediaItems(playlist.toMediaItems())
        if (index >= 0) current.seekTo(index, resumePosition(selected))
        current.prepare()
        current.play()
        recordPlay(selected.id)
    }

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        repository.saveFavorites(favorites)
    }

    fun shareSong(song: Song) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, song.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, song.title)) }
    }

    fun shareSongs(selected: List<Song>) {
        if (selected.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selected.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    // --- Удаление файлов с подтверждением системы (Android 10+) ---
    // Файлы, скачанные браузером/мессенджером, нельзя удалить молча: система
    // требует согласия. На Android 11+ показываем ОДИН системный диалог на
    // весь список (MediaStore.createDeleteRequest), на Android 10 — по одному.
    // Из состояния трек убираем только после того, как файл реально удалён.
    var pendingDeleteTargets by remember { mutableStateOf(emptyList<Song>()) }

    fun removeSongsFromLibrary(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val playingId = player?.currentMediaItem?.mediaId?.toLongOrNull()
        if (player != null && playingId != null && playingId in ids) {
            player.stop()
            player.clearMediaItems()
        }
        songs.removeAll(songs.filter { it.id in ids })
        favorites = favorites - ids
        playCounts = playCounts - ids
        historyIds = historyIds.filterNot { it in ids }
        ids.forEach { repository.clearPosition(it) }
        customAlbums = customAlbums.map { album ->
            album.copy(songIds = album.songIds.filterNot { it in ids })
        }
        repository.saveCustomAlbums(customAlbums)
        repository.save(songs)
        repository.saveFavorites(favorites)
    }

    val recoverDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (pendingDeleteTargets.isEmpty()) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // На Android 11+ файлы удалила система, если пользователь подтвердил.
            val removed = pendingDeleteTargets
            pendingDeleteTargets = emptyList()
            if (result.resultCode == Activity.RESULT_OK) {
                removeSongsFromLibrary(removed.map { it.id }.toSet())
            }
            return@rememberLauncherForActivityResult
        }
        // Android 10: пользователь разрешил — удаляем этот файл и спрашиваем про следующий.
        val first = pendingDeleteTargets.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && first != null) {
            val deleted = runCatching { context.contentResolver.delete(first.uri, null, null) }.isSuccess
            pendingDeleteTargets = pendingDeleteTargets.drop(1)
            if (deleted) removeSongsFromLibrary(setOf(first.id))
        } else {
            pendingDeleteTargets = emptyList()
        }
    }

    // Android 10 отдаёт согласие только на один файл за раз — идём по очереди.
    LaunchedEffect(pendingDeleteTargets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return@LaunchedEffect
        val target = pendingDeleteTargets.firstOrNull() ?: return@LaunchedEffect
        try {
            context.contentResolver.delete(target.uri, null, null)
            pendingDeleteTargets = pendingDeleteTargets.drop(1)
            removeSongsFromLibrary(setOf(target.id))
        } catch (e: RecoverableSecurityException) {
            runCatching {
                recoverDeleteLauncher.launch(
                    IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                )
            }.onFailure { pendingDeleteTargets = pendingDeleteTargets.drop(1) }
        } catch (e: Exception) {
            // Файл уже исчез или удалить нельзя — оставляем трек в библиотеке.
            pendingDeleteTargets = pendingDeleteTargets.drop(1)
            Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSongs(targets: List<Song>) {
        if (targets.isEmpty()) return
        val deletedIds = mutableSetOf<Long>()
        val failed = mutableListOf<Song>()
        for (song in targets) {
            try {
                context.contentResolver.delete(song.uri, null, null)
                deletedIds += song.id
            } catch (e: Exception) {
                failed += song
            }
        }
        removeSongsFromLibrary(deletedIds)
        // Добавленные вручную файлы (SAF-URI) система удалять не умеет — честно говорим об этом.
        val mediaStoreTargets = failed.filterNot { it.isCustom }
        if (mediaStoreTargets.size < failed.size) {
            Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
        if (mediaStoreTargets.isEmpty()) return
        pendingDeleteTargets = mediaStoreTargets
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    mediaStoreTargets.map { it.uri }
                )
                recoverDeleteLauncher.launch(
                    IntentSenderRequest.Builder(request.intentSender).build()
                )
            }.onFailure {
                pendingDeleteTargets = emptyList()
                Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteSelectedSongs() {
        val ids = selectedIds
        if (ids.isEmpty()) return
        deleteSongs(songs.filter { it.id in ids })
        pendingDeleteSelected = false
        selectionMode = false
        selectedIds = emptySet()
    }

    // --- Альбомы по исполнителям ---
    val albums = if (!settings.autoAlbums) {
        emptyList()
    } else {
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownNames = setOf("", "<unknown>", "unknown", "unknown artist")
        librarySongs.groupBy { it.artist.trim().lowercase() }
            .filter { (_, list) ->
                val artist = list.first().artist.trim()
                list.size >= settings.minSongsForAlbum &&
                    artist.isNotEmpty() && artist.lowercase() !in unknownNames &&
                    !artist.equals(unknownArtist, ignoreCase = true)
            }
            .map { (_, list) ->
                // Один и тот же исполнитель может быть записан по-разному (регистр, пробелы) —
                // объединяем в один альбом и берём самый частый вариант имени.
                val name = list.groupingBy { it.artist.trim() }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: list.first().artist
                Album(name, list)
            }
            .sortedBy { it.artist.lowercase() }
            // Исполнитель, у которого есть свой альбом с таким же именем, показывается
            // только как свой альбом (чтобы не было двух одинаковых).
            .filterNot { album -> customAlbums.any { it.name == album.artist } }
    }

    // --- Поиск ---
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleSongs = if (searchQuery.isBlank()) {
        null
    } else {
        librarySongs.filter { it.matchesQuery(searchQuery) }
    }

    // --- Альбомы, созданные пользователем ---
    val customAlbumItems = remember(customAlbums, librarySongs) {
        val byId = librarySongs.associateBy { it.id }
        customAlbums.map { custom ->
            Album(
                custom.name,
                custom.songIds.mapNotNull { byId[it] },
                isCustom = true,
                coverUri = custom.coverUri
            )
        }
    }

    // --- Сортировка треков ---
    val songsById = librarySongs.associateBy { it.id }
    val shuffledOrder = remember(librarySongs.size) { librarySongs.map { it.id }.shuffled() }
    val unknownArtistName = context.getString(R.string.unknown_artist)

    /** Ключ «по обложке»: свои обложки → настоящие альбомы → по исполнителю → остальное. */
    fun albumCoverKey(song: Song): String {
        if (song.customCover != null) return "0|custom"
        if (song.albumId != 0L) return "1|${song.albumId}"
        val artist = song.artist.trim()
        return if (artist.isEmpty() || artist.equals(unknownArtistName, ignoreCase = true)) {
            "9|"
        } else {
            "2|${artist.lowercase()}"
        }
    }

    val sortedSongs = when (settings.trackSort) {
        1 -> librarySongs.sortedBy { it.title.lowercase() }
        2 -> librarySongs.sortedByDescending { it.title.lowercase() }
        3 -> librarySongs.sortedBy { it.durationMs }
        4 -> librarySongs.sortedByDescending { it.durationMs }
        5 -> librarySongs.sortedWith(
            compareByDescending<Song> { coverPresence[it.id] == true }
                .thenBy { albumCoverKey(it) }
                .thenBy { it.title.lowercase() }
        )
        6 -> librarySongs.sortedByDescending { it.dateAdded }
        7 -> librarySongs.sortedWith(
            compareBy<Song> { if (it.id in favorites) 0 else 1 }
                .thenBy { it.title.lowercase() }
        )
        8 -> librarySongs.sortedWith(
            compareByDescending<Song> { playCounts[it.id] ?: 0 }
                .thenBy { it.title.lowercase() }
        )
        9 -> shuffledOrder.mapNotNull { songsById[it] }
        10 -> librarySongs.sortedWith(
            compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
        11 -> librarySongs.sortedWith(
            compareBy<Song> { it.album.lowercase() }.thenBy { it.title.lowercase() }
        )
        else -> librarySongs.toList()
    }
    val sortLabels = listOf(
        stringResource(R.string.sort_default),
        stringResource(R.string.sort_title_asc),
        stringResource(R.string.sort_title_desc),
        stringResource(R.string.sort_shortest),
        stringResource(R.string.sort_longest),
        stringResource(R.string.sort_by_album),
        stringResource(R.string.sort_newest),
        stringResource(R.string.sort_favorites),
        stringResource(R.string.sort_played),
        stringResource(R.string.sort_shuffle),
        stringResource(R.string.sort_artist),
        stringResource(R.string.sort_album)
    )

    // --- Быстрая прокрутка по алфавиту ---
    val scrollerText: (Song) -> String = when (settings.trackSort) {
        10 -> { song -> song.artist }
        11 -> { song -> song.album }
        else -> { song -> song.title }
    }
    val letterIndexes = linkedMapOf<String, Int>()
    sortedSongs.forEachIndexed { index, song ->
        val first = scrollerText(song).trim().firstOrNull()?.uppercaseChar()
        // Русские и латинские буквы — как есть, цифры и символы — в «#».
        val letter = if (first != null && first.isLetter()) first.toString() else "#"
        letterIndexes.getOrPut(letter) { index }
    }
    val scrollerLetters = letterIndexes.keys.toList()
    // На узкой полосе больше ~24 букв не читается — показываем каждую N-ю.
    val scrollerStep = if (scrollerLetters.size > 24) (scrollerLetters.size + 23) / 24 else 1
    val visibleScrollerLetters = scrollerLetters.filterIndexed { index, _ -> index % scrollerStep == 0 }
    // Полоса букв имеет смысл только когда список реально в алфавитном порядке.
    val showLetterScroller = settings.trackSort == 1 || settings.trackSort == 2 ||
        settings.trackSort == 10 || settings.trackSort == 11

    // --- Перетаскивание ---
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Индексы относятся к видимому списку: ищем песни по id,
        // потому что в полном списке могут быть скрытые папки.
        val fromSong = sortedSongs.getOrNull(from.index)
        val toSong = sortedSongs.getOrNull(to.index)
        if (fromSong != null && toSong != null) {
            val fromIndex = songs.indexOfFirst { it.id == fromSong.id }
            val toIndex = songs.indexOfFirst { it.id == toSong.id }
            if (fromIndex >= 0 && toIndex >= 0) {
                songs.add(toIndex, songs.removeAt(fromIndex))
                repository.save(songs)
            }
        }
    }

    // --- Навигация ---
    val addedSongs = librarySongs.filter { it.isCustom }
    var screen by rememberSaveable { mutableStateOf<Screen>(Screen.Songs) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Порядок пунктов шторки: перетаскивается за ручку и сохраняется в настройках.
    val drawerOrder = DrawerEntry.ordered(settings.drawerOrder)
    val drawerListState = rememberLazyListState()
    val drawerReorderState = rememberReorderableLazyListState(drawerListState) { from, to ->
        val order = DrawerEntry.ordered(settingsStore.settings.value.drawerOrder).toMutableList()
        if (from.index in order.indices && to.index in order.indices) {
            order.add(to.index, order.removeAt(from.index))
            settingsStore.setDrawerOrder(order.map { it.key })
        }
    }

    BackHandler(enabled = screen !is Screen.Songs) {
        screen = when (screen) {
            is Screen.AlbumDetail -> Screen.Albums
            is Screen.FolderDetail -> Screen.Folders
            else -> Screen.Songs
        }
    }

    LaunchedEffect(screen) {
        if (screen !is Screen.Songs) {
            selectionMode = false
            selectedIds = emptySet()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // По краям шторки — переключение треков назад/вперёд.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player?.seekToPrevious() }) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            stringResource(R.string.previous),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = currentSong?.title ?: "—",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { player?.seekToNext() }) {
                        Icon(
                            Icons.Default.SkipNext,
                            stringResource(R.string.next),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    state = drawerListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(drawerOrder, key = { _, entry -> entry.key }) { _, entry ->
                        ReorderableItem(drawerReorderState, key = entry.key) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NavigationDrawerItem(
                                    label = { Text(stringResource(entry.labelRes)) },
                                    icon = { Icon(entry.icon, null) },
                                    selected = entry.isSelected(screen),
                                    onClick = {
                                        when (entry) {
                                            DrawerEntry.Random -> {
                                                screen = Screen.Songs
                                                startRandomPlayback()
                                            }
                                            DrawerEntry.Songs -> screen = Screen.Songs
                                            DrawerEntry.Favorites -> screen = Screen.Favorites
                                            DrawerEntry.Added -> screen = Screen.Added
                                            DrawerEntry.Albums -> screen = Screen.Albums
                                            DrawerEntry.Folders -> screen = Screen.Folders
                                            DrawerEntry.Stats -> screen = Screen.Stats
                                            DrawerEntry.Recap -> screen = Screen.Recap
                                            DrawerEntry.Settings -> screen = Screen.Settings
                                        }
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (settings.drawerEditable) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.reorder),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .draggableHandle()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        if (screen is Screen.Player) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                PlayerScreen(
                    player = player,
                    imageLoader = imageLoader,
                    favoriteUris = songs.filter { it.id in favorites }.map { it.uri }.toSet(),
                    onToggleFavorite = { uri ->
                        songs.firstOrNull { it.uri == uri }?.let { toggleFavorite(it.id) }
                    },
                    songsUntilStop = songsUntilStop.takeIf { it > 0 },
                    onSongsUntilStopChange = { count ->
                        songsUntilStop = count ?: 0
                        PlaybackTimers.setSongsUntilStop(context, count ?: 0)
                        if (count != null) {
                            sleepEndAt = 0L
                            PlaybackTimers.setSleepEndAt(context, 0L)
                        }
                    },
                    sleepTimerEndAt = sleepEndAt.takeIf { it > 0 },
                    onSleepTimerChange = { endAt ->
                        sleepEndAt = endAt ?: 0L
                        PlaybackTimers.setSleepEndAt(context, endAt ?: 0L)
                        if (endAt != null) {
                            songsUntilStop = 0
                            PlaybackTimers.setSongsUntilStop(context, 0)
                        }
                    },
                    onOpenQueue = { showQueue = true },
                    coverRotation = settings.coverRotation,
                    rotateBackground = settings.backgroundRotation,
                    onClose = { screen = Screen.Songs }
                )
            }
        } else {
            Scaffold(
                topBar = {
                    AppTopBar(
                        screen = screen,
                        trackCount = if (screen is Screen.Added) addedSongs.size else librarySongs.size,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBack = {
                            screen = when (screen) {
                                is Screen.FolderDetail -> Screen.Folders
                                else -> Screen.Albums
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (screen is Screen.Songs || screen is Screen.Added) {
                        FloatingActionButton(onClick = {
                            songPickerOpen = true
                            songPickerLauncher.launch(arrayOf("audio/*"))
                        }) {
                            Icon(Icons.Default.Add, stringResource(R.string.add_song))
                        }
                    }
                },
                bottomBar = {
                    if (player != null) {
                        BottomControlBar(
                            isPlaying = isPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            currentPosition = currentPosition,
                            duration = duration,
                            songTitle = currentSong?.title,
                            onTitleClick = {
                                if (currentSong != null) screen = Screen.Player
                            },
                            onPlayPause = { if (isPlaying) player.pause() else player.play() },
                            onNext = { player.seekToNext() },
                            onPrevious = { player.seekToPrevious() },
                            onSeek = { position -> player.seekTo(position) },
                            onToggleShuffle = { player.shuffleModeEnabled = !shuffleEnabled },
                            onCycleRepeat = { player.repeatMode = nextRepeatMode(repeatMode) },
                            onScrubbingChange = { isSeeking = it }
                        )
                    }
                }
            ) { padding ->
                when (val current = screen) {
                    is Screen.Songs -> if (!hasStoragePermission) {
                        PermissionRequiredScreen(
                            modifier = Modifier.padding(padding),
                            onGrant = {
                                val permissions = buildList {
                                    add(requiredStoragePermission())
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                permissionLauncher.launch(permissions.toTypedArray())
                            },
                            onOpenSettings = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                }
                            }
                        )
                    } else Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    if (it.isNotBlank() && selectionMode) {
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true
                            )
                            if (visibleSongs == null && !selectionMode) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    showMenuIndex = -1
                                    selectionMode = true
                                }) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        stringResource(R.string.select_songs)
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    sortLabels.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = {
                                                if (index == settings.trackSort) {
                                                    Icon(Icons.Default.Check, null)
                                                }
                                            },
                                            onClick = {
                                                settingsStore.setTrackSort(index)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (selectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.selected_count, selectedIds.size),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                IconButton(
                                    onClick = {
                                        selectedIds = if (selectedIds.size == sortedSongs.size) {
                                            emptySet()
                                        } else {
                                            sortedSongs.map { it.id }.toSet()
                                        }
                                    },
                                    enabled = sortedSongs.isNotEmpty(),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Check, stringResource(R.string.select_all))
                                }
                                IconButton(
                                    onClick = { albumPickerForSelection = true },
                                    enabled = selectedIds.isNotEmpty(),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Album,
                                        stringResource(R.string.add_selected_to_album)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        shareSongs(librarySongs.filter { it.id in selectedIds })
                                    },
                                    enabled = selectedIds.isNotEmpty(),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        stringResource(R.string.share_selected)
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeleteSelected = true },
                                    enabled = selectedIds.isNotEmpty(),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.delete_selected),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        stringResource(R.string.close_selection)
                                    )
                                }
                            }
                        }
                        if (visibleSongs == null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                                        ReorderableItem(reorderableState, key = song.id) { isDragging ->
                                            SongRow(
                                                song = song,
                                                isDragging = isDragging,
                                                menuExpanded = showMenuIndex == index,
                                                imageLoader = imageLoader,
                                                isFavorite = song.id in favorites,
                                                isSelected = song.id in selectedIds,
                                                selectionMode = selectionMode,
                                                showDragHandle = settings.trackSort == 0 && !selectionMode,
                                                onClick = { playSong(sortedSongs, song) },
                                                onToggleSelection = {
                                                    selectedIds = if (song.id in selectedIds) {
                                                        selectedIds - song.id
                                                    } else {
                                                        selectedIds + song.id
                                                    }
                                                },
                                                onLongClick = { showMenuIndex = index },
                                                onDismissMenu = { showMenuIndex = -1 },
                                                onChangeCover = {
                                                    showMenuIndex = -1
                                                    pendingCoverChangeId = song.id
                                                    coverPickerLauncher.launch(arrayOf("image/*"))
                                                },
                                                onDelete = {
                                                    showMenuIndex = -1
                                                    pendingDeleteId = song.id
                                                },
                                                onToggleFavorite = { toggleFavorite(song.id) },
                                                onShare = { shareSong(song) }
                                            )
                                        }
                                    }
                                }
                                if (showLetterScroller) {
                                    FastScroller(
                                        listState = lazyListState,
                                        letters = visibleScrollerLetters,
                                        indexOfLetter = { letterIndexes[it] },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(28.dp)
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(visibleSongs, key = { _, song -> song.id }) { _, song ->
                                    SimpleSongRow(
                                        song = song,
                                        imageLoader = imageLoader,
                                        isFavorite = song.id in favorites,
                                        onToggleFavorite = { toggleFavorite(song.id) },
                                        onClick = { playSong(librarySongs, song) },
                                        onShare = { shareSong(song) },
                                        onDelete = { pendingDeleteId = song.id },
                                        onChangeCover = {
                                            pendingCoverChangeId = song.id
                                            coverPickerLauncher.launch(arrayOf("image/*"))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is Screen.Favorites -> {
                        val favoriteSongs = librarySongs.filter { it.id in favorites }
                        if (favoriteSongs.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.favorites_empty),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                            ) {
                                itemsIndexed(favoriteSongs, key = { _, song -> song.id }) { _, song ->
                                    SimpleSongRow(
                                        song = song,
                                        imageLoader = imageLoader,
                                        isFavorite = true,
                                        onToggleFavorite = { toggleFavorite(song.id) },
                                        onClick = { playSong(favoriteSongs, song) },
                                        onShare = { shareSong(song) },
                                        onDelete = { pendingDeleteId = song.id },
                                        onChangeCover = {
                                            pendingCoverChangeId = song.id
                                            coverPickerLauncher.launch(arrayOf("image/*"))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is Screen.Added -> {
                        if (addedSongs.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.added_empty),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                            ) {
                                itemsIndexed(addedSongs, key = { _, song -> song.id }) { _, song ->
                                    SimpleSongRow(
                                        song = song,
                                        imageLoader = imageLoader,
                                        isFavorite = song.id in favorites,
                                        onToggleFavorite = { toggleFavorite(song.id) },
                                        onClick = { playSong(addedSongs, song) },
                                        onShare = { shareSong(song) },
                                        onDelete = { pendingDeleteId = song.id },
                                        onChangeCover = {
                                            pendingCoverChangeId = song.id
                                            coverPickerLauncher.launch(arrayOf("image/*"))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is Screen.Albums -> AlbumsScreen(
                        albums = albums,
                        customAlbums = customAlbumItems,
                        imageLoader = imageLoader,
                        modifier = Modifier.padding(padding),
                        onOpenAlbum = { screen = Screen.AlbumDetail(it.artist) },
                        onOpenSettings = { screen = Screen.Settings },
                        onCreateAlbum = { showCreateAlbum = true },
                        onCreateFromArchive = {
                            archivePickerLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/x-rar-compressed",
                                    "application/vnd.rar",
                                    "application/x-7z-compressed",
                                    "application/octet-stream"
                                )
                            )
                        },
                        onChangeAlbumCover = { album ->
                            customAlbums.firstOrNull { it.name == album.artist }?.let { target ->
                                pendingAlbumCoverChange = target
                                albumCoverPickerLauncher.launch(arrayOf("image/*"))
                            }
                        },
                        onDeleteAlbum = { album ->
                            customAlbums.firstOrNull { it.name == album.artist }?.let {
                                pendingDeleteAlbum = it
                            }
                        },
                        onShareAlbum = { shareAlbumTarget = it }
                    )

                    is Screen.AlbumDetail -> {
                        val album = customAlbumItems.firstOrNull { it.artist == current.artist }
                            ?: albums.firstOrNull { it.artist == current.artist }
                        if (album != null) {
                            val customIndex = customAlbumItems.indexOf(album)
                            AlbumDetailScreen(
                                album = album,
                                imageLoader = imageLoader,
                                modifier = Modifier.padding(padding),
                                favorites = favorites,
                                onPlay = { song -> playSong(album.songs, song) },
                                onToggleFavorite = ::toggleFavorite,
                                onShare = ::shareSong,
                                onDelete = { song -> pendingDeleteId = song.id },
                                onChangeCover = { song ->
                                    pendingCoverChangeId = song.id
                                    coverPickerLauncher.launch(arrayOf("image/*"))
                                },
                                onAddTracks = {
                                    val target = if (customIndex >= 0) {
                                        customAlbums.getOrNull(customIndex)
                                    } else {
                                        // Автоальбом: превращаем его в свой альбом, чтобы дополнять.
                                        CustomAlbum(album.artist, album.songs.map { it.id })
                                            .also { created ->
                                                customAlbums = customAlbums + created
                                                repository.saveCustomAlbums(customAlbums)
                                            }
                                    }
                                    addToAlbumTarget = target
                                }
                            )
                        } else {
                            // Альбом исчез после изменения настроек — возвращаемся к списку.
                            LaunchedEffect(current.artist) { screen = Screen.Albums }
                        }
                    }

                    is Screen.Folders -> FoldersScreen(
                        songs = songs.toList(),
                        hiddenFolders = hiddenFolders,
                        modifier = Modifier.padding(padding),
                        onOpen = { screen = Screen.FolderDetail(it) },
                        onHide = { path ->
                            settingsStore.setHiddenFolders(hiddenFolders + path)
                        },
                        onShow = { path ->
                            settingsStore.setHiddenFolders(hiddenFolders - path)
                        }
                    )

                    is Screen.FolderDetail -> {
                        val folderSongs = songs.filter {
                            it.relativePath.trimEnd('/') == current.path
                        }
                        if (folderSongs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.folder_empty),
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                                    .padding(32.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                            ) {
                                items(folderSongs, key = { it.id }) { song ->
                                    SimpleSongRow(
                                        song = song,
                                        imageLoader = imageLoader,
                                        isFavorite = song.id in favorites,
                                        onToggleFavorite = { toggleFavorite(song.id) },
                                        onClick = { playSong(folderSongs, song) },
                                        onShare = { shareSong(song) },
                                        onDelete = { pendingDeleteId = song.id },
                                        onChangeCover = {
                                            pendingCoverChangeId = song.id
                                            coverPickerLauncher.launch(arrayOf("image/*"))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is Screen.Stats -> StatsScreen(
                        songs = librarySongs,
                        favoritesCount = librarySongs.count { it.id in favorites },
                        playCounts = playCounts,
                        historyIds = historyIds,
                        modifier = Modifier.padding(padding),
                        onPlaySong = { song -> playSong(librarySongs, song) }
                    )

                    is Screen.Recap -> RecapScreen(
                        songs = librarySongs,
                        playEvents = playEvents,
                        modifier = Modifier.padding(padding)
                    )

                    is Screen.Settings -> SettingsScreen(
                        settings = settings,
                        appVersion = appVersion,
                        modifier = Modifier.padding(padding),
                        onAutoAlbumsChange = settingsStore::setAutoAlbums,
                        onMinSongsChange = settingsStore::setMinSongsForAlbum,
                        onDynamicColorChange = settingsStore::setDynamicColor,
                        onDarkModeChange = settingsStore::setDarkMode,
                        onAutoDeleteDuplicatesChange = settingsStore::setAutoDeleteDuplicates,
                        onAutoDeleteDownloadsDuplicatesChange = settingsStore::setAutoDeleteDownloadsDuplicates,
                        onPauseOnOtherAudioChange = settingsStore::setPauseOnOtherAudio,
                        onPauseOnHeadphonesUnplugChange = settingsStore::setPauseOnHeadphonesUnplug,
                        onFadeOutMsChange = settingsStore::setFadeOutMs,
                        onFunRandomModeChange = settingsStore::setFunRandomMode,
                        onWheelVariantChange = settingsStore::setWheelVariant,
                        onResumeOnFocusGainChange = settingsStore::setResumeOnFocusGain,
                        onWheelSpeedPercentChange = settingsStore::setWheelSpeedPercent,
                        onResumeLongTracksChange = settingsStore::setResumeLongTracks,
                        onDrawerEditableChange = settingsStore::setDrawerEditable,
                        onAccentHueChange = settingsStore::setAccentHue,
                        onCrossfadeMsChange = settingsStore::setCrossfadeMs,
                        onCoverRotationChange = settingsStore::setCoverRotation,
                        onBackgroundRotationChange = settingsStore::setBackgroundRotation,
                        onSoundProfileChange = settingsStore::setSoundProfile,
                        onVolumeBalanceChange = settingsStore::setVolumeBalance,
                        onTargetLoudnessDbChange = settingsStore::setTargetLoudnessDb,
                        onCheckUpdatesChange = settingsStore::setCheckUpdates,
                        onUpdateRepoChange = settingsStore::setUpdateRepo,
                        onCheckNow = { runUpdateCheck(manual = true) },
                        onExportBackup = { backupExportLauncher.launch("music-backup.json") },
                        onImportBackup = { showImportConfirm = true }
                    )

                    is Screen.Player -> Unit
                }
            }
        }
    }

    if (showQueue && player != null) {
        QueueSheet(player = player, onDismiss = { showQueue = false })
    }

    if (pendingDeleteSelected) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSelected = false },
            title = { Text(stringResource(R.string.delete_selected_title)) },
            text = {
                Text(stringResource(R.string.delete_selected_message, selectedIds.size))
            },
            confirmButton = {
                TextButton(onClick = { deleteSelectedSongs() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSelected = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (albumPickerForSelection) {
        AlertDialog(
            onDismissRequest = { albumPickerForSelection = false },
            title = { Text(stringResource(R.string.add_to_album_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(onClick = {
                        albumPickerForSelection = false
                        createAlbumPreselected = selectedIds
                        selectionMode = false
                        selectedIds = emptySet()
                    }) {
                        Text(stringResource(R.string.new_album))
                    }
                    customAlbums.forEach { album ->
                        TextButton(onClick = {
                            val updated = album.copy(
                                songIds = (album.songIds + selectedIds).distinct()
                            )
                            val index = customAlbums.indexOf(album)
                            if (index >= 0) {
                                customAlbums = customAlbums.toMutableList().also {
                                    it[index] = updated
                                }
                                repository.saveCustomAlbums(customAlbums)
                            }
                            albumPickerForSelection = false
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Text(album.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { albumPickerForSelection = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    backupImportLauncher.launch(
                        arrayOf("application/json", "text/plain")
                    )
                }) {
                    Text(stringResource(R.string.backup_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isImportingArchive) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.archive_importing))
                }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                dismissedUpdateTag = info.tag
                updateInfo = null
            },
            title = { Text(stringResource(R.string.update_available_title, info.version)) },
            text = {
                Column {
                    if (info.notes.isNotBlank()) {
                        Text(
                            text = info.notes.take(600),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateInfo = null
                    isDownloadingUpdate = true
                    scope.launch {
                        val result = runCatching { UpdateChecker.download(context, info) }
                        isDownloadingUpdate = false
                        result.onSuccess { file ->
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                )
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        R.string.update_install_failed,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: context.getString(R.string.update_check_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) {
                    Text(stringResource(R.string.update_install))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    dismissedUpdateTag = info.tag
                    updateInfo = null
                }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }

    if (isDownloadingUpdate) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.update_downloading))
                }
            }
        )
    }

    shareAlbumTarget?.let { album ->
        AlertDialog(
            onDismissRequest = { shareAlbumTarget = null },
            title = { Text(stringResource(R.string.archive_format_title)) },
            text = {
                Column {
                    TextButton(onClick = {
                        shareAlbumTarget = null
                        shareAlbumArchive(album, AlbumArchiveFormat.Zip)
                    }) {
                        Text(stringResource(R.string.archive_format_zip))
                    }
                    TextButton(onClick = {
                        shareAlbumTarget = null
                        shareAlbumArchive(album, AlbumArchiveFormat.SevenZip)
                    }) {
                        Text(stringResource(R.string.archive_format_7z))
                    }
                    TextButton(onClick = {
                        shareAlbumTarget = null
                        Toast.makeText(context, R.string.rar_not_supported, Toast.LENGTH_LONG).show()
                    }) {
                        Text(stringResource(R.string.archive_format_rar))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shareAlbumTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isPackingAlbum) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.packing_album))
                }
            }
        )
    }

    songs.firstOrNull { it.id == pendingDeleteId }?.let { candidate ->
        DeleteSongDialog(
            title = candidate.title,
            onConfirm = {
                pendingDeleteId = -1L
                deleteSongs(listOf(candidate))
            },
            onDismiss = { pendingDeleteId = -1L }
        )
    }

    if (pendingDuplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDuplicates = emptyList() },
            title = { Text(stringResource(R.string.duplicates_found_title)) },
            text = {
                Text(stringResource(R.string.duplicates_found_message, pendingDuplicates.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSongs(pendingDuplicates)
                    pendingDuplicates = emptyList()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDuplicates = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingDownloadsDuplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDownloadsDuplicates = emptyList() },
            title = { Text(stringResource(R.string.downloads_duplicates_found_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.downloads_duplicates_found_message,
                        pendingDownloadsDuplicates.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSongs(pendingDownloadsDuplicates)
                    pendingDownloadsDuplicates = emptyList()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadsDuplicates = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCreateAlbum || createAlbumPreselected.isNotEmpty()) {
        SongPickerDialog(
            title = stringResource(R.string.create_album_title),
            songs = librarySongs.toList(),
            initialSelectedIds = createAlbumPreselected,
            existingNames = customAlbums.map { it.name.trim().lowercase() }.toSet(),
            showNameField = true,
            confirmLabel = stringResource(R.string.create),
            onDismiss = {
                showCreateAlbum = false
                createAlbumPreselected = emptySet()
            },
            onConfirm = { name, ids ->
                customAlbums = customAlbums + CustomAlbum(name, ids)
                repository.saveCustomAlbums(customAlbums)
                showCreateAlbum = false
                createAlbumPreselected = emptySet()
                selectionMode = false
                selectedIds = emptySet()
            }
        )
    }

    addToAlbumTarget?.let { target ->
        SongPickerDialog(
            title = stringResource(R.string.add_to_album_title),
            songs = librarySongs.toList(),
            excludeIds = target.songIds.toSet(),
            confirmLabel = stringResource(R.string.add),
            onDismiss = { addToAlbumTarget = null },
            onConfirm = { _, ids ->
                val updated = target.copy(songIds = (target.songIds + ids).distinct())
                val index = customAlbums.indexOf(target)
                if (index >= 0) {
                    customAlbums = customAlbums.toMutableList().also { it[index] = updated }
                } else {
                    customAlbums = customAlbums + updated
                }
                repository.saveCustomAlbums(customAlbums)
                addToAlbumTarget = null
            }
        )
    }

    if (showWheel) {
        FortuneWheelOverlay(
            candidates = wheelCandidates,
            onDismiss = { showWheel = false },
            onWin = { winner ->
                playRandomFlow(winner)
                showWheel = false
            },
            speedMultiplier = settings.wheelSpeedPercent / 100f,
            friction = 0.962f + (settings.wheelSpeedPercent - 50) / 250f * 0.026f,
            variant = settings.wheelVariant
        )
    }

    pendingDeleteAlbum?.let { album ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAlbum = null },
            title = { Text(stringResource(R.string.delete_album_title)) },
            text = { Text(stringResource(R.string.delete_album_message, album.name)) },
            confirmButton = {
                TextButton(onClick = {
                    customAlbums = customAlbums - album
                    repository.saveCustomAlbums(customAlbums)
                    pendingDeleteAlbum = null
                    screen = Screen.Albums
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAlbum = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Имя альбома, которого ещё нет: «Имя», «Имя (2)», ... */
private fun uniqueAlbumName(base: String, existing: List<String>): String {
    if (existing.none { it.equals(base, ignoreCase = true) }) return base
    var index = 2
    while (existing.any { it.equals("$base ($index)", ignoreCase = true) }) index++
    return "$base ($index)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    screen: Screen,
    trackCount: Int,
    onMenuClick: () -> Unit,
    onBack: () -> Unit
) {
    when (screen) {
        is Screen.Songs -> CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.my_music), fontWeight = FontWeight.Bold)
                    Text(
                        text = pluralStringResource(R.plurals.tracks_count, trackCount, trackCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.Albums -> CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.menu_albums), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.Favorites -> CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.menu_favorites), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.Added -> CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.menu_added), fontWeight = FontWeight.Bold)
                    Text(
                        text = pluralStringResource(R.plurals.tracks_count, trackCount, trackCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.Folders -> CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.menu_folders), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.FolderDetail -> CenterAlignedTopAppBar(
            title = {
                Text(
                    text = screen.path.substringAfterLast('/').ifBlank { screen.path },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )

        is Screen.Stats -> CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.menu_stats), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.Recap -> CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.menu_recap), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.Settings -> CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.menu_settings), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, stringResource(R.string.menu))
                }
            }
        )

        is Screen.AlbumDetail -> CenterAlignedTopAppBar(
            title = { Text(screen.artist, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )

        is Screen.Player -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableCollectionItemScope.SongRow(
    song: Song,
    isDragging: Boolean,
    menuExpanded: Boolean,
    imageLoader: ImageLoader,
    isFavorite: Boolean,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {},
    showDragHandle: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
    Surface(
        tonalElevation = elevation,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (selectionMode) onToggleSelection else onClick,
                onLongClick = if (selectionMode) onToggleSelection else onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
            } else if (showDragHandle) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.reorder),
                    modifier = Modifier.draggableHandle(),
                    tint = Color.LightGray
                )
            }
            CoverBox(
                seed = song.id,
                data = song.customCover ?: song.uri,
                imageLoader = imageLoader,
                requestSize = 150,
                cornerRadius = 4.dp,
                modifier = Modifier
                    .size(50.dp)
                    .padding(start = 8.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(song.durationText, style = MaterialTheme.typography.labelSmall)
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

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.change_cover)) },
                leadingIcon = { Icon(Icons.Default.Image, null) },
                onClick = onChangeCover
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(Icons.Default.Share, null) },
                onClick = onShare
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_file), color = Color.Red) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun DeleteSongDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_dialog_title)) },
        text = { Text(stringResource(R.string.delete_dialog_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun BottomControlBar(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    currentPosition: Long,
    duration: Long,
    songTitle: String?,
    onTitleClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit
) {
    Surface(
        tonalElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Пока тянем ползунок, показываем позицию пальца, а не позицию плеера.
        var dragValue by remember { mutableStateOf<Float?>(null) }
        val sliderValue = dragValue ?: currentPosition.coerceIn(0L, duration).toFloat()
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = 4.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            if (songTitle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTitleClick)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = songTitle,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragValue = it
                    onScrubbingChange(true)
                },
                onValueChangeFinished = {
                    dragValue?.let { onSeek(it.toLong()) }
                    dragValue = null
                    onScrubbingChange(false)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShuffleButton(enabled = shuffleEnabled, onClick = onToggleShuffle)
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        stringResource(R.string.previous),
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        stringResource(R.string.next),
                        modifier = Modifier.size(32.dp)
                    )
                }
                RepeatButton(repeatMode = repeatMode, onClick = onCycleRepeat)
            }
        }
    }
}

/** Папки библиотеки: открыть содержимое или убрать папку из основной выдачи. */
@Composable
private fun FoldersScreen(
    songs: List<Song>,
    hiddenFolders: Set<String>,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
    onHide: (String) -> Unit,
    onShow: (String) -> Unit
) {
    val counts = songs
        .mapNotNull { song ->
            song.relativePath.trimEnd('/').takeIf { it.isNotBlank() }
        }
        .groupingBy { it }
        .eachCount()
    val visible = counts.keys.filterNot { it in hiddenFolders }.sortedBy { it.lowercase() }
    val hidden = hiddenFolders.filter { it.isNotBlank() }.sortedBy { it.lowercase() }

    if (visible.isEmpty() && hidden.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.folders_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(visible, key = { "folder:$it" }) { path ->
            FolderRow(
                path = path,
                count = counts[path] ?: 0,
                hidden = false,
                onOpen = { onOpen(path) },
                onToggle = { onHide(path) }
            )
        }
        if (hidden.isNotEmpty()) {
            item(key = "hidden-folders-title") {
                Text(
                    text = stringResource(R.string.folders_hidden),
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(hidden, key = { "hidden-folder:$it" }) { path ->
                FolderRow(
                    path = path,
                    count = counts[path] ?: 0,
                    hidden = true,
                    onOpen = {},
                    onToggle = { onShow(path) }
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    path: String,
    count: Int,
    hidden: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !hidden, onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (hidden) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = path.substringAfterLast('/').ifBlank { path },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (count > 0) {
                    "${path} · " + pluralStringResource(R.plurals.tracks_count, count, count)
                } else {
                    path
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                stringResource(if (hidden) R.string.folders_show else R.string.folders_hide)
            )
        }
    }
}

/** Заглушка вместо списка, пока нет доступа к музыке: без него список всегда пуст. */
@Composable
private fun PermissionRequiredScreen(
    modifier: Modifier = Modifier,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_needed_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_needed_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text(stringResource(R.string.grant_access))
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_settings))
        }
    }
}
