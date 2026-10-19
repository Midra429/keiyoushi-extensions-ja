package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a series response keeps the series identity`() {
        val entity = json.decodeFromString<Entity>(
            """{"seriesId":12466,"seriesName":"キングダム","imageUrl":null}""",
        )
        assertEquals("12466", entity.mangaUrl)
        assertEquals("キングダム", entity.mangaTitle)
    }

    @Test
    fun `an ungrouped book is not turned into series zero`() {
        val entity = json.decodeFromString<Entity>(
            """{"seriesId":0,"seriesName":"","type":"normal","productId":3137659,"uuid":"48f2f317-3551-4000-8000-123456789abc","title":"単独の書籍","imageUrl":null}""",
        )
        assertEquals("de48f2f317-3551-4000-8000-123456789abc", entity.mangaUrl)
        assertEquals("単独の書籍", entity.mangaTitle)
    }

    @Test
    fun `a library page can mix series and books with numeric product ids`() {
        val response = json.decodeFromString<LibraryResponse>(
            """
            {
                "holdBookList": {
                    "entities": [
                        {"seriesId":12466,"seriesName":"キングダム","productId":1234567,"imageUrl":null},
                        {"type":"normal","productId":3137659,"uuid":"48f2f317-3551-4000-8000-123456789abc","title":"単独の書籍","imageUrl":null}
                    ],
                    "totalPage":2
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf("12466", "de48f2f317-3551-4000-8000-123456789abc"),
            response.holdBookList.entities.map { it.mangaUrl })
        assertEquals(listOf("キングダム", "単独の書籍"), response.holdBookList.entities.map { it.mangaTitle })
        assertEquals(2, response.holdBookList.totalPage)
    }

    @Test
    fun `purchased listings use high resolution covers for recognized thumbnail ids`() {
        val covers = listOf(
            "https://rimg.bookwalker.jp/8866361/eUnObgIVNjRTJtVUNQrbaQ__.jpg" to "https://c.bookwalker.jp/coverImage_1636687.jpg",
            "https://c.bookwalker.jp/thumbnailImage_1636688.jpg" to "https://c.bookwalker.jp/coverImage_1636687.jpg",
        )
        for ((cover, highResolutionCover) in covers) {
            val entity = json.decodeFromString<Entity>(
                """{"seriesId":12466,"seriesName":"キングダム","imageUrl":"$cover"}""",
            )
            assertEquals("12466", entity.mangaUrl)
            assertEquals("キングダム", entity.mangaTitle)
            assertEquals("$highResolutionCover#$cover", entity.mangaThumbnailUrl)
        }
    }

    @Test
    fun `purchased listings preserve thumbnails with unrecognized ids`() {
        val cover = "https://rimg.bookwalker.jp/2199dd559dc592b90d4c861f8f7fd7b/eUnObgIVNjRTJtVUNQrbaQ__.jpg"
        val entity = json.decodeFromString<Entity>(
            """{"seriesId":12466,"seriesName":"キングダム","imageUrl":"$cover"}""",
        )
        assertEquals(cover, entity.mangaThumbnailUrl)
    }

    @Test
    fun `purchased listings without a cover still preserve the book identity`() {
        for (cover in listOf("null", "\"\"")) {
            val entity = json.decodeFromString<Entity>(
                """{"uuid":"48f2f317-3551-4000-8000-123456789abc","title":"単独の書籍","imageUrl":$cover}""",
            )
            assertEquals("de48f2f317-3551-4000-8000-123456789abc", entity.mangaUrl)
            assertEquals("単独の書籍", entity.mangaTitle)
            assertNull(entity.mangaThumbnailUrl)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed library item fails instead of creating a fake id`() {
        json.decodeFromString<Entity>("""{"seriesId":0,"imageUrl":null}""").mangaUrl
    }
}
