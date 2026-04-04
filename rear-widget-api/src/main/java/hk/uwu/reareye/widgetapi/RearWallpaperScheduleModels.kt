package hk.uwu.reareye.widgetapi

import org.json.JSONArray
import org.json.JSONObject

data class RearWallpaperScheduleEntry(
    val wallpaperId: Int,
    val delayMs: Long,
)

object RearWallpaperScheduleCodec {
    const val EMPTY_ARRAY = "[]"
    const val DEFAULT_DELAY_MS = 60_000L
    const val MIN_DELAY_MS = 100L

    fun parse(raw: String): List<RearWallpaperScheduleEntry> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val wallpaperId = obj.optInt("wallpaperId", Int.MIN_VALUE)
                    if (wallpaperId == Int.MIN_VALUE) continue
                    add(
                        RearWallpaperScheduleEntry(
                            wallpaperId = wallpaperId,
                            delayMs = obj.optLong("delayMs", DEFAULT_DELAY_MS)
                                .coerceAtLeast(MIN_DELAY_MS),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encode(entries: List<RearWallpaperScheduleEntry>): String {
        return JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("wallpaperId", entry.wallpaperId)
                        .put("delayMs", entry.delayMs.coerceAtLeast(MIN_DELAY_MS))
                )
            }
        }.toString()
    }
}
