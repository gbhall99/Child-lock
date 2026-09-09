package com.gbhall.childlock.gesture

import java.security.MessageDigest

/** Salted SHA-256 of the PIN so the raw digits never sit in preferences. */
object PinHasher {
    private const val SALT = "childlock-v1:"

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((SALT + pin).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, expectedHash: String?): Boolean =
        !expectedHash.isNullOrEmpty() && hash(pin) == expectedHash
}
