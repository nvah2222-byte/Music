package com.example.music.data

import java.nio.charset.Charset

/**
 * Теги в файлах часто записаны в cp1251, а читаются как UTF-8/GBK — тогда вместо
 * «Пасха» видно «聚袇敲敷獗» или «ÐŸÐ°Ñ�Ñ…Ð°». Пробуем перекодировать и берём вариант,
 * который выглядит как нормальный текст.
 */
object TagText {

    private val sources = listOf("GBK", "Big5", "windows-1252", "ISO-8859-1")
    private val targets = listOf("windows-1251", "UTF-8")

    fun fix(raw: String): String {
        if (raw.isBlank() || raw.none { it.code > 127 }) return raw
        val baseScore = score(raw)
        if (baseScore >= 0 && !looksLikeUtf8Mojibake(raw)) return raw
        var best = raw
        var bestScore = baseScore
        for (source in sources) {
            for (target in targets) {
                val candidate = runCatching {
                    String(raw.toByteArray(Charset.forName(source)), Charset.forName(target))
                }.getOrNull() ?: continue
                val candidateScore = score(candidate)
                if (candidateScore > bestScore) {
                    bestScore = candidateScore
                    best = candidate
                }
                if (bestScore >= 0 && !looksLikeUtf8Mojibake(best)) return best
            }
        }
        return best
    }

    /** «ÐŸÐ°Ñ…» — типичный признак UTF-8, прочитанного как однобайтовая кодировка. */
    private fun looksLikeUtf8Mojibake(text: String): Boolean {
        var hits = 0
        for (index in 0 until text.length - 1) {
            val pair = "${text[index]}${text[index + 1]}"
            if (pair.startsWith("Ð") || pair.startsWith("Ñ") || pair.startsWith("Ã") ||
                pair.startsWith("â€")
            ) {
                hits++
            }
        }
        return hits >= 2
    }

    /** Больше нуля — текст похож на нормальный, меньше — на мусор. */
    private fun score(text: String): Int {
        var good = 0
        var bad = 0
        for (character in text) {
            when {
                character.code >= 0x2E80 -> bad += 4
                character.isLetterOrDigit() -> good++
                character in " .,!?()[]{}<>-–—_&+'\"·:;…/\\|~*#@" -> good++
                character.code in 0x80..0x24F -> good++
                else -> bad++
            }
        }
        return good - bad
    }
}
