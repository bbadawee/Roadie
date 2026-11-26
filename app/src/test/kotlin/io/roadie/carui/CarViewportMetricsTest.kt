package io.roadie.carui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarViewportMetricsTest {

    @Test
    fun `small car display scales down to preserve 1024x600 logical canvas`() {
        val metrics = CarViewportMetrics(widthDp = 800f, heightDp = 480f)
        assertTrue(metrics.contentScale < 1f)
        assertEquals(3, metrics.browseColumns)
        assertTrue(metrics.useWideWatchLayout)
    }

    @Test
    fun `standard 1024x600 car display uses scale 1 and 3 columns`() {
        val metrics = CarViewportMetrics(widthDp = 1024f, heightDp = 600f)
        assertEquals(1f, metrics.contentScale, 0.001f)
        assertEquals(3, metrics.browseColumns)
        assertTrue(metrics.useWideWatchLayout)
    }

    @Test
    fun `portrait phone display uses stacked watch layout`() {
        val metrics = CarViewportMetrics(widthDp = 412f, heightDp = 915f)
        assertFalse(metrics.useWideWatchLayout)
        assertEquals(3, metrics.browseColumns)
    }

    @Test
    fun `widescreen split screen 640x720 switches to stacked watch layout`() {
        val metrics = CarViewportMetrics(widthDp = 640f, heightDp = 720f)
        assertFalse(metrics.useWideWatchLayout)
        assertEquals(3, metrics.browseColumns)
    }

    @Test
    fun `ultrawide split screen 960x720 keeps side-by-side watch layout`() {
        val metrics = CarViewportMetrics(widthDp = 960f, heightDp = 720f)
        assertTrue(metrics.useWideWatchLayout)
        assertEquals(3, metrics.browseColumns)
    }
}
