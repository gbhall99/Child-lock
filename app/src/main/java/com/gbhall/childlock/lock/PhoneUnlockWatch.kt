package com.gbhall.childlock.lock

/**
 * Whether the person who just got past the phone's own lock screen proved who
 * they are. Only a lock screen that demanded a face, fingerprint, PIN, pattern
 * or password counts: a swipe-only lock screen, or one Smart Lock kept open,
 * lets anyone through, so it leaves Child Lock on.
 *
 * A child cannot get past a secure lock screen, so whoever does is the parent,
 * and it is the way out every parent already knows. The lock service feeds
 * this from the screen and lock-screen broadcasts; it holds no Android types
 * so the decision can be tested on its own.
 */
class PhoneUnlockWatch {
    private var credentialsDemanded = false

    /** One look at the lock screen: [deviceLocked] is KeyguardManager.isDeviceLocked at that moment. */
    fun observe(deviceLocked: Boolean) {
        if (deviceLocked) credentialsDemanded = true
    }

    /** The lock screen went away. True when getting past it took the owner's face, fingerprint or credential. */
    fun onPhoneUnlocked(): Boolean {
        val owner = credentialsDemanded
        credentialsDemanded = false
        return owner
    }

    fun reset() {
        credentialsDemanded = false
    }
}
