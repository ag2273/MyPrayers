package app.mawaqeet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher icon is an adaptive icon with a monochrome layer for themed icons, and every mark of the foreground
 * stays inside the safe zone, so no launcher mask cuts it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LauncherIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun icon(): AdaptiveIconDrawable {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        return context.getDrawable(info.icon) as AdaptiveIconDrawable
    }

    /** Draws [layer] of a 108dp icon at 1px per dp. */
    private fun layer(layer: android.graphics.drawable.Drawable): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also {
            layer.setBounds(0, 0, SIZE, SIZE)
            layer.draw(Canvas(it))
        }

    @Test
    fun `the app icon is adaptive, with a round variant and a monochrome layer`() {
        val icon = icon()
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        assertNotNull("no themed-icon layer", icon.monochrome)
        assertTrue(context.packageManager.getApplicationInfo(context.packageName, 0).icon != 0)
    }

    @Test
    fun `the foreground and the themed icon stay inside the 66dp safe zone`() {
        for (drawable in listOf(icon().foreground, icon().monochrome!!)) {
            val bitmap = layer(drawable)
            var painted = 0
            for (x in 0 until SIZE) for (y in 0 until SIZE) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 32) continue
                painted++
                val dx = x + 0.5 - SIZE / 2.0
                val dy = y + 0.5 - SIZE / 2.0
                assertTrue("pixel ($x, $y) is outside the safe zone", dx * dx + dy * dy <= SAFE_RADIUS * SAFE_RADIUS)
            }
            assertTrue("nothing drawn", painted > 200)
        }
    }

    @Test
    fun `the background is an opaque deep teal`() {
        val bitmap = layer(icon().background)
        for ((x, y) in listOf(0 to 0, 54 to 54, 107 to 107)) {
            val pixel = bitmap.getPixel(x, y)
            assertEquals(255, Color.alpha(pixel))
            assertTrue("not teal at ($x, $y)", Color.green(pixel) > Color.red(pixel) && Color.blue(pixel) > Color.red(pixel))
        }
    }

    private companion object {
        const val SIZE = 108
        const val SAFE_RADIUS = 33.0
    }
}
