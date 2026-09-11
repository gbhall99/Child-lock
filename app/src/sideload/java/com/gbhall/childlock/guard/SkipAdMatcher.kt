package com.gbhall.childlock.guard

/**
 * Decides whether an on-screen button is a "skip ad" control, using each
 * app's own wording. Exact match on a short per-app allowlist after
 * normalisation; anything that smells like the ad itself is refused. Pure,
 * so the rules are unit tested.
 *
 * Reality check: YouTube and a few others show a skippable-ad button; most
 * streaming services (Netflix, Disney+, Prime Video, ITVX, Channel 4, Now,
 * Paramount+, Peacock, Twitch) make ads unskippable, so nothing appears to
 * tap. For those, only the unambiguous "skip ad(s)" wording is accepted, so
 * a "Skip intro" or "Skip recap" button is never mistaken for an ad.
 */
object SkipAdMatcher {
    /** Wording that only ever means "skip this advert", in any app. */
    private val generic = setOf("skip ad", "skip ads", "skip advert", "skip adverts", "skip this ad", "skip the ad", "skip ad now")

    /** Apps whose skip control can be a bare "Skip" (YouTube's current label). */
    private val bareSkip = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.google.android.youtube.tv",
        "com.dailymotion.dailymotion",
    )

    /** Per-app extras beyond the generic set. */
    private val perApp: Map<String, Set<String>> = mapOf(
        "com.google.android.youtube" to setOf("skip"),
        "com.google.android.apps.youtube.kids" to setOf("skip"),
        "com.dailymotion.dailymotion" to setOf("skip"),
        "com.hulu.plus" to setOf("skip ad", "skip ads"),
        "com.vimeo.android.videoapp" to setOf("skip ad"),
        "com.plexapp.android" to setOf("skip ad"),
        "com.tubitv" to setOf("skip ad"),
        "com.crunchyroll.crunchyroid" to setOf("skip ad"),
        "com.spotify.music" to emptySet(), // audio ads are unskippable; "skip" there means next track
    )

    /** View-id hints that strengthen a match (never sufficient on their own). */
    private val viewIdHints = setOf("skip_ad_button", "skip_ad", "skipAd", "skip_button")

    private val forbidden = listOf("learn more", "visit", "install", "open", "shop", "download", "sponsored", "http", "buy", "sign up", "subscribe", "trial")

    /** Apps where tapping anything is refused outright. */
    private val excluded = setOf("com.spotify.music", "com.google.android.apps.youtube.music")

    fun isSupported(packageName: String): Boolean = packageName !in excluded

    fun labelsFor(packageName: String): Set<String> =
        generic + (perApp[packageName] ?: emptySet()) + (if (packageName in bareSkip) setOf("skip") else emptySet())

    fun normalise(label: CharSequence?): String =
        label?.toString()?.lowercase()?.replace(Regex("[›»>→.!…]+$"), "")?.replace(Regex("\\s+"), " ")?.trim() ?: ""

    fun isSkipLabel(label: CharSequence?, packageName: String): Boolean {
        val n = normalise(label)
        return n.isNotEmpty() && n in labelsFor(packageName) && !forbidden.any { it in n }
    }

    fun looksLikeAd(vararg texts: CharSequence?): Boolean =
        texts.any { t -> val n = normalise(t); forbidden.any { it in n } }

    fun viewIdSuggestsSkip(viewId: CharSequence?): Boolean {
        val v = viewId?.toString() ?: return false
        return viewIdHints.any { v.endsWith(it, ignoreCase = true) }
    }

    /** The skip button is a small pill; ad creatives are large surfaces. */
    fun sizeAcceptable(width: Int, height: Int, screenWidth: Int, screenHeight: Int): Boolean =
        width > 0 && height > 0 && width <= screenWidth * 0.35f && height <= screenHeight * 0.12f

    fun isCandidate(
        packageName: String,
        text: CharSequence?,
        contentDescription: CharSequence?,
        parentText: CharSequence?,
        clickable: Boolean,
        enabled: Boolean,
        visible: Boolean,
        editable: Boolean,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean =
        isSupported(packageName) &&
            clickable && enabled && visible && !editable &&
            (isSkipLabel(text, packageName) || isSkipLabel(contentDescription, packageName)) &&
            !looksLikeAd(text, contentDescription, parentText) &&
            sizeAcceptable(width, height, screenWidth, screenHeight)
}
