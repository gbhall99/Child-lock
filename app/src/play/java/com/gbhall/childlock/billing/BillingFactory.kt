package com.gbhall.childlock.billing

/** Play flavour: Google Play Billing sells the one-time unlock. */
object BillingFactory {
    fun create(): PurchaseBackend = PlayBilling()
}
