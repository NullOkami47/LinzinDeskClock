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
                val renderer = remember(cover, blurPx) { ArtworkRenderer(cover, blurPx) }
                DisposableEffect(renderer) { onDispose { renderer.release() } }
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas -> renderer.draw(canvas.nativeCanvas, size.width.roundToInt(), size.height.roundToInt()) }
                }
            }
        }
    }
}

private class ArtworkRenderer(private val bitmap: Bitmap, blurPx: Float) {
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

    fun draw(canvas: AndroidCanvas, newWidth: Int, newHeight: Int) {
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

    fun release() { node.discardDisplayList() }
}
