package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import keiyoushi.lib.coverbackground.withCoverBackground
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingTest {
    private val baseUrl = "https://bookwalker.jp"
    private val cover = "https://rimg.bookwalker.jp/8866361/eUnObgIVNjRTJtVUNQrbaQ__.jpg"

    @Test
    fun `desktop listings use high resolution covers without changing the document order`() {
        val document = Jsoup.parse(
            """
            <ul>
                <li class="m-tile">
                    <a class="m-thumb__image"><img data-original="$cover"></a>
                    <a class="m-book-item__title" href="/series/47597/list/">忍空～SECOND STAGE 干支忍編～</a>
                </li>
                <li class="m-tile">
                    <a class="m-thumb__image"><img data-original="/thumbnailImage_123.jpg"></a>
                    <a class="m-book-item__title" href="/de48f2f317-3551-47b3-93a2-152211fafa60/">単独の書籍</a>
                </li>
            </ul>
            """.trimIndent(),
            baseUrl,
        )
        val tiles = document.select(".m-tile")

        assertEquals(
            listOf(
                "https://c.bookwalker.jp/coverImage_1636687.jpg#$cover".withCoverBackground(),
                "https://c.bookwalker.jp/coverImage_122.jpg#$baseUrl/thumbnailImage_123.jpg".withCoverBackground(),
            ),
            tiles.map { it.listingCoverUrl() },
        )
        assertEquals(
            listOf("/series/47597/list/", "/de48f2f317-3551-47b3-93a2-152211fafa60/"),
            tiles.map { it.selectFirst("a.m-book-item__title")!!.attr("href") },
        )
    }

    @Test
    fun `mobile series listings use high resolution covers`() {
        val tile = Jsoup.parse(
            """<div class="o-tile--series"><div class="o-tile-book-img"><img data-original="$cover"></div></div>""",
            baseUrl,
        ).selectFirst(".o-tile--series")!!

        assertEquals(
            "https://c.bookwalker.jp/coverImage_1636687.jpg#$cover".withCoverBackground(),
            tile.listingCoverUrl()
        )
    }

    @Test
    fun `unrecognized cover ids preserve the original URL`() {
        val url = "https://rimg.bookwalker.jp/2199dd559dc592b90d4c861f8f7fd7b/eUnObgIVNjRTJtVUNQrbaQ__.jpg"
        assertEquals(url, url.getHiResCoverFromLegacyUrl())
    }

    @Test
    fun `an existing full size cover is not rewritten`() {
        val url = "https://c.bookwalker.jp/coverImage_1636687.jpg#$cover"
        assertEquals(url, url.getHiResCoverFromLegacyUrl())
    }

    @Test
    fun `legacy thumbnails retain the original URL for the interceptor fallback`() {
        val url = "https://c.bookwalker.jp/thumbnailImage_1636688.jpg"
        assertEquals("https://c.bookwalker.jp/coverImage_1636687.jpg#$url", url.getHiResCoverFromLegacyUrl())
    }

    @Test
    fun `a missing thumbnail does not become an image request`() {
        for (image in listOf("", "<img>")) {
            val tile = Jsoup.parse(
                """<li class="m-tile"><a class="m-thumb__image">$image</a></li>""",
                baseUrl,
            ).selectFirst(".m-tile")!!

            assertNull(tile.listingCoverUrl())
        }
    }
}
