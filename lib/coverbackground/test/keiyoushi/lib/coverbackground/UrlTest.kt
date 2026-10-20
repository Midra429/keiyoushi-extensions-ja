package keiyoushi.lib.coverbackground

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlTest {
    @Test
    fun `null empty and invalid optional covers are unchanged`() {
        assertNull(null.withCoverBackground())
        assertEquals("", "".withCoverBackground())
        assertEquals("not a URL", "not a URL".withCoverBackground())
    }

    @Test
    fun `marking is idempotent and changes the apps cover cache key`() {
        val original = "https://example.com/cover.jpg?size=600"
        val marked = original.withCoverBackground()
        assertNotEquals(original, marked)
        assertEquals(marked, marked.withCoverBackground())
    }

    @Test
    fun `previously marked covers get a new cache key without nesting markers`() {
        for (fragment in listOf("", "https://example.com/fallback.jpg?x=1&y=2#inner")) {
            for (version in listOf(1, 2, 3)) {
                val original = "https://example.com/cover.jpg#keiyoushi-cover-v$version:$fragment"
                val marked = original.withCoverBackground()
                assertEquals("https://example.com/cover.jpg#keiyoushi-cover-v4:$fragment", marked)
                assertEquals(marked, marked.withCoverBackground())
                assertNotEquals(original, marked)
            }
        }
    }

    @Test
    fun `original URL fragments and request headers are restored for downstream interceptors`() {
        for (url in listOf(
            "https://example.com/cover.jpg",
            "https://example.com/cover.jpg#https://example.com/fallback.jpg?x=1&y=2#inner",
            "https://example.com/cover.jpg#reader metadata 日本語",
        )) {
            val client = OkHttpClient.Builder()
                .addInterceptor(CoverBackgroundInterceptor())
                .addInterceptor { chain ->
                    val request = chain.request()
                    assertEquals(Request.Builder().url(url).build().url, request.url)
                    assertEquals("keep me", request.header("Referer"))
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("missing".toResponseBody())
                        .build()
                }
                .build()
            val marked = url.withCoverBackground()!!
            for (cover in listOf(
                marked,
                marked.replace("#keiyoushi-cover-v4:", "#keiyoushi-cover-v3:"),
                marked.replace("#keiyoushi-cover-v4:", "#keiyoushi-cover-v2:"),
                marked.replace("#keiyoushi-cover-v4:", "#keiyoushi-cover-v1:"),
            )) {
                client.newCall(Request.Builder().url(cover).header("Referer", "keep me").build())
                    .execute().close()
            }
        }
    }
}
