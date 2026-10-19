package eu.kanade.tachiyomi.extension.ja.shonenjumpplus

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.coverbackground.CoverBackgroundInterceptor
import keiyoushi.lib.coverbackground.withCoverBackground
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.ZoneId

@Source
abstract class ShonenJumpPlus : GigaViewer() {
    override val episodePageSize = 100

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder {
        addInterceptor(CoverBackgroundInterceptor())
        return configureGigaViewerClient()
    }

    override fun coverUrl(url: String?): String? = url.withCoverBackground()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = fetchSeriesPage("", "ul.series-list li a")
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val dayOfWeek = LocalDate.now(ZoneId.of("Asia/Tokyo")).dayOfWeek.name.lowercase()
        val mangas = fetchSeriesPage("", "h2.series-list-date-week.$dayOfWeek + ul.series-list li a")
        return MangasPage(mangas, false)
    }

    override suspend fun fetchCollection(ids: List<String>): List<SManga> = fetchSeriesPage(ids.single(), "ul.series-list li a")

    private suspend fun fetchSeriesPage(path: String, selector: String): List<SManga> = client.get("$baseUrl/series$path").asJsoup().select(selector).map {
        SManga.create().apply {
            title = it.selectFirst("h2.series-list-title")!!.text()
            val thumbnailUrl = it.selectFirst("div.series-list-thumb img")?.absUrl("data-src")
            setSeriesUrl(it.absUrl("href"), thumbnailUrl)
            thumbnail_url = coverUrl(thumbnailUrl)
        }
    }.distinctBy { it.url }

    override fun getFilterOptions() = listOf(
        "ジャンプ＋連載一覧" to listOf(""),
        "ジャンプ＋読切シリーズ" to listOf("/oneshot"),
        "連載終了作品" to listOf("/finished"),
    )
}
