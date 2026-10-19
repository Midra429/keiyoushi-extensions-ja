package eu.kanade.tachiyomi.extension.ja.dmm

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.io.IOException

class LibraryFilter : Filter.CheckBox("購入済み")

internal class LibraryBook(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@LibraryBook.url
        title = this@LibraryBook.title
        thumbnail_url = thumbnailUrl
    }
}

internal class LibraryPage(
    val books: List<LibraryBook>,
    val hasNextPage: Boolean,
) {
    fun toMangasPage() = MangasPage(books.map { it.toSManga() }, hasNextPage)
}

internal fun Document.parseLibraryPage(): LibraryPage {
    val items = select("[data-testid=purchasable-library-book]")
    if (items.isEmpty() && !text().contains("購入済みの作品がありません") && !text().contains("購入済みの商品はありません")) {
        throw IOException("購入済み一覧を取得できませんでした。WebViewでログイン状態と本棚を確認してください。")
    }
    val books = items.map { item ->
        val link = item.select("span[data-is-short-title=false] a[href*=/product/]").firstOrNull { it.text().isNotEmpty() }
            ?: item.select("a[href*=/product/]").firstOrNull { it.text().isNotEmpty() }
            ?: throw IOException("購入済み一覧の書籍タイトルを取得できませんでした。WebViewで本棚の表示を確認してください。")
        LibraryBook(
            url = link.absUrl("href").toHttpUrl().pathSegments[1],
            title = link.text(),
            thumbnailUrl = item.selectFirst("img")?.absUrl("src"),
        )
    }.distinctBy { it.url }
    val hasNextPage = select("a[href]").any {
        it.text() in listOf("＞", ">", "次へ") && it.absUrl("href") != location()
    }
    return LibraryPage(books, hasNextPage)
}

internal val LIBRARY_CONTENT_SCRIPT = """
    (() => {
        const items = Array.from(document.querySelectorAll('[data-testid="purchasable-library-book"]'));
        const ready = items.length > 0 && items.every(item =>
            Array.from(item.querySelectorAll('a[href*="/product/"]')).some(link => link.textContent.trim().length > 0)
        );
        const text = document.body?.innerText || '';
        const empty = text.includes('購入済みの作品がありません') || text.includes('購入済みの商品はありません');
        return ready || (items.length === 0 && empty) ? document.documentElement.outerHTML : null;
    })();
""".trimIndent()
