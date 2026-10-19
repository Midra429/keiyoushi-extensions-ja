package keiyoushi.lib.coverbackground

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterceptorTest {
    @Test
    fun `only marked covers are transformed and stale image headers are replaced`() {
        val original = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.RED)
        val input = Buffer()
        original.compress(Bitmap.CompressFormat.PNG, 100, input.outputStream())
        original.recycle()
        val body = TrackingBody(input)
        var fetchedUrl = ""
        val client = client { request ->
            fetchedUrl = request.url.toString()
            response(request, body)
                .newBuilder()
                .header("Content-Type", "image/jpeg")
                .header("Content-Length", "1")
                .header("ETag", "original")
                .header("Content-MD5", "original")
                .build()
        }
        val url = "https://example.com/cover.png?width=120"
        client.newCall(Request.Builder().url(url.withCoverBackground()!!).build()).execute().use { result ->
            assertEquals(url, fetchedUrl)
            assertTrue(body.closed)
            assertEquals("image/png", result.header("Content-Type"))
            assertEquals(result.body.contentLength().toString(), result.header("Content-Length"))
            assertNull(result.header("ETag"))
            assertNull(result.header("Content-MD5"))
            val cover = BitmapFactory.decodeStream(result.body.byteStream())!!
            try {
                assertEquals(120, cover.width)
                assertEquals(180, cover.height)
                assertTrue(Color.red(cover.getPixel(60, 90)) > 240)
                assertTrue(Color.red(cover.getPixel(60, 5)) in 1..200)
            } finally {
                cover.recycle()
            }
        }
    }

    @Test
    fun `small cover foregrounds survive encoding without additional compression loss`() {
        val width = 140
        val height = 190
        val pixels = IntArray(width * height) { Color.rgb(it * 17 % 256, it * 31 % 256, it * 47 % 256) }
        val original = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val input = Buffer()
        original.compress(Bitmap.CompressFormat.PNG, 100, input.outputStream())
        original.recycle()
        val client =
            client { request -> response(request, input.asResponseBody("image/png".toMediaType(), input.size)) }
        client.newCall(Request.Builder().url("https://example.com/native-cover".withCoverBackground()!!).build())
            .execute().use {
                val cover = BitmapFactory.decodeStream(it.body.byteStream())!!
                try {
                    val actual = IntArray(pixels.size)
                    cover.getPixels(actual, 0, width, 0, 10, width, height)
                    assertArrayEquals("Encoding must preserve the decoded foreground pixels", pixels, actual)
                    assertEquals("image/png", it.header("Content-Type"))
                    assertEquals("image/png", it.body.contentType().toString())
                } finally {
                    cover.recycle()
                }
            }
    }

    @Test
    fun `unmarked page bytes and fragments pass through unchanged`() {
        val body = "not even an image".toResponseBody()
        val url = "https://example.com/page.jpg#reader-key"
        val client = client { request ->
            assertEquals(url, request.url.toString())
            response(request, body)
        }
        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertSame(body, it.body)
            assertEquals("not even an image", it.body.string())
        }
    }

    @Test
    fun `HTTP errors are passed back without consuming the body`() {
        val body = TrackingBody(Buffer().writeUtf8("missing"))
        val client = client { request -> response(request, body).newBuilder().code(404).build() }
        client.newCall(Request.Builder().url("https://example.com/missing".withCoverBackground()!!).build()).execute()
            .use {
                assertEquals(404, it.code)
                assertSame(body, it.body)
                assertFalse(body.closed)
                assertEquals("missing", it.body.string())
            }
        assertTrue(body.closed)
    }

    @Test
    fun `decoding failures close the original response`() {
        val body = TrackingBody(Buffer().writeUtf8("invalid image"))
        val client = client { request -> response(request, body) }
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("https://example.com/broken".withCoverBackground()!!).build())
                .execute().close()
        }
        assertTrue(body.closed)
    }

    @Test
    fun `large image downloads are sampled before rendering`() {
        val original = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.BLUE)
        val input = Buffer()
        original.compress(Bitmap.CompressFormat.JPEG, 90, input.outputStream())
        original.recycle()
        val client =
            client { request -> response(request, input.asResponseBody("image/jpeg".toMediaType(), input.size)) }
        client.newCall(Request.Builder().url("https://example.com/large".withCoverBackground()!!).build()).execute()
            .use {
                val cover = BitmapFactory.decodeStream(it.body.byteStream())!!
                try {
                    assertEquals(600, cover.width)
                    assertEquals(900, cover.height)
                    assertTrue(Color.blue(cover.getPixel(300, 450)) > 240)
                } finally {
                    cover.recycle()
                }
            }
    }

    private fun client(serve: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(CoverBackgroundInterceptor())
        .addInterceptor { chain -> serve(chain.request()) }
        .build()

    private fun response(request: Request, body: ResponseBody) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

    private class TrackingBody(buffer: Buffer) : ResponseBody() {
        var closed = false
        private val size = buffer.size
        private val trackedSource = object : ForwardingSource(buffer) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        override fun contentType() = "image/png".toMediaType()
        override fun contentLength() = size
        override fun source(): BufferedSource = trackedSource
    }
}
