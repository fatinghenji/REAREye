package hk.uwu.reareye.repository.rearwidget

import hk.uwu.reareye.hook.core.HookPrefs
import hk.uwu.reareye.hook.core.HookPrefsEditor
import hk.uwu.reareye.hook.core.LEGACY_PREFS_MIGRATION_COMPLETED_V3
import hk.uwu.reareye.hook.core.LEGACY_PREFS_MIGRATION_COMPLETED_V4
import hk.uwu.reareye.hook.core.LEGACY_PREFS_MIGRATION_STATUS_KEY
import hk.uwu.reareye.ui.config.LegacyPreferenceMigrationOutcome
import hk.uwu.reareye.ui.config.LegacyPreferenceMigrator
import hk.uwu.reareye.ui.config.LegacyPreferencePath
import hk.uwu.reareye.ui.config.LegacyPreferenceSnapshot
import hk.uwu.reareye.ui.config.PrefsManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RearWidgetRemoteBlobMigrationTest {
    @Test
    fun markerIsStableSafeAndSmall() {
        val firstName = RearWidgetConfigCodec.businessBlobRemoteFileName("taxi")
        val secondName = RearWidgetConfigCodec.businessBlobRemoteFileName("taxi")
        val marker = RearWidgetConfigCodec.remoteBlobMarker(firstName)

        assertEquals(firstName, secondName)
        assertTrue(firstName.length < 128)
        assertTrue(firstName.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        assertEquals(firstName, RearWidgetConfigCodec.remoteBlobFileNameFromMarker(marker))
        assertTrue(marker.length < 256)
    }

    @Test
    fun v3MigrationWritesBlobFileMarkerAndV4InOnePreferenceCommit() {
        val blob = "zip-payload".toByteArray()
        val blobKey = RearWidgetConfigCodec.businessBlobKey("taxi")
        val fakePrefs = InMemoryRemotePrefs(
            initialValues = linkedMapOf(LEGACY_PREFS_MIGRATION_STATUS_KEY to LEGACY_PREFS_MIGRATION_COMPLETED_V3),
        )
        val source = sourceOf(
            blobKey to Base64.getEncoder().encodeToString(blob),
            "ordinary" to true,
            RearWidgetConfigCodec.businessBlobSourceKey("taxi") to "/legacy/taxi.zip",
            RearWidgetConfigCodec.businessBlobMetaKey("taxi") to "hash:11",
        )

        val result = LegacyPreferenceMigrator(source).migrate(PrefsManager(fakePrefs))

        assertEquals(LegacyPreferenceMigrationOutcome.COMPLETED, result.outcome)
        assertEquals(1, result.omittedDerivedCount)
        assertEquals(blob.size.toLong(), result.omittedDerivedBytes)
        assertEquals(1, fakePrefs.clearCalls)
        assertEquals(1, fakePrefs.commitCalls)
        assertEquals(
            LEGACY_PREFS_MIGRATION_COMPLETED_V4,
            fakePrefs.values[LEGACY_PREFS_MIGRATION_STATUS_KEY]
        )
        assertEquals(true, fakePrefs.values["ordinary"])
        val marker = fakePrefs.values[blobKey] as String
        val fileName = RearWidgetConfigCodec.remoteBlobFileNameFromMarker(marker)
        assertTrue(fileName != null)
        assertArrayEquals(blob, fakePrefs.remoteFiles[fileName])
        assertEquals(
            "/legacy/taxi.zip",
            fakePrefs.values[RearWidgetConfigCodec.businessBlobSourceKey("taxi")]
        )
    }

    @Test
    fun remoteFileWriteFailureLeavesExistingPreferencesAndDoesNotCommitCompletion() {
        val existing = linkedMapOf<String, Any?>("old" to "value")
        val fakePrefs = InMemoryRemotePrefs(existing, failRemoteFileWrite = true)
        val source = sourceOf(
            RearWidgetConfigCodec.businessBlobKey("taxi") to
                    Base64.getEncoder().encodeToString("zip-payload".toByteArray()),
        )

        val result = LegacyPreferenceMigrator(source).migrate(PrefsManager(fakePrefs))

        assertEquals(LegacyPreferenceMigrationOutcome.FAILED, result.outcome)
        assertEquals(0, fakePrefs.clearCalls)
        assertEquals(0, fakePrefs.commitCalls)
        assertEquals(existing, fakePrefs.values)
        assertFalse(fakePrefs.values.containsKey(LEGACY_PREFS_MIGRATION_STATUS_KEY))
    }

    private fun sourceOf(vararg entries: Pair<String, Any?>) =
        LegacyPreferenceSnapshot(
            sourcePath = LegacyPreferencePath.fromDiscoveredPath(
                "/data/misc/apexdata/user_de/0/prefs/hk.uwu.reareye/hk.uwu.reareye_preferences.xml",
            ),
            values = linkedMapOf(*entries),
        ).let { snapshot ->
            hk.uwu.reareye.ui.config.LegacyPreferenceSource { snapshot }
        }

    private class InMemoryRemotePrefs(
        initialValues: MutableMap<String, Any?>,
        private val failRemoteFileWrite: Boolean = false,
    ) : HookPrefs {
        val values = initialValues
        val remoteFiles = linkedMapOf<String, ByteArray>()
        var clearCalls = 0
        var commitCalls = 0

        override fun getString(key: String, defaultValue: String): String =
            values[key] as? String ?: defaultValue

        override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
            (values[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: defaultValue

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key] as? Int ?: defaultValue

        override fun getLong(key: String, defaultValue: Long): Long =
            values[key] as? Long ?: defaultValue

        override fun getFloat(key: String, defaultValue: Float): Float =
            values[key] as? Float ?: defaultValue

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun all(): Map<String, Any?> = values.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }

        override fun writeRemoteFile(name: String, bytes: ByteArray): Boolean {
            if (failRemoteFileWrite) return false
            remoteFiles[name] = bytes.copyOf()
            return true
        }

        override fun clearRemotePreferences(): Boolean {
            clearCalls++
            values.clear()
            return true
        }

        override fun edit(): HookPrefsEditor = Editor()

        private inner class Editor : HookPrefsEditor {
            private var clear = false
            private val puts = linkedMapOf<String, Any?>()
            private val removes = linkedSetOf<String>()

            override fun putString(key: String, value: String?): HookPrefsEditor = put(key, value)
            override fun putStringSet(key: String, value: Set<String>?): HookPrefsEditor =
                put(key, value)

            override fun putInt(key: String, value: Int): HookPrefsEditor = put(key, value)
            override fun putLong(key: String, value: Long): HookPrefsEditor = put(key, value)
            override fun putFloat(key: String, value: Float): HookPrefsEditor = put(key, value)
            override fun putBoolean(key: String, value: Boolean): HookPrefsEditor = put(key, value)

            override fun remove(key: String): HookPrefsEditor {
                removes += key
                puts.remove(key)
                return this
            }

            override fun clear(): HookPrefsEditor {
                clear = true
                puts.clear()
                removes.clear()
                return this
            }

            override fun commit(): Boolean {
                commitCalls++
                if (clear) values.clear()
                removes.forEach(values::remove)
                values.putAll(puts)
                return true
            }

            override fun apply() {
                commit()
            }

            private fun put(key: String, value: Any?): HookPrefsEditor {
                removes.remove(key)
                puts[key] = value
                return this
            }
        }
    }
}
