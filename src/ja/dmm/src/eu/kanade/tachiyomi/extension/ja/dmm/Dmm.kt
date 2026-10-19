package eu.kanade.tachiyomi.extension.ja.dmm

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class Dmm :
    KeiSource(),
    ConfigurableSource {
    private val shopName get() = if (baseUrl.toHttpUrl().host == "book.dmm.co.jp") "adult" else "general"
    private val apiUrl get() = "$baseUrl/ajax/bff"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders
        get() = headersBuilder()
            .set(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36",
            )
            .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(PublusInterceptor())
        addCookie(listOf("book_safe_mode_level" to "off", "age_check_done" to "1"))
        addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val path = response.request.url.encodedPath
            val libraryRequest = chain.request().url.encodedPath in listOf("/shelf/", "/library/", "/ajax/bff/library/")
            val loginRequired = response.code == 401 || response.request.url.host.startsWith("accounts.") ||
                path.startsWith("/service/login/")
            if (libraryRequest && loginRequired) {
                response.close()
                throw IOException("購入済みの書籍を表示するには、WebViewでログインしてください。")
            }
            if (!libraryRequest && (path.startsWith("/service/login/password") || path == "/shelf/")) {
                response.close()
                throw IOException("お住まいの国・地域では利用できません。")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = listingRequestUrl(baseUrl, page, "ranking")
        return client.get(url, desktopHeaders).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = listingRequestUrl(baseUrl, page, "date")
        return client.get(url, desktopHeaders).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (filters.firstInstanceOrNull<LibraryFilter>()?.state == true) {
            return getLibraryManga(page)
        }
        val url = searchRequestUrl(baseUrl, page, query)
        return client.get(url, desktopHeaders).toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("購入済みを選択すると、検索語にかかわらず購入済みの作品を表示します。"),
        Filter.Header("事前にWebViewでログインしてください。"),
        LibraryFilter(),
    )

    private suspend fun getLibraryManga(page: Int): MangasPage {
        val url = libraryRequestUrl(baseUrl, shopName, page)
        return client.get(url, cacheControl = CacheControl.FORCE_NETWORK)
            .parseAs<LibraryResponse>()
            .toMangasPage(shopName)
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select(".m-boxListBookProduct2__item, .m-boxSearchBookProduct__item").map {
            SManga.create().apply {
                url = it.selectFirst("a[href*=/product/]")!!.absUrl("href").toHttpUrl().pathSegments[1]
                title = it.selectFirst(".m-boxListBookProduct2Tmb__ttl, .m-boxSearchListTmb__ttl")!!.text()
                    .replace(TITLE_REGEX, "")
                thumbnail_url = it.selectFirst("img.m-bookImage__img")?.absUrl("src")?.replace(THUMBNAIL_REGEX, "l")
            }
        }

        val hasNextPage = document.selectFirst("a.m-boxPaging:contains(>)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/product/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val firstPage = async { fetchVolumeBooks(manga.url, 1) }

        val details = async {
            if (!fetchDetails) return@async manga
            val url = "$apiUrl/product_volume/".toHttpUrl().newBuilder()
                .addQueryParameter("shop_name", shopName)
                .addQueryParameter("series_id", manga.url)
                .addQueryParameter("content_id", firstPage.await().volumeBooks.first().contentId)
                .addQueryParameter("device_type", "sp")
                .addQueryParameter("format_webp", "1")
                .build()
            client.get(url).parseAs<DetailsResponse>().toSManga()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val first = firstPage.await()
            val pageCount = (first.pager.totalCount + PER_PAGE - 1) / PER_PAGE
            val otherPages = (2..pageCount).map { page -> async { fetchVolumeBooks(manga.url, page) } }
            (first.volumeBooks + otherPages.awaitAll().flatMap { it.volumeBooks })
                .distinctBy { it.contentId }
                .filter { !hideLocked || !it.isLocked || it.isPreview }
                .map { it.toSChapter(manga.url) }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    private suspend fun fetchVolumeBooks(seriesId: String, page: Int): ChapterResponse {
        val url = "$apiUrl/contents_book/".toHttpUrl().newBuilder()
            .addQueryParameter("shop_name", shopName)
            .addQueryParameter("series_id", seriesId)
            .addQueryParameter("format_webp", "1")
            .addQueryParameter("order", "desc")
            .addQueryParameter("purchase_status", "all")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE.toString())
            .build()
        return client.get(url).parseAs<ChapterResponse>()
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.memo["viewerUrl"]?.string
        ?: "$baseUrl/product/${chapter.memo["seriesId"]!!.string}/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val viewerUrl = chapter.memo["viewerUrl"]?.string?.toHttpUrl()
            ?: throw Exception("WebViewでログインし、この書籍を購入してください。")

        val url = viewerUrl.takeIf { it.queryParameter("cid") != null }
            ?: client.get(viewerUrl).use { it.request.url }

        val authUrl = "$baseUrl/viewerapi/auth/".toHttpUrl().newBuilder()
            .addQueryParameter("cid", url.queryParameter("cid"))
            .apply {
                url.queryParameter("lin")?.let { addQueryParameter("lin", it) }
            }
            .build()

        val content = client.get(authUrl).parseAs<PublusContent>()
        return client.fetchPages(content.url!!, content.authInfo?.toAuth())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "ロックされた章を非表示にする"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PER_PAGE = 100
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val THUMBNAIL_REGEX = Regex(".(?=\\.\\w+$)")
        private val TITLE_REGEX =
            Regex("(?:(?<=\\s|】)(第?\\d+巻|第?\\d+話|\\d+(?=\\s*$))|（[０-９0-9]+）|【第?\\d+[巻話]】|#\\d+).*$")
    }
}
