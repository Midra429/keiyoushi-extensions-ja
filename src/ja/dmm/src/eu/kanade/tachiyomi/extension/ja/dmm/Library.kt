package eu.kanade.tachiyomi.extension.ja.dmm

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.io.IOException

class LibraryFilter : Filter.CheckBox("購入済み")

internal fun Document.toLibraryMangasPage(): MangasPage {
    val items = select("[data-testid=purchasable-library-book]")
    if (items.isEmpty() && !text().contains("購入済みの作品がありません") && !text().contains("購入済みの商品はありません")) {
        throw IOException("購入済み一覧を取得できませんでした。WebViewでログイン状態と本棚を確認してください。")
    }
    val mangas = items.map { item ->
        val link = item.selectFirst("span[data-is-short-title=false] a[href*=/product/]")!!
        SManga.create().apply {
            url = link.absUrl("href").toHttpUrl().pathSegments[1]
            title = link.text()
            thumbnail_url = item.selectFirst("img")?.absUrl("src")
        }
    }.distinctBy { it.url }
    val hasNextPage = select("a[href]").any {
        it.text() in listOf("＞", ">", "次へ") && it.absUrl("href") != location()
    }
    return MangasPage(mangas, hasNextPage)
}
