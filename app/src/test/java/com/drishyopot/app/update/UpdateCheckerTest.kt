package com.drishyopot.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun newerVersions() {
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.9"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("0.1.1", "0.1.0-debug"))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "1.0.0"))
    }
}
