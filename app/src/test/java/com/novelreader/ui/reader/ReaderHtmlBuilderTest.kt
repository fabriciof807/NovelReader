package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.preferences.ReaderConfig
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
    fun `themeVars returns the four color keys for each theme`() {
        for (theme in listOf("light", "dark", "sepia", "gray")) {
            val map = themeVars(ReaderConfig(theme = theme))
            assertThat(map).containsKey("bgColor")
            assertThat(map).containsKey("textColor")
            assertThat(map).containsKey("accentColor")
            assertThat(map).containsKey("linkColor")
        }
    }

    @Test
    fun `buildReaderHtml emits kebab-case CSS custom properties matching the theme`() {
        val html = buildReaderHtml(
            content = "<p>x</p>",
            config = ReaderConfig(theme = "dark")
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
    fun `applyConfigJs passes the config object to applyConfig without JSON parse`() {
        val js = applyConfigJs(ReaderConfig(theme = "dark"))
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
    fun `searchHighlightJs restores scroll position when no match is found`() {
        val js = searchHighlightJs(query = "nonexistent", restoreRatio = 0.3f)
        assertThat(js).contains("args.restoreRatio")
    }
}
