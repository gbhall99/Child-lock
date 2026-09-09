package com.gbhall.childlock.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun `matching pin verifies`() {
        assertTrue(PinHasher.matches("2468", PinHasher.hash("2468")))
    }

    @Test
    fun `wrong pin, empty hash and null hash all fail`() {
        assertFalse(PinHasher.matches("2469", PinHasher.hash("2468")))
        assertFalse(PinHasher.matches("2468", ""))
        assertFalse(PinHasher.matches("2468", null))
    }

    @Test
    fun `hash is not the pin`() {
        assertNotEquals("2468", PinHasher.hash("2468"))
    }
}
