package hk.uwu.reareye.widgetapi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RearWidgetApiClient(
    private val hookHostPackage: String = RearWidgetApiContract.HOOK_HOST_PACKAGE,
) {
    @Volatile
    private var remote: IRearWidgetApiService? = null

    fun bind(
        context: Context,
        onConnected: (() -> Unit)? = null
    ): Boolean {
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
        val callback = object : IRearWidgetApiConnection.Stub() {
            override fun onServiceConnected(service: IRearWidgetApiService?) {
                remote = service
                latch.countDown()
            }
        }

        requestHookServiceBootstrap(context = context, callback = callback, forceSync = forceSync)
        return runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
            .getOrDefault(false) && remote != null
    }

    fun unbind() {
        remote = null
    }

    fun isConnected(): Boolean = remote != null

    fun registerBusinessFile(business: String, filePath: String) {
        requireRemote().registerBusinessFile(business, filePath)
    }

    fun unregisterBusinessFile(business: String) {
        requireRemote().unregisterBusinessFile(business)
    }

    fun registerBusiness(
        targetPackage: String,
        business: String,
        filePath: String,
        defaultIndex: Int = 0,
        defaultPriority: Int = 500,
    ) {
        requireRemote().registerBusiness(
            targetPackage,
            business,
            filePath,
            defaultIndex,
            defaultPriority,
        )
    }

    fun registerBusinessWithoutFile(
        targetPackage: String,
        business: String,
        defaultIndex: Int = 0,
        defaultPriority: Int = 500,
    ) {
        requireRemote().registerBusinessWithoutFile(
            targetPackage,
            business,
            defaultIndex,
            defaultPriority,
        )
    }

    fun unregisterBusiness(targetPackage: String, business: String) {
        requireRemote().unregisterBusiness(targetPackage, business)
    }

    fun registerSceneRoute(
        targetPackage: String,
        scene: String,
        business: String,
    ) {
        requireRemote().registerSceneRoute(targetPackage, scene, business)
    }

    fun unregisterSceneRoute(targetPackage: String, scene: String) {
        requireRemote().unregisterSceneRoute(targetPackage, scene)
    }

    fun disableBusinessDisplay(targetPackage: String, business: String) {
        requireRemote().disableBusinessDisplay(targetPackage, business)
    }

    fun postNotice(
        targetPackage: String,
        business: String,
        payload: Bundle = Bundle(),
        options: RearWidgetNoticeOptions = RearWidgetNoticeOptions(),
    ) {
        requireRemote().postNotice(targetPackage, business, payload, options.toBundle())
    }

    fun updateNotice(
        ticket: RearWidgetNoticeTicket,
        payload: Bundle? = null,
        options: RearWidgetNoticeOptions? = null,
    ) {
        requireRemote().updateNotice(
            ticket.toBundle(),
            payload ?: Bundle(),
            options?.toBundle() ?: Bundle(),
            payload != null,
            options != null,
        )
    }

    fun removeNotice(ticket: RearWidgetNoticeTicket) {
        requireRemote().removeNotice(ticket.toBundle())
    }

    fun syncState() {
        requireRemote().syncState()
    }

    fun resolveTemplateImagePreview(
        business: String,
        sourceFilePath: String,
        imageValue: String,
    ): RearWidgetTemplateImagePreview? {
        return RearWidgetTemplateImagePreview.fromBundle(
            requireRemote().resolveTemplateImagePreview(business, sourceFilePath, imageValue)
        )
    }

    fun resolveTemplateConfigState(
        business: String,
        sourceFilePath: String,
        currentOneConfigJson: String?,
    ): RearWidgetTemplateConfigState? {
        return RearWidgetTemplateConfigState.fromBundle(
            requireRemote().resolveTemplateConfigState(
                business,
                sourceFilePath,
                currentOneConfigJson.orEmpty(),
            )
        )
    }

    fun importCardCustomImage(
        cardKey: String,
        fieldName: String,
        sourceUri: String,
        displayNameHint: String,
    ): String? {
        return requireRemote()
            .importCardCustomImage(cardKey, fieldName, sourceUri, displayNameHint)
            ?.trim()
            ?.ifBlank { null }
    }

    private fun requireRemote(): IRearWidgetApiService {
        return remote ?: error("RearWidget API service is not connected")
    }

    private fun requestHookServiceBootstrap(
        context: Context,
        callback: IRearWidgetApiConnection,
        forceSync: Boolean,
    ) {
        runCatching {
            val bundle = Bundle().apply {
                putBinder(RearWidgetApiContract.Extras.BINDER, callback.asBinder())
            }
            val intent = Intent(RearWidgetApiContract.ACTION_REQUEST_HOOK_SERVICE)
                .setPackage(hookHostPackage)
                .putExtra(RearWidgetApiContract.Extras.BUNDLE, bundle)
                .putExtra(RearWidgetApiContract.Extras.FORCE_SYNC, forceSync)
            context.sendBroadcast(intent)
        }
    }
}
