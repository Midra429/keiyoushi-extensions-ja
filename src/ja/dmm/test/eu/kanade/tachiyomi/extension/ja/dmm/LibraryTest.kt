package eu.kanade.tachiyomi.extension.ja.dmm

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LibraryTest {
    private fun book(seriesId: String, contentId: String, title: String) = """
        <div data-testid="purchasable-library-book">
            <img src="/covers/$contentId.jpg">
            <span data-is-short-title="true"><a href="/product/999/other/">省略された別タイトル</a></span>
            <span data-is-short-title="false"><a href="/product/$seriesId/$contentId/">$title</a></span>
        </div>
    """.trimIndent()

    @Test
    fun `rendered purchased books keep series identity and full title`() {
        val page = Jsoup.parse(book("123", "book001", "購入済み作品 1"), "https://book.dmm.com/library/").parseLibraryPage()
        assertEquals(1, page.books.size)
        assertEquals("123", page.books.single().url)
        assertEquals("購入済み作品 1", page.books.single().title)
        assertEquals("https://book.dmm.com/covers/book001.jpg", page.books.single().thumbnailUrl)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `FANZA uses its own host when resolving book links`() {
        val page = Jsoup.parse(book("456", "adult001", "購入済み作品"), "https://book.dmm.co.jp/library/").parseLibraryPage()
        assertEquals("456", page.books.single().url)
        assertEquals("https://book.dmm.co.jp/covers/adult001.jpg", page.books.single().thumbnailUrl)
    }

    @Test
    fun `multiple purchased volumes are grouped by series`() {
        val page = Jsoup.parse(
            book("123", "book001", "作品 1") + book("123", "book002", "作品 2") + book("456", "book003", "別作品"),
            "https://book.dmm.com/library/",
        ).parseLibraryPage()
        assertEquals(listOf("123", "456"), page.books.map { it.url })
    }

    @Test
    fun `a next page is detected in the rendered page`() {
        val page = Jsoup.parse(
            book("123", "book001", "作品") + """<a href="?page=2">＞</a>""",
            "https://book.dmm.com/library/?page=1",
        ).parseLibraryPage()
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `an explicit empty shelf is not a loading error`() {
        val page = Jsoup.parse("<p>購入済みの作品がありません</p>").parseLibraryPage()
        assertTrue(page.books.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test(expected = IOException::class)
    fun `an initial SPA shell must not be treated as a completed empty shelf`() {
        Jsoup.parse("<html><body><div id='root'></div><script src='/library.js'></script></body></html>").parseLibraryPage()
    }

    @Test
    fun `a text product link is usable without the full title span`() {
        val page = Jsoup.parse(
            """
            <div data-testid="purchasable-library-book">
                <a href="/product/123/book001/"><img src="/cover.jpg"></a>
                <a href="/product/123/book001/">購入済み作品</a>
            </div>
            """.trimIndent(),
            "https://book.dmm.com/library/",
        ).parseLibraryPage()
        assertEquals("123", page.books.single().url)
        assertEquals("購入済み作品", page.books.single().title)
    }

    @Test
    fun `an empty full title link must not hide a usable text link`() {
        val page = Jsoup.parse(
            """
            <div data-testid="purchasable-library-book">
                <span data-is-short-title="false"><a href="/product/123/book001/"></a></span>
                <a href="/product/123/book001/">購入済み作品</a>
            </div>
            """.trimIndent(),
            "https://book.dmm.com/library/",
        ).parseLibraryPage()
        assertEquals("購入済み作品", page.books.single().title)
    }

    @Test(expected = IOException::class)
    fun `an incomplete book item gives a descriptive parsing error instead of a null pointer`() {
        Jsoup.parse(
            """<div data-testid="purchasable-library-book"><a href="/product/123/book001/"><img src="/cover.jpg"></a></div>""",
            "https://book.dmm.com/library/",
        ).parseLibraryPage()
    }
}
