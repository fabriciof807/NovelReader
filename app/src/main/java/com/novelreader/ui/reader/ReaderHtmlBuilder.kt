package com.novelreader.ui.reader

import com.novelreader.data.local.preferences.ReaderConfig
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

private val READER_SAFELIST = Safelist.none()
    .addTags("p", "h1", "h2", "h3", "h4", "h5", "h6", "br", "strong", "em", "b", "i", "u", "sub", "sup")

private val NAV_PATTERNS = listOf(
    Regex("apert[ea]\\s*.{1,4}\\s*para\\s*(ir ao\\s*)?pr[oó]ximo\\s*cap.tul", RegexOption.IGNORE_CASE),
    Regex("pressione\\s*.{1,4}\\s*para\\s*(ir ao\\s*)?pr[oó]ximo\\s*cap.tul", RegexOption.IGNORE_CASE),
    Regex("use as setas para navegar", RegexOption.IGNORE_CASE),
    Regex("navegu[ea]\\s*(pelos|entre)\\s*cap.tulos", RegexOption.IGNORE_CASE),
    Regex("^(next|previous)\\s+chapter\\.?\$", RegexOption.IGNORE_CASE),
    Regex("press\\s*.{1,4}\\s*(key|to\\s+go)\\s*(to\\s+)?(the\\s+)?(next|previous)\\s+chapter", RegexOption.IGNORE_CASE),
    Regex("nav(e|i)gat(e|ing).{0,20}(chapter|cap.tulo)", RegexOption.IGNORE_CASE),
)

