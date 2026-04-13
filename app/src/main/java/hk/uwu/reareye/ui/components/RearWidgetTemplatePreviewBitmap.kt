package hk.uwu.reareye.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import hk.uwu.reareye.repository.rearwidget.RearWidgetManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object RearWidgetTemplatePreviewBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

@Composable
fun rememberRearWidgetTemplatePreviewBitmap(
    business: String?,
    templateSourcePath: String?,
    imageValue: String?,
): ImageBitmap? {
    val context = LocalContext.current
    val key = listOf(business.orEmpty(), templateSourcePath.orEmpty(), imageValue.orEmpty())
        .joinToString("|")
        .takeIf { it.isNotBlank() }

    val bitmap by produceState(
        initialValue = key?.let(RearWidgetTemplatePreviewBitmapCache::get),
        key1 = key,
        key2 = context.applicationContext,
    ) {
        val cacheKey = key ?: return@produceState
        if (value != null) return@produceState

        val loadedBitmap = withContext(Dispatchers.IO) {
            loadTemplatePreviewBitmap(
                context = context.applicationContext,
                business = business.orEmpty(),
                templateSourcePath = templateSourcePath.orEmpty(),
                imageValue = imageValue.orEmpty(),
            )
        }

        if (loadedBitmap != null) {
            RearWidgetTemplatePreviewBitmapCache.put(cacheKey, loadedBitmap)
            value = loadedBitmap
        }
    }

    return bitmap
}

private fun loadTemplatePreviewBitmap(
    context: android.content.Context,
    business: String,
    templateSourcePath: String,
    imageValue: String,
): ImageBitmap? {
    val preview = RearWidgetManagerRepository.resolveTemplateImagePreview(
        context = context,
        business = business,
        sourceFilePath = templateSourcePath,
        imageValue = imageValue,
    ) ?: return null
    val bytes = runCatching {
        Base64.decode(preview.previewBase64, Base64.DEFAULT)
    }.getOrNull() ?: return null
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
