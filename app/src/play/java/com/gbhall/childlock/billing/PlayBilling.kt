package com.gbhall.childlock.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing behind [PurchaseBackend]: one non-consumable product,
 * [FeatureGate.PRODUCT_ID], bought once and restored from Play on every
 * launch. Client-only: Play verifies the payment, the app keeps a flag.
 *
 * Play's callbacks may arrive on any thread; everything that touches state
 * or the UI is posted to the main thread first.
 */
internal class PlayBilling : PurchaseBackend {
    override val canSell = true

    @Volatile
    override var price: String? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var appContext: Context? = null
    private val pendingRestores = mutableListOf<(Boolean) -> Unit>()

    override fun connect(context: Context) {
        appContext = context.applicationContext
        client?.let { refresh(); return }
        val built = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases.orEmpty()) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = built
        built.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    Log.w(TAG, "Play Billing unavailable: ${result.responseCode} ${result.debugMessage}")
                    settleRestores(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection re-establishes the link on the next call.
            }
        })
    }

    override fun buy(activity: Activity): Boolean {
        val c = client ?: return false
        val product = details
        if (!c.isReady || product == null) {
            connect(activity)
            return false
        }
        // A product with a single offer reports it through the singular getter;
        // the list is only populated once Play Console holds several offers.
        val offer = product.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: product.oneTimePurchaseOfferDetails
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        offer?.offerToken?.takeIf { it.isNotEmpty() }?.let { item.setOfferToken(it) }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item.build())).build()
        val result = c.launchBillingFlow(activity, params)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> true
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryPurchases() // bought on another device or before a reinstall
                true
            }
            else -> {
                Log.w(TAG, "launchBillingFlow: ${result.responseCode} ${result.debugMessage}")
                false
            }
        }
    }

    override fun restore(context: Context, onDone: (Boolean) -> Unit) {
        synchronized(pendingRestores) { pendingRestores += onDone }
        if (client == null) connect(context) else queryPurchases()
        // A first connection settles the callback from onBillingSetupFinished.
    }

    /** Fetches the price and the purchase state. A dropped connection is re-established by the library. */
    private fun refresh() {
        val c = client ?: return
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(FeatureGate.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        c.queryProductDetailsAsync(query) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails: ${result.responseCode} ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val product = found?.productDetailsList?.firstOrNull { it.productId == FeatureGate.PRODUCT_ID } ?: return@queryProductDetailsAsync
            val offer = product.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: product.oneTimePurchaseOfferDetails
            main.post {
                details = product
                price = offer?.formattedPrice
                Billing.changed()
            }
        }
        queryPurchases()
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            // A purchase sheet result only ever grants; a cancelled or pending
            // sheet must not take away what a previous query established.
            BillingClient.BillingResponseCode.OK -> apply(purchases, authoritative = false)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> Log.i(TAG, "purchase flow ended: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun queryPurchases() {
        val c = client ?: return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        c.queryPurchasesAsync(params) { result, purchases ->
            val owned = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                apply(purchases.orEmpty(), authoritative = true)
            } else {
                Log.w(TAG, "queryPurchases: ${result.responseCode} ${result.debugMessage}")
                appContext?.let(FeatureGate::isPurchased) ?: false
            }
            settleRestores(owned)
        }
    }

    /**
     * Records what the store said. An authoritative answer (a full query) may
     * also revoke, which is how a refund takes effect; a purchase-sheet
     * result may only grant.
     */
    private fun apply(purchases: List<Purchase>, authoritative: Boolean): Boolean {
        var owned = false
        for (p in purchases) {
            if (FeatureGate.PRODUCT_ID !in p.products) continue
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            owned = true
            if (!p.isAcknowledged) {
                val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                client?.acknowledgePurchase(ack) { r ->
                    if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "acknowledge: ${r.responseCode} ${r.debugMessage}")
                }
            }
        }
        val ctx = appContext
        if (ctx != null && (owned || authoritative)) {
            main.post {
                FeatureGate.recordPurchase(ctx, owned)
                Billing.changed()
            }
        }
        return owned || (ctx != null && FeatureGate.isPurchased(ctx))
    }

    private fun settleRestores(owned: Boolean) {
        val callbacks = synchronized(pendingRestores) { pendingRestores.toList().also { pendingRestores.clear() } }
        if (callbacks.isEmpty()) return
        main.post { callbacks.forEach { it(owned) } }
    }

    private companion object {
        const val TAG = "PlayBilling"
    }
}
