package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class BookWalkerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = request.url.fragment
        val url = response.request.url
        if (response.code == 401 && "holdBooks-api" in url.pathSegments) {
            response.close()
            throw IOException("購入済みの書籍を表示するには、WebViewでログインしてください。")
        }

        if (url.encodedPath == "/app/03/login") {
            response.close()
            throw IOException("ログインの有効期限が切れました。WebViewで再度ログインしてください。")
        }

        if (response.isSuccessful || fallbackUrl.isNullOrEmpty() || !request.url.encodedPath.contains("/coverImage_")) {
            return response
        }
        response.close()
        return chain.proceed(GET(fallbackUrl, request.headers))
    }
}
