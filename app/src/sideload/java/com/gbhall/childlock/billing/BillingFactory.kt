package com.gbhall.childlock.billing

import android.app.Activity
import android.content.Context

/** Sideload flavour: no store, so nothing is for sale and nothing is withheld. */
object BillingFactory {
    fun create(): PurchaseBackend = NoBilling
}

private object NoBilling : PurchaseBackend {
    override val canSell = false
    override val price: String? = null
    override fun connect(context: Context) = Unit
    override fun buy(activity: Activity) = false
    override fun restore(context: Context, onDone: (Boolean) -> Unit) = onDone(false)
}
