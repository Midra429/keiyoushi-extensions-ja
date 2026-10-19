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
            """{"seriesId":0,"seriesName":"","productId":"12345678-abcd","title":"単独の書籍","imageUrl":null}""",
        )
        assertEquals("de12345678-abcd", entity.mangaUrl)
        assertEquals("単独の書籍", entity.mangaTitle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed library item fails instead of creating a fake id`() {
        json.decodeFromString<Entity>("""{"seriesId":0,"imageUrl":null}""").mangaUrl
    }
}
