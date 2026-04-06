package hk.uwu.reareye.lyrics

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import java.util.Locale

class LyricParser {

    private companion object {
        private const val MIUI_LRC_LINE_ENDING = "\r\n"
        private const val ARTIST_SEPARATOR = "/"
    }

    enum class DisplayMode(val mask: Int) {
        ORIGINAL(0x01),   // 显示原文
        TRANSLATION(0x02),// 显示翻译
        ROMANIZATION(0x04);// 显示罗马音

        companion object {
            fun shouldShowOriginal(mask: Int): Boolean =
                (mask and ORIGINAL.mask) != 0

            fun shouldShowTranslation(mask: Int): Boolean =
                (mask and TRANSLATION.mask) != 0

            fun shouldShowRomanization(mask: Int): Boolean =
                (mask and ROMANIZATION.mask) != 0
        }
    }

    fun toLrc(
        song: Song?,
        displayMode: Int,
        showArtistBeforeFirstLine: Boolean = false,
    ): String {
        if (song == null) return ""
        val builder = StringBuilder()
        val sortedLyrics = song.lyrics
            ?.sortedBy { it.begin }
            .orEmpty()

        appendLrcTag(builder, "ti", song.name)
        appendLrcTag(builder, "ar", song.artist)
        appendLrcTag(builder, "id", song.id)
        if (song.duration > 0) {
            appendLrcTag(builder, "length", formatLength(song.duration))
        }
        if (builder.isNotEmpty()) builder.append(MIUI_LRC_LINE_ENDING)

        if (showArtistBeforeFirstLine) {
            appendArtistLeadIn(builder, song.artist, sortedLyrics.firstOrNull()?.begin ?: 0L)
        }

        sortedLyrics.forEach { line ->
            val timestamp = formatTimestamp(line.begin)
            line.toLrcTexts(displayMode).forEach { text ->
                builder.append('[')
                    .append(timestamp)
                    .append(']')
                    .append(text)
                    .append(MIUI_LRC_LINE_ENDING)
            }
        }

        return builder.toString().removeSuffix(MIUI_LRC_LINE_ENDING)
    }

    private fun appendArtistLeadIn(
        builder: StringBuilder,
        rawArtist: String?,
        firstLineBegin: Long
    ) {
        if (firstLineBegin <= 0L) return

        val artists = rawArtist
            ?.split(ARTIST_SEPARATOR)
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (artists.isEmpty()) return

        val artistPrefix = if (Locale.getDefault().language == Locale.CHINESE.language) {
            "歌手："
        } else {
            "Artist: "
        }

        artists.forEachIndexed { index, artist ->
            val timestamp = formatTimestamp(firstLineBegin * index / artists.size)
            builder.append('[')
                .append(timestamp)
                .append(']')
                .append(artistPrefix)
                .append(artist)
                .append(MIUI_LRC_LINE_ENDING)
        }
    }

    private fun RichLyricLine.toLrcTexts(displayMode: Int): List<String> {
        val main = resolveText(text, words)
        val secondaryText = resolveText(secondary, secondaryWords)
        val translationText = resolveText(translation, translationWords)
        val romaText = normalizeText(roma)

        val result = mutableListOf<String>()

        if (DisplayMode.shouldShowOriginal(displayMode)) {
            result.add(main)
            result.add(secondaryText)
        }
        if (DisplayMode.shouldShowTranslation(displayMode)) {
            result.add(translationText)
        }
        if (DisplayMode.shouldShowRomanization(displayMode)) {
            result.add(romaText)
        }

        return result.filter { it.isNotBlank() }
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
