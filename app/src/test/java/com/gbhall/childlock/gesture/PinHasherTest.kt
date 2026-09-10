package com.gbhall.childlock.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val salt = PinHasher.newSalt()

    @Test
    fun `matching pin verifies`() {
        assertTrue(PinHasher.matches("2468", salt, PinHasher.hash("2468", salt)))
    }

    @Test
    fun `wrong pin, missing salt and missing hash all fail`() {
        assertFalse(PinHasher.matches("2469", salt, PinHasher.hash("2468", salt)))
        assertFalse(PinHasher.matches("2468", salt, ""))
        assertFalse(PinHasher.matches("2468", null, PinHasher.hash("2468", salt)))
        assertFalse(PinHasher.matches("2468", salt, null))
    }

    @Test
    fun `hash is not the pin`() {
        assertNotEquals("2468", PinHasher.hash("2468", salt))
    }

    @Test
    fun `the same pin hashes differently on different installs`() {
        val other = PinHasher.newSalt()
        assertNotEquals(salt, other)
        assertNotEquals(
            "a shared salt would let one rainbow table cover every phone",
            PinHasher.hash("2468", salt),
            PinHasher.hash("2468", other),
        )
    }
}
