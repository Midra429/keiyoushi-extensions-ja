package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import org.jsoup.nodes.Element

const val DOMAIN = "bookwalker.jp"

internal fun Element.listingCoverUrl(): String? = selectFirst(".m-thumb__image img, .o-tile-book-img img")
    ?.absUrl("data-original")
    ?.takeIf(String::isNotEmpty)
