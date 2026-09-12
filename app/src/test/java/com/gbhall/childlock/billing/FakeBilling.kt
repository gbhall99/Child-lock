package com.gbhall.childlock.billing

import android.app.Activity
import android.content.Context

/** A store that answers instantly and records what was asked of it. */
class FakeBilling : PurchaseBackend {
    override var canSell = true
    override var price: String? = "£2.99"
    var connects = 0
    var buys = 0
    var restores = 0
    var buyResult = true
    var restoreResult = false

    override fun connect(context: Context) {
        connects++
    }

    override fun buy(activity: Activity): Boolean {
        buys++
        return buyResult
    }

    override fun restore(context: Context, onDone: (Boolean) -> Unit) {
        restores++
        onDone(restoreResult)
    }

    companion object {
        val current: FakeBilling get() = Billing.backend as FakeBilling

        /** Grants the purchase the way the real backend does: flag first, then the listeners. */
        fun purchase(context: Context, purchased: Boolean = true) {
            FeatureGate.recordPurchase(context, purchased)
            Billing.changed()
        }
    }
}
