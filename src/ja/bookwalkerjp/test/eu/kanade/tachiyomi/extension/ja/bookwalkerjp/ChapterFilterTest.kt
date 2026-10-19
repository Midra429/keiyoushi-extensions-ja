package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterFilterTest {
    @Test
    fun `free volumes are hidden`() {
        assertTrue(shouldHideFreeChapter(purchased = false, free = true, title = "キングダム 1"))
    }

    @Test
    fun `a purchased regular volume survives a current free campaign`() {
        assertFalse(shouldHideFreeChapter(purchased = true, free = true, title = "キングダム 1"))
    }

    @Test
    fun `limited free editions are hidden even after acquisition`() {
        assertTrue(shouldHideFreeChapter(purchased = true, free = false, title = "キングダム【期間限定無料】 1"))
        assertTrue(shouldHideFreeChapter(purchased = false, free = false, title = "作品名［無料お試し版］"))
        assertTrue(shouldHideFreeChapter(purchased = false, free = false, title = "作品名[無料版]"))
        assertTrue(shouldHideFreeChapter(purchased = false, free = false, title = "作品名（期間限定無料）"))
        assertTrue(shouldHideFreeChapter(purchased = false, free = false, title = "作品名(無料お試し版)"))
    }

    @Test
    fun `paid volumes with a trial remain visible`() {
        assertFalse(shouldHideFreeChapter(purchased = false, free = false, title = "キングダム 2"))
    }

    @Test
    fun `the word free in a regular title is not a promotional edition`() {
        assertFalse(shouldHideFreeChapter(purchased = true, free = false, title = "無料で遊べるゲームの世界 1"))
    }
}
