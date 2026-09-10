package com.gbhall.childlock.guard

/**
 * Decides whether an on-screen button is a "skip ad" control. Exact-match on
 * a short allowlist after normalisation; anything that smells like the ad
 * itself is refused. Pure, so the rules are unit tested.
 */
object SkipAdMatcher {
    /** Apps where skip-ad tapping is allowed at all. */
    val supportedPackages: Set<String> = setOf("com.google.android.youtube")

    private val allowed = setOf("skip ad", "skip ads", "skip advert", "skip adverts", "skip")
    private val forbidden = listOf("learn more", "visit", "install", "open", "shop", "download", "sponsored", "http", "buy", "sign up", "subscribe")

    fun normalise(label: CharSequence?): String =
        label?.toString()?.lowercase()?.replace(Regex("[›»>»→.!…]+$"), "")?.replace(Regex("\\s+"), " ")?.trim() ?: ""

    fun isSkipLabel(label: CharSequence?): Boolean = normalise(label) in allowed

    fun looksLikeAd(vararg texts: CharSequence?): Boolean =
        texts.any { t -> val n = normalise(t); forbidden.any { it in n } }

    /** The skip button is a small pill; ad creatives are large surfaces. */
    fun sizeAcceptable(width: Int, height: Int, screenWidth: Int, screenHeight: Int): Boolean =
        width > 0 && height > 0 && width <= screenWidth * 0.35f && height <= screenHeight * 0.12f

    fun isCandidate(
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
        clickable && enabled && visible && !editable &&
            (isSkipLabel(text) || isSkipLabel(contentDescription)) &&
            !looksLikeAd(text, contentDescription, parentText) &&
            sizeAcceptable(width, height, screenWidth, screenHeight)
}
