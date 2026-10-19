package eu.kanade.tachiyomi.multisrc.gigaviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverUrlTest {
    private val cover = "https://example.com/series-1/123-cover.jpg?size=large"
    private val volumeCover = "https://example.com/volume.jpg"
    private val episode = Permalink("https://example.com/episode/456")

    @Test
    fun `default DTO mapping leaves covers unchanged for other sites`() {
        assertEquals(cover, listing(cover, volumeCover).toSManga()!!.thumbnail_url)
        assertEquals(cover, details(cover, volumeCover).toSManga().thumbnail_url)
    }

    @Test
    fun `cover hooks receive the selected series cover in both DTOs`() {
        val urls = mutableListOf<String?>()
        val transform: (String?) -> String? = {
            urls += it
            it?.plus("#test-cover-hook")
        }
        val listing = listing(cover, volumeCover).toSManga(transform)!!
        val details = details(cover, volumeCover).toSManga(transform)

        assertEquals(listOf(cover, cover), urls)
        assertEquals("$cover#test-cover-hook", listing.thumbnail_url)
        assertEquals(listing.thumbnail_url, details.thumbnail_url)
        assertEquals("123", listing.url)
        assertEquals(listing.url, details.url)
        assertEquals(listing.memo, details.memo)
    }

    @Test
    fun `cover hooks also receive the latest volume fallback`() {
        val transform: (String?) -> String? = { it?.plus("#test-cover-hook") }
        assertEquals("$volumeCover#test-cover-hook", listing(null, volumeCover).toSManga(transform)!!.thumbnail_url)
        assertEquals("$volumeCover#test-cover-hook", details(null, volumeCover).toSManga(transform).thumbnail_url)
        assertEquals(volumeCover, listing(null, volumeCover).toSManga()!!.thumbnail_url)
        assertEquals(volumeCover, details(null, volumeCover).toSManga().thumbnail_url)
    }

    @Test
    fun `missing covers remain nullable in both DTOs`() {
        val urls = mutableListOf<String?>()
        val transform: (String?) -> String? = {
            urls += it
            it
        }
        assertNull(listing(null, null).toSManga(transform)!!.thumbnail_url)
        assertNull(details(null, null).toSManga(transform).thumbnail_url)
        assertEquals(listOf(null, null), urls)
    }

    private fun listing(thumbnail: String?, volumeThumbnail: String?) = SeriesListItem(
        databaseId = "123",
        title = "作品",
        thumbnailUri = thumbnail,
        firstEpisode = episode,
        firstVolume = null,
        latestEpisode = null,
        latestVolume = volumeThumbnail?.let(::LatestVolume),
        likeCount = 1,
    )

    private fun details(thumbnail: String?, volumeThumbnail: String?) = SeriesDetails(
        databaseId = "123",
        title = "作品",
        author = null,
        description = null,
        thumbnailUri = thumbnail,
        firstEpisode = episode,
        firstVolume = null,
        latestVolume = volumeThumbnail?.let(::LatestVolume),
        episodes = TotalCount(1),
        volumes = TotalCount(0),
    )
}
