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
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.pro_title))
            .setMessage(activity.getString(R.string.pro_body))
            .setPositiveButton(activity.getString(R.string.pro_upgrade, FeatureGate.PRICE_LABEL)) { _, _ ->
                // Play Billing launches the purchase flow here; until then, explain.
                Toast.makeText(activity, R.string.pro_coming, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.pro_not_now, null)
            .show()
    }
}
