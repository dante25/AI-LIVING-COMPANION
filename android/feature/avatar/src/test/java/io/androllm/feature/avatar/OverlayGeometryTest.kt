package io.androllm.feature.avatar

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {
    @Test
    fun insidePositionIsUnchanged() {
        assertEquals(100 to 200, clampOverlayPosition(100, 200, 300, 360, 1080, 2400))
    }

    @Test
    fun negativePositionIsClampedToZero() {
        assertEquals(0 to 0, clampOverlayPosition(-50, -10, 300, 360, 1080, 2400))
    }

    @Test
    fun overflowIsClampedToLastValidPosition() {
        assertEquals(780 to 2040, clampOverlayPosition(2000, 5000, 300, 360, 1080, 2400))
    }

    @Test
    fun windowLargerThanScreenGoesToZero() {
        assertEquals(0 to 0, clampOverlayPosition(40, 40, 2000, 3000, 1080, 2400))
    }
}
