package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandaloneBookTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val bookUrl = "https://bookwalker.jp/de48f2f317-3551-47b3-93a2-152211fafa60/"
    private val details get() = fixture("standalone-details.json")

    private fun fixture(name: String): DetailsResponse = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use {
        json.decodeFromString<List<DetailsResponse>>(it.readText()).single()
    }

    @Test
    fun `a standalone book retains its uuid and product title when series fields are null`() {
        assertNull(details.seriesId)
        assertEquals("de48f2f317-3551-47b3-93a2-152211fafa60", details.mangaUrl)
        assertEquals("超かぐや姫！ 公式ガイドブック ハッピーエンドのその先へ！", details.mangaTitle)
    }

    @Test
    fun `a series book retains its series identity`() {
        val series = fixture("series-details.json")
        assertEquals(12466, series.seriesId)
        assertEquals("12466", series.mangaUrl)
        assertEquals("キングダム（ヤングジャンプコミックスDIGITAL）", series.mangaTitle)
    }

    @Test
    fun `the standalone book page provides one trial chapter with the correct uuid`() {
        val document = javaClass.getResourceAsStream("/standalone-buttons.html")!!.bufferedReader().use {
            Jsoup.parse("<div id='js-read-check'>${it.readText()}</div>", bookUrl)
        }
        val chapter = document.toStandaloneChapter(details, hideLocked = false, hideFree = true)!!
        assertEquals("🔒 （試し読み） ${details.mangaTitle}", chapter.name)
        assertEquals("48f2f317-3551-47b3-93a2-152211fafa60", chapter.uuid)
        assertEquals("${bookUrl}?sample=1", chapter.viewerUrl)
        assertNull(document.toStandaloneChapter(details, hideLocked = true, hideFree = true))
    }

    @Test
    fun `purchased books use the full reader link even when a trial link is present`() {
        val document = Jsoup.parse(
            """
            <div id="js-read-check">
                <div id="js-read-check-book-cover-main-button">
                    <div class="t-c-read-trial-button"><a href="?sample=1">試し読み</a></div>
                </div>
                <div class="t-c-product-read-action">
                    <div class="t-c-read-button"><a href="https://viewer.bookwalker.jp/purchased-reader">読む</a></div>
                </div>
            </div>
            """.trimIndent(),
            bookUrl,
        )
        val chapter = document.toStandaloneChapter(details, hideLocked = true, hideFree = true)!!
        assertEquals(details.mangaTitle, chapter.name)
        assertEquals("https://viewer.bookwalker.jp/purchased-reader", chapter.viewerUrl)
    }

    @Test
    fun `the free chapter preference applies to standalone books`() {
        val document = Jsoup.parse(
            """<div id="js-read-check"><div id="js-read-check-book-cover-main-button"><div class="t-c-read-free-button"><a href="?sample=2">無料で読む</a></div></div></div>""",
            bookUrl,
        )
        assertNull(document.toStandaloneChapter(details, hideLocked = false, hideFree = true))
        val chapter = document.toStandaloneChapter(details, hideLocked = false, hideFree = false)!!
        assertEquals(details.mangaTitle, chapter.name)
        assertEquals("${bookUrl}?sample=2", chapter.viewerUrl)
    }

    @Test
    fun `a book without a reader is locked and keeps its real book url`() {
        val document = Jsoup.parse("<div id='js-read-check'><div id='js-read-check-book-cover-main-button'></div></div>", bookUrl)
        assertNull(document.toStandaloneChapter(details, hideLocked = true, hideFree = true))
        val chapter = document.toStandaloneChapter(details, hideLocked = false, hideFree = true)!!
        assertEquals("🔒 ${details.mangaTitle}", chapter.name)
        assertEquals(bookUrl, chapter.viewerUrl)
    }

    @Test
    fun `the browser reader is used instead of an app deeplink`() {
        val document = Jsoup.parse(
            """
            <div id="js-read-check"><div class="t-c-product-read-action"><div class="t-c-read-button">
                <a href="bookwalker://reader/open">アプリで読む</a>
                <a href="https://viewer.bookwalker.jp/full-reader">ブラウザで読む</a>
            </div></div></div>
            """.trimIndent(),
            bookUrl,
        )
        val chapter = document.toStandaloneChapter(details, hideLocked = true, hideFree = true)!!
        assertEquals(details.mangaTitle, chapter.name)
        assertEquals("https://viewer.bookwalker.jp/full-reader", chapter.viewerUrl)
    }

    @Test
    fun `an explicit browser button takes priority over an app launcher using https`() {
        val document = Jsoup.parse(
            """
            <div id="js-read-check"><div class="t-c-product-read-action"><div class="t-c-read-button">
                <a href="https://member.bookwalker.jp/app-launcher">アプリで読む</a>
                <a href="https://viewer.bookwalker.jp/full-reader">ブラウザで読む</a>
            </div></div></div>
            """.trimIndent(),
            bookUrl,
        )
        val chapter = document.toStandaloneChapter(details, hideLocked = true, hideFree = true)!!
        assertEquals("https://viewer.bookwalker.jp/full-reader", chapter.viewerUrl)
    }

    @Test
    fun `reader buttons for another book cannot unlock the current book`() {
        val document = Jsoup.parse(
            """
            <div id="js-read-check"><div id="js-read-check-book-cover-main-button">
                <div class="t-c-read-trial-button"><a href="?sample=1">試し読み</a></div>
            </div></div>
            <div class="t-c-product-read-action"><div class="t-c-read-button"><a href="https://viewer.bookwalker.jp/other-book">読む</a></div></div>
            """.trimIndent(),
            bookUrl,
        )
        assertNull(document.toStandaloneChapter(details, hideLocked = true, hideFree = true))
        assertEquals("${bookUrl}?sample=1", document.toStandaloneChapter(details, hideLocked = false, hideFree = true)!!.viewerUrl)
    }
}
