package com.gbhall.childlock.lock

import android.view.MotionEvent
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.TestSupport.motion
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class TouchShieldViewTest {
    private class Host : TouchShieldView.Host {
        var unlocks = 0
        var pinPads = 0
        override fun onUnlock() { unlocks++ }
        override fun onShowPinPad() { pinPads++ }
    }

    private val w = 1080
    private val h = 2400

    private fun shield(settings: LockSettings = LockSettings(), host: Host = Host()): Pair<TouchShieldView, Host> {
        val v = TouchShieldView(TestSupport.app, settings, host)
        v.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, w, h)
        return v to host
    }

    @Test
    fun `every touch is consumed`() {
        val (v, host) = shield()
        assertTrue(v.onTouchEvent(motion(MotionEvent.ACTION_DOWN, 500f to 900f)))
        assertTrue(v.onTouchEvent(motion(MotionEvent.ACTION_MOVE, 520f to 950f)))
        assertTrue(v.onTouchEvent(motion(MotionEvent.ACTION_UP, 520f to 950f)))
        assertTrue(v.onTouchEvent(motion(MotionEvent.ACTION_CANCEL, 520f to 950f)))
        idle(5000)
        assertEquals(0, host.unlocks)
    }

    @Test
    fun `hover is consumed too, so explore-by-touch cannot reach the app underneath`() {
        // Explore-by-touch delivers hover instead of touch. A shield that only
        // handles onTouchEvent swallows nothing at all in that mode, which is
        // how a child reached the YouTube controls through a locked screen.
        val (v, host) = shield()
        assertTrue(v.onHoverEvent(motion(MotionEvent.ACTION_HOVER_ENTER, 500f to 900f)))
        assertTrue(v.onHoverEvent(motion(MotionEvent.ACTION_HOVER_MOVE, 520f to 950f)))
        assertTrue(v.onHoverEvent(motion(MotionEvent.ACTION_HOVER_EXIT, 520f to 950f)))
        idle(5000)
        assertEquals(0, host.unlocks)
    }

    @Test
    fun `corner hold through real MotionEvents unlocks after the hold time`() {
        val (v, host) = shield(LockSettings(holdMs = 1000))
        v.onTouchEvent(motion(MotionEvent.ACTION_DOWN, 40f to 60f))
        v.onTouchEvent(motion(MotionEvent.ACTION_POINTER_DOWN, 40f to 60f, 1040f to 2350f, actionIndex = 1))
        idle(600)
        assertEquals(0, host.unlocks)
        idle(500)
        assertEquals(1, host.unlocks)
    }

    @Test
    fun `lifting one finger before the hold time does not unlock`() {
        val (v, host) = shield(LockSettings(holdMs = 1000))
        v.onTouchEvent(motion(MotionEvent.ACTION_DOWN, 40f to 60f))
        v.onTouchEvent(motion(MotionEvent.ACTION_POINTER_DOWN, 40f to 60f, 1040f to 2350f, actionIndex = 1))
        idle(500)
        v.onTouchEvent(motion(MotionEvent.ACTION_POINTER_UP, 40f to 60f, 1040f to 2350f, actionIndex = 1))
        idle(2000)
        assertEquals(0, host.unlocks)
    }

    @Test
    fun `badge sits in the configured corner, clear of the edges`() {
        for (corner in Corner.entries) {
            val (v, _) = shield(LockSettings(badgeCorner = corner))
            val r = v.badgeRect
            assertFalse(r.isEmpty)
            val left = r.left < w / 2
            val top = r.top < h / 2
            assertEquals(corner.name, corner == Corner.TOP_LEFT || corner == Corner.BOTTOM_LEFT, left)
            assertEquals(corner.name, corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT, top)
            assertTrue(r.left >= 0 && r.top >= 0 && r.right <= w && r.bottom <= h)
        }
    }

    @Test
    fun `badge pin gesture asks the host for the pin pad after a long press`() {
        val (v, host) = shield(LockSettings(gesture = GestureType.BADGE_PIN, holdMs = 800, pinHash = "x", pinLength = 4))
        val r = v.badgeRect
        v.onTouchEvent(motion(MotionEvent.ACTION_DOWN, r.centerX() to r.centerY()))
        idle(900)
        assertEquals(1, host.pinPads)
        assertEquals(0, host.unlocks)
    }

    @Test
    fun `volume chord mode keeps corner hold as a fallback`() {
        val (v, host) = shield(LockSettings(gesture = GestureType.VOLUME_CHORD, holdMs = 1000))
        v.onTouchEvent(motion(MotionEvent.ACTION_DOWN, 40f to 60f, 1040f to 2350f))
        idle(1100)
        assertEquals(1, host.unlocks)
    }

    @Test
    fun `disposed shield ignores touches and stops ticking`() {
        val (v, host) = shield(LockSettings(holdMs = 500))
        v.onTouchEvent(motion(MotionEvent.ACTION_DOWN, 40f to 60f, 1040f to 2350f))
        v.dispose()
        idle(2000)
        assertEquals(0, host.unlocks)
    }

    @Test
    fun `draws without crashing`() {
        val (v, _) = shield()
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bitmap))
    }
}
