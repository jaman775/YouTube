package com.drishyopot.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureMathTest {
    @Test fun horizontalDragSeeks() =
        assertEquals(GestureMath.Mode.SEEK, GestureMath.classify(80f, 10f, 500f, 1000f))

    @Test fun verticalLeftIsBrightness() =
        assertEquals(GestureMath.Mode.BRIGHTNESS, GestureMath.classify(2f, -60f, 100f, 1000f))

    @Test fun verticalRightIsVolume() =
        assertEquals(GestureMath.Mode.VOLUME, GestureMath.classify(2f, -60f, 900f, 1000f))

    @Test fun verticalCenterIsFullscreen() =
        assertEquals(GestureMath.Mode.FULLSCREEN, GestureMath.classify(2f, -60f, 500f, 1000f))

    @Test fun tinyMovementIsIgnored() =
        assertEquals(GestureMath.Mode.NONE, GestureMath.classify(3f, 4f, 500f, 1000f))

    @Test fun seekIsClampedToDuration() {
        assertEquals(60_000L, GestureMath.seekTarget(50_000L, 500f, 1000f, 60_000L))
        assertEquals(0L, GestureMath.seekTarget(10_000L, -900f, 1000f, 60_000L))
    }

    @Test fun fullscreenIntentFollowsDirection() {
        assertEquals(1, GestureMath.fullscreenIntent(-200f, 1000f))
        assertEquals(-1, GestureMath.fullscreenIntent(200f, 1000f))
        assertEquals(0, GestureMath.fullscreenIntent(20f, 1000f))
    }

    @Test fun doubleTapZones() {
        assertEquals(-1, GestureMath.doubleTapZone(100f, 900f))
        assertEquals(0, GestureMath.doubleTapZone(450f, 900f))
        assertEquals(1, GestureMath.doubleTapZone(800f, 900f))
    }

    @Test fun levelAdjustIsClamped() {
        assertEquals(1f, GestureMath.adjustLevel(0.9f, -1000f, 500f), 0.0001f)
        assertEquals(0f, GestureMath.adjustLevel(0.1f, 1000f, 500f), 0.0001f)
    }
}
