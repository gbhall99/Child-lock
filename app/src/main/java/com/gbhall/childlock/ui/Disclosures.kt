package com.gbhall.childlock.ui

import android.app.Activity
import android.app.AlertDialog
import com.gbhall.childlock.R

/**
 * Google Play's AccessibilityService policy requires a prominent in-app
 * disclosure, and consent, before sending the user to enable the service.
 */
object Disclosures {
    fun accessibility(activity: Activity, onAgree: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_body)
            .setPositiveButton(R.string.disclosure_agree) { _, _ -> onAgree() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
