package eu.kanade.tachiyomi.extension.ja.onepunchman

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.Normalizer
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class OnePunchMan : KeiSource() {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = Normalizer.normalize(query, Normalizer.Form.NFKC).trim()
        val manga = createManga()
        return MangasPage(
            listOf(manga).filter { it.title.contains(normalizedQuery, ignoreCase = true) },
            false,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return when (url.encodedPath) {
            "/", "/index.html" -> createManga()
            "/fc2-imageviewer/" -> if (url.queryParameter("aid") == "1") createManga() else null
            else -> null
        }
    }

    private fun createManga(): SManga = SManga.create().apply {
        url = "/"
        title = "ワンパンマン"
        author = "ＯＮＥ"
        artist = author
        thumbnail_url = "$baseUrl/top.jpg"
        status = SManga.ONGOING
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl).asJsoup()
        val chapterList = document.select("a[href^=/fc2-imageviewer/?]").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
                chapter_number = Normalizer.normalize(name, Normalizer.Form.NFKC)
                    .removePrefix("第").removeSuffix("話").toFloat()
            }
        }.asReversed()
        chapterList.firstOrNull()?.date_upload = dateFormat.tryParseDate(
            updateDateRegex.find(document.text())?.groupValues?.get(1),
            ZoneId.of("Asia/Tokyo"),
        )
        return SMangaUpdate(createManga(), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val albumId = chapterUrl.queryParameter("aid")!!
        val imageSetId = chapterUrl.queryParameter("iid")!!
        val imageRoot = "$baseUrl/fc2-imageviewer/$albumId/$imageSetId/"
        val data = client.get("${imageRoot}metadata.json").parseAs<ImageSetDto>()
        return data.imageItemList.mapIndexed { index, image ->
            val imageUrl = imageRoot.toHttpUrl().newBuilder()
                .addPathSegment(image.fileName)
                .addQueryParameter("_", data.updateDate.toString())
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    companion object {
        private val updateDateRegex = Regex("(\\d{4}年\\d{1,2}月\\d{1,2}日)\\s+\\d+話更新")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")
    }
}
