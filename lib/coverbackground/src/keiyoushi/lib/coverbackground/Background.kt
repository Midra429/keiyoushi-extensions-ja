package keiyoushi.lib.coverbackground

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.roundToInt

internal fun renderCoverBackground(original: Bitmap): Bitmap {
    val geometry = CoverGeometry(original.width, original.height)
    val output = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val background = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888)
        try {
            val backgroundCanvas = Canvas(background)
            backgroundCanvas.drawColor(Color.BLACK)
            val scale = maxOf(48f / original.width, 72f / original.height)
            backgroundCanvas.drawBitmap(
                original,
                null,
                centeredRect(original.width * scale, original.height * scale, 48, 72),
                paint,
            )

            val pixels = IntArray(48 * 72)
            background.getPixels(pixels, 0, 48, 0, 0, 48, 72)
            blurBackground(pixels, 48, 72)
            background.setPixels(pixels, 0, 48, 0, 0, 48, 72)
            canvas.drawBitmap(background, null, RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()), paint)

            val luminance = pixels.sumOf { pixel ->
                0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)
            } / (pixels.size * 255)
            canvas.drawColor(Color.argb(((0.28 + luminance * 0.30) * 255).roundToInt(), 0, 0, 0))
            paint.shader = RadialGradient(
                output.width / 2f,
                output.height / 2f,
                output.height * 0.7f,
                Color.TRANSPARENT,
                0x40000000,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)
            paint.shader = null
        } finally {
            background.recycle()
        }

        val foreground = centeredRect(geometry.imageWidth, geometry.imageHeight, output.width, output.height)
        canvas.drawBitmap(original, null, foreground, paint)
        return output
    } catch (e: Exception) {
        output.recycle()
        throw e
    }
}

private fun centeredRect(width: Float, height: Float, canvasWidth: Int, canvasHeight: Int): RectF {
    val left = (canvasWidth - width) / 2f
    val top = (canvasHeight - height) / 2f
    return RectF(left, top, left + width, top + height)
}

internal class CoverGeometry(sourceWidth: Int, sourceHeight: Int) {
    private val scale = minOf(1.0, 600.0 / sourceWidth, 900.0 / sourceHeight)
    private val widthUnits = (maxOf(sourceWidth * scale / 2, sourceHeight * scale / 3)).roundToInt().coerceIn(1, 300)
    val width = widthUnits * 2
    val height = widthUnits * 3
    private val fit = minOf(1.0, width.toDouble() / sourceWidth, height.toDouble() / sourceHeight)
    val imageWidth = (sourceWidth * fit).toFloat()
    val imageHeight = (sourceHeight * fit).toFloat()
}

// Three small box passes approximate a Gaussian blur without RenderScript or Android-version-specific APIs.
internal fun blurBackground(pixels: IntArray, width: Int, height: Int) {
    val scratch = IntArray(pixels.size)
    repeat(3) {
        boxBlur(pixels, scratch, width, height, horizontal = true)
        boxBlur(scratch, pixels, width, height, horizontal = false)
    }
}

private fun boxBlur(input: IntArray, output: IntArray, width: Int, height: Int, horizontal: Boolean) {
    val radius = 4
    val count = radius * 2 + 1
    val lineLength = if (horizontal) width else height
    val lineCount = if (horizontal) height else width
    val step = if (horizontal) 1 else width
    for (line in 0 until lineCount) {
        val start = if (horizontal) line * width else line
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = input[start + offset.coerceIn(0, lineLength - 1) * step]
            red += pixel shr 16 and 0xff
            green += pixel shr 8 and 0xff
            blue += pixel and 0xff
        }
        for (position in 0 until lineLength) {
            output[start + position * step] =
                (0xff shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            val leaving = input[start + (position - radius).coerceIn(0, lineLength - 1) * step]
            val entering = input[start + (position + radius + 1).coerceIn(0, lineLength - 1) * step]
            red += (entering shr 16 and 0xff) - (leaving shr 16 and 0xff)
            green += (entering shr 8 and 0xff) - (leaving shr 8 and 0xff)
            blue += (entering and 0xff) - (leaving and 0xff)
        }
    }
}
