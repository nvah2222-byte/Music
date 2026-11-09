package com.example.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Пользовательские настройки приложения. */
data class AppSettings(
    val autoAlbums: Boolean = true,
    val minSongsForAlbum: Int = 3,
    val dynamicColor: Boolean = false,
    /** 0 — как в системе, 1 — светлая, 2 — тёмная. */
    val darkMode: Int = 0,
    /** Автоудаление дублей: копии с обложкой удаляются, версии с заглушкой остаются. */
    val autoDeleteDuplicates: Boolean = false,
    /** Автоудаление дублей из системной папки «Загрузки». */
    val autoDeleteDownloadsDuplicates: Boolean = false,
    /** Сортировка треков: 0 — по умолчанию, 1–11 — остальные режимы. */
    val trackSort: Int = 0,
    /** Пауза при других звуках (YouTube, TikTok, звонки). */
    val pauseOnOtherAudio: Boolean = true,
    /** Пауза при отключении наушников. */
    val pauseOnHeadphonesUnplug: Boolean = true,
    /** Длительность плавного затухания, мс (0 — сразу). */
    val fadeOutMs: Int = 800,
    /** Рофляничек: «Случайный трек» запускает колесо фортуны. */
    val funRandomMode: Boolean = false,
    /** Вариант колеса: 0 — огромное, 1 — классическое. */
    val wheelVariant: Int = 0,
    /** Продолжать играть, когда другой звук (YouTube, звонок) закончился. */
    val resumeOnFocusGain: Boolean = true,
    /** Сила раскрутки колеса фортуны, %. */
    val wheelSpeedPercent: Int = 100,
    /** Папки, которые не показываются в основной библиотеке. */
    val hiddenFolders: Set<String> = emptySet(),
    /** Продолжать длинные треки (аудиокниги) с сохранённого места. */
    val resumeLongTracks: Boolean = true,
    /** Порядок пунктов в боковой шторке: ключи DrawerEntry. */
    val drawerOrder: List<String> = emptyList(),
    /** Показывать ручки перетаскивания в шторке (иначе порядок не изменить случайно). */
    val drawerEditable: Boolean = false,
    /** Акцентный оттенок 0..359; -1 — стандартная фиолетовая тема. */
    val accentHue: Int = -1,
    /** Затухание в конце трека и плавный старт следующего, мс; 0 — выключено. */
    val crossfadeMs: Int = 1200,
    /** Крутить обложку на полноэкранном плеере. */
    val coverRotation: Boolean = true,
    /** Крутить размытый фон вместе с обложкой (рофл). */
    val backgroundRotation: Boolean = false,
    /** Профиль звучания: 0 — без обработки, 1 — бас, 2 — вокал, 3 — рок, 4 — джаз. */
    val soundProfile: Int = 0,
    /** Выравнивать громкость всех треков под один уровень. */
    val volumeBalance: Boolean = false,
    /** Целевая громкость нормализации в дБFS. */
    val targetLoudnessDb: Int = -18,
    /** Проверять обновления при запуске. */
    val checkUpdates: Boolean = true,
    /** Репозиторий GitHub в виде owner/repo для проверки релизов. */
    val updateRepo: String = "nvah2222-byte/Music"
)

