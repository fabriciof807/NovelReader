package com.novelreader.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperToneSamplingTest {

    private lateinit var context: Context
    private lateinit var storage: WallpaperStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = WallpaperStorage(context, Dispatchers.Unconfined)
        File(context.filesDir, WallpaperStorage.DIR).deleteRecursively()
    }

    private fun writeImage(name: String, size: Int, paint: (Bitmap) -> Unit): File {
        val dir = File(context.filesDir, WallpaperStorage.DIR).apply { mkdirs() }
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        paint(bitmap)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun `a light image reads light and a dark one reads dark`() = runTest {
        writeImage("home_light.png", 64) { it.eraseColor(android.graphics.Color.WHITE) }
        writeImage("home_dark.png", 64) { it.eraseColor(android.graphics.Color.BLACK) }

        assertThat(sampledIsLight(storage.wallpaperLuminance("file:home_light.png"))).isTrue()
        assertThat(sampledIsLight(storage.wallpaperLuminance("file:home_dark.png"))).isFalse()
    }

    @Test
    fun `the luminance is the average over the whole image`() = runTest {
        val file = writeImage("home_half.png", 64) { bitmap ->
            val pixels = IntArray(bitmap.width * bitmap.height) { index ->
                if (index % bitmap.width < bitmap.width / 2) android.graphics.Color.WHITE
                else android.graphics.Color.BLACK
            }
            bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

        assertThat(storage.wallpaperLuminance("file:${file.name}")).isWithin(0.05f).of(0.5f)
    }

    // A 1024px wallpaper decodes at 1/16 through inSampleSize; averaging it at full size would cost
    // 4 MB of bitmap for a single boolean.
    @Test
    fun `a large image is sampled down instead of decoded whole`() = runTest {
        writeImage("home_big.png", 1024) { it.eraseColor(android.graphics.Color.WHITE) }

        assertThat(sampledIsLight(storage.wallpaperLuminance("file:home_big.png"))).isTrue()
    }

    @Test
    fun `a reference that does not resolve has no luminance`() = runTest {
        assertThat(storage.wallpaperLuminance("file:missing.png")).isNull()
        assertThat(storage.wallpaperLuminance("builtin:areia")).isNull()
        assertThat(storage.wallpaperLuminance("file:../../app_prefs.xml")).isNull()
        assertThat(storage.wallpaperLuminance(WallpaperStorage.SLOT_HOME)).isNull()
    }

    @Test
    fun `a file that is not an image has no luminance`() = runTest {
        val dir = File(context.filesDir, WallpaperStorage.DIR).apply { mkdirs() }
        File(dir, "home_notes.png").writeBytes(ByteArray(64) { it.toByte() })

        assertThat(storage.wallpaperLuminance("file:home_notes.png")).isNull()
    }

    @Test
    fun `the pending crop image can be sampled before it is copied`() = runTest {
        val source = File(context.cacheDir, "picked.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        assertThat(sampledIsLight(storage.uriLuminance(Uri.fromFile(source)))).isTrue()
        assertThat(storage.uriLuminance(Uri.fromFile(File(context.cacheDir, "nope.png")))).isNull()
    }

    private fun sampledIsLight(luminance: Float?): Boolean? = luminance?.let(::isLightWallpaper)
}
