package eu.kanade.tachiyomi.extension.ja.dmm

import keiyoushi.lib.coverbackground.withCoverBackground
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun book(seriesId: String = "123", shopName: String = "general", title: String = "購入済み作品") = """
        {
            "shop_name": "$shopName",
            "series_id": "$seriesId",
            "content_id": "sample001",
            "title": "$title",
            "image_urls": {
                "pt": "https://example.com/cover-small.webp",
                "ps": "https://example.com/cover-medium.webp",
                "pl": "https://example.com/cover-large.webp"
            },
            "author": [{"id": "789", "name": "架空の著者", "is_valid": true}],
            "actor": null,
            "related_series": null,
            "remaining_seconds": null,
            "is_rental": false,
            "product_url": "https://book.dmm.com/product/$seriesId/sample001/"
        }
    """.trimIndent()

    private fun response(books: String, page: Int = 1, perPage: Int = 20, totalCount: Int = 1) =
        json.decodeFromString<LibraryResponse>(
            """
            {
                "series_books": [$books],
                "pager": {"page": $page, "per_page": $perPage, "total_count": $totalCount}
            }
            """.trimIndent(),
        )

    @Test
    fun `DMM requests the general library API with the observed shelf options`() {
        val url = libraryRequestUrl("https://book.dmm.com", "general", 1)
        assertEquals("https", url.scheme)
        assertEquals("book.dmm.com", url.host)
        assertEquals("/ajax/bff/library/", url.encodedPath)
        assertEquals(setOf("shop_name", "page", "order", "show_expired", "format_webp"), url.queryParameterNames)
        assertEquals("general", url.queryParameter("shop_name"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("added_desc", url.queryParameter("order"))
        assertEquals("0", url.queryParameter("show_expired"))
        assertEquals("1", url.queryParameter("format_webp"))
    }

    @Test
    fun `FANZA requests its own host and the adult shop on subsequent pages`() {
        val url = libraryRequestUrl("https://book.dmm.co.jp", "adult", 2)
        assertEquals("book.dmm.co.jp", url.host)
        assertEquals("/ajax/bff/library/", url.encodedPath)
        assertEquals("adult", url.queryParameter("shop_name"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun `a purchased series keeps its string ID full title and large cover`() {
        val page = response(book(title = "購入済み作品【特装版】"))
        val manga = page.booksForShop("general").single()
        assertEquals("123", manga.url)
        assertEquals("購入済み作品【特装版】", manga.title)
        assertEquals("https://example.com/cover-large.webp", manga.thumbnailUrl)
        assertEquals("https://example.com/cover-large.webp".withCoverBackground(), manga.toSManga().thumbnail_url)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `DMM excludes FANZA books even when the response contains both shops`() {
        val page = response(
            book("123", "general") + "," + book("456", "adult"),
            totalCount = 2,
        )
        assertEquals(listOf("123"), page.booksForShop("general").map { it.url })
    }

    @Test
    fun `FANZA excludes general books even when product URLs use the DMM host`() {
        val page = response(
            book("123", "general") + "," + book("456", "adult"),
            totalCount = 2,
        )
        assertEquals(listOf("456"), page.booksForShop("adult").map { it.url })
    }

    @Test
    fun `duplicate entries in a series are shown once`() {
        val page = response(book() + "," + book(), totalCount = 2)
        assertEquals(1, page.booksForShop("general").size)
    }

    @Test
    fun `pagination uses API totals rather than the number of parsed books`() {
        val page = response(book(), totalCount = 21)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `a full last page has no next page`() {
        val page = response(book(), page = 2, totalCount = 40)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `a partial last page has no next page`() {
        val page = response(book(), page = 2, totalCount = 21)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `an empty purchased library is returned without a rendering wait`() {
        val page = response("", totalCount = 0)
        assertTrue(page.booksForShop("general").isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `a book without cover data still retains its ID and title`() {
        val page = response(
            """{"shop_name":"general","series_id":"123","title":"購入済み作品","image_urls":null}""",
        )
        val book = page.booksForShop("general").single()
        assertEquals("123", book.url)
        assertEquals("購入済み作品", book.title)
        assertNull(book.thumbnailUrl)
        assertNull(book.toSManga().thumbnail_url)
    }

    @Test(expected = SerializationException::class)
    fun `a missing series ID is not replaced with a content ID or a fake URL`() {
        response(book().replace("\"series_id\": \"123\",", ""))
    }

    @Test(expected = SerializationException::class)
    fun `a missing title fails instead of creating an unnamed book`() {
        response(book().replace("\"title\": \"購入済み作品\",", ""))
    }

    @Test(expected = SerializationException::class)
    fun `an API error is not decoded as an empty purchased library`() {
        json.decodeFromString<LibraryResponse>("""{"code":"AUTHENTICATION_ERROR","status":401,"message":"Login is required."}""")
    }
}
