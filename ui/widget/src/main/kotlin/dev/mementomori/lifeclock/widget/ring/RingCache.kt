package dev.mementomori.lifeclock.widget.ring

import kotlin.math.floor

/**
 * Cache identity of a rendered frame.
 *
 * The label is intentionally absent: text is a Glance `Text`, never baked into the bitmap, so two
 * styles that differ only by label can share a frame.
 */
data class RingCacheKey(
    val fraction: Double,
    val diameterPx: Int,
    val strokeRatio: Double,
    val color: Int,
    val trackColor: Int,
    val startAngleDeg: Float,
) {
    companion object {
        /** Quantizes before drawing, so a bucket always produces the same bitmap regardless of order. */
        fun of(style: RingStyle, fraction: Double, buckets: Int): RingCacheKey {
            require(buckets > 0) { "buckets must be > 0" }
            val quantized = floor(fraction.coerceIn(0.0, 1.0) * buckets) / buckets
            return RingCacheKey(
                fraction = quantized,
                diameterPx = style.diameterPx,
                strokeRatio = style.strokeRatio,
                color = style.color,
                trackColor = style.trackColor,
                startAngleDeg = style.startAngleDeg,
            )
        }
    }
}

/** Small LRU over [LinkedHashMap] in access order. Synchronised because renders run on a worker. */
class LruCache<K, V>(private val maxEntries: Int) {

    init {
        require(maxEntries > 0) { "maxEntries must be > 0" }
    }

    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun getOrPut(key: K, create: () -> V): V = entries[key] ?: create().also { entries[key] = it }

    @Synchronized
    fun clear() = entries.clear()

    val size: Int
        @Synchronized get() = entries.size
}
