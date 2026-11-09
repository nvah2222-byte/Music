package com.example.music.data

/** Параметры песни, по которым ищутся дубли. Отдельный тип — чтобы правила можно было тестировать без Android. */
internal data class SongKey(val title: String, val artist: String, val durationMs: Long)

/** «Туман» и «туман !!!» считаем одним названием. */
internal fun normalizeTitle(title: String): String =
    title.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

internal fun songKey(title: String, artist: String, durationMs: Long): SongKey =
    SongKey(normalizeTitle(title), artist.trim().lowercase(), durationMs)

/**
 * Группы индексов дублей: одно название и исполнитель, длительность в пределах 5 секунд
 * от первой копии группы. Исполнитель обязателен: «Intro» у разных артистов — не дубль.
 */
internal fun duplicateGroups(keys: List<SongKey>): List<List<Int>> {
    val byTitleAndArtist = LinkedHashMap<Pair<String, String>, MutableList<Int>>()
    keys.forEachIndexed { index, key ->
        byTitleAndArtist.getOrPut(key.title to key.artist) { mutableListOf() }.add(index)
    }
    return byTitleAndArtist.values.flatMap { indices ->
        val sorted = indices.sortedBy { keys[it].durationMs }
        val groups = mutableListOf<MutableList<Int>>()
        var anchorMs = 0L
        for (index in sorted) {
            val duration = keys[index].durationMs
            val current = groups.lastOrNull()
            if (current == null || duration - anchorMs > DURATION_TOLERANCE_MS) {
                groups += mutableListOf(index)
                anchorMs = duration
            } else {
                current += index
            }
        }
        groups.filter { it.size >= 2 }
    }
}

internal const val DURATION_TOLERANCE_MS = 5000L
