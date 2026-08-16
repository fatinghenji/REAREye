package hk.uwu.reareye.repository.rearwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RearWidgetManagerRepositoryTest {

    private fun card(
        id: String,
        pkg: String = "com.xiaomi.subscreencenter",
        business: String = "music",
        enabled: Boolean = true,
    ) = RearCardConfig(
        id = id,
        title = id,
        packageName = pkg,
        business = business,
        enabled = enabled,
    )

    @Test
    fun computeDisabledCardsIncludesCardDisabledWhileSameBusinessStillEnabled() {
        val oldCards = listOf(
            card("card-a", enabled = true),
            card("card-b", enabled = true),
        )
        val newCards = listOf(
            card("card-a", enabled = false),
            card("card-b", enabled = true),
        )

        val disabled = RearWidgetManagerRepository.computeDisabledCards(oldCards, newCards)
        assertEquals(listOf("card-a"), disabled.map { it.id })
    }

    @Test
    fun computeDisabledCardsIgnoresCardsUnchanged() {
        val oldCards = listOf(
            card("card-a", enabled = true),
            card("card-b", enabled = false),
            card("card-c", enabled = true),
        )
        val newCards = listOf(
            card("card-a", enabled = true),
            card("card-b", enabled = false),
            card("card-c", enabled = true),
        )
        val disabled = RearWidgetManagerRepository.computeDisabledCards(oldCards, newCards)
        assertTrue(disabled.isEmpty())
    }

    @Test
    fun computeDisabledCardsHandlesRemovedCard() {
        val oldCards = listOf(
            card("card-a", enabled = true),
            card("card-b", enabled = true),
        )
        val newCards = listOf(card("card-b", enabled = true))
        val disabled = RearWidgetManagerRepository.computeDisabledCards(oldCards, newCards)
        assertEquals(listOf("card-a"), disabled.map { it.id })
    }

    @Test
    fun computeDisabledCardsKeepsEnabledCardAcrossBusinessPair() {
        val oldCards = listOf(
            card("card-a", business = "music", enabled = true),
            card("card-b", business = "music", enabled = true),
            card("card-c", business = "alarm", enabled = true),
        )
        val newCards = listOf(
            card("card-a", business = "music", enabled = false),
            card("card-b", business = "music", enabled = true),
            card("card-c", business = "alarm", enabled = true),
        )
        val disabled = RearWidgetManagerRepository.computeDisabledCards(oldCards, newCards)
        assertEquals(listOf("card-a"), disabled.map { it.id })
    }
}
