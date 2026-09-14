package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.preferences.ReaderConfig
import com.novelreader.ui.theme.AppPalette
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderHtmlBuilderTest {

    @Test
    fun `buildReaderHtml does not embed bookmark indicators in initial content`() {
        val html = buildReaderHtml(
            content = "<p>one</p><p>two</p>",
            config = ReaderConfig()
        )
        val body = html.substringAfter("<div id=\"content\">").substringBefore("</div>")
        assertThat(body).doesNotContain("bookmark-indicator")
        assertThat(body).doesNotContain("class=\"bookmarked\"")
    }

    @Test
    fun `buildReaderHtml includes applyConfig and applyBookmarks JS functions`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig()
        )
        assertThat(html).contains("function applyConfig(")
        assertThat(html).contains("function applyBookmarks(")
        assertThat(html).contains("function setAutoScrollSpeed(")
    }

    @Test
    fun `themeVars returns the four color keys for every palette and variant`() {
        for (palette in AppPalette.entries) {
            for (dark in listOf(false, true)) {
                val map = themeVars(ReaderConfig(theme = palette.id, themeDark = dark))
                assertThat(map.keys)
                    .containsExactly("bgColor", "textColor", "accentColor", "linkColor")
            }
        }
    }

    @Test
    fun `themeVars resolves the legacy reader themes to their original colours`() {
        val light = themeVars(ReaderConfig(theme = "indigo", themeDark = false))
        assertThat(light["bgColor"]).isEqualTo("#f5f0e8")
        assertThat(light["textColor"]).isEqualTo("#333333")
        assertThat(light["accentColor"]).isEqualTo("#1a237e")

        val sepia = themeVars(ReaderConfig(theme = "papel", themeDark = false))
        assertThat(sepia["bgColor"]).isEqualTo("#f4e4c1")
        assertThat(sepia["textColor"]).isEqualTo("#5b4636")

        val gray = themeVars(ReaderConfig(theme = "grafite", themeDark = true))
        assertThat(gray["bgColor"]).isEqualTo("#2d2d2d")
        assertThat(gray["accentColor"]).isEqualTo("#90a4ae")
    }

    @Test
    fun `themeVars uses the reader accent for accents and links`() {
        val map = themeVars(
            ReaderConfig(theme = "indigo", themeDark = false, accentColor = "#8d6e63")
        )
        assertThat(map["accentColor"]).isEqualTo("#8d6e63")
        assertThat(map["linkColor"]).isEqualTo("#8d6e63")
        assertThat(map["bgColor"]).isEqualTo("#f5f0e8")
    }

    @Test
    fun `themeVars ignores an accent that could escape the css value`() {
        val map = themeVars(
            ReaderConfig(
                theme = "indigo",
                themeDark = false,
                accentColor = "#fff;} body { display: none }"
            )
        )
        assertThat(map["accentColor"]).isEqualTo("#1a237e")
        assertThat(map["linkColor"]).isEqualTo("#1565c0")
    }

    @Test
    fun `themeVars goes transparent while a wallpaper is set`() {
        val plain = themeVars(
            ReaderConfig(theme = "papel", themeDark = false, wallpaper = "none")
        )
        assertThat(plain["bgColor"]).isEqualTo("#f4e4c1")

        listOf("builtin:oceano", "file:reader_1.jpg").forEach { ref ->
            val map = themeVars(
                ReaderConfig(theme = "papel", themeDark = false, wallpaper = ref)
            )
            assertThat(map["bgColor"]).isEqualTo("transparent")
            assertThat(map["textColor"]).isEqualTo("#5b4636")
        }
    }

    @Test
    fun `buildReaderHtml emits kebab-case CSS custom properties matching the theme`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(theme = "indigo", themeDark = true)
        )
        val root = html.substringAfter("<style>").substringBefore("</style>")
            .substringAfter(":root {").substringBefore("}")
        assertThat(root).contains("--bg-color: #0a0a0f;")
        assertThat(root).contains("--text-color: #e0e0e0;")
        assertThat(root).contains("--accent-color: #90caf9;")
        assertThat(root).contains("--link-color: #64b5f6;")
        assertThat(root).doesNotContain("--bgColor:")
        assertThat(root).doesNotContain("--textColor:")
        assertThat(root).doesNotContain("--accentColor:")
        assertThat(root).doesNotContain("--linkColor:")
    }

    @Test
    fun `buildReaderHtml still emits the auto-scroll script when speed is positive`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(autoScrollSpeed = 1.5f)
        )
        assertThat(html).contains("_asSpeed")
        assertThat(html).contains("startAutoScroll")
        assertThat(html).contains("stopAutoScroll")
    }

    @Test
    fun `buildReaderHtml emits startAutoScroll and stopAutoScroll even when speed is zero`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(autoScrollSpeed = 0f)
        )
        assertThat(html).contains("function startAutoScroll(")
        assertThat(html).contains("function stopAutoScroll(")
        assertThat(html).contains("_asSpeed")
    }

    @Test
    fun `buildReaderHtml default transition keeps plain content div`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig()
        )
        assertThat(html).contains("<div id=\"content\"><p>x</p></div>")
        assertThat(html).doesNotContain("<div id=\"content\" class=")
    }

    @Test
    fun `buildReaderHtml with FROM_RIGHT adds enter-from-right class and keyframes`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(),
            transition = ChapterTransition.FROM_RIGHT
        )
        assertThat(html).contains("<div id=\"content\" class=\"enter-from-right\">")
        assertThat(html).contains("@keyframes enterFromRight")
    }

    @Test
    fun `buildReaderHtml with FROM_LEFT adds enter-from-left class and keyframes`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(),
            transition = ChapterTransition.FROM_LEFT
        )
        assertThat(html).contains("<div id=\"content\" class=\"enter-from-left\">")
        assertThat(html).contains("@keyframes enterFromLeft")
    }

    @Test
    fun `buildReaderHtml with FROM_TOP and FROM_BOTTOM add vertical classes`() {
        val top = buildReaderHtml("<p>x</p>", ReaderConfig(), ChapterTransition.FROM_TOP)
        val bottom = buildReaderHtml("<p>x</p>", ReaderConfig(), ChapterTransition.FROM_BOTTOM)
        assertThat(top).contains("<div id=\"content\" class=\"enter-from-top\">")
        assertThat(bottom).contains("<div id=\"content\" class=\"enter-from-bottom\">")
    }

    @Test
    fun `chapterTransitionFor maps swipe direction and axis to entry transition`() {
        assertThat(chapterTransitionFor("next", "h")).isEqualTo(ChapterTransition.FROM_RIGHT)
        assertThat(chapterTransitionFor("next", "v")).isEqualTo(ChapterTransition.FROM_BOTTOM)
        assertThat(chapterTransitionFor("prev", "h")).isEqualTo(ChapterTransition.FROM_LEFT)
        assertThat(chapterTransitionFor("prev", "v")).isEqualTo(ChapterTransition.FROM_TOP)
    }

    @Test
    fun `applyConfigJs passes the config object to applyConfig without JSON parse`() {
        val js = applyConfigJs(ReaderConfig(theme = "indigo", themeDark = true))
        assertThat(js).contains("applyConfig(args.args);")
        assertThat(js).doesNotContain("JSON.parse(args)")
        assertThat(js).contains("\"fontFamily\":\"serif\"")
        assertThat(js).contains("\"bgColor\":\"#0a0a0f\"")
        assertThat(js).contains("\"autoScrollSpeed\":0")
    }

    @Test
    fun `applyBookmarksJs passes scroll positions as a JSON string to applyBookmarks`() {
        val js = applyBookmarksJs(
            listOf(
                BookmarkEntity(id = 1, chapterId = 1, title = "a", scrollPosition = 120),
                BookmarkEntity(id = 2, chapterId = 1, title = "b", scrollPosition = 350),
                BookmarkEntity(id = 3, chapterId = 1, title = "c", scrollPosition = 500)
            )
        )
        assertThat(js).contains("applyBookmarks(args.args);")
        assertThat(js).doesNotContain("JSON.parse(args)")
        assertThat(js).contains("\"[120,350,500]\"")
    }

    @Test
    fun `bookmarkCaptureRatioJs uses viewport-aware clamped scroll ratio`() {
        val js = bookmarkCaptureRatioJs()
        assertThat(js).contains("document.body.scrollHeight - window.innerHeight")
        assertThat(js).contains("Math.min(1, Math.max(0,")
        assertThat(js).doesNotContain("window.scrollY / document.body.scrollHeight")
    }

    @Test
    fun `scrollRestoreJs waits for layout before restoring scroll position`() {
        val js = scrollRestoreJs(0.5f)
        assertThat(js).contains("requestAnimationFrame")
    }

    @Test
    fun `scrollRestoreJs supports chapter top`() {
        val js = scrollRestoreJs(0f)
        assertThat(js).contains("ratio")
        assertThat(js).contains("0")
    }

    @Test
    fun `scrollRestoreJs reports completion for the current load`() {
        val js = scrollRestoreJs(0f, completionToken = 7)
        assertThat(js).contains("Android.onScrollRestoreComplete(7)")
    }

    @Test
    fun `searchHighlightJs restores scroll position when no match is found`() {
        val js = searchHighlightJs(query = "nonexistent", restoreRatio = 0.3f)
        assertThat(js).contains("args.restoreRatio")
    }

    @Test
    fun `buildReaderHtml with swipeDirection vertical sets vertical direction and uses vertical-branch guard`() {
        val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "vertical"))
        assertThat(html).contains("_swipeDir = 'vertical'")
        assertThat(html).contains("_swipeDir === 'vertical' || _swipeDir === 'both'")
        assertThat(html).contains("dy < 0 && isAtEnd()")
        assertThat(html).contains("dy > 0 && isAtStart()")
    }

    @Test
    fun `buildReaderHtml with swipeDirection horizontal sets horizontal direction and uses horizontal-branch guard`() {
        val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "horizontal"))
        assertThat(html).contains("_swipeDir = 'horizontal'")
        assertThat(html).contains("_swipeDir === 'horizontal' || _swipeDir === 'both'")
        assertThat(html).contains("dx < 0 ? 'next' : 'prev'")
    }

    @Test
    fun `buildReaderHtml with swipeDirection both contains both vertical and horizontal guards`() {
        val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "both"))
        assertThat(html).contains("_swipeDir = 'both'")
        assertThat(html).contains("dy < 0 && isAtEnd()")
        assertThat(html).contains("dx < 0 ? 'next' : 'prev'")
    }

    @Test
    fun `buildReaderHtml emits chapter boundary helpers for vertical swipe navigation`() {
        val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "both"))
        assertThat(html).contains("function isAtStart()")
        assertThat(html).contains("return window.scrollY <= 20;")
        assertThat(html).contains("function isAtEnd()")
        assertThat(html).contains("document.body.scrollHeight - window.scrollY - window.innerHeight")
        assertThat(html).contains("return remaining <= 20;")
    }

    @Test
    fun `buildReaderHtml with swipeDirection none sets none and guards never match`() {
        val html = buildReaderHtml("<p>x</p>", ReaderConfig(swipeDirection = "none"))
        assertThat(html).contains("_swipeDir = 'none'")
        // ponytail: both branch guards present; runtime _swipeDir === 'none' matches neither
        assertThat(html).contains("_swipeDir === 'vertical' || _swipeDir === 'both'")
        assertThat(html).contains("_swipeDir === 'horizontal' || _swipeDir === 'both'")
    }

    @Test
    fun `buildReaderHtml strips the first heading that contains the chapter title`() {
        val html = buildReaderHtml(
            content = "<h4>Chapter 1: Chapter 1: What Bad Intentions Could an Uncle Have?</h4><p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "Chapter 1: What Bad Intentions Could an Uncle Have?"
        )
        assertThat(html).doesNotContain("Chapter 1: Chapter 1:")
        assertThat(html).contains("<p>Body.</p>")
    }

    @Test
    fun `buildReaderHtml keeps a heading that does not duplicate the chapter title`() {
        val html = buildReaderHtml(
            content = "<h2>Prologue</h2><p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "Chapter 1: What Bad Intentions Could an Uncle Have?"
        )
        assertThat(html).contains("<h2>Prologue</h2>")
        assertThat(html).contains("<p>Body.</p>")
    }

    @Test
    fun `buildReaderHtml strips a short-titled heading whose remainder is a title suffix`() {
        val html = buildReaderHtml(
            content = "<h4>Chapter 1: Opening</h4><p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "Chapter 1"
        )
        assertThat(html).doesNotContain("<h4>Chapter 1: Opening</h4>")
        assertThat(html).contains("<h1 class=\"chapter-title\">Chapter 1</h1>")
    }

    @Test
    fun `buildReaderHtml keeps a heading that only shares a number prefix`() {
        val html = buildReaderHtml(
            content = "<h4>Chapter 10: Later</h4><p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "Chapter 1"
        )
        assertThat(html).contains("<h4>Chapter 10: Later</h4>")
    }

    @Test
    fun `buildReaderHtml prepends the chapter title at the top of the content`() {
        val html = buildReaderHtml(
            content = "<p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "Chapter 1: Opening"
        )
        val body = html.substringAfter("<div id=\"content\">").substringBefore("</div>")
        assertThat(body).startsWith("<h1 class=\"chapter-title\">Chapter 1: Opening</h1>")
        assertThat(body).contains("<p>Body.</p>")
    }

    @Test
    fun `buildReaderHtml escapes HTML in the chapter title`() {
        val html = buildReaderHtml(
            content = "<p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "A <b>B</b> & C"
        )
        assertThat(html).contains("A &lt;b&gt;B&lt;/b&gt; &amp; C")
        assertThat(html).doesNotContain("<h1 class=\"chapter-title\">A <b>")
    }

    @Test
    fun `buildReaderHtml omits the title heading when chapterTitle is blank`() {
        val html = buildReaderHtml(
            content = "<p>Body.</p>",
            config = ReaderConfig(),
            chapterTitle = "   "
        )
        assertThat(html).doesNotContain("<h1 class=\"chapter-title\">")
        assertThat(html).contains("<p>Body.</p>")
    }

    @Test
    fun `buildReaderHtml leaves content untouched when there is no heading`() {
        val html = buildReaderHtml(
            content = "<p>Just body.</p>",
            config = ReaderConfig(),
            chapterTitle = "Chapter 1: What Bad Intentions Could an Uncle Have?"
        )
        assertThat(html).contains("<p>Just body.</p>")
    }

    @Test
    fun `buildReaderHtml fires onTap on tap release without a long-press timer`() {
        val html = buildReaderHtml(content = "<p>x</p>", config = ReaderConfig())
        assertThat(html).doesNotContain("_lpTimer")
        assertThat(html).contains("Android.onTap()")
    }

    @Test
    fun `buildReaderHtml only taps when the gesture stays inside the tap slop`() {
        val html = buildReaderHtml(content = "<p>x</p>", config = ReaderConfig())
        assertThat(html).contains("Math.abs(dx) < 24 && Math.abs(dy) < 24")
    }

    @Test
    fun `buildReaderHtml escapes the font family in the stylesheet`() {
        val html = buildReaderHtml(
            content = "<p>Body.</p>",
            config = ReaderConfig(
                fontFamily = "serif;} </style><script>alert(1)</script><style>a{"
            )
        )

        assertThat(html).doesNotContain("</style><script>alert(1)")
        assertThat(html).doesNotContain("<script>alert(1)</script>")
        assertThat(html).contains("\\3c ")
    }

    @Test
    fun `buildReaderHtml emits a per-load nonce and drops unsafe-inline`() {
        val html = buildReaderHtml(content = "<p>x</p>", config = ReaderConfig())

        val nonce = Regex("script-src 'nonce-([^']+)'")
            .find(html)?.groupValues?.get(1)
        assertThat(nonce).isNotNull()
        assertThat(html).contains("<style nonce=\"$nonce\">")
        assertThat(html).contains("<script nonce=\"$nonce\">")
        assertThat(html).doesNotContain("'unsafe-inline'")
    }

    @Test
    fun `buildReaderHtml uses a fresh nonce per build`() {
        val first = Regex("script-src 'nonce-([^']+)'")
            .find(buildReaderHtml(content = "<p>x</p>", config = ReaderConfig()))!!.groupValues[1]
        val second = Regex("script-src 'nonce-([^']+)'")
            .find(buildReaderHtml(content = "<p>x</p>", config = ReaderConfig()))!!.groupValues[1]

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `applyConfigJs passes swipeDirection to applyConfig`() {
        val js = applyConfigJs(ReaderConfig(swipeDirection = "horizontal"))
        assertThat(js).contains("\"swipeDirection\":\"horizontal\"")
    }
}
