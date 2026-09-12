package com.gbhall.childlock.lock

import android.os.Build
import android.view.MotionEvent
import android.view.View
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.TestSupport.motion
import com.gbhall.childlock.gesture.PinHasher
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Swipes through the whole overlay window while locked. A child dragging a
 * finger up from the bottom, down from the top, or anywhere in between must
 * get nothing for it: every event is consumed, nothing unlocks, and the PIN
 * keypad never appears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class SwipeShieldTest {
    private val w = 1080
    private val h = 2400
    private var unlocks = 0

    private fun root(settings: LockSettings = LockSettings()): OverlayRoot {
        unlocks = 0
        val r = OverlayRoot(TestSupport.app, settings) { unlocks++ }
        r.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        r.layout(0, 0, w, h)
        return r
    }

    private inline fun <reified T : View> child(root: OverlayRoot): T? =
        (0 until root.childCount).map(root::getChildAt).filterIsInstance<T>().firstOrNull()

    private val salt = PinHasher.newSalt()

    private fun pinSettings(holdMs: Long = 800) = LockSettings(
        gesture = GestureType.BADGE_PIN,
        holdMs = holdMs,
        pinHash = PinHasher.hash("2468", salt),
        pinSalt = salt,
        pinLength = 4,
    )

    private fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float) =
        (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)

    /**
     * Drags one finger per entry of [from] to the matching entry of [to] in
     * [steps] moves about 16 ms apart, the way the input system delivers a
     * real swipe, and reports whether every event was consumed.
     */
    private fun swipe(
        root: OverlayRoot,
        from: List<Pair<Float, Float>>,
        to: List<Pair<Float, Float>>,
        steps: Int = 12,
    ): Boolean {
        var consumed = root.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, from[0]))
        for (i in 1 until from.size) {
            consumed = root.dispatchTouchEvent(
                motion(MotionEvent.ACTION_POINTER_DOWN, *from.take(i + 1).toTypedArray(), actionIndex = i),
            ) && consumed
        }
        for (s in 1..steps) {
            idle(16)
            val points = from.indices.map { i -> lerp(from[i], to[i], s / steps.toFloat()) }
            consumed = root.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, *points.toTypedArray())) && consumed
        }
        idle(16)
        for (i in to.size - 1 downTo 1) {
            consumed = root.dispatchTouchEvent(
                motion(MotionEvent.ACTION_POINTER_UP, *to.take(i + 1).toTypedArray(), actionIndex = i),
            ) && consumed
        }
        return root.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, to[0])) && consumed
    }

    private fun swipe(root: OverlayRoot, from: Pair<Float, Float>, to: Pair<Float, Float>, steps: Int = 12) =
        swipe(root, listOf(from), listOf(to), steps)

    private fun assertNothingHappened(root: OverlayRoot) {
        idle(3000) // longer than any hold time in these settings
        assertEquals("nothing may unlock", 0, unlocks)
        child<PinPadView>(root)?.let { assertEquals("the keypad must stay hidden", View.GONE, it.visibility) }
    }

    @Test
    fun `swipe up from the bottom edge is swallowed`() {
        val root = root()
        assertTrue(swipe(root, 540f to (h - 1).toFloat(), 540f to 900f))
        assertNothingHappened(root)
    }

    @Test
    fun `swipe down from the top edge is swallowed`() {
        val root = root()
        assertTrue(swipe(root, 540f to 0f, 540f to 1500f))
        assertNothingHappened(root)
    }

    @Test
    fun `swipes through the middle of the screen are swallowed in both directions`() {
        val root = root()
        assertTrue(swipe(root, 540f to 1800f, 540f to 600f))
        assertTrue(swipe(root, 540f to 600f, 540f to 1800f))
        assertNothingHappened(root)
    }

    @Test
    fun `a fast fling is swallowed like a slow drag`() {
        val root = root()
        assertTrue(swipe(root, 540f to 2300f, 540f to 200f, steps = 2))
        assertTrue(swipe(root, 540f to 200f, 540f to 2300f, steps = 2))
        assertNothingHappened(root)
    }

    @Test
    fun `swipes along the side edges are swallowed too`() {
        val root = root()
        assertTrue(swipe(root, 5f to 2200f, 5f to 300f))
        assertTrue(swipe(root, (w - 5).toFloat() to 300f, (w - 5).toFloat() to 2200f))
        assertNothingHappened(root)
    }

    @Test
    fun `two-finger vertical swipes are swallowed and do not unlock`() {
        val root = root()
        assertTrue(swipe(root, listOf(300f to 2300f, 780f to 2300f), listOf(300f to 300f, 780f to 300f)))
        assertTrue(swipe(root, listOf(300f to 300f, 780f to 300f), listOf(300f to 2300f, 780f to 2300f)))
        assertNothingHappened(root)
    }

    @Test
    fun `fingers that start in the unlock corners but swipe away never unlock`() {
        // The corner hold is the fallback in every gesture mode. Both fingers
        // begin in the right corners, then drag: leaving the zones resets it.
        val root = root(LockSettings(gesture = GestureType.CORNER_HOLD, holdMs = 1000))
        assertTrue(swipe(root, listOf(40f to 60f, 1040f to 2350f), listOf(40f to 1200f, 1040f to 1300f), steps = 30))
        assertNothingHappened(root)
    }

    @Test
    fun `swiping across the badge in PIN mode does not open the keypad`() {
        val root = root(pinSettings())
        val badge = child<TouchShieldView>(root)!!.badgeRect
        assertTrue(swipe(root, badge.centerX() to 0f, badge.centerX() to 1500f))
        assertTrue(swipe(root, badge.centerX() to badge.centerY(), badge.centerX() to 1500f))
        assertTrue(swipe(root, 0f to badge.centerY(), (w - 1).toFloat() to badge.centerY()))
        assertNothingHappened(root)
    }

    @Test
    fun `every gesture mode swallows swipes without unlocking`() {
        for (gesture in GestureType.entries) {
            val settings = if (gesture == GestureType.BADGE_PIN) pinSettings() else LockSettings(gesture = gesture)
            val root = root(settings)
            assertTrue(gesture.name, swipe(root, 540f to (h - 1).toFloat(), 540f to 400f))
            assertTrue(gesture.name, swipe(root, 540f to 0f, 540f to 2000f))
            assertNothingHappened(root)
        }
    }

    @Test
    @Config(sdk = [35])
    fun `the shield claims the side edges from the system back gesture`() {
        // Only the sides can be claimed; Android never gives up the top and
        // bottom edges, so home and shade swipes are the accessibility guard's job.
        val shield = child<TouchShieldView>(root())!!
        val rects = shield.systemGestureExclusionRects
        assertEquals(2, rects.size)
        val edge = (TouchShieldView.EDGE_EXCLUSION_DP * shield.resources.displayMetrics.density).toInt()
        assertEquals(android.graphics.Rect(0, 0, edge, h), rects[0])
        assertEquals(android.graphics.Rect(w - edge, 0, w, h), rects[1])
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }
}
