package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

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
import keiyoushi.lib.coverbackground.CoverBackgroundInterceptor
import keiyoushi.lib.publus.PublusAuthHandler
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.lib.publus.parseFragmentOrNull
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BookWalkerJp :
    KeiSource(),
    ConfigurableSource {
    private val memberApiUrl get() = "https://member.$DOMAIN/api"
    private val viewerUrl get() = "https://viewer.$DOMAIN"
    private val trialUrl get() = "https://viewer-trial.$DOMAIN"
    private val dfViewer get() = "https://viewer-df.$DOMAIN"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders
        get() = headersBuilder()
            .set(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36",
            )
            .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(CoverBackgroundInterceptor())
        addCookie(listOf("holdBook-series" to "1", "safeSearch" to "111", "mySetting/showCoverR15" to "1"))
        addInterceptor(PublusInterceptor())
        addInterceptor(BookWalkerInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = categoryListingUrl(baseUrl, "rank", page)
        return client.get(url, desktopHeaders).asJsoup().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = categoryListingUrl(baseUrl, "release", page)
        return client.get(url, desktopHeaders).asJsoup().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val search = filters.firstInstance<SearchFilter>()
        val sort = filters.firstInstance<SortFilter>()
        val library = filters.firstInstance<LibraryFilter>()
        if (library.state) {
            val url = "$baseUrl/prx/holdBooks-api/hold-book-list/".toHttpUrl().newBuilder()
                .addQueryParameter("holdBook-series", "1")
                .addQueryParameter("page", page.toString())
                .build()
            val result = client.get(url, desktopHeaders, cacheControl = CacheControl.FORCE_NETWORK)
                .parseAs<LibraryResponse>().holdBookList
            val hasNextPage = page < result.totalPage
            val mangas = result.entities.map { it.toSManga() }.distinctBy { it.url }
            return MangasPage(mangas, hasNextPage)
        }

        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("order", sort.value)
            .addQueryParameter("wa", search.value)
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, desktopHeaders).asJsoup().toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("検索語とフィルターは併用できます（購入済みを除く）。"),
        Filter.Header("小説には対応していません。"),
        SearchFilter(),
        SortFilter(),
        Filter.Header("購入済みの書籍を表示（ほかのフィルターは適用されません）"),
        LibraryFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val hideFree = preferences.getBoolean(HIDE_FREE_PREF_KEY, true)
        val isBook = manga.url.startsWith("de")
        val seriesDoc = if (!isBook) client.get(seriesUrl(manga.url), desktopHeaders).asJsoup() else null
        val wayomi = seriesDoc?.selectFirst(".p-episode__list") != null
        val (details, firstListPage) = fetchBookDetails(manga, seriesDoc, wayomi)

        return SMangaUpdate(
            manga = details.toSManga(),
            chapters = when {
                !fetchChapters -> chapters
                wayomi -> seriesDoc.toWayomiChapters(hideLocked, hideFree)
                details.seriesId == null -> client.get(
                    "$baseUrl/${details.mangaUrl}/",
                    desktopHeaders,
                    cacheControl = CacheControl.FORCE_NETWORK,
                ).asJsoup()
                    .toStandaloneChapter(details, hideLocked, hideFree)?.let { listOf(it.toSChapter()) }.orEmpty()

                else -> chapterList(details.seriesId.toString(), hideLocked, hideFree, firstListPage)
            },
        )
    }

    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith("de")) "$baseUrl/${manga.url}/" else "$baseUrl/series/${manga.url}/list/"

    private suspend fun fetchBookDetails(
        manga: SManga,
        seriesDoc: Document?,
        wayomi: Boolean,
    ): Pair<DetailsResponse, Document?> {
        var firstListPage: Document? = null
        val bookId = when {
            manga.url.startsWith("de") -> manga.url
            wayomi -> seriesDoc!!.firstEpisodeId()
            else -> client.get(seriesListUrl(manga.url, 1), desktopHeaders).asJsoup()
                .also { firstListPage = it }
                .firstBookId()
        }
        val details = client.get(booksUpdatesUrl(bookId)).parseAs<List<DetailsResponse>>().first()
        return details to firstListPage
    }

    private suspend fun chapterList(
        seriesId: String,
        hideLocked: Boolean,
        hideFree: Boolean,
        firstPage: Document? = null,
    ): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        var document = firstPage ?: client.get(seriesListUrl(seriesId, page), desktopHeaders).asJsoup()
        while (true) {
            val items = document.select(".m-tile-list .m-book-item")
            if (items.isEmpty()) break
            items.mapNotNullTo(chapters) { it.toSChapter(hideLocked, hideFree) }
            if (!document.hasNextPage()) break
            page++
            document = client.get(seriesListUrl(seriesId, page), desktopHeaders).asJsoup()
        }
        return chapters.reversed()
    }

    private fun booksUpdatesUrl(bookId: String): HttpUrl = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
        .addQueryParameter("fileType", "EPUB")
        .addQueryParameter(bookId.removePrefix("de"), "0")
        .build()

    private fun seriesListUrl(seriesId: String, page: Int): HttpUrl = "$baseUrl/series/$seriesId/list/".toHttpUrl().newBuilder()
        .addQueryParameter("order", "title")
        .addQueryParameter("detail", "0")
        .addQueryParameter("page", page.toString())
        .build()

    private fun Element.toSChapter(hideLocked: Boolean, hideFree: Boolean): SChapter? {
        val purchasedButton = selectFirst(".a-icon-btn--read")
        val freeButton = selectFirst(".a-icon-btn--free")
        val readButton = purchasedButton ?: freeButton
        val trialButton = selectFirst(".a-icon-btn--trial")
        val link = bookLink()
        if (hideFree && shouldHideFreeChapter(purchasedButton != null, freeButton != null, link.text())) return null
        val prefix = when {
            readButton != null -> ""
            trialButton != null -> "🔒 （試し読み） "
            else -> "🔒 "
        }
        if (hideLocked && prefix.isNotEmpty()) return null
        val viewerUrl = (readButton ?: trialButton)?.absUrl("href") ?: link.absUrl("href")

        return SChapter.create().apply {
            name = prefix + link.text()
            url = link.attr("data-uuid")
            memo = buildJsonObject { put("viewerUrl", viewerUrl) }
        }
    }

    private fun Document.toWayomiChapters(hideLocked: Boolean, hideFree: Boolean): List<SChapter> = select(".p-episode__list a[data-book-uuid]")
        .mapNotNull { it.toWayomiChapter(hideLocked, hideFree) }
        .reversed()

    private fun Element.toWayomiChapter(hideLocked: Boolean, hideFree: Boolean): SChapter? {
        val free = attr("data-is-free") == "1"
        val rented = attr("data-is-now-rental") == "1"
        val purchased = attr("data-is-settled") == "1"
        val title = selectFirst(".o-ttsk-list-item__title")!!.text()
        if (hideFree && shouldHideFreeChapter(purchased, free, title)) return null
        val unlocked = free || rented || purchased
        if (hideLocked && !unlocked) return null

        return SChapter.create().apply {
            name = (if (unlocked) "" else "🔒 ") + title
            url = attr("data-book-uuid")
            val viewerUrl = when {
                rented -> attr("data-df-viewer-url")
                free -> absUrl("href")
                else -> attr("data-viewer-url")
            }
            memo = buildJsonObject { put("viewerUrl", viewerUrl) }
        }
    }

    private fun seriesUrl(seriesId: String): HttpUrl = "$baseUrl/series/$seriesId/".toHttpUrl()
    private fun Document.firstEpisodeId(): String = "de" + selectFirst(".p-episode__list a[data-book-uuid]")!!.attr("data-book-uuid")

    private fun Document.firstBookId(): String = selectFirst(".m-tile-list .m-book-item")!!.bookId()

    private fun Element.bookLink(): Element = selectFirst("a.m-book-item__title")!!
    private fun Element.bookId(): String = bookLink().absUrl("href").toHttpUrl().pathSegments.first()

    private val publusAuth = PublusAuthHandler(AUTH_REFRESH_INTERVAL) { session ->
        val cUrl = session["cUrl"]!!.toHttpUrl()
        val cookies = client.cookieJar.loadForRequest(cUrl)
        val refreshUrl = cUrl.newBuilder().apply {
            addQueryParameter("cid", session["cid"])
            addQueryParameter("BID", "0")
            addQueryParameter("cr", session["cr"])
            cookies.find { it.name == "u1" }?.let { addQueryParameter("u1", it.value) }
            cookies.find { it.name == "u2" }?.let { addQueryParameter("u2", it.value) }
        }.build()

        client.get(refreshUrl).parseAs<PublusContent>().authInfo?.toAuth(bid = "0")
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.memo["viewerUrl"]?.string ?: throw Exception("章の一覧を更新してください。")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl =
            client.get(getChapterUrl(chapter), desktopHeaders, ensureSuccess = false).use { it.request.url }
        val cid = chapterUrl.queryParameter("cid")
        val cty = chapterUrl.queryParameter("cty")?.toIntOrNull()
        val host = chapterUrl.host
        val isTrial = host == trialUrl.toHttpUrl().host
        val isDf = host == dfViewer.toHttpUrl().host

        val cookies = client.cookieJar.loadForRequest(chapterUrl)
        val u1 = cookies.find { it.name == "u1" }?.value
        val u2 = cookies.find { it.name == "u2" }?.value

        return if (cid != null) {
            if (cty != null && cty != 1 && cty != 2) {
                throw Exception("小説には対応していません。")
            }

            var cr: String? = null
            if (!isTrial) {
                val loaderUrl = if (isDf) {
                    "$dfViewer/browserWebApi4/04/getLoader"
                } else {
                    "$viewerUrl/browserWebApi/03/getLoader"
                }
                val loaderScript = client.get(loaderUrl).body.string()
                cr = fetchCr(loaderScript, chapterUrl.toString())
            }

            val cApiBase = when {
                isTrial -> "$trialUrl/trial-page/c"
                isDf -> "$dfViewer/browserWebApi4/c"
                else -> "$viewerUrl/browserWebApi/c"
            }

            val cApiUrl = cApiBase.toHttpUrl().newBuilder().apply {
                addQueryParameter("cid", cid)
                if (!isTrial) {
                    if (u1 != null) addQueryParameter("u1", u1)
                    if (u2 != null) addQueryParameter("u2", u2)
                    addQueryParameter("cr", cr)
                }
                addQueryParameter("BID", "0") // universal
            }.build()

            val content = client.get(cApiUrl).parseAs<PublusContent>()
            val contentUrl = content.url
                ?: throw Exception("WebViewでログインし、この章をレンタルまたは購入してください。")

            if (content.cty != 1 && content.cty != 2) {
                throw Exception("小説には対応していません。")
            }

            val auth = content.authInfo?.toAuth(bid = "0", includeBookAuth = !isTrial)

            val sessionData = if (isTrial || auth == null) {
                null
            } else {
                publusAuth.store(cid, auth)
                buildMap<String, String> {
                    put("cid", cid)
                    put("cUrl", cApiBase)
                    if (cr != null) put("cr", cr)
                }
            }

            client.fetchPages(contentUrl, auth, sessionData)
        } else {
            throw Exception("試し読みがないか、この巻の購入が必要です。")
        }
    }

    private suspend fun fetchCr(scriptContent: String, viewerUrl: String): String? {
        val match = SCRIPT_REGEX.find(scriptContent) ?: return null
        val functionName = match.groupValues[1]

        return try {
            runWebView(timeout = 10.seconds) {
                blockImages = true
                onPageFinished {
                    evaluateJs(scriptContent) {
                        // c9P = function()
                        evaluateJs("$functionName()") { value ->
                            val result = value.trim('"')
                            resolve(result.takeIf { it != "null" && it.isNotBlank() })
                        }
                    }
                }
                loadData(viewerUrl, " ")
            }
        } catch (_: WebViewTimeoutException) {
            null
        }
    }

    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        val session = request.url.fragment?.parseFragmentOrNull()?.extra ?: return request
        val auth = runBlocking { publusAuth.currentAuth(session["cid"]!!, session) } ?: return request
        val url = auth.applyTo(request.url.newBuilder().query(null)).build()
        return request.newBuilder().url(url).build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "ロックされた章を非表示にする"
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_FREE_PREF_KEY
            title = "無料の章を非表示にする"
            summary = "期間限定無料・無料お試し版も非表示にします。通常版の購入済みの巻は残します。"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        // Normal (non-trial) auth data expires after 60s
        private val AUTH_REFRESH_INTERVAL = 45.seconds
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val HIDE_FREE_PREF_KEY = "hide_free"
        private val SCRIPT_REGEX = Regex("""^(\w+)=function\(\)\{[\s\S]*?\};""", RegexOption.MULTILINE)
    }
}
