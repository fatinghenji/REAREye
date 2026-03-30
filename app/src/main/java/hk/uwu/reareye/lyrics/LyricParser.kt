package hk.uwu.reareye.lyrics

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import java.util.Locale

class LyricParser {

    private companion object {
        private const val MIUI_LRC_LINE_ENDING = "\r\n"
    }

    fun toLrc(song: Song?): String {
        if (song == null) return ""
        val builder = StringBuilder()

        appendLrcTag(builder, "ti", song.name)
        appendLrcTag(builder, "ar", song.artist)
        appendLrcTag(builder, "id", song.id)
        if (song.duration > 0) {
            appendLrcTag(builder, "length", formatLength(song.duration))
        }
        if (builder.isNotEmpty()) builder.append(MIUI_LRC_LINE_ENDING)

        song.lyrics
            ?.sortedBy { it.begin }
            ?.forEach { line ->
                val timestamp = formatTimestamp(line.begin)
                line.toLrcTexts().forEach { text ->
                    builder.append('[')
                        .append(timestamp)
                        .append(']')
                        .append(text)
                        .append(MIUI_LRC_LINE_ENDING)
                }
            }

        return builder.toString().removeSuffix(MIUI_LRC_LINE_ENDING)
    }

    private fun RichLyricLine.toLrcTexts(): List<String> {
        val main = resolveText(text, words)
        val secondaryText = resolveText(secondary, secondaryWords)
        val translationText = resolveText(translation, translationWords)
        val romaText = normalizeText(roma)

        return linkedSetOf(main, secondaryText, translationText, romaText)
            .filter { it.isNotBlank() }
    }

    private fun resolveText(rawText: String?, words: List<LyricWord>?): String {
        val directText = normalizeText(rawText)
        if (directText.isNotEmpty()) return directText

        val wordsText = words
            ?.joinToString(separator = "") { it.text.orEmpty() }
            .orEmpty()
        return normalizeText(wordsText)
    }

    private fun normalizeText(text: String?): String =
        text
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.trim()
            .orEmpty()

    private fun appendLrcTag(builder: StringBuilder, key: String, value: String?) {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return
        builder.append('[')
            .append(key)
            .append(':')
            .append(normalized)
            .append(']')
            .append(MIUI_LRC_LINE_ENDING)
    }

    private fun formatLength(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    private fun formatTimestamp(timeMs: Long): String {
        val ms = timeMs.coerceAtLeast(0)
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1_000
        val centiseconds = (ms % 1_000) / 10
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centiseconds)
    }
}
