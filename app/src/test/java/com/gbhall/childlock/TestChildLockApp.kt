package com.gbhall.childlock

import com.gbhall.childlock.billing.Billing
import com.gbhall.childlock.billing.FakeBilling

/**
 * Robolectric's application (see robolectric.properties): the real app with
 * a fake store in front of it, so no test talks to Google Play and both
 * flavours exercise the trial the same way.
 */
class TestChildLockApp : ChildLockApp() {
    override fun onCreate() {
        Billing.backend = FakeBilling()
        super.onCreate()
    }
}
