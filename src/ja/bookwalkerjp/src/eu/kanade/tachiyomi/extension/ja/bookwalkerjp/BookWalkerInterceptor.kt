package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class BookWalkerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = response.request.url
        if (response.code == 401 && "holdBooks-api" in url.pathSegments) {
            response.close()
            throw IOException("購入済みの書籍を表示するには、WebViewでログインしてください。")
        }

        if (url.encodedPath == "/app/03/login") {
            response.close()
            throw IOException("ログインの有効期限が切れました。WebViewで再度ログインしてください。")
        }

        return response
    }
}
