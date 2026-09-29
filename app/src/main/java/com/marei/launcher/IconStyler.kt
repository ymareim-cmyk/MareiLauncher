package com.marei.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Turns any app icon into the single-color outline look of your PC sidebar:
 * dark gray ink (#52514E) on a light paper tile (#F0EFEC).
 */
object IconStyler {
    const val LINE = "line"
    const val ORIGINAL = "original"

    private val INK = Color.parseColor("#52514E")
    private val PAPER = Color.parseColor("#F0EFEC")

    fun render(src: Drawable, style: String, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (style == ORIGINAL) {
            drawInto(src, canvas, size, 0f)
            return out
        }

        // 1) Best case: the app ships a monochrome "themed icon" (Android 13+).
        if (Build.VERSION.SDK_INT >= 33 && src is AdaptiveIconDrawable) {
            val mono = src.monochrome
            if (mono != null) {
                val d = mono.mutate()
                d.setBounds(0, 0, size, size)
                d.colorFilter = PorterDuffColorFilter(INK, PorterDuff.Mode.SRC_IN)
                d.draw(canvas)
                return out
            }
        }

        // 2) Adaptive icon with a glyph-style foreground: use its silhouette.
        if (src is AdaptiveIconDrawable) {
            val fg = src.foreground
            if (fg != null) {
                val fgBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                fg.setBounds(0, 0, size, size)
                fg.draw(Canvas(fgBmp))
                if (coverage(fgBmp) < 0.8f) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    p.colorFilter = PorterDuffColorFilter(INK, PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(fgBmp, 0f, 0f, p)
                    return out
                }
            }
        }

        // 3) Fallback: two-tone version of the full icon.
        val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawInto(src, Canvas(raw), size, 0.06f)
        val invert = averageLuminance(raw) < 0.6f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = ColorMatrixColorFilter(duotone(invert))
        canvas.drawBitmap(raw, 0f, 0f, p)
        return out
    }

    private fun drawInto(d: Drawable, c: Canvas, size: Int, insetFraction: Float) {
        val i = (size * insetFraction).toInt()
        d.setBounds(i, i, size - i, size - i)
        d.draw(c)
    }

    /** Share of opaque pixels inside the adaptive-icon safe zone. */
    private fun coverage(bmp: Bitmap): Float {
        val w = bmp.width
        val px = IntArray(w * w)
        bmp.getPixels(px, 0, w, 0, 0, w, w)
        val lo = (w * 0.19f).toInt()
        val hi = (w * 0.81f).toInt()
        var opaque = 0
        var total = 0
        for (y in lo until hi) for (x in lo until hi) {
            total++
            if (Color.alpha(px[y * w + x]) > 128) opaque++
        }
        return if (total == 0) 1f else opaque.toFloat() / total
    }

    private fun averageLuminance(bmp: Bitmap): Float {
        val w = bmp.width
        val px = IntArray(w * bmp.height)
        bmp.getPixels(px, 0, w, 0, 0, w, bmp.height)
        var sum = 0f
        var n = 0
        for (c in px) {
            if (Color.alpha(c) > 128) {
                sum += (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
                n++
            }
        }
        return if (n == 0) 1f else sum / n
    }

    /** Maps brightness onto a two-color ramp between INK and PAPER. */
    private fun duotone(invert: Boolean): ColorMatrix {
        val from = if (invert) PAPER else INK   // color for darkest pixels
        val to = if (invert) INK else PAPER     // color for brightest pixels
        fun row(f: Int, t: Int): FloatArray {
            val k = (t - f) / 255f
            return floatArrayOf(k * 0.299f, k * 0.587f, k * 0.114f, 0f, f.toFloat())
        }
        return ColorMatrix(
            row(Color.red(from), Color.red(to)) +
                row(Color.green(from), Color.green(to)) +
                row(Color.blue(from), Color.blue(to)) +
                floatArrayOf(0f, 0f, 0f, 1f, 0f)
        )
    }
}
