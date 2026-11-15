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
    /** «Буерак - Неважно (hitmo.net).mp3» → название «Неважно (hitmo.net)» и артист «Буерак». */
    fun splitFileName(fileName: String): Pair<String, String?> {
        val base = fileName.substringBeforeLast('.').trim()
        val parts = base.split(" - ", limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[1].trim() to parts[0].trim()
        } else {
            base to null
        }
    }
    /**
     * Тег разрушен: больше половины символов — «?» (байты потеряны при записи).
     * «???» и «???????Ğ®?????» считаем битыми, «Что? Где? Когда?» — нет.
     */
    fun looksBroken(text: String): Boolean {
        if (text.isBlank()) return true
        val questions = text.count { it == '?' }
        return questions >= 3 && questions * 2 >= text.length
    }
    private val domainRegex = Regex("""\.[a-zA-Z]{2,6}($|[/\s])""")
    private val bracketLink = Regex(
        """\[[^\]]*(\.[a-zA-Z]{2,6}|www\.|@|http)[^\]]*]""",
        RegexOption.IGNORE_CASE
    )
    private val parenLink = Regex(
        """\([^)]*(\.[a-zA-Z]{2,6}|www\.|@|http)[^)]*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Убирает из названия ссылки на сайты-источники, но не трогает обычные скобки:
     * «Неважно (hitmo.net)» → «Неважно», «Song (feat. X)» → без изменений.
     */
    fun stripLinks(text: String): String {
        val withoutBrackets = text
            .replace(bracketLink, " ")
            .replace(parenLink, " ")
        val words = withoutBrackets
            .split(' ')
            .filter { word ->
                word.isNotBlank() &&
                    !word.startsWith("http", ignoreCase = true) &&
                    !word.contains("www.") &&
                    !domainRegex.containsMatchIn(word)
            }
        val cleaned = words.joinToString(" ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim('-', '—', '–', '|')
            .trim()
        return cleaned.ifBlank { text }
    }
}