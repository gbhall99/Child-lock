package com.gbhall.childlock.billing

import android.app.Activity
import android.content.Context

/**
 * The store this build can sell through. The `play` flavour talks to Google
 * Play Billing; `sideload` has no store, so nothing is ever withheld there.
 * Tests swap in a fake through [Billing.backend].
 */
interface PurchaseBackend {
    /** False when this build cannot sell anything, in which case the app is simply unlocked. */
    val canSell: Boolean

    /** The localised price of the one-time purchase, or null until the store has answered. */
    val price: String?

    /**
     * Connects to the store once and restores an existing purchase. Cheap to
     * call again: later calls only refresh the price and purchase state.
     */
    fun connect(context: Context)

    /** Opens the store's purchase sheet. False when the store is not reachable right now. */
    fun buy(activity: Activity): Boolean

    /** Asks the store again whether this account owns the app; [onDone] gets the answer on the main thread. */
    fun restore(context: Context, onDone: (purchased: Boolean) -> Unit)
}

/** The single [PurchaseBackend] in use, and the screens that want to hear when it changes. */
object Billing {
    var backend: PurchaseBackend = BillingFactory.create()

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Backends call this on the main thread after the price or the purchase state changed. */
    fun changed() {
        listeners.toList().forEach { it() }
    }
}
