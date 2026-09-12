package com.gbhall.childlock.settings

import android.content.pm.ApplicationInfo
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.ui.UiTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppCatalogTest {
    private fun profile(pkg: String) = AppCatalog.profile(TestSupport.app, pkg)

    @Test
    fun `well-known apps get the right moment by default`() {
        assertEquals(AutoLockTrigger.FULLSCREEN_PLAYBACK, profile("com.google.android.youtube").default)
        assertEquals(AutoLockTrigger.FULLSCREEN_PLAYBACK, profile("com.google.android.apps.youtube.kids").default)
        assertEquals(AutoLockTrigger.VIDEO_CALL, profile("com.whatsapp").default)
        assertEquals(AutoLockTrigger.VIDEO_CALL, profile("com.google.android.apps.tachyon").default)
        assertEquals(AutoLockTrigger.OPEN, profile("com.roblox.client").default)
        assertEquals(AutoLockTrigger.PLAYBACK, profile("com.spotify.music").default)
        assertEquals("a player opens on anything, not only video", AutoLockTrigger.PLAYBACK, profile("org.videolan.vlc").default)
    }

    @Test
    fun `a video app offers video moments, a calling app offers call moments`() {
        assertEquals(listOf(AutoLockTrigger.FULLSCREEN_PLAYBACK, AutoLockTrigger.PLAYBACK, AutoLockTrigger.OPEN), profile("com.netflix.mediaclient").options)
        assertTrue(AutoLockTrigger.VOICE_CALL in profile("com.whatsapp").options)
        assertTrue(AutoLockTrigger.FULLSCREEN_PLAYBACK !in profile("com.whatsapp").options)
    }

    @Test
    fun `an unknown app is classified by the category it declares`() {
        UiTestSupport.installApp("com.example.game", "Blocks", ApplicationInfo.CATEGORY_GAME)
        UiTestSupport.installApp("com.example.tv", "Telly", ApplicationInfo.CATEGORY_VIDEO)
        UiTestSupport.installApp("com.example.radio", "Radio", ApplicationInfo.CATEGORY_AUDIO)
        assertEquals(AutoLockTrigger.OPEN, profile("com.example.game").default)
        assertEquals(AppCatalog.Kind.GAME, profile("com.example.game").kind)
        assertEquals(AutoLockTrigger.FULLSCREEN_PLAYBACK, profile("com.example.tv").default)
        assertEquals(AutoLockTrigger.PLAYBACK, profile("com.example.radio").default)
    }

    @Test
    fun `then by a keyword in its package name, then everything is offered`() {
        assertEquals(AppCatalog.Kind.CALLING, profile("net.example.voip.dialer").kind)
        assertEquals(AppCatalog.Kind.VIDEO, profile("com.example.iplayer.clone").kind)
        val other = profile("com.example.unknown")
        assertEquals(AppCatalog.Kind.OTHER, other.kind)
        assertEquals(AutoLockTrigger.OPEN, other.default)
        assertEquals(AutoLockTrigger.entries.toSet(), other.options.toSet())
    }

    @Test
    fun `a rule keeps its current choice even if the profile would not offer it`() {
        val video = AppCatalog.VIDEO
        assertEquals(video.options, AppCatalog.optionsFor(video, null))
        assertEquals(video.options, AppCatalog.optionsFor(video, AutoLockTrigger.OPEN))
        assertEquals(video.options + AutoLockTrigger.CALL, AppCatalog.optionsFor(video, AutoLockTrigger.CALL))
    }
}
