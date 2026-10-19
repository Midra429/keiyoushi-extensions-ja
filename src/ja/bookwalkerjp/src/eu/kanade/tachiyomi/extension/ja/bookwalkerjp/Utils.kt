package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element

const val DOMAIN = "bookwalker.jp"
private const val RIMG_URL = "https://rimg.$DOMAIN"
private const val C_URL = "https://c.$DOMAIN"

internal fun Element.listingCoverUrl(): String? = selectFirst(".m-thumb__image img, .o-tile-book-img img")
    ?.absUrl("data-original")
    .getHiResCoverFromLegacyUrl()

internal fun String?.getHiResCoverFromLegacyUrl(): String? {
    if (this.isNullOrEmpty()) return null
    val segments = this.toHttpUrlOrNull()?.pathSegments ?: return this
    val fileName = segments.last()
    val extension = fileName.substringAfterLast('.')
    val coverId = when {
        this.startsWith(RIMG_URL) -> segments.first().reversed().toLongOrNull() ?: return this
        fileName.startsWith("thumbnailImage_") -> fileName.substringAfter("thumbnailImage_").substringBefore('.')
            .toLongOrNull() ?: return this

        else -> return this
    }
    return "$C_URL/coverImage_${coverId - 1}.$extension#$this"
}
