package eu.kanade.tachiyomi.extension.ja.dmm

import keiyoushi.lib.coverbackground.withCoverBackground
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverBackgroundTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `details mark the large cover without changing its query`() {
        val cover = "https://example.com/cover.webp?size=large"
        assertEquals(cover.withCoverBackground(), details("\"$cover\"").toSManga().thumbnail_url)
    }

    @Test
    fun `details without a cover do not create an image URL`() {
        assertNull(details("null").toSManga().thumbnail_url)
    }

    private fun details(cover: String) = json.decodeFromString<DetailsResponse>(
        """
        {
            "series": {"title": "作品", "status": null},
            "volumes": {
                "image_urls": {"pl": $cover},
                "author": null,
                "genre": null,
                "synopsis": null,
                "publisher": null,
                "label": null,
                "category": null
            }
        }
        """.trimIndent(),
    )
}
