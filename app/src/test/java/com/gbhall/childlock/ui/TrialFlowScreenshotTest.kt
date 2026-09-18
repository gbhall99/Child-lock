package com.gbhall.childlock.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.gbhall.childlock.TestSupport
import com.gbhall.childlock.billing.FakeBilling
import com.gbhall.childlock.billing.FeatureGate
import com.gbhall.childlock.settings.GestureType
import com.gbhall.childlock.settings.SettingsRepository
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the trial and purchase journey to PNG files for visual review:
 * first day, last days, trial over, the purchase sheet, and bought. Writes
 * nothing unless CHILDLOCK_SHOTS names a directory.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class TrialFlowScreenshotTest {
    private val outDir: File? = System.getenv("CHILDLOCK_SHOTS")?.let(::File)?.takeIf { it.isDirectory }
    private val day = 24L * 60 * 60 * 1000
    private var now = System.currentTimeMillis()

    @After
    fun tearDown() {
        FeatureGate.clock = { System.currentTimeMillis() }
    }

    private fun fresh() {
        ShadowSettings.setCanDrawOverlays(true)
        TestSupport.clearSettings()
        FeatureGate.clock = { now }
        FeatureGate.trialStart(TestSupport.app)
        SettingsRepository.get(TestSupport.app).apply {
            setupDismissed = true
            update { it.copy(gesture = GestureType.VOLUME_SEQUENCE) }
        }
    }

    private fun save(name: String, root: View, width: Int, minHeight: Int) {
        val dir = outDir ?: return
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val h = root.measuredHeight.coerceAtLeast(minHeight)
        root.layout(0, 0, width, h)
        val bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun home(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    private fun about(): SettingsActivity =
        Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(TestSupport.app, SettingsActivity.Page.ABOUT)).setup().get()

    private fun screen(name: String, activity: Activity) = save(name, activity.window.decorView, 1080, 2400)

    @Test
    fun journey() {
        if (outDir == null) return
        val start = now

        fresh()
        screen("flow-1-day1", home())
        screen("flow-1-about", about())

        now = start + 27 * day + day / 2
        fresh(); FeatureGate.trialStart(TestSupport.app) // fresh() records "now" as the start; move it back
        TestSupport.app.getSharedPreferences("childlock", 0).edit().putLong("trial_start_ms", start).apply()
        screen("flow-2-day28", home())

        now = start + 31 * day
        TestSupport.app.getSharedPreferences("childlock", 0).edit().putLong("trial_start_ms", start).apply()
        val expired = home()
        screen("flow-3-over", expired)
        screen("flow-3-about", about())

        Paywall.show(expired)
        val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
        save("flow-4-sheet", dialog.window!!.decorView, 980, 1250)

        FakeBilling.purchase(expired)
        screen("flow-5-bought", expired)
        screen("flow-5-about", about())
    }
}
