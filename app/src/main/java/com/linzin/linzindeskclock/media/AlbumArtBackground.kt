package com.linzin.linzindeskclock.media

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The whole cover is fitted and drawn sharply. MIRROR tiles extend its edges into only the
 * surrounding rectangles. RenderEffect is clipped at draw time, so blur cannot cover the art.
 * Privacy callers must remove this composable entirely, bypassing the outgoing crossfade.
 */
@Composable
fun AlbumArtBackground(artwork: Bitmap?, modifier: Modifier = Modifier) {
    val blurPx = with(LocalDensity.current) { 22.dp.toPx() }
    Box(modifier.background(Color.Black).clipToBounds()) {
        Crossfade(targetState = artwork, modifier = Modifier.fillMaxSize(), animationSpec = tween(240), label = "mediaCover") { cover ->
            if (cover != null && !cover.isRecycled) {
                val renderer = remember(cover, blurPx) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ArtworkRenderer(cover, blurPx)
                    else LegacyArtworkRenderer(cover, blurPx, glass = false)
                }
                DisposableEffect(renderer) { onDispose { renderer.release() } }
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas -> renderer.draw(canvas.nativeCanvas, size.width.roundToInt(), size.height.roundToInt()) }
                }
            }
        }
    }
}

private interface ArtworkDrawing {
    fun draw(canvas: AndroidCanvas, newWidth: Int, newHeight: Int)
    fun release()
}

@RequiresApi(Build.VERSION_CODES.S)
private class ArtworkRenderer(private val bitmap: Bitmap, blurPx: Float) : ArtworkDrawing {
    private val sharpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mirror = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    private val node = RenderNode("media-artwork-extensions").apply {
        setRenderEffect(RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.MIRROR))
        setClipToBounds(true)
    }
    private var width = 0
    private var height = 0
    private var layout: ArtworkLayout? = null
    private val destination = RectF()

    override fun draw(canvas: AndroidCanvas, newWidth: Int, newHeight: Int) {
        if (bitmap.isRecycled || newWidth <= 0 || newHeight <= 0) return
        if (newWidth != width || newHeight != height) {
            width = newWidth
            height = newHeight
            layout = fitArtwork(bitmap.width, bitmap.height, width, height)
            val fit = layout ?: return
            mirror.setLocalMatrix(Matrix().apply {
                setScale(fit.scale, fit.scale)
                postTranslate(fit.artwork.left, fit.artwork.top)
            })
            mirrorPaint.shader = mirror
            destination.set(fit.artwork.left, fit.artwork.top, fit.artwork.right, fit.artwork.bottom)
            node.setPosition(0, 0, width, height)
            val recording = node.beginRecording(width, height)
            recording.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mirrorPaint)
            node.endRecording()
        }
        val fit = layout ?: return
        for (extension in fit.extensions) {
            val saved = canvas.save()
            canvas.clipRect(extension.left, extension.top, extension.right, extension.bottom)
            if (canvas.isHardwareAccelerated) {
                canvas.drawRenderNode(node)
            } else {
                // Software previews cannot draw RenderNode. Preserve geometry without blur.
                canvas.drawRect(extension.left, extension.top, extension.right, extension.bottom, mirrorPaint)
            }
            canvas.restoreToCount(saved)
        }
        canvas.drawBitmap(bitmap, null, destination, sharpPaint)
    }

    override fun release() { node.discardDisplayList() }
}

/**
 * A second, fully blurred copy supplies the control panel's material. It is deliberately a
 * background-only layer: the sharp fitted cover and the controls are different siblings.
 * Crop-fill here only samples colour; AlbumArtBackground still displays the entire original.
 */
@Composable
fun AlbumArtGlassBackground(artwork: Bitmap?, modifier: Modifier = Modifier) {
    val blurPx = with(LocalDensity.current) { 28.dp.toPx() }
    Box(modifier.background(Color(0xFF1E1E22)).clipToBounds()) {
        Crossfade(targetState = artwork, modifier = Modifier.fillMaxSize(), animationSpec = tween(240), label = "mediaGlass") { cover ->
            if (cover != null && !cover.isRecycled) {
                val renderer = remember(cover, blurPx) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlassArtworkRenderer(cover, blurPx)
                    else LegacyArtworkRenderer(cover, blurPx, glass = true)
                }
                DisposableEffect(renderer) { onDispose { renderer.release() } }
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas -> renderer.draw(canvas.nativeCanvas, size.width.roundToInt(), size.height.roundToInt()) }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class GlassArtworkRenderer(private val bitmap: Bitmap, blurPx: Float) : ArtworkDrawing {
    private val shader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = this@GlassArtworkRenderer.shader }
    private val node = RenderNode("media-control-glass").apply {
        setRenderEffect(RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.MIRROR))
        setClipToBounds(true)
    }
    private var width = 0
    private var height = 0

    override fun draw(canvas: AndroidCanvas, newWidth: Int, newHeight: Int) {
        if (bitmap.isRecycled || newWidth <= 0 || newHeight <= 0) return
        if (newWidth != width || newHeight != height) {
            width = newWidth
            height = newHeight
            val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate((width - bitmap.width * scale) / 2f, (height - bitmap.height * scale) / 2f)
            })
            node.setPosition(0, 0, width, height)
            val recording = node.beginRecording(width, height)
            recording.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            node.endRecording()
        }
        val saved = canvas.save()
        canvas.clipRect(0, 0, width, height)
        if (canvas.isHardwareAccelerated) canvas.drawRenderNode(node)
        else {
            // Software preview has colour and clipping, but must not be claimed as blur QA.
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        canvas.restoreToCount(saved)
    }

    override fun release() { node.discardDisplayList() }
}

