package com.gbhall.childlock.guard

import com.gbhall.childlock.settings.AutoLockTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLockEngineTest {
    private val armed = mutableListOf<String>()
    private var cancels = 0
    private val engine = AutoLockEngine(object : AutoLockEngine.Listener {
        override fun requestArm(packageName: String) { armed += packageName }
        override fun cancelArm() { cancels++ }
    }).apply {
        rules = mapOf("com.video" to AutoLockTrigger.FULLSCREEN_PLAYBACK, "com.game" to AutoLockTrigger.OPEN)
    }

    @Test
    fun `chosen app arms once its moment arrives`() {
        engine.onForeground("com.video")
        assertEquals(AutoLockEngine.State.WATCHING, engine.state)
        engine.onTick(false)
        assertEquals(0, armed.size)
        engine.onTick(true)
        assertEquals(listOf("com.video"), armed)
        assertEquals(AutoLockEngine.State.ARMING, engine.state)
    }

    @Test
    fun `other apps are ignored`() {
        engine.onForeground("com.other")
        engine.onTick(true)
        assertEquals(0, armed.size)
        assertEquals(AutoLockEngine.State.IDLE, engine.state)
    }

    @Test
    fun `leaving during the countdown cancels it and returning re-watches`() {
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onForeground("com.launcher")
        assertEquals(1, cancels)
        engine.onUnlocked(byParent = false)
        engine.onForeground("com.video")
        assertEquals(AutoLockEngine.State.WATCHING, engine.state)
    }

    @Test
    fun `parent cancelling the countdown disarms until the app is left`() {
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onUnlocked(byParent = true)
        assertEquals(AutoLockEngine.State.DISARMED, engine.state)
        repeat(10) { engine.onTick(true) }
        assertEquals(1, armed.size)
        engine.onForeground("com.launcher")
        engine.onForeground("com.video")
        engine.onTick(true)
        assertEquals(2, armed.size)
    }

    @Test
    fun `after unlock, full screen again re-locks only after it ended and restarted`() {
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        assertEquals(AutoLockEngine.State.HOT, engine.state)
        repeat(5) { engine.onTick(true) }           // still full screen: nothing
        assertEquals(1, armed.size)
        repeat(AutoLockEngine.FALL_POLLS) { engine.onTick(false) }
        assertEquals(AutoLockEngine.State.REARMABLE, engine.state)
        // The parent is owed some peace after unlocking, whatever the screen does.
        repeat(AutoLockEngine.GRACE_POLLS) { engine.onTick(false) }
        engine.onTick(true)
        assertEquals(1, armed.size)                 // one poll is a blip
        engine.onTick(true)
        assertEquals(2, armed.size)
    }

    @Test
    fun `re-lock respects the switch and never applies to open rules`() {
        engine.relockEnabled = false
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        assertEquals(AutoLockEngine.State.DISARMED, engine.state)

        engine.relockEnabled = true
        engine.onForeground("com.launcher")
        engine.onForeground("com.game")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        assertEquals("open rule cannot re-lock while the app stays in front", AutoLockEngine.State.DISARMED, engine.state)
    }

    @Test
    fun `nothing re-locks during the grace period after unlocking`() {
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        repeat(AutoLockEngine.FALL_POLLS) { engine.onTick(false) }
        // Condition back on straight away, but the parent's grace is not over.
        repeat(AutoLockEngine.GRACE_POLLS - AutoLockEngine.FALL_POLLS - 1) { engine.onTick(true) }
        assertEquals(1, armed.size)
    }

    @Test
    fun `brief interruptions do not re-lock`() {
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        engine.onTick(false)
        engine.onTick(false)
        engine.onTick(true)
        engine.onTick(true)
        assertEquals("two false polls are a notification peek, not the end", 1, armed.size)
    }
}

/**
 * Reproduces what a parent actually does in YouTube: unlock, then touch the
 * screen. YouTube shows its controls (the status bar reappears, so the
 * full-screen condition reads false) and hides them again a few seconds
 * later. That must not be mistaken for leaving and re-entering full screen.
 */
class AutoLockYouTubeRelockTest {
    private val armed = mutableListOf<String>()
    private val engine = AutoLockEngine(object : AutoLockEngine.Listener {
        override fun requestArm(packageName: String) { armed += packageName }
        override fun cancelArm() {}
    }).apply { rules = mapOf("com.google.android.youtube" to AutoLockTrigger.FULLSCREEN_PLAYBACK) }

    private fun seconds(n: Int, conditionTrue: Boolean) = repeat(n) { engine.onTick(conditionTrue) }

    @Test
    fun `showing and hiding the player controls does not re-lock`() {
        engine.onForeground("com.google.android.youtube")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        val armsAfterUnlock = armed.size

        // The parent taps: controls appear for three seconds, then fade.
        seconds(3, false)
        seconds(10, true)
        assertEquals("a control bar flicker must not re-lock", armsAfterUnlock, armed.size)

        // And again, because parents tap more than once.
        seconds(4, false)
        seconds(10, true)
        assertEquals(armsAfterUnlock, armed.size)
    }

    @Test
    fun `genuinely leaving full screen and going back in does re-lock`() {
        engine.onForeground("com.google.android.youtube")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        val armsAfterUnlock = armed.size

        // Out of full screen for a good while: browsing, choosing another video.
        seconds(40, false)
        // Back into full screen.
        seconds(5, true)
        assertEquals("this is the case the option exists for", armsAfterUnlock + 1, armed.size)
    }
}

/** If the parent keeps having to unlock, the app must stop locking them. */
class AutoLockBackoffTest {
    private val armed = mutableListOf<String>()
    private val engine = AutoLockEngine(object : AutoLockEngine.Listener {
        override fun requestArm(packageName: String) { armed += packageName }
        override fun cancelArm() {}
    }).apply { rules = mapOf("com.video" to AutoLockTrigger.FULLSCREEN_PLAYBACK) }

    private fun lockThenUnlock() {
        repeat(AutoLockEngine.GRACE_POLLS + AutoLockEngine.FALL_POLLS + 2) { engine.onTick(false) }
        repeat(AutoLockEngine.RISE_POLLS + 1) { engine.onTick(true) }
        engine.onLocked()
        engine.onUnlocked(byParent = true)
    }

    @Test
    fun `after three unlocks in the same app it stops locking until they leave`() {
        engine.onForeground("com.video")
        engine.onTick(true)
        engine.onLocked()
        engine.onUnlocked(byParent = true)
        lockThenUnlock()
        lockThenUnlock()
        val armsSoFar = armed.size
        assertEquals(AutoLockEngine.State.DISARMED, engine.state)

        // However long the video plays, it will not lock again here.
        repeat(300) { engine.onTick(true) }
        assertEquals(armsSoFar, armed.size)

        // Leaving and coming back is a fresh start, as the parent would expect.
        engine.onForeground("com.launcher")
        engine.onForeground("com.video")
        engine.onTick(true)
        assertEquals(armsSoFar + 1, armed.size)
    }
}
