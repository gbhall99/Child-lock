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