/** A small CPU blur keeps legacy devices usable; the centre cover still uses its sharp source. */
private class LegacyArtworkRenderer(
    private val bitmap: Bitmap,
    private val blurPx: Float,
    private val glass: Boolean
) : ArtworkDrawing {
    private val sharpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var blurred: Bitmap? = null
    private var width = 0
    private var height = 0
    private var layout: ArtworkLayout? = null
    private val destination = RectF()

    override fun draw(canvas: AndroidCanvas, newWidth: Int, newHeight: Int) {
        if (bitmap.isRecycled || newWidth <= 0 || newHeight <= 0) return
        if (newWidth != width || newHeight != height) {
            width = newWidth
            height = newHeight
            val fit = fitArtwork(bitmap.width, bitmap.height, width, height) ?: return
            layout = fit
            destination.set(fit.artwork.left, fit.artwork.top, fit.artwork.right, fit.artwork.bottom)
            val scale = if (glass) maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height) else fit.scale
            val sampleScale = minOf(1f, 128f / maxOf(bitmap.width, bitmap.height))
            val sampleWidth = (bitmap.width * sampleScale).roundToInt().coerceAtLeast(1)
            val sampleHeight = (bitmap.height * sampleScale).roundToInt().coerceAtLeast(1)
            val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
            val radius = (blurPx * sampleScale / scale).roundToInt().coerceIn(1, 12)
            val pixels = IntArray(sampleWidth * sampleHeight)
            sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            if (sample !== bitmap) sample.recycle()
            val filtered = blurArtworkPixels(pixels, sampleWidth, sampleHeight, radius)
            blurred?.recycle()
            blurred = Bitmap.createBitmap(filtered, sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
            mirrorPaint.shader = BitmapShader(blurred!!, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(bitmap.width * scale / sampleWidth, bitmap.height * scale / sampleHeight)
                    postTranslate((width - bitmap.width * scale) / 2f, (height - bitmap.height * scale) / 2f)
                })
            }
        }
        val fit = layout ?: return
        if (glass) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mirrorPaint)
        else {
            for (extension in fit.extensions) {
                canvas.drawRect(extension.left, extension.top, extension.right, extension.bottom, mirrorPaint)
            }
            canvas.drawBitmap(bitmap, null, destination, sharpPaint)
        }
    }

    override fun release() { mirrorPaint.shader = null; blurred?.recycle(); blurred = null }
}

/** Two bounded box passes use at most a 128 x 128 sample, independent of the original cover. */
internal fun blurArtworkPixels(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height && radius in 1..12)
    fun pass(source: IntArray, horizontal: Boolean): IntArray {
        val output = IntArray(source.size)
        val length = if (horizontal) width else height
        val rows = if (horizontal) height else width
        val divisor = radius * 2 + 1
        fun index(row: Int, position: Int): Int = if (horizontal) row * width + position else position * width + row
        for (row in 0 until rows) {
            var alpha = 0; var red = 0; var green = 0; var blue = 0
            fun add(position: Int, sign: Int) {
                val colour = source[index(row, position.coerceIn(0, length - 1))]
                alpha += (colour ushr 24) * sign
                red += ((colour ushr 16) and 255) * sign
                green += ((colour ushr 8) and 255) * sign
                blue += (colour and 255) * sign
            }
            for (offset in -radius..radius) add(offset, 1)
            for (position in 0 until length) {
                output[index(row, position)] = ((alpha / divisor) shl 24) or ((red / divisor) shl 16) or
                    ((green / divisor) shl 8) or (blue / divisor)
                add(position - radius, -1)
                add(position + radius + 1, 1)
            }
        }
        return output
    }
    return pass(pass(pixels, horizontal = true), horizontal = false)
}
