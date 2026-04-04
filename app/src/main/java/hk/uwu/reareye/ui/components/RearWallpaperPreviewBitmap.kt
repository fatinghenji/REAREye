package hk.uwu.reareye.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object RearWallpaperPreviewBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }
}

@Composable
fun rememberRearWallpaperPreviewBitmap(cachePath: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = cachePath?.let(RearWallpaperPreviewBitmapCache::get),
        key1 = cachePath,
    ) {
        val path = cachePath?.takeIf { it.isNotBlank() } ?: return@produceState
        if (value != null) return@produceState

        val loadedBitmap = withContext(Dispatchers.IO) {
            loadPreviewBitmap(path)
        }

        if (loadedBitmap != null) {
            RearWallpaperPreviewBitmapCache.put(path, loadedBitmap)
            value = loadedBitmap
        }
    }
    return bitmap
}

private fun loadPreviewBitmap(path: String): ImageBitmap? {
    val file = File(path)
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }.getOrNull()
}
