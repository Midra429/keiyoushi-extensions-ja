package eu.kanade.tachiyomi.extension.ja.bibibicomic

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import keiyoushi.annotation.Source
import keiyoushi.lib.coverbackground.CoverBackgroundInterceptor
import keiyoushi.lib.coverbackground.withCoverBackground
import okhttp3.OkHttpClient

@Source
abstract class BibibiComic : ComiciViewer() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder {
        addInterceptor(CoverBackgroundInterceptor())
        return configureComiciViewerClient()
    }

    override fun coverUrl(url: String?): String? = url.withCoverBackground()
}
