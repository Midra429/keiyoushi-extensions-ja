package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

private val FREE_EDITION = Regex("""[【［\[（(][^】］\]）)]*(?:無料|試し読み)[^】］\]）)]*[】］\]）)]""")

internal fun shouldHideFreeChapter(purchased: Boolean, free: Boolean, title: String): Boolean = (!purchased && free) || FREE_EDITION.containsMatchIn(title)
