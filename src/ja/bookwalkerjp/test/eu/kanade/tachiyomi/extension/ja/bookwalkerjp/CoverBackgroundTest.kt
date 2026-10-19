package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import keiyoushi.lib.coverbackground.CoverBackgroundInterceptor
import keiyoushi.lib.coverbackground.withCoverBackground
import keiyoushi.lib.publus.PublusInterceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverBackgroundTest {
    @Test
    fun `marked covers restore the legacy fragment before falling back`() {
        val legacyUrl = "https://rimg.bookwalker.jp/8866361/cover.jpg#original-fragment"
        val coverUrl = legacyUrl.getHiResCoverFromLegacyUrl()!!
        val requests = mutableListOf<Request>()
        val client = client(requests)
        val request = Request.Builder()
            .url(coverUrl.withCoverBackground()!!)
            .header("Referer", "https://bookwalker.jp/")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(404, response.code)
            assertEquals(legacyUrl, response.request.url.toString())
        }
        assertEquals(listOf(coverUrl, legacyUrl), requests.map { it.url.toString() })
        assertEquals(legacyUrl, requests.first().url.fragment)
        assertEquals(listOf("https://bookwalker.jp/", "https://bookwalker.jp/"), requests.map { it.header("Referer") })
    }

    @Test
    fun `unmarked chapter image fragments pass through unchanged without cover fallback`() {
        val imageUrl = "https://viewer.bookwalker.jp/pages/1.jpg#reader-metadata"
        val requests = mutableListOf<Request>()
        client(requests).newCall(Request.Builder().url(imageUrl).build()).execute().close()

        assertEquals(listOf(imageUrl), requests.map { it.url.toString() })
    }

    @Test
    fun `marked covers without a fallback do not retry`() {
        val coverUrl = "https://c.bookwalker.jp/coverImage_123.jpg"
        val requests = mutableListOf<Request>()
        client(requests).newCall(Request.Builder().url(coverUrl.withCoverBackground()!!).build()).execute().close()

        assertEquals(listOf(coverUrl), requests.map { it.url.toString() })
    }

    private fun client(requests: MutableList<Request>) = OkHttpClient.Builder()
        .addInterceptor(CoverBackgroundInterceptor())
        .addInterceptor(PublusInterceptor())
        .addInterceptor(BookWalkerInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            requests += request
            // An error response exercises URL routing without Android bitmap decoding.
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("missing".toResponseBody())
                .build()
        }
        .build()
}
