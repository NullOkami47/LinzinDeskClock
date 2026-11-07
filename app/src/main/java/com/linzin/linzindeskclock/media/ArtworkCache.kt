package com.linzin.linzindeskclock.media

import android.graphics.Bitmap
import java.lang.ref.WeakReference

/**
 * Memory-only, bounded cache. Accepts embedded Bitmaps exclusively: no URI, content resolver,
 * file, network or package icon loading. Weak source references cannot retain full-size covers.
 */
internal class ArtworkCache(private val maxEdge: Int = 1024, private val maxBytes: Int = 12 * 1024 * 1024) {
    private data class Entry(val source: WeakReference<Bitmap>, val generation: Int, val bitmap: Bitmap)
    private val entries = LinkedHashMap<Int, Entry>(8, 0.75f, true)
    private var bytes = 0
    private var epoch = 0L

    /** Run on a worker dispatcher. Returned bitmaps are immutable and owned by the cache/UI. */
    fun load(source: Bitmap): Bitmap? {
        if (source.isRecycled) return null
        val identity = System.identityHashCode(source)
        val generation = source.generationId
        val ticket = synchronized(this) {
            entries[identity]?.let { entry ->
                if (entry.source.get() === source && entry.generation == generation) return entry.bitmap
            }
            epoch
        }
        val size = downsampleSize(source.width, source.height, maxEdge) ?: return null
        val bitmap = try {
            val scaled = Bitmap.createScaledBitmap(source, size.width, size.height, true)
            // Always copy a borrowed bitmap; a producer can mutate or recycle its original.
            if (scaled === source || scaled.config != Bitmap.Config.ARGB_8888 || scaled.isMutable) {
                val copy = scaled.copy(Bitmap.Config.ARGB_8888, false)
                if (scaled !== source) scaled.recycle()
                copy
            } else scaled
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        return synchronized(this) {
            // In-flight work must never repopulate the cache after stop, lock or revocation.
            if (ticket != epoch || source.isRecycled || source.generationId != generation) return null
            entries.remove(identity)?.let { bytes -= it.bitmap.allocationByteCount }
            if (bitmap.allocationByteCount <= maxBytes) {
                entries[identity] = Entry(WeakReference(source), generation, bitmap)
                bytes += bitmap.allocationByteCount
                val iterator = entries.entries.iterator()
                while ((bytes > maxBytes || entries.size > 8) && iterator.hasNext()) {
                    bytes -= iterator.next().value.bitmap.allocationByteCount
                    iterator.remove()
                }
            }
            bitmap
        }
    }

    @Synchronized fun clear() {
        epoch++
        entries.clear()
        bytes = 0
        // Never recycle a published bitmap: Compose may still be crossfading that frame.
    }
}
