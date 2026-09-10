package com.gbhall.childlock.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.gbhall.childlock.TestSupport
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the settings screen to PNG files for visual review. Writes nothing
 * unless the CHILDLOCK_SHOTS environment variable names a directory.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {
    private val outDir: File? = System.getenv("CHILDLOCK_SHOTS")?.let(::File)?.takeIf { it.isDirectory }

    private fun shoot(name: String, night: Boolean, overlay: Boolean, setup: Boolean = false) {
        val dir = outDir ?: return
        ShadowSettings.setCanDrawOverlays(overlay)
        TestSupport.clearSettings()
        com.gbhall.childlock.settings.SettingsRepository.get(TestSupport.app).setupDismissed = true
        org.robolectric.RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val activity = if (setup) {
            Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        } else {
            Robolectric.buildActivity(MainActivity::class.java).setup().get()
        }
        val root = activity.window.decorView
        val w = 1080
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val h = root.measuredHeight.coerceAtLeast(2400)
        root.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun banners() {
        val dir = outDir ?: return
        val ctx = TestSupport.app
        val d = ctx.resources.displayMetrics.density
        val frame = android.widget.FrameLayout(ctx).apply { setBackgroundColor(0xFF6B7280.toInt()) }
        val column = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL }
        listOf(
            com.gbhall.childlock.lock.BannerWindow.Kind.ARMING to "Touches stop working in 15 s. Switch away to cancel.",
            com.gbhall.childlock.lock.BannerWindow.Kind.ON to "Touches do nothing. Volume up then down to unlock.",
            com.gbhall.childlock.lock.BannerWindow.Kind.OFF to "Touch works again.",
        ).forEach { (kind, detail) ->
            column.addView(com.gbhall.childlock.lock.BannerWindow.build(ctx, kind, detail),
                android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (24 * d).toInt() })
        }
        frame.addView(column, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val w = 1080; val h = 700
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        FileOutputStream(File(dir, "banners.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun light() = shoot("settings-light", night = false, overlay = true)
    @Test fun dark() = shoot("settings-dark", night = true, overlay = false)
    @Test fun setupLight() = shoot("setup-light", night = false, overlay = true, setup = true)
    @Test fun setupDark() = shoot("setup-dark", night = true, overlay = false, setup = true)
}
