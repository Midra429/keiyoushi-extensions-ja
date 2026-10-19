package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class DetailsResponse(
    private val authors: List<Author>?,
    private val companyName: String?,
    private val copyRightString: String?,
    private val productExplanationShort: String?,
    private val productExplanationDetails: String?,
    private val coverImageUrl: String?,
    private val labelName: String?,
    val seriesId: Int,
    private val seriesName: String,
    private val seriesNameKana: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = seriesId.toString()
        title = seriesName
        author = authors?.joinToString { it.authorName }
        description = buildString {
            productExplanationShort?.let { append("$it\n\n") }
            productExplanationDetails?.let { append(it) }
            seriesNameKana?.let { append("\n\n読み仮名： $it") }
            companyName?.let { append("\n\n出版社： $it") }
            labelName?.let { append("\n\nレーベル： $it") }
            copyRightString?.let { append("\n\n$it") }
        }
        thumbnail_url = coverImageUrl
    }
}

@Serializable
class Author(
    val authorName: String,
)

@Serializable
class LibraryResponse(
    val holdBookList: HoldBookList,
)

@Serializable
class HoldBookList(
    val entities: List<Entity>,
    val totalPage: Int,
)

@Serializable
class Entity(
    private val seriesId: Int? = null,
    private val seriesName: String? = null,
    private val productId: String? = null,
    private val title: String? = null,
    private val imageUrl: String?,
) {
    private val grouped: Boolean
        get() = seriesId != null && seriesId > 0 && !seriesName.isNullOrBlank()

    val mangaUrl: String
        get() = if (grouped) {
            seriesId.toString()
        } else {
            val bookId = requireNotNull(productId) { "購入済み書籍のIDを取得できませんでした。" }
            "de${bookId.removePrefix("de")}"
        }

    val mangaTitle: String
        get() = requireNotNull(if (grouped) seriesName else title) { "購入済み書籍のタイトルを取得できませんでした。" }

    fun toSManga() = SManga.create().apply {
        url = mangaUrl
        title = mangaTitle
        thumbnail_url = imageUrl?.getHiResCoverFromLegacyUrl()
    }
}
