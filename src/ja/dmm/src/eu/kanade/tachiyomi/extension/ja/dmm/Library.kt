package eu.kanade.tachiyomi.extension.ja.dmm

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

class LibraryFilter : Filter.CheckBox("購入済み")

internal fun libraryRequestUrl(baseUrl: String, shopName: String, page: Int) = "$baseUrl/ajax/bff/library/".toHttpUrl().newBuilder()
    .addQueryParameter("shop_name", shopName)
    .addQueryParameter("page", page.toString())
    .addQueryParameter("order", "added_desc")
    .addQueryParameter("show_expired", "0")
    .addQueryParameter("format_webp", "1")
    .build()

@Serializable
internal class LibraryResponse(
    @SerialName("series_books") private val seriesBooks: List<LibraryBook>,
    private val pager: LibraryPager,
) {
    val hasNextPage: Boolean
        get() = pager.hasNextPage

    fun booksForShop(shopName: String) = seriesBooks.filter { it.shopName == shopName }.distinctBy { it.url }

    fun toMangasPage(shopName: String) = MangasPage(booksForShop(shopName).map { it.toSManga() }, hasNextPage)
}

@Serializable
internal class LibraryBook(
    @SerialName("shop_name") val shopName: String,
    @SerialName("series_id") val url: String,
    val title: String,
    @SerialName("image_urls") private val imageUrls: ImageUrls?,
) {
    val thumbnailUrl: String?
        get() = imageUrls?.pl

    fun toSManga() = SManga.create().apply {
        url = this@LibraryBook.url
        title = this@LibraryBook.title
        thumbnail_url = thumbnailUrl
    }
}

@Serializable
internal class LibraryPager(
    private val page: Int,
    @SerialName("per_page") private val perPage: Int,
    @SerialName("total_count") private val totalCount: Int,
) {
    val hasNextPage: Boolean
        get() = page * perPage < totalCount
}
