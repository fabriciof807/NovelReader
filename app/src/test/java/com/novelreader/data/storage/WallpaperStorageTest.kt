package com.novelreader.data.storage

import android.content.Context
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WallpaperStorageTest {

    private lateinit var context: Context
    private lateinit var storage: WallpaperStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = WallpaperStorage(context, Dispatchers.Unconfined)
        File(context.filesDir, WallpaperStorage.DIR).deleteRecursively()
    }

    private fun sourceFile(name: String, size: Int = 64): File {
        val file = File(context.cacheDir, name)
        file.writeBytes(ByteArray(size) { it.toByte() })
        return file
    }

    private fun wallpapersDir(): File = File(context.filesDir, WallpaperStorage.DIR)

    @Test
    fun `imports a picked image into the app private directory`() = runTest {
        val ref = storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("pic.jpg")))

        assertThat(ref).isNotNull()
        assertThat(ref).startsWith("file:home_")
        assertThat(ref).endsWith(".jpg")
        val stored = File(wallpapersDir(), requireNotNull(ref).removePrefix("file:"))
        assertThat(stored.exists()).isTrue()
        assertThat(stored.canonicalPath).startsWith(wallpapersDir().canonicalPath)
    }

    @Test
    fun `re-importing a slot replaces the previous file`() = runTest {
        val first = storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("a.jpg")))
        val second = storage.importFromUri(WallpaperStorage.SLOT_READER, Uri.fromFile(sourceFile("b.png")))
        val third = storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("c.webp")))

        assertThat(first).isNotNull()
        assertThat(second).isNotNull()
        assertThat(third).isNotNull()
        assertThat(wallpapersDir().listFiles()!!.map { it.name })
            .containsExactly(
                requireNotNull(second).removePrefix("file:"),
                requireNotNull(third).removePrefix("file:")
            )
    }

    @Test
    fun `rejects an unknown slot`() = runTest {
        val ref = storage.importFromUri("../../shared_prefs", Uri.fromFile(sourceFile("pic.jpg")))

        assertThat(ref).isNull()
        assertThat(wallpapersDir().listFiles()).isNull()
    }

    @Test
    fun `rejects a file that is not an image`() = runTest {
        assertThat(
            storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("notes.exe")))
        ).isNull()
        assertThat(
            storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("notes.jpg.exe")))
        ).isNull()
        assertThat(
            storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("noextension")))
        ).isNull()
    }

    @Test
    fun `rejects an image larger than the cap and leaves nothing behind`() = runTest {
        val big = sourceFile("big.jpg", size = 1024)

        assertThat(storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(big))).isNotNull()

        val capped = WallpaperStorage(context, Dispatchers.Unconfined)
        val oversized = File(context.cacheDir, "huge.jpg")
        oversized.outputStream().use { output ->
            val chunk = ByteArray(1024 * 1024)
            repeat((WallpaperStorage.MAX_BYTES / chunk.size).toInt() + 2) { output.write(chunk) }
        }

        assertThat(capped.importFromUri(WallpaperStorage.SLOT_READER, Uri.fromFile(oversized)))
            .isNull()
        assertThat(wallpapersDir().listFiles()!!.map { it.name.startsWith("reader_") }).contains(false)
    }

    @Test
    fun `delete only removes files inside the wallpaper directory`() = runTest {
        val outside = sourceFile("outside.jpg")
        val ref = storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("in.jpg")))

        assertThat(storage.delete("file:../cache/outside.jpg")).isFalse()
        assertThat(outside.exists()).isTrue()
        assertThat(storage.delete(requireNotNull(ref))).isTrue()
        assertThat(File(wallpapersDir(), ref.removePrefix("file:")).exists()).isFalse()
    }

    @Test
    fun `clearSlot removes only the files of that slot`() = runTest {
        val home = storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("a.jpg")))
        val reader = storage.importFromUri(WallpaperStorage.SLOT_READER, Uri.fromFile(sourceFile("b.jpg")))

        storage.clearSlot(WallpaperStorage.SLOT_HOME)

        assertThat(WallpaperStorage.fileNameOf(requireNotNull(home))).isNotNull()
        assertThat(wallpapersDir().listFiles()!!.map { it.name })
            .containsExactly(requireNotNull(reader).removePrefix("file:"))
    }

    @Test
    fun `clearSlot ignores an unknown slot`() = runTest {
        val home = storage.importFromUri(WallpaperStorage.SLOT_HOME, Uri.fromFile(sourceFile("a.jpg")))

        storage.clearSlot("..")

        assertThat(wallpapersDir().listFiles()!!.map { it.name })
            .containsExactly(requireNotNull(home).removePrefix("file:"))
    }

    @Test
    fun `resolveFile refuses traversal and missing files`() {
        val filesDir = context.filesDir

        assertThat(WallpaperStorage.resolveFile(filesDir, "file:../../app_prefs.xml")).isNull()
        assertThat(WallpaperStorage.resolveFile(filesDir, "file:/data/data/x.jpg")).isNull()
        assertThat(WallpaperStorage.resolveFile(filesDir, "file:a/b.jpg")).isNull()
        assertThat(WallpaperStorage.resolveFile(filesDir, "builtin:noite")).isNull()
        assertThat(WallpaperStorage.resolveFile(filesDir, "none")).isNull()
        assertThat(WallpaperStorage.resolveFile(filesDir, "file:missing.jpg")).isNull()
    }

    @Test
    fun `resolveFile returns the stored file for a valid reference`() {
        val dir = wallpapersDir().apply { mkdirs() }
        val file = File(dir, "home_1.jpg").apply { writeBytes(ByteArray(4)) }

        val resolved = WallpaperStorage.resolveFile(context.filesDir, "file:home_1.jpg")

        assertThat(resolved).isEqualTo(file)
    }

    @Test
    fun `builtin references are recognised and unknown ones rejected`() {
        assertThat(WallpaperStorage.builtinId("builtin:noite")).isEqualTo("noite")
        assertThat(WallpaperStorage.builtinId("builtin:inventado")).isNull()
        assertThat(WallpaperStorage.builtinId("none")).isNull()
        assertThat(WallpaperStorage.builtinId("file:home_1.jpg")).isNull()
        assertThat(WallpaperStorage.isBuiltin("builtin:aurora")).isTrue()
        assertThat(WallpaperStorage.isBuiltin("builtin:nope")).isFalse()
    }
}
