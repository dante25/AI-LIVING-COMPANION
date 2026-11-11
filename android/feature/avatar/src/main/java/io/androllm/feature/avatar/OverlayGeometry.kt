package io.androllm.feature.avatar

/**
 * Drži prozor lika unutar ekrana. Vraća (x, y) lijevog gornjeg ugla tako da
 * cijeli prozor [w] x [h] ostane unutar ekrana [screenW] x [screenH].
 * Ako je prozor veći od ekrana, vraća 0.
 */
fun clampOverlayPosition(x: Int, y: Int, w: Int, h: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
    val maxX = (screenW - w).coerceAtLeast(0)
    val maxY = (screenH - h).coerceAtLeast(0)
    return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
}
