package com.gbhall.childlock.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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

    private fun shoot(name: String, night: Boolean, overlay: Boolean) {
        val dir = outDir ?: return
        ShadowSettings.setCanDrawOverlays(overlay)
        TestSupport.clearSettings()
        org.robolectric.RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
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

    @Test fun light() = shoot("settings-light", night = false, overlay = true)
    @Test fun dark() = shoot("settings-dark", night = true, overlay = false)
}
