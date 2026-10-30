package com.kastoriadevs.astikoktel.app.ui.common

import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.Stop
import java.text.Normalizer
import java.util.Locale

private val DIACRITICS = Regex("\\p{Mn}+")
private val STOP_WORDS = setOf("σταση", "στασεισ", "stop", "stops")
private val LINE_WORDS = setOf("γραμμη", "γραμμεσ", "line", "lines", "λεωφορειο", "bus")
private val GREEK_SUFFIXES = listOf(
    "ιου", "εως", "εων", "ουσ", "ου", "ησ", "ασ", "οσ", "ων", "εσ", "ει", "α", "η", "ο", "υ", "σ"
)

/** Strips accents, lower-cases, and unifies Greek final sigma */
fun normalizeSearch(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.forLanguageTag("el")).trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace('ς', 'σ')

/** Transliterates Greek text into a phonetic Latin form to allow Greeklish searching */
fun toPhonetic(s: String): String {
    val norm = normalizeSearch(s)
    var res = norm
        .replace("αι", "e")
        .replace("ει", "i")
        .replace("οι", "i")
        .replace("ου", "u")
        .replace("αυ", "av")
        .replace("ευ", "ev")
        .replace("μπ", "b")
        .replace("ντ", "nt")
        .replace("γκ", "gk")
        .replace("γγ", "ng")
        .replace("τσ", "ts")
        .replace("τζ", "tz")

    val sb = StringBuilder(res.length)
    for (ch in res) {
        val mapped = when (ch) {
            'α' -> "a"
            'β' -> "v"
            'γ' -> "g"
            'δ' -> "d"
            'ε' -> "e"
            'ζ' -> "z"
            'η' -> "i"
            'θ' -> "th"
            'ι' -> "i"
            'κ' -> "k"
            'λ' -> "l"
            'μ' -> "m"
            'ν' -> "n"
            'ξ' -> "x"
            'ο' -> "o"
            'π' -> "p"
            'ρ' -> "r"
            'σ' -> "s"
            'τ' -> "t"
            'υ' -> "i"
            'φ' -> "f"
            'χ' -> "ch"
            'ψ' -> "ps"
            'ω' -> "o"
            else -> ch.toString()
        }
        sb.append(mapped)
    }

    return sb.toString()
        .replace("ou", "u")
        .replace("ei", "i")
        .replace("oi", "i")
        .replace("ai", "e")
        .replace("y", "i")
        .replace("c", "k")
        .replace("w", "o")
        .replace("ph", "f")
}

/** Strips common Greek inflectional noun/adjective suffixes (e.g. genitive -ου, -ης) */
fun greekStem(s: String): String {
    val norm = normalizeSearch(s)
    for (suffix in GREEK_SUFFIXES) {
        if (norm.endsWith(suffix) && norm.length - suffix.length >= 3) {
            return norm.dropLast(suffix.length)
        }
    }
    return norm
}

/** Splits query into non-empty tokens, discarding filler words like "στάση" when multiple words are present */
private fun tokenizeQuery(query: String, ignoreWords: Set<String>): List<String> {
    val raw = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (raw.size > 1) {
        val filtered = raw.filter { normalizeSearch(it) !in ignoreWords }
        if (filtered.isNotEmpty()) return filtered
    }
    return raw
}

/**
 * Searches and ranks stops matching the given query across Greek names, English names,
 * qualifiers (e.g. "αναχωρήσεις", "τέρμα"), full composed titles, and Greeklish phonetic inputs.
 */
fun searchStops(stops: List<Stop>, query: String, language: String = "el"): List<Stop> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val tokens = tokenizeQuery(trimmed, STOP_WORDS)
    if (tokens.isEmpty()) return emptyList()

    val fullQueryNorm = normalizeSearch(trimmed)
    val fullQueryPhonetic = toPhonetic(trimmed)

    data class StopMatch(val stop: Stop, val rank: Int, val name: String)

    val matched = mutableListOf<StopMatch>()

    for (stop in stops) {
        val rawTexts = listOfNotNull(
            stop.name.el,
            stop.name.en,
            stop.qualifier?.el,
            stop.qualifier?.en,
            stop.name.el + (stop.qualifier?.el?.let { " $it" } ?: ""),
            stop.name.en + (stop.qualifier?.en?.let { " $it" } ?: ""),
        )

        val normTexts = rawTexts.map { normalizeSearch(it) }
        val phoneticTexts = rawTexts.map { toPhonetic(it) }
        val stemmedTexts = normTexts.map { greekStem(it) }

        var allTokensMatched = true
        for (token in tokens) {
            val nToken = normalizeSearch(token)
            val pToken = toPhonetic(token)
            val sToken = greekStem(nToken)

            val matchesToken = normTexts.any { it.contains(nToken) } ||
                phoneticTexts.any { it.contains(pToken) } ||
                stemmedTexts.any { it.contains(sToken) } ||
                (sToken.length >= 3 && stemmedTexts.any { it.startsWith(sToken) || sToken.startsWith(it) })

            if (!matchesToken) {
                allTokensMatched = false
                break
            }
        }

        if (allTokensMatched) {
            // Rank priority: exact match (0) > starts with (1) > phonetic starts with (2) > contains (3)
            val rank = when {
                normTexts.any { it == fullQueryNorm } -> 0
                normTexts.any { it.startsWith(fullQueryNorm) } -> 1
                phoneticTexts.any { it.startsWith(fullQueryPhonetic) } -> 2
                else -> 3
            }
            matched.add(StopMatch(stop, rank, stop.name.get(language)))
        }
    }

    return matched.sortedWith(compareBy({ it.rank }, { it.name })).map { it.stop }
}

/**
 * Searches and ranks bus lines matching a query by line number (e.g. "4", "04", "γραμμή 4")
 * or line destination / route name in Greek or English.
 */
fun searchLines(lines: List<Line>, query: String, language: String = "el"): List<Line> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val tokens = tokenizeQuery(trimmed, LINE_WORDS)
    if (tokens.isEmpty()) return emptyList()

    val cleanQuery = tokens.joinToString(" ")
    val fullNorm = normalizeSearch(cleanQuery)
    val fullPhonetic = toPhonetic(cleanQuery)

    data class LineMatch(val line: Line, val rank: Int, val idNum: Int)

    val matched = mutableListOf<LineMatch>()

    for (line in lines) {
        val rawNum = line.id.trimStart('0')
        val lineId = line.id
        val idNum = line.id.toIntOrNull() ?: 999

        // Exact line number match e.g. "4" or "04"
        val isNumberMatch = fullNorm == rawNum || fullNorm == lineId ||
            tokens.any { normalizeSearch(it) == rawNum || normalizeSearch(it) == lineId }

        if (isNumberMatch) {
            matched.add(LineMatch(line, 0, idNum))
            continue
        }

        val names = listOf(
            normalizeSearch(line.name.el),
            normalizeSearch(line.name.en),
            toPhonetic(line.name.el),
            toPhonetic(line.name.en),
        )

        val matchesName = tokens.all { token ->
            val nToken = normalizeSearch(token)
            val pToken = toPhonetic(token)
            names.any { it.contains(nToken) || it.contains(pToken) }
        }

        if (matchesName) {
            val rank = when {
                names.any { it.startsWith(fullNorm) || it.startsWith(fullPhonetic) } -> 1
                else -> 2
            }
            matched.add(LineMatch(line, rank, idNum))
        }
    }

    return matched.sortedWith(compareBy({ it.rank }, { it.idNum })).map { it.line }
}
