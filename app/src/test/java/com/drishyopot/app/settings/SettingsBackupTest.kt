package com.drishyopot.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupTest {
    @Test fun roundTripKeepsValues() {
        val values = linkedMapOf<String, Any>(PrefKeys.SKIP_SPONSORS to false, PrefKeys.HAPTIC_STRENGTH to 4)
        val parsed = SettingsBackup.parse(SettingsBackup.exportMap(values))!!
        assertEquals(false, parsed[PrefKeys.SKIP_SPONSORS])
        assertEquals(4, parsed[PrefKeys.HAPTIC_STRENGTH])
    }

    @Test fun outOfRangeIntIsClamped() {
        val text = SettingsBackup.exportMap(mapOf(PrefKeys.HAPTIC_STRENGTH to 99))
        assertEquals(5, SettingsBackup.parse(text)!![PrefKeys.HAPTIC_STRENGTH])
    }

    @Test fun wrongAppIsRejected() {
        assertNull(SettingsBackup.parse("""{"app":"other","settings":{}}"""))
    }

    @Test fun wrongTypeIsRejected() {
        assertNull(SettingsBackup.parse("""{"app":"drishyopot","settings":{"skip_sponsors":"yes"}}"""))
    }

    @Test fun garbageIsRejected() {
        assertNull(SettingsBackup.parse("not json"))
    }
}
