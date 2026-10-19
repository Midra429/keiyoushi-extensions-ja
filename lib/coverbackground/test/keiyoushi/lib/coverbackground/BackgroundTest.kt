package keiyoushi.lib.coverbackground

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundTest {
    @Test
    fun `square covers retain the complete foreground with a subdued background`() {
        val original = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.WHITE)
        val paint = Paint().apply { color = Color.RED }
        Canvas(original).drawRect(0f, 0f, 60f, 120f, paint)
        val output = renderCoverBackground(original)
        try {
            assertEquals(120, output.width)
            assertEquals(180, output.height)
            assertEquals(Color.RED, output.getPixel(0, 30))
            assertEquals(Color.WHITE, output.getPixel(119, 149))
            assertEquals(Color.RED, output.getPixel(10, 90))
            assertEquals(Color.WHITE, output.getPixel(110, 90))
            val background = output.getPixel(60, 10)
            assertTrue(Color.red(background) > Color.green(background))
            assertTrue(Color.green(background) > 0)
            assertTrue(Color.red(background) < 200)
        } finally {
            original.recycle()
            output.recycle()
        }
    }

    @Test
    fun `small covers with odd padding retain every foreground pixel`() {
        for ((width, height) in listOf(140 to 191, 141 to 190)) {
            val pixels = IntArray(width * height) { if (it / width % 2 == 0) Color.BLACK else Color.WHITE }
            val original = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            val output = renderCoverBackground(original)
            try {
                val actual = IntArray(pixels.size)
                val left = ((output.width - width) / 2f).roundToInt()
                val top = ((output.height - height) / 2f).roundToInt()
                output.getPixels(actual, 0, width, left, top, width, height)
                assertArrayEquals("Foreground must not be softened by fractional-pixel positioning", pixels, actual)
            } finally {
                original.recycle()
                output.recycle()
            }
        }
    }

    @Test
    fun `backgrounds are slightly brighter while retaining adaptive dimming`() {
        for ((color, expected) in listOf(Color.WHITE to 104, Color.rgb(128, 128, 128) to 68)) {
            val original = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            original.eraseColor(color)
            val output = renderCoverBackground(original)
            try {
                val background = output.getPixel(60, 15)
                assertTrue(
                    "Expected background near $expected, got ${Color.red(background)}",
                    Color.red(background) in expected - 2..expected + 2
                )
                assertEquals(Color.red(background), Color.green(background))
                assertEquals(Color.red(background), Color.blue(background))
                assertEquals(color, output.getPixel(60, 90))
            } finally {
                original.recycle()
                output.recycle()
            }
        }
    }

    @Test
    fun `tall covers get side padding without cropping their top and bottom`() {
        val original = Bitmap.createBitmap(60, 180, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.GREEN)
        val output = renderCoverBackground(original)
        try {
            assertEquals(120, output.width)
            assertEquals(180, output.height)
            assertEquals(Color.GREEN, output.getPixel(30, 0))
            assertEquals(Color.GREEN, output.getPixel(89, 179))
            assertTrue(Color.green(output.getPixel(5, 90)) in 1..200)
        } finally {
            original.recycle()
            output.recycle()
        }
    }

    @Test
    fun `landscape covers remain entirely visible`() {
        val original = Bitmap.createBitmap(180, 60, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.BLUE)
        val output = renderCoverBackground(original)
        try {
            assertEquals(180, output.width)
            assertEquals(270, output.height)
            assertEquals(Color.BLUE, output.getPixel(0, 105))
            assertEquals(Color.BLUE, output.getPixel(179, 164))
            assertTrue(Color.blue(output.getPixel(90, 10)) in 1..200)
        } finally {
            original.recycle()
            output.recycle()
        }
    }

    @Test
    fun `large covers are bounded and the input remains usable`() {
        val original = Bitmap.createBitmap(1600, 1600, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.CYAN)
        val output = renderCoverBackground(original)
        try {
            assertEquals(600, output.width)
            assertEquals(900, output.height)
            assertEquals(Color.CYAN, output.getPixel(300, 450))
            assertEquals(Color.CYAN, original.getPixel(0, 0))
        } finally {
            original.recycle()
            output.recycle()
        }
    }

    @Test
    fun `book shaped covers keep their foreground unchanged`() {
        val original = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.MAGENTA)
        val output = renderCoverBackground(original)
        try {
            assertEquals(120, output.width)
            assertEquals(180, output.height)
            assertEquals(Color.MAGENTA, output.getPixel(0, 0))
            assertEquals(Color.MAGENTA, output.getPixel(119, 179))
        } finally {
            original.recycle()
            output.recycle()
        }
    }
}
