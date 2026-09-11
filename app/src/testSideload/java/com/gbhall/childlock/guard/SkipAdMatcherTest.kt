package com.gbhall.childlock.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipAdMatcherTest {
    private val yt = "com.google.android.youtube"
    private val netflix = "com.netflix.mediaclient"

    @Test
    fun `labels match exactly after normalisation, per app`() {
        assertTrue(SkipAdMatcher.isSkipLabel("Skip ad", yt))
        assertTrue(SkipAdMatcher.isSkipLabel("SKIP ADS ›", yt))
        assertTrue("YouTube's bare Skip", SkipAdMatcher.isSkipLabel("  skip  ", yt))
        assertTrue(SkipAdMatcher.isSkipLabel("Skip advert", netflix))
        assertFalse("bare Skip elsewhere means intro or recap", SkipAdMatcher.isSkipLabel("Skip", netflix))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip intro", netflix))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip recap", "com.disney.disneyplus"))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip in 5", yt))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip trial", yt))
        assertFalse(SkipAdMatcher.isSkipLabel("Skip trailer", yt))
        assertFalse(SkipAdMatcher.isSkipLabel(null, yt))
    }

    @Test
    fun `music apps are excluded because skip means next track`() {
        assertFalse(SkipAdMatcher.isSupported("com.spotify.music"))
        assertTrue(SkipAdMatcher.isSupported(netflix))
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
    ) = SkipAdMatcher.isCandidate("com.google.android.youtube", text, desc, parent, clickable, enabled, visible, editable, w, h, 1080, 2400)

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
