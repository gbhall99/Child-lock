package com.gbhall.childlock.gesture

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the PIN as a slow hash with a per-install salt.
 *
 * A four-digit PIN is only ten thousand possibilities, so a single fast hash
 * with a shared salt could be reversed from preferences instantly, and the
 * same PIN would produce the same hash on every phone. A random salt per
 * install plus many PBKDF2 rounds makes both attacks impractical while still
 * costing a few milliseconds when a parent types their PIN.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.fromHex(), ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
    }

    /** Constant-time comparison, so a wrong PIN reveals nothing by how long it took. */
    fun matches(pin: String, salt: String?, expectedHash: String?): Boolean {
        if (salt.isNullOrEmpty() || expectedHash.isNullOrEmpty()) return false
        val actual = hash(pin, salt)
        if (actual.length != expectedHash.length) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].code xor expectedHash[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
