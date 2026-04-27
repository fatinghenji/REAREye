package hk.uwu.reareye.widgetapi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class RearWallpaperApiClient(
    private val hookHostPackage: String = RearWallpaperApiContract.HOOK_HOST_PACKAGE,
) {
    @Volatile
    private var remote: IRearWallpaperApiService? = null

    open fun bind(context: Context, onConnected: (() -> Unit)? = null): Boolean {
        remote?.let {
            onConnected?.invoke()
            return true
        }

        val appContext = context.applicationContext
        if (bindOnce(appContext, forceSync = false, timeoutMs = 1500L) ||
            bindOnce(appContext, forceSync = true, timeoutMs = 2500L)
        ) {
            onConnected?.invoke()
            return true
        }
        return false
    }

    private fun bindOnce(context: Context, forceSync: Boolean, timeoutMs: Long): Boolean {
        remote?.let { return true }

        val latch = CountDownLatch(1)
        val callback = object : IRearWallpaperApiConnection.Stub() {
            override fun onServiceConnected(service: IRearWallpaperApiService?) {
                remote = service
                latch.countDown()
            }
        }

        requestHookServiceBootstrap(context, callback, forceSync)
        return runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
            .getOrDefault(false) && remote != null
    }

    open fun unbind() {
        remote = null
    }

    open fun isConnected(): Boolean = remote != null

    open fun getCatalog(): Bundle = requireRemote().getCatalog() ?: Bundle()

    open fun getPreview(wallpaperId: Int): ByteArray? = requireRemote().getPreview(wallpaperId)

    open fun switchWallpaper(wallpaperId: Int): Boolean =
        requireRemote().switchWallpaper(wallpaperId)

    open fun syncSchedule(enabled: Boolean, scheduleData: String): Boolean {
        return requireRemote().syncSchedule(enabled, scheduleData)
    }

    open fun importWallpaperPackage(
        packageFd: ParcelFileDescriptor,
        displayNameHint: String,
        previewUri: String?,
        options: Bundle,
    ): Bundle {
        return requireRemote().importWallpaperPackage(
            packageFd,
            displayNameHint,
            previewUri,
            options,
        ) ?: operationFailureBundle("hook service returned empty import result")
    }

    open fun updateWallpaperMetadata(
        wallpaperId: Int,
        previewUri: String?,
        options: Bundle,
    ): Bundle {
        return requireRemote().updateWallpaperMetadata(wallpaperId, previewUri, options)
            ?: operationFailureBundle("hook service returned empty metadata update result")
    }

    open fun generateWallpaperPreview(wallpaperId: Int): Bundle {
        return requireRemote().generateWallpaperPreview(wallpaperId)
            ?: operationFailureBundle("hook service returned empty preview generation result")
    }

    open fun deleteWallpaper(wallpaperId: Int): Bundle {
        return requireRemote().deleteWallpaper(wallpaperId)
            ?: operationFailureBundle("hook service returned empty delete result")
    }

    open fun resolveTemplateConfigState(
        wallpaperId: Int,
        currentOneConfigJson: String?,
    ): RearWidgetTemplateConfigState? {
        return RearWidgetTemplateConfigState.fromBundle(
            requireRemote().resolveTemplateConfigState(
                wallpaperId,
                currentOneConfigJson.orEmpty(),
            )
        )
    }

    open fun saveTemplateConfig(wallpaperId: Int, oneConfigJson: String?): Bundle {
        return requireRemote().saveTemplateConfig(wallpaperId, oneConfigJson.orEmpty())
            ?: operationFailureBundle("hook service returned empty template config save result")
    }

    private fun requireRemote(): IRearWallpaperApiService {
        return remote ?: error("RearWallpaper API service is not connected")
    }

    private fun operationFailureBundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean(RearWallpaperApiContract.BundleKeys.SUCCESS, false)
            putString(RearWallpaperApiContract.BundleKeys.ERROR, message)
        }
    }

    private fun requestHookServiceBootstrap(
        context: Context,
        callback: IRearWallpaperApiConnection,
        forceSync: Boolean,
    ) {
        runCatching {
            val bundle = Bundle().apply {
                putBinder(RearWallpaperApiContract.Extras.BINDER, callback.asBinder())
            }
            val intent = Intent(RearWallpaperApiContract.ACTION_REQUEST_HOOK_SERVICE)
                .setPackage(hookHostPackage)
                .putExtra(RearWallpaperApiContract.Extras.BUNDLE, bundle)
                .putExtra(RearWallpaperApiContract.Extras.FORCE_SYNC, forceSync)
            context.sendBroadcast(intent)
        }
    }
}
