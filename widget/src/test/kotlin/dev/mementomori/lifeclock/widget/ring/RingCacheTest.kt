package dev.mementomori.lifeclock.widget.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T2.5 - cache identity and eviction, with no bitmaps involved. */
class RingCacheTest {

    private val style = RingStyle(
        diameterPx = 240,
        strokeRatio = 0.12,
        color = 0xFFE5484D.toInt(),
        trackColor = 0xFF2A2A2E.toInt(),
    )

    @Test
    fun `fractions in the same bucket share a key`() {
        val a = RingCacheKey.of(style, 0.500, buckets = 100)
        val b = RingCacheKey.of(style, 0.509, buckets = 100)
        assertEquals(a, b)
    }

    @Test
    fun `a different bucket is a different key`() {
        assertNotEquals(
            RingCacheKey.of(style, 0.50, buckets = 100),
            RingCacheKey.of(style, 0.51, buckets = 100),
        )
    }

    @Test
    fun `out-of-range fractions clamp`() {
        assertEquals(
            RingCacheKey.of(style, 1.0, buckets = 100),
            RingCacheKey.of(style, 4.2, buckets = 100),
        )
    }

    @Test
    fun `the label does not affect the bitmap key`() {
        assertEquals(
            RingCacheKey.of(style.copy(label = "a"), 0.5, buckets = 100),
            RingCacheKey.of(style.copy(label = "b"), 0.5, buckets = 100),
        )
    }

    @Test
    fun `visual changes do change the key`() {
        assertNotEquals(
            RingCacheKey.of(style, 0.5, buckets = 100),
            RingCacheKey.of(style.copy(color = 0xFFFFFFFF.toInt()), 0.5, buckets = 100),
        )
    }

    @Test
    fun `lru evicts the least recently used entry`() {
        val cache = LruCache<String, Int>(maxEntries = 2)
        cache.getOrPut("a") { 1 }
        cache.getOrPut("b") { 2 }
        cache.getOrPut("a") { 0 } // touch "a" so "b" becomes the eldest
        cache.getOrPut("c") { 3 } // evicts "b"

        assertEquals(2, cache.size)
        assertEquals(3, cache.getOrPut("c") { 0 })
        assertEquals(1, cache.getOrPut("a") { 0 })
        assertEquals(4, cache.getOrPut("b") { 4 }) // evicted, so created again
    }

    @Test
    fun `create runs once per key`() {
        val cache = LruCache<String, Int>(maxEntries = 4)
        var calls = 0
        repeat(5) { cache.getOrPut("k") { calls++; calls } }
        assertEquals(1, calls)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero-sized cache is a programming error`() {
        LruCache<String, Int>(0)
    }
}
