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
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CoverBackgroundTest {
    @Test
    fun `cover failures do not retry a legacy fallback fragment`() {
        val coverUrl = "https://c.bookwalker.jp/coverImage_1636687.jpg#https://rimg.bookwalker.jp/8866361/cover.jpg"
        val requests = mutableListOf<Request>()
        val client = client(requests)
        val request = Request.Builder()
            .url(coverUrl.withCoverBackground()!!)
            .header("Referer", "https://bookwalker.jp/")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(404, response.code)
            assertEquals(coverUrl, response.request.url.toString())
        }
        assertEquals(listOf(coverUrl), requests.map { it.url.toString() })
        assertEquals("https://bookwalker.jp/", requests.single().header("Referer"))
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

    @Test
    fun `purchased library authentication errors still prompt a login`() {
        val requests = mutableListOf<Request>()
        val error = assertThrows(IOException::class.java) {
            client(requests, 401).newCall(
                Request.Builder().url("https://bookwalker.jp/prx/holdBooks-api/hold-book-list/").build(),
            ).execute().close()
        }
        assertEquals("購入済みの書籍を表示するには、WebViewでログインしてください。", error.message)
        assertEquals(1, requests.size)
    }

    @Test
    fun `expired login redirects still prompt a login`() {
        val requests = mutableListOf<Request>()
        val error = assertThrows(IOException::class.java) {
            client(requests).newCall(Request.Builder().url("https://bookwalker.jp/app/03/login").build()).execute()
                .close()
        }
        assertEquals("ログインの有効期限が切れました。WebViewで再度ログインしてください。", error.message)
        assertEquals(1, requests.size)
    }

    private fun client(requests: MutableList<Request>, code: Int = 404) = OkHttpClient.Builder()
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
                .code(code)
                .message("Not Found")
                .body("missing".toResponseBody())
                .build()
        }
        .build()
}
