package com.gbhall.childlock.review

import android.app.Activity

/** Sideload flavour: no store, so no review sheet. */
object ReviewFactory {
    fun create(): ReviewBackend = object : ReviewBackend {
        override fun request(activity: Activity, onDone: () -> Unit) = onDone()
    }
}
