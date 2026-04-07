package hk.uwu.reareye.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object PackageIconBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

@Composable
fun rememberApplicationIconBitmap(
    packageManager: PackageManager,
    applicationInfo: ApplicationInfo,
): ImageBitmap? {
    val packageName = applicationInfo.packageName
    val bitmap by produceState(
        initialValue = PackageIconBitmapCache.get(packageName),
        key1 = packageName,
        key2 = packageManager,
    ) {
        if (value != null) return@produceState

        val loadedBitmap = withContext(Dispatchers.IO) {
            loadIconBitmap {
                applicationInfo.loadIcon(packageManager)
            }
        }

        if (loadedBitmap != null) {
            PackageIconBitmapCache.put(packageName, loadedBitmap)
            value = loadedBitmap
        }
    }

    return bitmap
}

@Composable
fun rememberPackageIconBitmap(
    packageManager: PackageManager,
    packageName: String,
): ImageBitmap? {
    val bitmap by produceState(
        initialValue = PackageIconBitmapCache.get(packageName),
        key1 = packageName,
        key2 = packageManager,
    ) {
        if (value != null) return@produceState

        val loadedBitmap = withContext(Dispatchers.IO) {
            loadIconBitmap {
                packageManager.getApplicationIcon(packageName)
            }
        }

        if (loadedBitmap != null) {
            PackageIconBitmapCache.put(packageName, loadedBitmap)
            value = loadedBitmap
        }
    }

    return bitmap
}

private fun loadIconBitmap(
    drawableProvider: () -> android.graphics.drawable.Drawable,
): ImageBitmap? {
    return runCatching {
        val drawable = drawableProvider()
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
            val createdBitmap = createBitmap(width, height)
            val canvas = Canvas(createdBitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            createdBitmap
        }
        bitmap.asImageBitmap()
    }.getOrNull()
}
