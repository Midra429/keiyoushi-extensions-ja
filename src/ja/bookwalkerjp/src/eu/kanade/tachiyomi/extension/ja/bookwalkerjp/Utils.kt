package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.coverbackground.withCoverBackground
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

const val DOMAIN = "bookwalker.jp"

internal fun categoryListingUrl(baseUrl: String, order: String, page: Int): HttpUrl = "$baseUrl/category/2/".toHttpUrl().newBuilder()
    .addQueryParameter("order", order)
    .addQueryParameter("detail", "1")
    .addQueryParameter("page", page.toString())
    .build()

internal fun Document.toMangasPage(): MangasPage {
    val mangas = select(".o-card-wrap, .m-tile, .o-tile--series").map {
        val link = it.selectFirst(".o-card-ttl a, a.m-book-item__title, .o-tile-ttl a")!!
        SManga.create().apply {
            title = link.selectFirst(".o-card-ttl__text")?.text() ?: link.text()
            thumbnail_url = it.listingCoverUrl()
            val href = link.absUrl("href").toHttpUrl().pathSegments
            url = if (href.first() == "series") href[1] else href[0]
        }
    }
    return MangasPage(mangas, hasNextPage())
}

internal fun Document.hasNextPage(): Boolean = selectFirst(".o-pager-next a:not(.o-pager-box-btn_hidden), a[data-action-label=次のページへ]:not(.o-pager-box-btn_hidden)") != null

internal fun Element.listingCoverUrl(): String? = selectFirst(".m-thumb__image img, .o-tile-book-img img")
    ?.takeIf { it.attr("data-original").isNotEmpty() }
    ?.absUrl("data-original")
    ?.takeIf(String::isNotEmpty)
    .withCoverBackground()
