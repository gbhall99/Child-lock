package com.gbhall.childlock.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUnlockWatchTest {
    @Test
    fun `getting past a lock screen that asked for a face or PIN is the owner`() {
        val w = PhoneUnlockWatch()
        w.observe(deviceLocked = true)
        assertTrue(w.onPhoneUnlocked())
    }

    @Test
    fun `a lock screen that asked for nothing proves nothing`() {
        val w = PhoneUnlockWatch()
        w.observe(deviceLocked = false)
        w.observe(deviceLocked = false)
        assertFalse("swipe-only or Smart Lock", w.onPhoneUnlocked())
    }

    @Test
    fun `face unlock before the swipe still counts`() {
        // The phone asked for credentials when the screen went dark; by the time
        // the parent swipes up, face unlock has already passed.
        val w = PhoneUnlockWatch()
        w.observe(deviceLocked = true)
        w.observe(deviceLocked = false)
        assertTrue(w.onPhoneUnlocked())
    }

    @Test
    fun `each trip past the lock screen is judged on its own`() {
        val w = PhoneUnlockWatch()
        w.observe(deviceLocked = true)
        assertTrue(w.onPhoneUnlocked())
        assertFalse("the last unlock does not carry over", w.onPhoneUnlocked())
        w.observe(deviceLocked = true)
        w.reset()
        assertFalse(w.onPhoneUnlocked())
    }
}
