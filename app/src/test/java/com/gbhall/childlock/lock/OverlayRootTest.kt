package com.gbhall.childlock.lock

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.TestSupport.idle
import com.gbhall.childlock.gesture.PinHasher
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.LockSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayRootTest {
    private var unlocks = 0

    private fun root(settings: LockSettings): OverlayRoot {
        unlocks = 0
        val r = OverlayRoot(TestSupport.app, settings) { unlocks++ }
        r.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        r.layout(0, 0, 1080, 2400)
        return r
    }

    private val salt = PinHasher.newSalt()

    private fun pinSettings() = LockSettings(
        gesture = GestureType.BADGE_PIN,
        pinHash = PinHasher.hash("2468", salt),
        pinSalt = salt,
        pinLength = 4,
    )

    private fun pad(root: OverlayRoot): PinPadView? =
        (0 until root.childCount).map(root::getChildAt).filterIsInstance<PinPadView>().firstOrNull()

    private fun press(pad: PinPadView, label: String) {
        fun find(v: View): TextView? {
            if (v is TextView && v.text == label && v.isClickable) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        val key = find(pad) ?: error("no key $label")
        key.performClick()
    }

    private fun type(pad: PinPadView, digits: String) = digits.forEach { press(pad, it.toString()) }

    @Test
    fun `corner hold mode has no pin pad`() {
        assertNull(pad(root(LockSettings())))
    }

    @Test
    fun `badge pin mode without a pin falls back to no pad`() {
        assertNull(pad(root(LockSettings(gesture = GestureType.BADGE_PIN))))
    }

    @Test
    fun `correct pin unlocks`() {
        val r = root(pinSettings())
        val pad = pad(r)
        assertNotNull(pad)
        assertEquals(View.GONE, pad!!.visibility)
        r.onShowPinPad()
        assertEquals(View.VISIBLE, pad.visibility)
        type(pad, "2468")
        assertEquals(1, unlocks)
        assertEquals(View.GONE, pad.visibility)
    }

    @Test
    fun `wrong pin does not unlock and backspace works`() {
        val r = root(pinSettings())
        val pad = pad(r)!!
        r.onShowPinPad()
        type(pad, "246")
        press(pad, "⌫")
        type(pad, "79")
        assertEquals(0, unlocks)
        idle(1000)
        type(pad, "2468")
        assertEquals(1, unlocks)
    }

    @Test
    fun `three wrong pins hide the pad and start a cooldown`() {
        val r = root(pinSettings())
        val pad = pad(r)!!
        r.onShowPinPad()
        repeat(3) { type(pad, "0000") }
        assertEquals(0, unlocks)
        assertEquals(View.GONE, pad.visibility)
        r.onShowPinPad()
        assertEquals("pad must stay hidden during cooldown", View.GONE, pad.visibility)
        idle(31_000)
        r.onShowPinPad()
        assertEquals(View.VISIBLE, pad.visibility)
    }

    @Test
    fun `pad hides itself when idle`() {
        val r = root(pinSettings())
        val pad = pad(r)!!
        r.onShowPinPad()
        idle(9_000)
        assertEquals(View.GONE, pad.visibility)
    }
}
