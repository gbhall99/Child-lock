package com.gbhall.childlock.review

import android.app.Activity

/** Shows the store's own "rate this app" sheet, if this build has a store. */
interface ReviewBackend {
    fun request(activity: Activity, onDone: () -> Unit)
}

object Review {
    var backend: ReviewBackend = ReviewFactory.create()

    /** Asks once, and only when [ReviewSignals] says the parent is likely pleased. */
    fun maybeAsk(activity: Activity) {
        if (!ReviewSignals.shouldAsk(activity)) return
        ReviewSignals.markAsked(activity) // whether or not Play chooses to show it
        backend.request(activity) {}
    }
}
