package com.gbhall.childlock.settings

import android.content.Context
import android.content.SharedPreferences
import com.gbhall.childlock.gesture.Corner
import com.gbhall.childlock.gesture.CornerPair
import com.gbhall.childlock.gesture.VolumePattern

/** What has to happen inside a chosen app before the lock arms itself. */
enum class AutoLockTrigger {
    /** As soon as the app is in front. */
    OPEN,
    /** Any call connected: the app put the phone into call audio mode. */
    CALL,
    /** A video call: call audio mode and a camera in use. */
    VIDEO_CALL,
    /** A voice call: call audio mode with no camera in use. */
    VOICE_CALL,
    /** When the app is playing sound or video with the status bar hidden. */
    FULLSCREEN_PLAYBACK,
    /** When the app is playing sound or video, full screen or not. */
    PLAYBACK,
}

enum class GestureType {
    /** Volume press pattern; locks and unlocks. Needs the accessibility guard. */
    VOLUME_SEQUENCE,
    CORNER_HOLD,
    BADGE_PIN,
    VOLUME_CHORD;

    /** Gestures that only the accessibility guard can see. */
    val needsGuard: Boolean get() = this == VOLUME_SEQUENCE || this == VOLUME_CHORD
}

data class LockSettings(
    val gesture: GestureType = GestureType.VOLUME_SEQUENCE,
    /** Hold duration for corner hold and volume chord; long-press duration for the badge PIN. */
    val holdMs: Long = 1500,
    val cornerPair: CornerPair = CornerPair.TOP_LEFT_BOTTOM_RIGHT,
    val badgeCorner: Corner = Corner.TOP_LEFT,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinLength: Int = 0,
    val volumePattern: VolumePattern = VolumePattern.UP_THEN_DOWN,
    /** 1 = press the pattern once, 2 = twice in a row. */
    val volumeRepeats: Int = 1,
    val keepScreenOn: Boolean = true,
    val armDelaySec: Int = 5,
    val blockKeys: Boolean = true,
    val blockShade: Boolean = true,
    val relaunchApp: Boolean = true,
    /** Stop home/back swipes outright while locked (needs the guard and a volume gesture). */
    val blockGestures: Boolean = true,
    /** Pin the screen to whatever orientation it has when the lock engages. */
    val keepOrientation: Boolean = true,
    /** Tap "Skip ad" style buttons in the app you handed over while locked. Off by default; reads button labels. */
    val skipAds: Boolean = false,
    /** After an unlock, lock again when the same app's moment happens again (full screen again, another call). */
    val relockSameApp: Boolean = true,
    /** Packages that arm the lock automatically, each with the moment that arms it (needs the guard). */
    val autoLockRules: Map<String, AutoLockTrigger> = emptyMap(),
    val autoLockDelaySec: Int = 15,
    /** Hand the phone back after this many minutes. 0 means no timer. */
    val sessionMinutes: Int = 0,
) {
    val autoLockApps: Set<String> get() = autoLockRules.keys

    val hasPin: Boolean get() = !pinHash.isNullOrEmpty() && !pinSalt.isNullOrEmpty() && pinLength >= MIN_PIN_LENGTH

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        const val MIN_HOLD_MS = 800L
        const val MAX_HOLD_MS = 3000L
        const val MIN_ARM_DELAY_SEC = 2
        const val MAX_ARM_DELAY_SEC = 10
        const val MIN_AUTO_LOCK_DELAY_SEC = 3
        const val MAX_AUTO_LOCK_DELAY_SEC = 60
        const val MAX_SESSION_MINUTES = 60
    }
}

/**
 * Preference-backed settings. Only configuration lives here; the lock state
 * itself is deliberately never persisted (see LockController).
 */
class SettingsRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("childlock", Context.MODE_PRIVATE)

    fun load(): LockSettings = LockSettings(
        gesture = prefs.enum(KEY_GESTURE, GestureType.VOLUME_SEQUENCE),
        holdMs = prefs.getLong(KEY_HOLD_MS, 1500).coerceIn(LockSettings.MIN_HOLD_MS, LockSettings.MAX_HOLD_MS),
        cornerPair = prefs.enum(KEY_CORNER_PAIR, CornerPair.TOP_LEFT_BOTTOM_RIGHT),
        badgeCorner = prefs.enum(KEY_BADGE_CORNER, Corner.TOP_LEFT),
        pinHash = prefs.getString(KEY_PIN_HASH, null),
        pinSalt = prefs.getString(KEY_PIN_SALT, null),
        pinLength = prefs.getInt(KEY_PIN_LENGTH, 0),
        volumePattern = prefs.enum(KEY_VOLUME_PATTERN, VolumePattern.UP_THEN_DOWN),
        volumeRepeats = prefs.getInt(KEY_VOLUME_REPEATS, 1).coerceIn(1, 2),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
        armDelaySec = prefs.getInt(KEY_ARM_DELAY, 5).coerceIn(LockSettings.MIN_ARM_DELAY_SEC, LockSettings.MAX_ARM_DELAY_SEC),
        blockKeys = prefs.getBoolean(KEY_BLOCK_KEYS, true),
        blockShade = prefs.getBoolean(KEY_BLOCK_SHADE, true),
        relaunchApp = prefs.getBoolean(KEY_RELAUNCH, true),
        blockGestures = prefs.getBoolean(KEY_BLOCK_GESTURES, true),
        keepOrientation = prefs.getBoolean(KEY_KEEP_ORIENTATION, true),
        skipAds = prefs.getBoolean(KEY_SKIP_ADS, false),
        relockSameApp = prefs.getBoolean(KEY_RELOCK, true),
        autoLockRules = (prefs.getStringSet(KEY_AUTO_LOCK_RULES, emptySet()) ?: emptySet()).mapNotNull { entry ->
            val i = entry.lastIndexOf('=')
            if (i <= 0) return@mapNotNull null
            val trigger = AutoLockTrigger.entries.firstOrNull { it.name == entry.substring(i + 1) } ?: return@mapNotNull null
            entry.substring(0, i) to trigger
        }.toMap(),
        autoLockDelaySec = prefs.getInt(KEY_AUTO_LOCK_DELAY, 15).coerceIn(LockSettings.MIN_AUTO_LOCK_DELAY_SEC, LockSettings.MAX_AUTO_LOCK_DELAY_SEC),
        sessionMinutes = prefs.getInt(KEY_SESSION_MIN, 0).coerceIn(0, LockSettings.MAX_SESSION_MINUTES),
    )

    fun save(s: LockSettings) {
        prefs.edit()
            .putString(KEY_GESTURE, s.gesture.name)
            .putLong(KEY_HOLD_MS, s.holdMs)
            .putString(KEY_CORNER_PAIR, s.cornerPair.name)
            .putString(KEY_BADGE_CORNER, s.badgeCorner.name)
            .putString(KEY_PIN_HASH, s.pinHash)
            .putString(KEY_PIN_SALT, s.pinSalt)
            .putInt(KEY_PIN_LENGTH, s.pinLength)
            .putString(KEY_VOLUME_PATTERN, s.volumePattern.name)
            .putInt(KEY_VOLUME_REPEATS, s.volumeRepeats)
            .putBoolean(KEY_KEEP_SCREEN_ON, s.keepScreenOn)
            .putInt(KEY_ARM_DELAY, s.armDelaySec)
            .putBoolean(KEY_BLOCK_KEYS, s.blockKeys)
            .putBoolean(KEY_BLOCK_SHADE, s.blockShade)
            .putBoolean(KEY_RELAUNCH, s.relaunchApp)
            .putBoolean(KEY_BLOCK_GESTURES, s.blockGestures)
            .putBoolean(KEY_KEEP_ORIENTATION, s.keepOrientation)
            .putBoolean(KEY_SKIP_ADS, s.skipAds)
            .putBoolean(KEY_RELOCK, s.relockSameApp)
            .putStringSet(KEY_AUTO_LOCK_RULES, s.autoLockRules.map { (pkg, t) -> "$pkg=${t.name}" }.toSet())
            .putInt(KEY_AUTO_LOCK_DELAY, s.autoLockDelaySec)
            .putInt(KEY_SESSION_MIN, s.sessionMinutes)
            .apply()
    }

    inline fun update(block: (LockSettings) -> LockSettings) = save(block(load()))

    // Setup-assistant bookkeeping, deliberately outside LockSettings.
    var tileAdded: Boolean
        get() = prefs.getBoolean(KEY_TILE_ADDED, false)
        set(v) = prefs.edit().putBoolean(KEY_TILE_ADDED, v).apply()

    var setupDismissed: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DISMISSED, false)
        set(v) = prefs.edit().putBoolean(KEY_SETUP_DISMISSED, v).apply()

    var overlayAttempted: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ATTEMPTED, false)
        set(v) = prefs.edit().putBoolean(KEY_OVERLAY_ATTEMPTED, v).apply()

    /** The caller must keep a strong reference to [listener]; SharedPreferences holds it weakly. */
    fun addChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun removeChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E {
        val name = getString(key, null) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    companion object {
        private const val KEY_GESTURE = "gesture"
        private const val KEY_HOLD_MS = "hold_ms"
        private const val KEY_CORNER_PAIR = "corner_pair"
        private const val KEY_BADGE_CORNER = "badge_corner"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_VOLUME_PATTERN = "volume_pattern"
        private const val KEY_VOLUME_REPEATS = "volume_repeats"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_ARM_DELAY = "arm_delay_sec"
        private const val KEY_BLOCK_KEYS = "block_keys"
        private const val KEY_BLOCK_SHADE = "block_shade"
        private const val KEY_RELAUNCH = "relaunch_app"
        private const val KEY_BLOCK_GESTURES = "block_gestures"
        private const val KEY_KEEP_ORIENTATION = "keep_orientation"
        private const val KEY_SKIP_ADS = "skip_ads"
        private const val KEY_RELOCK = "relock_same_app"
        private const val KEY_AUTO_LOCK_RULES = "auto_lock_rules"
        private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay_sec"
        private const val KEY_SESSION_MIN = "session_minutes"
        private const val KEY_TILE_ADDED = "tile_added"
        private const val KEY_SETUP_DISMISSED = "setup_dismissed"
        private const val KEY_OVERLAY_ATTEMPTED = "overlay_attempted"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }

        /** Tests create a fresh Application per case; drop the cached instance so it follows. */
        internal fun resetForTests() {
            instance = null
        }
    }
}
