package eu.kanade.tachiyomi.extension.ja.dmm

import okhttp3.HttpUrl.Companion.toHttpUrl

private const val LIST_PAGE_SIZE = 30

internal fun listingRequestUrl(baseUrl: String, page: Int, sort: String) = "$baseUrl/list/".toHttpUrl().newBuilder()
    .addQueryParameter("sort", sort)
    .addQueryParameter("limit", LIST_PAGE_SIZE.toString())
    .apply {
        if (page > 1) {
            addQueryParameter("page", page.toString())
        }
    }
    .build()

internal fun searchRequestUrl(baseUrl: String, page: Int, query: String) = "$baseUrl/search/".toHttpUrl().newBuilder()
    .addQueryParameter("searchstr", query)
    .addQueryParameter("limit", LIST_PAGE_SIZE.toString())
    .addQueryParameter("page", page.toString())
    .build()
