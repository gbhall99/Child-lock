package com.gbhall.childlock.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.billing.FeatureGate

/** One place that explains Pro and starts a purchase. Billing wiring lands here later. */
object Paywall {
    /** Runs [onAllowed] if the feature is available, otherwise shows the upgrade sheet. */
    fun require(activity: Activity, feature: FeatureGate.Feature, onAllowed: () -> Unit) {
        if (FeatureGate.has(activity, feature)) onAllowed() else show(activity)
    }

    fun show(activity: Activity) {
        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.pro_title))
        if (FeatureGate.BILLING_READY) {
            builder.setMessage(activity.getString(R.string.pro_body))
                .setPositiveButton(activity.getString(R.string.pro_upgrade, FeatureGate.priceLabel(activity))) { _, _ ->
                    // Play Billing launches the purchase flow here.
                    Toast.makeText(activity, R.string.pro_coming, Toast.LENGTH_LONG).show()
                }
                .setNegativeButton(R.string.pro_not_now, null)
        } else {
            // Never offer a purchase that cannot be completed.
            builder.setMessage(activity.getString(R.string.pro_body_soon))
                .setPositiveButton(R.string.done, null)
        }
        builder.show()
    }
}
