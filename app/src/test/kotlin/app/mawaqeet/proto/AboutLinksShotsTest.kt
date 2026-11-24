package app.mawaqeet.proto

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.AlertPermissionsRule
import app.mawaqeet.MainActivity
import app.mawaqeet.ui.settings.aboutLinksVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** PROTOTYPE: renders the About page's top for the about-links proposal. Not committed. */
abstract class AboutShots(private val tag: String) {
    @get:Rule(order = 0) val permissions = AlertPermissionsRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(variant: Char) {
        aboutLinksVariant = variant
        compose.onNodeWithText("الإعدادات").performClick(); compose.waitForIdle()
        compose.onNodeWithText("حول مواقيت وبياناتك").performScrollTo().performClick(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("user.dir"), "../build/about-links").apply { mkdirs() }
        File(dir, "$variant-$tag.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun variantA() = shot('A')
    @Test fun variantB() = shot('B')
}

@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [36], qualifiers = "ar-w360dp-h740dp-notnight-xxhdpi") class AboutLight360 : AboutShots("light-360x740")
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [36], qualifiers = "ar-w393dp-h852dp-notnight-xxhdpi") class AboutLight393 : AboutShots("light-393x852")
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-notnight-xxhdpi") class AboutLight412 : AboutShots("light-412x915")
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [36], qualifiers = "ar-w360dp-h740dp-night-xxhdpi") class AboutDark360 : AboutShots("dark-360x740")
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [36], qualifiers = "ar-w393dp-h852dp-night-xxhdpi") class AboutDark393 : AboutShots("dark-393x852")
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-night-xxhdpi") class AboutDark412 : AboutShots("dark-412x915")
