package com.gbhall.childlock.review

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play In-App Review. Play decides whether the sheet actually appears
 * (it has its own quota), and never says whether a review was left; the app
 * asks once and moves on.
 */
internal class PlayReview : ReviewBackend {
    override fun request(activity: Activity, onDone: () -> Unit) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.i(TAG, "review flow unavailable: ${request.exception?.message}")
                onDone()
                return@addOnCompleteListener
            }
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener { onDone() }
        }
    }

    private companion object {
        const val TAG = "PlayReview"
    }
}
