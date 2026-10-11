package com.gbhall.childlock

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Robolectric shows every app to every app; a real phone (Android 11+) does not. */
class ManifestTest {
    private val queries = File("src/main/AndroidManifest.xml").readText()
        .substringAfter("<queries>").substringBefore("</queries>")

    @Test
    fun `the home screen app is visible, so a lock started there keeps the home screen`() {
        assertTrue(queries.contains("""<category android:name="android.intent.category.HOME" />"""))
    }

    @Test
    fun `launchable apps are visible, so the protected app can be brought back`() {
        assertTrue(queries.contains("""<category android:name="android.intent.category.LAUNCHER" />"""))
    }
}