fun buildReaderHtml(
    content: String,
    config: ReaderConfig,
    bookmarksScrollPositions: List<Int> = emptyList()
): String {
    val themeVars = when (config.theme) {
        "dark" -> """
            --bg-color: #1a1a2e;
            --text-color: #e0e0e0;
            --accent-color: #90caf9;
            --link-color: #64b5f6;
        """.trimIndent()
        "sepia" -> """
            --bg-color: #f4e4c1;
            --text-color: #5b4636;
            --accent-color: #8d6e63;
            --link-color: #6d4c41;
        """.trimIndent()
        "gray" -> """
            --bg-color: #2d2d2d;
            --text-color: #d0d0d0;
            --accent-color: #90a4ae;
            --link-color: #81d4fa;
        """.trimIndent()
        else -> """
            --bg-color: #f5f0e8;
            --text-color: #333333;
            --accent-color: #1a237e;
            --link-color: #1565c0;
        """.trimIndent()
    }

    val sanitized = Jsoup.clean(content, READER_SAFELIST)
    val cleaned = stripJunkContent(sanitized)

    val finalContent = if (bookmarksScrollPositions.any { it > 0 }) {
        insertBookmarkInContent(cleaned, bookmarksScrollPositions.filter { it > 0 })
    } else {
        cleaned
    }

    val css = """
        :root {
            $themeVars
            --font-family: '${config.fontFamily}', Georgia, serif;
            --font-size: ${config.fontSize}px;
            --line-height: ${config.lineHeight};
            --padding: 20px;
            --max-width: 800px;
        }
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
            background-color: transparent !important;
        }
        html {
            background-color: var(--bg-color) !important;
        }
        body {
            background-color: var(--bg-color) !important;
            color: var(--text-color) !important;
            font-family: var(--font-family);
            font-size: var(--font-size);
            line-height: var(--line-height);
            padding: var(--padding);
            -webkit-font-smoothing: antialiased;
            word-wrap: break-word;
        }
        #content {
            max-width: var(--max-width);
            margin: 0 auto;
            background-color: transparent !important;
        }
        p {
            margin: 0 0 1.2em 0;
            text-indent: 2em;
            color: var(--text-color) !important;
        }
        p:first-of-type { text-indent: 0; }
        .bookmarked {
            border-left: 3px solid var(--accent-color);
            padding-left: 12px;
            text-indent: 0;
        }
        .bookmark-indicator {
            display: inline-flex;
            align-items: center;
            margin-right: 6px;
            color: var(--accent-color);
        }
        h1, h2, h3, h4 {
            margin: 1.5em 0 0.8em 0;
            font-weight: bold;
            text-indent: 0;
            color: var(--text-color) !important;
        }
        img {
            max-width: 100%;
            height: auto;
            display: block;
            margin: 1em auto;
        }
        .search-highlight {
            background-color: rgba(255, 235, 59, 0.4);
            border-radius: 2px;
            padding: 1px 0;
            animation: pulse 0.6s ease-in-out 3;
        }
        @keyframes pulse {
            0%   { background-color: rgba(255, 235, 59, 0.3); }
            50%  { background-color: rgba(255, 235, 59, 1.0); }
            100% { background-color: rgba(255, 235, 59, 0.3); }
        }
    """.trimIndent()

    val autoScrollJs = if (config.autoScrollSpeed > 0f) {
        """
        var _asSpeed = ${config.autoScrollSpeed};
        var _asRunning = false, _asPaused = false, _asTimer = null;
        function _asStep() {
            if (!_asRunning) return;
            if (!_asPaused) window.scrollBy(0, _asSpeed * 2);
            var atEnd = (window.scrollY + window.innerHeight >= document.body.scrollHeight - 20);
            if (atEnd) { _asRunning = false; try{Android.onAutoScrollReachedEnd();}catch(e){} return; }
            requestAnimationFrame(_asStep);
        }
        function startAutoScroll() { if (_asSpeed <= 0 || _asRunning) return; _asRunning = true; _asPaused = false; _asStep(); }
        function stopAutoScroll() { _asRunning = false; _asPaused = false; clearTimeout(_asTimer); }
        document.addEventListener('touchstart', function() { if (_asRunning && !_asPaused) { _asPaused = true; clearTimeout(_asTimer); } });
        document.addEventListener('touchend', function() { if (_asRunning && _asPaused) { clearTimeout(_asTimer); _asTimer = setTimeout(function(){ _asPaused = false; }, 2000); } });
        setTimeout(startAutoScroll, 500);
        """.trimIndent()
    } else ""

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>$css</style>
            <script>
            var _lastSel = '';
            document.addEventListener('selectionchange', function() {
                var sel = window.getSelection().toString().trim();
                if (sel !== _lastSel) { _lastSel = sel; Android.onTextSelected(sel); }
            });

            (function() {
                var _ts = {x:0, y:0, t:0};
                document.addEventListener('touchstart', function(e) {
                    var t = e.touches[0]; _ts = {x: t.clientX, y: t.clientY, t: Date.now()};
                });
                document.addEventListener('touchend', function(e) {
                    var dx = e.changedTouches[0].clientX - _ts.x;
                    var dy = e.changedTouches[0].clientY - _ts.y;
                    var dt = Date.now() - _ts.t;
                    if (Math.abs(dx) < 15 && Math.abs(dy) < 15 && dt < 300) {
                        try { Android.onTap(); } catch(e) {}
                    } else if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
                        try { Android.onSwipe(dx > 0 ? 'prev' : 'next'); } catch(e) {}
                    }
                });
            })();

            $autoScrollJs
            </script>
        </head>
        <body>
            <div id="content">$finalContent</div>
        </body>
        </html>
    """.trimIndent()
}

private fun insertBookmarkInContent(content: String, scrollPositions: List<Int>): String {
    val doc = Jsoup.parseBodyFragment(content)
    val paragraphs = doc.select("p")
    if (paragraphs.isEmpty()) return content

    val usedIndices = mutableSetOf<Int>()
    for (scrollPos in scrollPositions.sorted()) {
        val index = ((scrollPos / 1000f) * paragraphs.size).toInt()
            .coerceIn(0, paragraphs.size - 1)
        if (usedIndices.add(index)) {
            val targetP = paragraphs[index]
            targetP.before(
                """<span class="bookmark-indicator"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg></span>"""
            )
            targetP.addClass("bookmarked")
        }
    }

    return doc.body().html()
}

private fun stripJunkContent(html: String): String {
    val doc = Jsoup.parseBodyFragment(html)

    for (p in doc.select("p").toList()) {
        val text = p.text().trim()
        if (text.isBlank()) {
            p.remove()
            continue
        }
        if (text.length < 120) {
            val lower = text.lowercase()
            for (pattern in NAV_PATTERNS) {
                if (pattern.containsMatchIn(lower)) {
                    val withoutNav = pattern.replace(lower, "").trim()
                    if (withoutNav.length < 30) {
                        p.remove()
                        break
                    }
                }
            }
        }
    }

    for (child in doc.body().children().toList()) {
        if (child.text().trim().isEmpty() && child.tagName() == "p") {
            child.remove()
        } else {
            break
        }
    }

    for (i in doc.body().children().size - 1 downTo 0) {
        val child = doc.body().children()[i]
        if (child.text().trim().isEmpty() && child.tagName() == "p") {
            child.remove()
        } else {
            break
        }
    }

    return doc.body().html()
}
