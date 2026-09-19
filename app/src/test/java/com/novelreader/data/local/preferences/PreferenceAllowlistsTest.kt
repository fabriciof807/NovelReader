package com.novelreader.data.local.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreferenceAllowlistsTest {

    @Test
    fun `sanitizeFontFamily keeps an allowlisted family`() {
        assertThat(PreferenceAllowlists.sanitizeFontFamily("serif")).isEqualTo("serif")
        assertThat(PreferenceAllowlists.sanitizeFontFamily("monospace")).isEqualTo("monospace")
        assertThat(PreferenceAllowlists.sanitizeFontFamily("Sans-Serif")).isEqualTo("sans-serif")
    }

    @Test
    fun `sanitizeFontFamily maps a hostile value to the default`() {
        assertThat(
            PreferenceAllowlists.sanitizeFontFamily(
                "serif;} </style><script>alert(1)</script><style>a{"
            )
        ).isEqualTo("serif")
        assertThat(PreferenceAllowlists.sanitizeFontFamily(null)).isEqualTo("serif")
        assertThat(PreferenceAllowlists.sanitizeFontFamily("  ")).isEqualTo("serif")
    }

    @Test
    fun `sanitizeReaderTheme keeps a canonical palette id`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("papel")).isEqualTo("papel")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("amoled")).isEqualTo("amoled")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("Papel")).isEqualTo("papel")
    }

    @Test
    fun `sanitizeReaderTheme keeps an explicit variant`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("floresta:dark"))
            .isEqualTo("floresta:dark")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("floresta:light"))
            .isEqualTo("floresta:light")
    }

    @Test
    fun `sanitizeReaderTheme maps the legacy themes to their exact palette and variant`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("light")).isEqualTo("indigo:light")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("dark")).isEqualTo("indigo:dark")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("sepia")).isEqualTo("papel:light")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("gray")).isEqualTo("grafite:dark")
    }

    @Test
    fun `sanitizeReaderTheme rejects unknown and hostile themes`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("neon")).isEqualTo("auto")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("indigo:purple")).isEqualTo("auto")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("indigo:light:extra")).isEqualTo("auto")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("papel;}body{display:none}"))
            .isEqualTo("auto")
        assertThat(PreferenceAllowlists.sanitizeReaderTheme(null)).isEqualTo("auto")
    }

    @Test
    fun `sanitizeReaderTheme keeps auto so the reader can follow the app theme`() {
        assertThat(PreferenceAllowlists.sanitizeReaderTheme("auto")).isEqualTo("auto")
    }

    @Test
    fun `sanitizeAppPalette keeps every allowlisted palette plus dynamic`() {
        PreferenceAllowlists.PALETTES.forEach { palette ->
            assertThat(PreferenceAllowlists.sanitizeAppPalette(palette)).isEqualTo(palette)
        }
        assertThat(PreferenceAllowlists.sanitizeAppPalette("dynamic"))
            .isEqualTo(PreferenceAllowlists.PALETTE_DYNAMIC)
        assertThat(PreferenceAllowlists.sanitizeAppPalette("Papel")).isEqualTo("papel")
    }

    @Test
    fun `sanitizeAppPalette falls back to indigo`() {
        assertThat(PreferenceAllowlists.sanitizeAppPalette("neon")).isEqualTo("indigo")
        assertThat(PreferenceAllowlists.sanitizeAppPalette("papel:dark")).isEqualTo("indigo")
        assertThat(PreferenceAllowlists.sanitizeAppPalette(null)).isEqualTo("indigo")
    }

    @Test
    fun `sanitizeAccentColor accepts only a strict rgb hex`() {
        assertThat(PreferenceAllowlists.sanitizeAccentColor("#FF6F00")).isEqualTo("#ff6f00")
        assertThat(PreferenceAllowlists.sanitizeAccentColor(" #1a237e ")).isEqualTo("#1a237e")
    }

    @Test
    fun `sanitizeAccentColor rejects anything that could escape a css value`() {
        assertThat(PreferenceAllowlists.sanitizeAccentColor("#fff")).isNull()
        assertThat(PreferenceAllowlists.sanitizeAccentColor("#ff6f00;}body{display:none}")).isNull()
        assertThat(PreferenceAllowlists.sanitizeAccentColor("red")).isNull()
        assertThat(PreferenceAllowlists.sanitizeAccentColor("rgb(1,2,3)")).isNull()
        assertThat(PreferenceAllowlists.sanitizeAccentColor("url(javascript:alert(1))")).isNull()
        assertThat(PreferenceAllowlists.sanitizeAccentColor("#gggggg")).isNull()
        assertThat(PreferenceAllowlists.sanitizeAccentColor(null)).isNull()
    }

    @Test
    fun `sanitizeWallpaperRef keeps none and allowlisted builtins`() {
        assertThat(PreferenceAllowlists.sanitizeWallpaperRef("none"))
            .isEqualTo(PreferenceAllowlists.WALLPAPER_NONE)
        assertThat(PreferenceAllowlists.sanitizeWallpaperRef("builtin:oceano"))
            .isEqualTo("builtin:oceano")
        assertThat(PreferenceAllowlists.sanitizeWallpaperRef(null))
            .isEqualTo(PreferenceAllowlists.WALLPAPER_NONE)
        assertThat(PreferenceAllowlists.sanitizeWallpaperRef("builtin:nao-existe"))
            .isEqualTo(PreferenceAllowlists.WALLPAPER_NONE)
    }

    @Test
    fun `the solid builtin wallpapers are allowed`() {
        listOf("builtin:areia", "builtin:ardosia", "builtin:musgo").forEach { ref ->
            assertThat(PreferenceAllowlists.sanitizeWallpaperRef(ref)).isEqualTo(ref)
        }
    }

    @Test
    fun `sanitizeWallpaperRef accepts only a flat image file name`() {
        assertThat(PreferenceAllowlists.sanitizeWallpaperRef("file:home_1700000000.jpg"))
            .isEqualTo("file:home_1700000000.jpg")
        assertThat(PreferenceAllowlists.sanitizeWallpaperRef("file:foto.webp"))
            .isEqualTo("file:foto.webp")
    }

    @Test
    fun `sanitizeWallpaperRef rejects arbitrary paths`() {
        listOf(
            "file:../../shared_prefs/app_prefs.xml",
            "file:/data/data/com.other/files/x.jpg",
            "file:..%2fsecret.jpg",
            "file:sub/dir/x.jpg",
            "file:x.jpg/../../y.jpg",
            "file:x.exe",
            "file:x.jpg.exe",
            "file:.jpg",
            "/sdcard/x.jpg",
            "content://media/external/images/1",
            "https://example.com/x.jpg",
            "file:x.jpg;}body{display:none}",
            "file:x jpg"
        ).forEach { hostile ->
            assertThat(PreferenceAllowlists.sanitizeWallpaperRef(hostile))
                .isEqualTo(PreferenceAllowlists.WALLPAPER_NONE)
        }
    }

    @Test
    fun `sanitizeBlur clamps to the supported range`() {
        assertThat(PreferenceAllowlists.sanitizeBlur(null)).isEqualTo(0)
        assertThat(PreferenceAllowlists.sanitizeBlur(0)).isEqualTo(0)
        assertThat(PreferenceAllowlists.sanitizeBlur(24)).isEqualTo(24)
        assertThat(PreferenceAllowlists.sanitizeBlur(60)).isEqualTo(60)
        assertThat(PreferenceAllowlists.sanitizeBlur(-5)).isEqualTo(0)
        assertThat(PreferenceAllowlists.sanitizeBlur(9999))
            .isEqualTo(PreferenceAllowlists.MAX_BLUR)
    }

    @Test
    fun `sanitizeVeil keeps zero reachable and defaults to eighty`() {
        assertThat(PreferenceAllowlists.sanitizeVeil(null))
            .isEqualTo(PreferenceAllowlists.DEFAULT_VEIL)
        assertThat(PreferenceAllowlists.sanitizeVeil(0)).isEqualTo(0)
        assertThat(PreferenceAllowlists.sanitizeVeil(80)).isEqualTo(80)
        assertThat(PreferenceAllowlists.sanitizeVeil(100)).isEqualTo(100)
        assertThat(PreferenceAllowlists.sanitizeVeil(-1)).isEqualTo(0)
        assertThat(PreferenceAllowlists.sanitizeVeil(500))
            .isEqualTo(PreferenceAllowlists.MAX_VEIL)
    }

    @Test
    fun `sanitizeSwipeDirection keeps the four directions`() {
        listOf("vertical", "horizontal", "both", "none").forEach { direction ->
            assertThat(PreferenceAllowlists.sanitizeSwipeDirection(direction)).isEqualTo(direction)
        }
        assertThat(PreferenceAllowlists.sanitizeSwipeDirection("diagonal")).isEqualTo("vertical")
        assertThat(PreferenceAllowlists.sanitizeSwipeDirection(null)).isEqualTo("vertical")
    }

    @Test
    fun `sanitizeAppTheme and locale fall back to their defaults`() {
        assertThat(PreferenceAllowlists.sanitizeAppTheme("dark")).isEqualTo("dark")
        assertThat(PreferenceAllowlists.sanitizeAppTheme("garbage")).isEqualTo("system")
        assertThat(PreferenceAllowlists.sanitizeLocale("en")).isEqualTo("en")
        assertThat(PreferenceAllowlists.sanitizeLocale("system")).isEqualTo(PreferenceAllowlists.LOCALE_SYSTEM)
        assertThat(PreferenceAllowlists.sanitizeLocale("xx")).isEqualTo(PreferenceAllowlists.LOCALE_SYSTEM)
        assertThat(PreferenceAllowlists.sanitizeLocale(null)).isEqualTo(PreferenceAllowlists.LOCALE_SYSTEM)
    }

    @Test
    fun `library preference sanitizers fall back to their defaults`() {
        assertThat(PreferenceAllowlists.sanitizeSortOrder("TITLE")).isEqualTo("TITLE")
        assertThat(PreferenceAllowlists.sanitizeSortOrder("DROP TABLE")).isEqualTo("LAST_READ")
        assertThat(PreferenceAllowlists.sanitizeChapterSortOrder("DESCENDING")).isEqualTo("DESCENDING")
        assertThat(PreferenceAllowlists.sanitizeChapterSortOrder("nope")).isEqualTo("ASCENDING")
        assertThat(PreferenceAllowlists.sanitizeViewMode("LIST")).isEqualTo("LIST")
        assertThat(PreferenceAllowlists.sanitizeViewMode("nope")).isEqualTo("GRID")
    }
}
