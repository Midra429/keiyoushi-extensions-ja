package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.nodes.Document

internal class BookChapter(
    val name: String,
    val uuid: String,
    val viewerUrl: String,
) {
    fun toSChapter() = SChapter.create().apply {
        name = this@BookChapter.name
        url = uuid
        memo = buildJsonObject { put("viewerUrl", viewerUrl) }
    }
}

internal fun Document.toStandaloneChapter(details: DetailsResponse, hideLocked: Boolean, hideFree: Boolean): BookChapter? {
    val buttons = selectFirst("#js-read-check-book-cover-main-button")
    val purchasedButton = buttons?.selectFirst(".t-c-read-button a")
    val freeButton = buttons?.selectFirst(".t-c-read-free-button a")
    val readButton = purchasedButton ?: freeButton
    val trialButton = buttons?.selectFirst(".t-c-read-trial-button a")
    if (hideFree && shouldHideFreeChapter(purchasedButton != null, freeButton != null, details.mangaTitle)) return null
    val prefix = when {
        readButton != null -> ""
        trialButton != null -> "🔒 （試し読み） "
        else -> "🔒 "
    }
    if (hideLocked && prefix.isNotEmpty()) return null
    return BookChapter(
        name = prefix + details.mangaTitle,
        uuid = details.uuid,
        viewerUrl = (readButton ?: trialButton)?.absUrl("href") ?: location(),
    )
}
