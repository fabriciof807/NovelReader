package com.novelreader.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
class WallpaperCropStorageTest {

    private lateinit var context: Context
    private lateinit var storage: WallpaperStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = WallpaperStorage(context, Dispatchers.Unconfined)
        File(context.filesDir, WallpaperStorage.DIR).deleteRecursively()
    }

    private fun sourceImage(width: Int = 200, height: Int = 400): File {
        val file = File(context.cacheDir, "source.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun wallpapersDir(): File = File(context.filesDir, WallpaperStorage.DIR)

    @Test
    fun `crops into a new slot file and reports its reference`() = runTest {
        val source = sourceImage()

        val ref = storage.saveCropped(
            slot = WallpaperStorage.SLOT_HOME,
            uri = Uri.fromFile(source),
            crop = WallpaperCrop(zoom = 1.5f, panX = 0.4f),
            targetWidth = 100,
            targetHeight = 200
        )

        assertThat(ref).isNotNull()
        assertThat(ref).startsWith("file:home_")
        assertThat(ref).endsWith(".jpg")
        val stored = File(wallpapersDir(), requireNotNull(ref).removePrefix("file:"))
        assertThat(stored.exists()).isTrue()
        val decoded = BitmapFactory.decodeFile(stored.absolutePath)
        assertThat(decoded.width).isEqualTo(100)
        assertThat(decoded.height).isEqualTo(200)
    }

    @Test
    fun `cropping replaces the previous file of that slot`() = runTest {
        val first = storage.saveCropped(
            slot = WallpaperStorage.SLOT_HOME,
            uri = Uri.fromFile(sourceImage()),
            crop = WallpaperCrop(),
            targetWidth = 100,
            targetHeight = 200
        )
        val second = storage.saveCropped(
            slot = WallpaperStorage.SLOT_HOME,
            uri = Uri.fromFile(sourceImage()),
            crop = WallpaperCrop(zoom = 2f),
            targetWidth = 100,
            targetHeight = 200
        )
        assertThat(first).isNotNull()
        assertThat(second).isNotNull()

        val names = wallpapersDir().listFiles()!!.map { it.name }
        assertThat(names).doesNotContain(requireNotNull(first).removePrefix("file:"))
    }

    @Test
    fun `both slots can hold a crop at the same time`() = runTest {
        val home = storage.saveCropped(
            slot = WallpaperStorage.SLOT_HOME,
            uri = Uri.fromFile(sourceImage()),
            crop = WallpaperCrop(),
            targetWidth = 100,
            targetHeight = 200
        )
        val reader = storage.saveCropped(
            slot = WallpaperStorage.SLOT_READER,
            uri = Uri.fromFile(sourceImage()),
            crop = WallpaperCrop(),
            targetWidth = 100,
            targetHeight = 200
        )
        assertThat(home).startsWith("file:home_")
        assertThat(reader).startsWith("file:reader_")
    }

    @Test
    fun `rejects an unknown slot and an empty target`() = runTest {
        val source = sourceImage()

        assertThat(
            storage.saveCropped("../../x", Uri.fromFile(source), WallpaperCrop(), 100, 200)
        ).isNull()
        assertThat(
            storage.saveCropped(WallpaperStorage.SLOT_HOME, Uri.fromFile(source), WallpaperCrop(), 0, 0)
        ).isNull()
        assertThat(wallpapersDir().listFiles()).isNull()
    }

    @Test
    fun `rejects a source that is not an image`() = runTest {
        val notAnImage = File(context.cacheDir, "notes.txt").apply { writeText("hello") }

        val ref = storage.saveCropped(
            slot = WallpaperStorage.SLOT_HOME,
            uri = Uri.fromFile(notAnImage),
            crop = WallpaperCrop(),
            targetWidth = 100,
            targetHeight = 200
        )

        assertThat(ref).isNull()
    }
}
