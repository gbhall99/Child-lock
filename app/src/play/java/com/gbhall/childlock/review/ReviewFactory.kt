package com.gbhall.childlock.review

/** Play flavour: Google Play's in-app review sheet. */
object ReviewFactory {
    fun create(): ReviewBackend = PlayReview()
}
