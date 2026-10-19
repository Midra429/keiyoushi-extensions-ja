package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
        assertEquals(listOf("12466", "de48f2f317-3551-4000-8000-123456789abc"), response.holdBookList.entities.map { it.mangaUrl })
        assertEquals(listOf("キングダム", "単独の書籍"), response.holdBookList.entities.map { it.mangaTitle })
        assertEquals(2, response.holdBookList.totalPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed library item fails instead of creating a fake id`() {
        json.decodeFromString<Entity>("""{"seriesId":0,"imageUrl":null}""").mangaUrl
    }
}
