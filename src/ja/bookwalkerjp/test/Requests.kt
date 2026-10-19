package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Request

// RequestsKt in extensions-lib throws Stub! during initialization. Only the GET
// overload used by BookWalkerInterceptor is needed for the fallback routing test.
@Suppress("FunctionName", "ktlint:standard:function-naming")
fun GET(
    url: String,
    headers: Headers? = null,
    cache: CacheControl = CacheControl.Builder().build(),
): Request = Request.Builder()
    .url(url)
    .get()
    .apply { headers?.let { headers(it) } }
    .cacheControl(cache)
    .build()
