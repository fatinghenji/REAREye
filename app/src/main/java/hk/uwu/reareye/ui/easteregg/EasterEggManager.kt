package hk.uwu.reareye.ui.easteregg

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar

enum class EasterEggType {
    NONE,
    NEW_YEAR,
    APRIL_FOOLS,
    EASTER
}

enum class EasterEggDisableScope {
    YEAR,
    DATE,
}

sealed interface EasterEggTrigger {
    data class Annual(val month: Int, val day: Int) : EasterEggTrigger
    data class ExactDate(val year: Int, val month: Int, val day: Int) : EasterEggTrigger
}

data class EasterEggDefinition(
    val type: EasterEggType,
    val trigger: EasterEggTrigger,
)

data class EasterEggToggleResult(
    val matchedToday: Boolean,
    val type: EasterEggType,
    val isEnabled: Boolean,
    val disableScope: EasterEggDisableScope,
)

object EasterEggManager {
    private const val PREF_NAME = "reareye_easter_eggs"
    private const val PREF_DISABLED_SET = "disabled_records"

    private val lock = Any()
    private var disabledLoaded = false
    private val disabledRecords = linkedSetOf<String>()

    private var cachedDateKey = -1
    private var cachedType = EasterEggType.NONE

    private val easterEggs = listOf(
        EasterEggDefinition(EasterEggType.NEW_YEAR, EasterEggTrigger.Annual(month = 1, day = 1)),
        EasterEggDefinition(EasterEggType.APRIL_FOOLS, EasterEggTrigger.Annual(month = 4, day = 1)),
        EasterEggDefinition(
            EasterEggType.EASTER,
            EasterEggTrigger.ExactDate(year = 2026, month = 4, day = 5)
        )
    )

    fun getCurrentEasterEggType(context: Context): EasterEggType {
        synchronized(lock) {
            val today = DateSnapshot.now()
            ensureDisabledRecordsLoaded(context.applicationContext)
            if (cachedDateKey == today.key) return cachedType

            val matched = findTodayEasterEgg(today)
            cachedType = matched
                ?.takeUnless { isDisabled(it, today) }
                ?.type
                ?: EasterEggType.NONE
            cachedDateKey = today.key
            return cachedType
        }
    }

    fun toggleTodayEasterEggEnabled(context: Context): EasterEggToggleResult {
        synchronized(lock) {
            val appContext = context.applicationContext
            ensureDisabledRecordsLoaded(appContext)
            val today = DateSnapshot.now()
            val matched = findTodayEasterEgg(today) ?: return EasterEggToggleResult(
                matchedToday = false,
                type = EasterEggType.NONE,
                isEnabled = false,
                disableScope = EasterEggDisableScope.DATE,
            )

            val disableKey = buildDisableKey(matched, today)
            val isEnabled = if (disabledRecords.contains(disableKey)) {
                disabledRecords.remove(disableKey)
                true
            } else {
                disabledRecords.add(disableKey)
                false
            }

            appContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit {
                    putStringSet(PREF_DISABLED_SET, disabledRecords.toSet())
                }

            cachedDateKey = -1
            cachedType = EasterEggType.NONE

            return EasterEggToggleResult(
                matchedToday = true,
                type = matched.type,
                isEnabled = isEnabled,
                disableScope = when (matched.trigger) {
                    is EasterEggTrigger.Annual -> EasterEggDisableScope.YEAR
                    is EasterEggTrigger.ExactDate -> EasterEggDisableScope.DATE
                },
            )
        }
    }

    private fun ensureDisabledRecordsLoaded(context: Context) {
        if (disabledLoaded) return
        val storedSet = context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREF_DISABLED_SET, emptySet())
            .orEmpty()
        disabledRecords.clear()
        disabledRecords.addAll(storedSet)
        disabledLoaded = true
    }

    private fun findTodayEasterEgg(today: DateSnapshot): EasterEggDefinition? {
        return easterEggs.firstOrNull { definition ->
            when (val trigger = definition.trigger) {
                is EasterEggTrigger.Annual -> {
                    trigger.month == today.month && trigger.day == today.day
                }

                is EasterEggTrigger.ExactDate -> {
                    trigger.year == today.year &&
                            trigger.month == today.month &&
                            trigger.day == today.day
                }
            }
        }
    }

    private fun isDisabled(definition: EasterEggDefinition, today: DateSnapshot): Boolean {
        return disabledRecords.contains(buildDisableKey(definition, today))
    }

    private fun buildDisableKey(definition: EasterEggDefinition, today: DateSnapshot): String {
        return when (val trigger = definition.trigger) {
            is EasterEggTrigger.Annual -> {
                "annual:${definition.type.name}:${today.year}:${trigger.month}:${trigger.day}"
            }

            is EasterEggTrigger.ExactDate -> {
                "date:${definition.type.name}:${trigger.year}:${trigger.month}:${trigger.day}"
            }
        }
    }

    private data class DateSnapshot(
        val year: Int,
        val month: Int,
        val day: Int,
    ) {
        val key: Int = year * 10_000 + month * 100 + day

        companion object {
            fun now(): DateSnapshot {
                val calendar = Calendar.getInstance()
                return DateSnapshot(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1,
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                )
            }
        }
    }
}