/** Хранит настройки в SharedPreferences и раздаёт их как StateFlow. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setAutoAlbums(enabled: Boolean) = update { it.copy(autoAlbums = enabled) }

    fun setMinSongsForAlbum(count: Int) =
        update { it.copy(minSongsForAlbum = count.coerceIn(1, MAX_SONGS_PER_ALBUM)) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setDarkMode(mode: Int) = update { it.copy(darkMode = mode.coerceIn(0, 2)) }

    fun setAutoDeleteDuplicates(enabled: Boolean) = update { it.copy(autoDeleteDuplicates = enabled) }

    fun setAutoDeleteDownloadsDuplicates(enabled: Boolean) =
        update { it.copy(autoDeleteDownloadsDuplicates = enabled) }

    fun setTrackSort(sort: Int) = update { it.copy(trackSort = sort.coerceIn(0, 11)) }

    fun setPauseOnOtherAudio(enabled: Boolean) = update { it.copy(pauseOnOtherAudio = enabled) }

    fun setPauseOnHeadphonesUnplug(enabled: Boolean) =
        update { it.copy(pauseOnHeadphonesUnplug = enabled) }

    fun setFadeOutMs(ms: Int) = update { it.copy(fadeOutMs = ms.coerceIn(0, 2000)) }

    fun setFunRandomMode(enabled: Boolean) = update { it.copy(funRandomMode = enabled) }

    fun setResumeOnFocusGain(enabled: Boolean) = update { it.copy(resumeOnFocusGain = enabled) }

    fun setWheelSpeedPercent(percent: Int) =
        update { it.copy(wheelSpeedPercent = percent.coerceIn(50, 300)) }

    fun setWheelVariant(variant: Int) = update { it.copy(wheelVariant = variant.coerceIn(0, 1)) }

    fun setHiddenFolders(folders: Set<String>) = update { it.copy(hiddenFolders = folders) }

    fun setResumeLongTracks(enabled: Boolean) = update { it.copy(resumeLongTracks = enabled) }

    fun setDrawerOrder(order: List<String>) = update { it.copy(drawerOrder = order) }

    fun setDrawerEditable(enabled: Boolean) = update { it.copy(drawerEditable = enabled) }

    fun setAccentHue(hue: Int) = update { it.copy(accentHue = hue.coerceIn(-1, 359)) }

    fun setCrossfadeMs(ms: Int) = update { it.copy(crossfadeMs = ms.coerceIn(0, 6000)) }

    fun setCoverRotation(enabled: Boolean) = update { it.copy(coverRotation = enabled) }

    fun setBackgroundRotation(enabled: Boolean) = update { it.copy(backgroundRotation = enabled) }

    fun setSoundProfile(profile: Int) = update { it.copy(soundProfile = profile.coerceIn(0, 4)) }

    fun setVolumeBalance(enabled: Boolean) = update { it.copy(volumeBalance = enabled) }

    fun setTargetLoudnessDb(db: Int) = update { it.copy(targetLoudnessDb = db.coerceIn(-30, -6)) }

    fun setCheckUpdates(enabled: Boolean) = update { it.copy(checkUpdates = enabled) }

    fun setUpdateRepo(repo: String) = update { it.copy(updateRepo = repo.trim()) }

    fun exportPrefs(): JSONObject = dumpPrefs(prefs)

    fun importPrefs(json: JSONObject) {
        restorePrefs(prefs, json)
        _settings.value = load()
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val oldValue = _settings.value
        val newValue = transform(oldValue)
        // Ползунки шлют события чаще, чем меняется округлённое значение — не пишем впустую.
        if (newValue == oldValue) return
        prefs.edit()
            .putBoolean(KEY_AUTO_ALBUMS, newValue.autoAlbums)
            .putInt(KEY_MIN_SONGS, newValue.minSongsForAlbum)
            .putBoolean(KEY_DYNAMIC_COLOR, newValue.dynamicColor)
            .putInt(KEY_DARK_MODE, newValue.darkMode)
            .putBoolean(KEY_AUTO_DELETE_DUPLICATES, newValue.autoDeleteDuplicates)
            .putBoolean(KEY_AUTO_DELETE_DOWNLOADS, newValue.autoDeleteDownloadsDuplicates)
            .putInt(KEY_TRACK_SORT, newValue.trackSort)
            .putBoolean(KEY_PAUSE_ON_OTHER_AUDIO, newValue.pauseOnOtherAudio)
            .putBoolean(KEY_PAUSE_ON_HEADPHONES, newValue.pauseOnHeadphonesUnplug)
            .putInt(KEY_FADE_OUT_MS, newValue.fadeOutMs)
            .putBoolean(KEY_FUN_RANDOM_MODE, newValue.funRandomMode)
            .putInt(KEY_WHEEL_VARIANT, newValue.wheelVariant)
            .putBoolean(KEY_RESUME_ON_FOCUS_GAIN, newValue.resumeOnFocusGain)
            .putInt(KEY_WHEEL_SPEED, newValue.wheelSpeedPercent)
            .putStringSet(KEY_HIDDEN_FOLDERS, newValue.hiddenFolders)
            .putBoolean(KEY_RESUME_LONG_TRACKS, newValue.resumeLongTracks)
            .putString(KEY_DRAWER_ORDER, newValue.drawerOrder.joinToString(","))
            .putBoolean(KEY_DRAWER_EDITABLE, newValue.drawerEditable)
            .putInt(KEY_ACCENT_HUE, newValue.accentHue)
            .putInt(KEY_CROSSFADE_MS, newValue.crossfadeMs)
            .putBoolean(KEY_COVER_ROTATION, newValue.coverRotation)
            .putBoolean(KEY_BACKGROUND_ROTATION, newValue.backgroundRotation)
            .putInt(KEY_SOUND_PROFILE, newValue.soundProfile)
            .putBoolean(KEY_VOLUME_BALANCE, newValue.volumeBalance)
            .putInt(KEY_TARGET_DB, newValue.targetLoudnessDb)
            .putBoolean(KEY_CHECK_UPDATES, newValue.checkUpdates)
            .putString(KEY_UPDATE_REPO, newValue.updateRepo)
            .apply()
        _settings.value = newValue
    }

    private fun load() = AppSettings(
        autoAlbums = prefs.getBoolean(KEY_AUTO_ALBUMS, true),
        minSongsForAlbum = prefs.getInt(KEY_MIN_SONGS, 3).coerceIn(1, MAX_SONGS_PER_ALBUM),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
        darkMode = prefs.getInt(KEY_DARK_MODE, 0).coerceIn(0, 2),
        autoDeleteDuplicates = prefs.getBoolean(KEY_AUTO_DELETE_DUPLICATES, false),
        autoDeleteDownloadsDuplicates = prefs.getBoolean(KEY_AUTO_DELETE_DOWNLOADS, false),
        trackSort = prefs.getInt(KEY_TRACK_SORT, 0).coerceIn(0, 11),
        pauseOnOtherAudio = prefs.getBoolean(KEY_PAUSE_ON_OTHER_AUDIO, true),
        pauseOnHeadphonesUnplug = prefs.getBoolean(KEY_PAUSE_ON_HEADPHONES, true),
        fadeOutMs = prefs.getInt(KEY_FADE_OUT_MS, 800).coerceIn(0, 2000),
        funRandomMode = prefs.getBoolean(KEY_FUN_RANDOM_MODE, false),
        wheelVariant = prefs.getInt(KEY_WHEEL_VARIANT, 0).coerceIn(0, 1),
        resumeOnFocusGain = prefs.getBoolean(KEY_RESUME_ON_FOCUS_GAIN, true),
        wheelSpeedPercent = prefs.getInt(KEY_WHEEL_SPEED, 100).coerceIn(50, 300),
        hiddenFolders = prefs.getStringSet(KEY_HIDDEN_FOLDERS, emptySet()).orEmpty(),
        resumeLongTracks = prefs.getBoolean(KEY_RESUME_LONG_TRACKS, true),
        drawerOrder = prefs.getString(KEY_DRAWER_ORDER, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty(),
        drawerEditable = prefs.getBoolean(KEY_DRAWER_EDITABLE, false),
        accentHue = prefs.getInt(KEY_ACCENT_HUE, -1).coerceIn(-1, 359),
        crossfadeMs = prefs.getInt(KEY_CROSSFADE_MS, 1200).coerceIn(0, 6000),
        coverRotation = prefs.getBoolean(KEY_COVER_ROTATION, true),
        backgroundRotation = prefs.getBoolean(KEY_BACKGROUND_ROTATION, false),
        soundProfile = prefs.getInt(KEY_SOUND_PROFILE, 0).coerceIn(0, 4),
        volumeBalance = prefs.getBoolean(KEY_VOLUME_BALANCE, false),
        targetLoudnessDb = prefs.getInt(KEY_TARGET_DB, -18).coerceIn(-30, -6),
        checkUpdates = prefs.getBoolean(KEY_CHECK_UPDATES, true),
        updateRepo = prefs.getString(KEY_UPDATE_REPO, AppSettings().updateRepo)
            .orEmpty().ifBlank { AppSettings().updateRepo }
    )

    companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_AUTO_ALBUMS = "auto_albums"
        const val KEY_MIN_SONGS = "min_songs_for_album"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_AUTO_DELETE_DUPLICATES = "auto_delete_duplicates"
        const val KEY_AUTO_DELETE_DOWNLOADS = "auto_delete_downloads"
        const val KEY_TRACK_SORT = "track_sort"
        const val KEY_PAUSE_ON_OTHER_AUDIO = "pause_on_other_audio"
        const val KEY_PAUSE_ON_HEADPHONES = "pause_on_headphones_unplug"
        const val KEY_FADE_OUT_MS = "fade_out_ms"
        const val KEY_FUN_RANDOM_MODE = "fun_random_mode"
        const val KEY_WHEEL_VARIANT = "wheel_variant"
        const val KEY_RESUME_ON_FOCUS_GAIN = "resume_on_focus_gain"
        const val KEY_WHEEL_SPEED = "wheel_speed_percent"
        const val KEY_HIDDEN_FOLDERS = "hidden_folders"
        const val KEY_RESUME_LONG_TRACKS = "resume_long_tracks"
        const val KEY_DRAWER_ORDER = "drawer_order"
        const val KEY_DRAWER_EDITABLE = "drawer_editable"
        const val KEY_ACCENT_HUE = "accent_hue"
        const val KEY_CROSSFADE_MS = "crossfade_ms"
        const val KEY_COVER_ROTATION = "cover_rotation"
        const val KEY_BACKGROUND_ROTATION = "background_rotation"
        const val KEY_SOUND_PROFILE = "sound_profile"
        const val KEY_VOLUME_BALANCE = "volume_balance"
        const val KEY_TARGET_DB = "target_loudness_db"
        const val KEY_CHECK_UPDATES = "check_updates"
        const val KEY_UPDATE_REPO = "update_repo"
        const val MAX_SONGS_PER_ALBUM = 10
    }
}
