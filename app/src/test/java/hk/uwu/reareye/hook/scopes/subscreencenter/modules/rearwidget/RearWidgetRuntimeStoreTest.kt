package hk.uwu.reareye.hook.scopes.subscreencenter.modules.rearwidget

import android.os.Bundle
import hk.uwu.reareye.widgetapi.RearWidgetNoticeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RearWidgetRuntimeStoreTest {

    private val pkg = "com.xiaomi.subscreencenter"
    private val business = "music"

    @Before
    fun setUp() {
        RearWidgetRuntimeStore.resetForTest()
        RearWidgetRuntimeStore.install(pkg)
        RearWidgetRuntimeStore.registerBusiness(pkg, business, "/tmp/music.zip", 0, 500)
    }

    private fun postCard(cardId: String) = RearWidgetRuntimeStore.postNotice(
        business = business,
        payload = Bundle().apply { putString("__rear_card_id__", cardId) },
        options = RearWidgetNoticeOptions(sticky = true),
        packageName = pkg,
    )

    @Test
    fun disableCardDisplayOnlyRemovesTargetCard() {
        val ticketA = postCard("card-a")
        val ticketB = postCard("card-b")
        assertEquals(2, RearWidgetRuntimeStore.listNotices().size)

        val removed = RearWidgetRuntimeStore.disableCardDisplay(pkg, business, "card-a")
        assertNotNull(removed)
        assertEquals(ticketA.compositeKey, removed!!.compositeKey)

        val remain = RearWidgetRuntimeStore.listNotices()
        assertEquals(1, remain.size)
        assertEquals(ticketB.compositeKey, remain.single().ticket.compositeKey)
        assertNull(RearWidgetRuntimeStore.getNotice(ticketA.compositeKey))
        assertNotNull(RearWidgetRuntimeStore.getNotice(ticketB.compositeKey))
    }

    @Test
    fun disableCardDisplayReturnsNullForUnknownCard() {
        postCard("card-a")
        assertNull(RearWidgetRuntimeStore.disableCardDisplay(pkg, business, "unknown-card"))
        assertEquals(1, RearWidgetRuntimeStore.listNotices().size)
    }

    @Test
    fun disableCardDisplayIsIdempotent() {
        postCard("card-a")
        postCard("card-b")

        val first = RearWidgetRuntimeStore.disableCardDisplay(pkg, business, "card-a")
        assertNotNull(first)
        val second = RearWidgetRuntimeStore.disableCardDisplay(pkg, business, "card-a")
        assertNull(second)
        assertEquals(1, RearWidgetRuntimeStore.listNotices().size)
    }

    @Test
    fun repostingSameCardReusesStableNotificationIdAfterDisable() {
        val ticketA = postCard("card-a")
        postCard("card-b")

        RearWidgetRuntimeStore.disableCardDisplay(pkg, business, "card-a")

        // cardNoticeIdIndex 刻意保留：同一 cardId 重新启用时复用相同 notificationId，
        // compositeKey 保持稳定，避免 SubScreenCenter 恢复与重放之间出现重复。
        val reposted = postCard("card-a")
        assertEquals(ticketA.notificationId, reposted.notificationId)
        assertEquals(ticketA.compositeKey, reposted.compositeKey)
        assertEquals(2, RearWidgetRuntimeStore.listNotices().size)
    }

    @Test
    fun disableBusinessDisplayStillClearsWholeBusiness() {
        postCard("card-a")
        postCard("card-b")
        val removed = RearWidgetRuntimeStore.disableBusinessDisplay(pkg, business)
        assertEquals(2, removed)
        assertTrue(RearWidgetRuntimeStore.listNotices().isEmpty())
    }
}
