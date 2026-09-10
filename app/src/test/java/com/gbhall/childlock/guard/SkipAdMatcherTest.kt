package com.gbhall.childlock.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipAdMatcherTest {
    @Test
    fun `labels match exactly after normalisation`() {
        assertTrue(SkipAdMatcher.isSkipLabel("Skip ad"))
        assertTrue(SkipAdMatcher.isSkipLabel("SKIP ADS ›"))
        assertTrue(SkipAdMatcher.isSkipLabel("  skip  "))
        assertTrue(SkipAdMatcher.isSkipLabel("Skip advert"))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip in 5"))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip trailer"))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip intro"))
        assertFalse(SkipAdMatcher.isSkipLabel(null))
    }

    @Test
    fun `ad-like text is refused`() {
        assertTrue(SkipAdMatcher.looksLikeAd("Learn more", null))
        assertTrue(SkipAdMatcher.looksLikeAd(null, "Sponsored · Visit site"))
        assertFalse(SkipAdMatcher.looksLikeAd("Skip ad", null))
    }

    private fun candidate(
        text: String? = "Skip ad", desc: String? = null, parent: String? = null,
        clickable: Boolean = true, enabled: Boolean = true, visible: Boolean = true, editable: Boolean = false,
        w: Int = 300, h: Int = 120,
    ) = SkipAdMatcher.isCandidate(text, desc, parent, clickable, enabled, visible, editable, w, h, 1080, 2400)

    @Test
    fun `only small clickable enabled visible non-editable skip buttons qualify`() {
        assertTrue(candidate())
        assertTrue(candidate(text = null, desc = "Skip ads"))
        assertFalse(candidate(clickable = false))
        assertFalse(candidate(enabled = false))
        assertFalse(candidate(visible = false))
        assertFalse(candidate(editable = true))
        assertFalse("a creative-sized surface", candidate(w = 1080, h = 600))
        assertFalse("parent says it is the ad", candidate(parent = "Sponsored"))
        assertFalse(candidate(text = "Skip in 3"))
    }
}
