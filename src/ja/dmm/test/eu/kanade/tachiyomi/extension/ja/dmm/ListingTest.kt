package eu.kanade.tachiyomi.extension.ja.dmm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingTest {
    @Test
    fun `popular listings request thirty books in the existing ranking order`() {
        val url = listingRequestUrl("https://book.dmm.com", 1, "ranking")
        assertEquals("book.dmm.com", url.host)
        assertEquals("/list/", url.encodedPath)
        assertEquals(setOf("sort", "limit"), url.queryParameterNames)
        assertEquals("ranking", url.queryParameter("sort"))
        assertEquals("30", url.queryParameter("limit"))
        assertNull(url.queryParameter("page"))
    }

    @Test
    fun `latest listings keep the date order and page size on both hosts`() {
        for (host in listOf("book.dmm.com", "book.dmm.co.jp")) {
            val url = listingRequestUrl("https://$host", 1, "date")
            assertEquals(host, url.host)
            assertEquals("/list/", url.encodedPath)
            assertEquals("date", url.queryParameter("sort"))
            assertEquals("30", url.queryParameter("limit"))
        }
    }

    @Test
    fun `subsequent listing pages retain the smaller limit for both orders and hosts`() {
        for (host in listOf("book.dmm.com", "book.dmm.co.jp")) {
            for (sort in listOf("ranking", "date")) {
                val url = listingRequestUrl("https://$host", 2, sort)
                assertEquals(host, url.host)
                assertEquals(sort, url.queryParameter("sort"))
                assertEquals("30", url.queryParameter("limit"))
                assertEquals("2", url.queryParameter("page"))
            }
        }
    }

    @Test
    fun `search requests thirty results and preserves query encoding and pagination`() {
        for (host in listOf("book.dmm.com", "book.dmm.co.jp")) {
            for (page in listOf(1, 2)) {
                val url = searchRequestUrl("https://$host", page, "作品名 & 特装版")
                assertEquals(host, url.host)
                assertEquals("/search/", url.encodedPath)
                assertEquals(setOf("searchstr", "limit", "page"), url.queryParameterNames)
                assertEquals("作品名 & 特装版", url.queryParameter("searchstr"))
                assertEquals("30", url.queryParameter("limit"))
                assertEquals(page.toString(), url.queryParameter("page"))
            }
        }
    }
}
