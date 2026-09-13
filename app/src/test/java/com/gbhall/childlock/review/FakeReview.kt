package com.gbhall.childlock.review

import android.app.Activity

/** Records review requests instead of showing Play's sheet. */
class FakeReview : ReviewBackend {
    var requests = 0

    override fun request(activity: Activity, onDone: () -> Unit) {
        requests++
        onDone()
    }

    companion object {
        val current: FakeReview get() = Review.backend as FakeReview
    }
}
