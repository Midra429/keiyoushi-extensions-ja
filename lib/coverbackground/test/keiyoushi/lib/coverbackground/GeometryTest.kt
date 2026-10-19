package keiyoushi.lib.coverbackground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    @Test
    fun `all cover shapes fit into bounded two by three canvases`() {
        for ((width, height) in listOf(
            600 to 600,
            600 to 900,
            900 to 300,
            200 to 1600,
            8000 to 8000,
            1 to 1,
            1 to 10000
        )) {
            val geometry = CoverGeometry(width, height)
            assertEquals(geometry.width * 3, geometry.height * 2)
            assertTrue(geometry.width in 2..600)
            assertTrue(geometry.height in 3..900)
            assertTrue(geometry.imageWidth <= geometry.width)
            assertTrue(geometry.imageHeight <= geometry.height)
            assertEquals(width.toFloat() / height, geometry.imageWidth / geometry.imageHeight, 0.0001f)
            assertTrue(geometry.imageWidth <= width)
            assertTrue(geometry.imageHeight <= height)
        }
    }

    @Test
    fun `blur preserves uniform colors including tiny backgrounds`() {
        for ((width, height) in listOf(48 to 72, 1 to 1, 1 to 8, 8 to 1)) {
            val pixels = IntArray(width * height) { 0xff80c020.toInt() }
            blurBackground(pixels, width, height)
            assertTrue(pixels.all { it == 0xff80c020.toInt() })
        }
    }

    @Test
    fun `blur softens contrast without mixing color channels`() {
        val pixels = IntArray(48 * 72) { index -> if (index % 48 < 24) 0xffff0000.toInt() else 0xff000000.toInt() }
        blurBackground(pixels, 48, 72)
        val center = pixels[36 * 48 + 24]
        assertTrue((center shr 16 and 0xff) in 1..254)
        assertEquals(0, center and 0xffff)
        assertEquals(0xffff0000.toInt(), pixels[36 * 48])
        assertEquals(0xff000000.toInt(), pixels[36 * 48 + 47])
    }
}
