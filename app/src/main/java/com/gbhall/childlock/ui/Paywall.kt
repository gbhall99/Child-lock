package com.gbhall.childlock.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.gbhall.childlock.R
import com.gbhall.childlock.billing.Billing
import com.gbhall.childlock.billing.FeatureGate

/** One place that explains the trial, starts the purchase and restores it. */
object Paywall {
    fun show(activity: Activity) {
        val access = FeatureGate.access(activity)
        val builder = AlertDialog.Builder(activity).setTitle(R.string.buy_title)
        if (access is FeatureGate.Access.Purchased) {
            builder.setMessage(R.string.buy_done).setPositiveButton(R.string.done, null).show()
            return
        }
        val lead = when (access) {
            is FeatureGate.Access.Trial -> trialLine(activity, access)
            else -> activity.getString(R.string.trial_over) + ". " + activity.getString(R.string.trial_over_body)
        }
        builder.setMessage(lead + "\n\n" + activity.getString(R.string.buy_body))
            .setPositiveButton(buyLabel(activity)) { _, _ -> buy(activity) }
            .setNegativeButton(R.string.pro_not_now, null)
            .setNeutralButton(R.string.buy_restore) { _, _ -> restore(activity) }
            .show()
    }

    /** "Buy for £2.99" once Play has answered, "Buy" before that. */
    fun buyLabel(context: Context): String =
        Billing.backend.price?.let { context.getString(R.string.buy_for, it) } ?: context.getString(R.string.buy)

    /** "23 days left". */
    fun daysLeft(context: Context, trial: FeatureGate.Access.Trial): String =
        context.resources.getQuantityString(R.plurals.trial_days_left, trial.daysLeft, trial.daysLeft)

    /** "Free trial: 23 days left". */
    fun trialLine(context: Context, trial: FeatureGate.Access.Trial): String =
        context.getString(R.string.trial_title) + ": " + daysLeft(context, trial)

    fun buy(activity: Activity) {
        if (!Billing.backend.buy(activity)) toast(activity, R.string.toast_store_unavailable)
    }

    fun restore(context: Context) {
        toast(context, R.string.toast_restore_checking)
        Billing.backend.restore(context) { purchased ->
            toast(context, if (purchased) R.string.toast_restored else R.string.toast_restore_none)
        }
    }

    private fun toast(context: Context, resId: Int) = Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
}
