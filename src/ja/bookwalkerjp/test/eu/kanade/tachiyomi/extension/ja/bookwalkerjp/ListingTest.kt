package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import keiyoushi.lib.coverbackground.withCoverBackground
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingTest {
    private val baseUrl = "https://bookwalker.jp"
    private val cover = "https://rimg.bookwalker.jp/8866361/uafXIf56W6UsswyZ4GK03g__.jpg"
    private val hashCover = "https://rimg.bookwalker.jp/2199dd559dc592b90d4c861f8f7fd7b/uafXIf56W6UsswyZ4GK03g__.jpg"

    @Test
    fun `popular and latest URLs explicitly request detailed listings on every page`() {
        for (order in listOf("rank", "release")) {
            for (page in listOf(1, 2)) {
                assertEquals(
                    "$baseUrl/category/2/?order=$order&detail=1&page=$page",
                    categoryListingUrl(baseUrl, order, page).toString(),
                )
            }
        }
    }

    @Test
    fun `detailed listings preserve the sites images titles identities and order`() {
        val document = Jsoup.parse(
            """
            <div class="o-card-list">
                <div class="js-item o-card-wrap">
                    <div class="o-book-image image"><a class="m-thumb__image"><img data-original="$cover"></a></div>
                    <h2 class="o-card-ttl"><a href="/series/47597/list/">
                        <span class="a-tag-comic">マンガ</span>
                        <span class="o-card-ttl__text">忍空～SECOND STAGE 干支忍編～</span>
                    </a></h2>
                </div>
                <div class="js-item o-card-wrap">
                    <a class="m-thumb__image"><img data-original="$hashCover"></a>
                    <h2 class="o-card-ttl"><a href="/series/123/list/">
                        <span class="a-tag-comic">マンガ</span>
                        <span class="o-card-ttl__text">まんがで読破（コルク）</span>
                    </a></h2>
                </div>
                <div class="js-item o-card-wrap">
                    <a class="m-thumb__image"><img data-original="/images/book.jpg"></a>
                    <h2 class="o-card-ttl"><a href="/de48f2f317-3551-47b3-93a2-152211fafa60/">
                        <span class="a-tag-comic">マンガ</span>
                        <span class="o-card-ttl__text">単独の書籍 &amp; 特装版</span>
                    </a></h2>
                </div>
            </div>
            <div class="o-pager-next"><a href="?order=rank&amp;detail=1&amp;page=2">次へ</a></div>
            """.trimIndent(),
            baseUrl,
        )
        val before = document.outerHtml()
        val result = document.toMangasPage()

        assertEquals(listOf("47597", "123", "de48f2f317-3551-47b3-93a2-152211fafa60"), result.mangas.map { it.url })
        assertEquals(
            listOf("忍空～SECOND STAGE 干支忍編～", "まんがで読破（コルク）", "単独の書籍 & 特装版"),
            result.mangas.map { it.title },
        )
        assertEquals(
            listOf(cover, hashCover, "$baseUrl/images/book.jpg").map { it.withCoverBackground() },
            result.mangas.map { it.thumbnail_url },
        )
        assertTrue(result.hasNextPage)
        assertEquals(before, document.outerHtml())
    }

    @Test
    fun `compact search listings still use their original image URLs`() {
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
        val result = document.toMangasPage()
        assertEquals(listOf("47597", "de48f2f317-3551-47b3-93a2-152211fafa60"), result.mangas.map { it.url })
        assertEquals(
            listOf(cover.withCoverBackground(), "$baseUrl/thumbnailImage_123.jpg".withCoverBackground()),
            result.mangas.map { it.thumbnail_url },
        )
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `mobile series listings keep their original covers`() {
        val document = Jsoup.parse(
            """
            <div class="o-tile--series">
                <div class="o-tile-book-img"><img data-original="$hashCover"></div>
                <h2 class="o-tile-ttl"><a href="/series/123/list/">まんがで読破（コルク）</a></h2>
            </div>
            """.trimIndent(),
            baseUrl,
        )
        val manga = document.toMangasPage().mangas.single()
        assertEquals("123", manga.url)
        assertEquals("まんがで読破（コルク）", manga.title)
        assertEquals(hashCover.withCoverBackground(), manga.thumbnail_url)
    }

    @Test
    fun `the last page does not follow hidden next page links`() {
        val document = Jsoup.parse(
            """<div class="o-pager-next"><a class="o-pager-box-btn_hidden" href="?page=3">次へ</a></div>""",
            baseUrl,
        )
        assertFalse(document.hasNextPage())
    }

    @Test
    fun `a missing thumbnail does not become an image request`() {
        for (image in listOf("", "<img>", """<img data-original="">""")) {
            val tile = Jsoup.parse(
                """<li class="m-tile"><a class="m-thumb__image">$image</a></li>""",
                baseUrl,
            ).selectFirst(".m-tile")!!
            assertNull(tile.listingCoverUrl())
        }
    }
}
