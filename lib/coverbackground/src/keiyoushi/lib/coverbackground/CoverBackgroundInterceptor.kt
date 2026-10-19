package keiyoushi.lib.coverbackground

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

private const val COVER_MARKER = "keiyoushi-cover-v1:"

/** Marks a cover for 2:3 padding, retaining any fragment needed by downstream interceptors. */
fun String?.withCoverBackground(): String? {
    val url = this?.toHttpUrlOrNull() ?: return this
    if (url.fragment?.startsWith(COVER_MARKER) == true) return this
    return url.newBuilder().fragment(COVER_MARKER + url.fragment.orEmpty()).build().toString()
}

/** Install before other image interceptors. Only marked covers are transformed; pages are untouched. */
class CoverBackgroundInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment?.startsWith(COVER_MARKER) != true) return chain.proceed(request)

        val url = request.url.newBuilder()
            .fragment(fragment.removePrefix(COVER_MARKER).ifEmpty { null })
            .build()
        val response = chain.proceed(request.newBuilder().url(url).build())
        if (!response.isSuccessful) return response

        val output = Buffer()
        response.use {
            val source = it.body.source()
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inSampleSize = 1
            }
            source.peek().inputStream().use { stream -> BitmapFactory.decodeStream(stream, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Could not decode cover image")
            options.inJustDecodeBounds = false
            // Cover downloads can be full-resolution artwork; bound the decoded bitmap as well as the output.
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1200) {
                options.inSampleSize *= 2
            }
            val original = BitmapFactory.decodeStream(source.inputStream(), null, options)
                ?: throw IOException("Could not decode cover image")
            try {
                val cover = renderCoverBackground(original)
                try {
                    if (!cover.compress(Bitmap.CompressFormat.JPEG, 94, output.outputStream())) {
                        throw IOException("Could not encode cover image")
                    }
                } finally {
                    cover.recycle()
                }
            } finally {
                original.recycle()
            }
        }
        return response.newBuilder()
            .header("Content-Type", "image/jpeg")
            .header("Content-Length", output.size.toString())
            .removeHeader("Content-Encoding")
            .removeHeader("Content-MD5")
            .removeHeader("ETag")
            .body(output.asResponseBody("image/jpeg".toMediaType(), output.size))
            .build()
    }
}
