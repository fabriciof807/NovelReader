package com.novelreader.ui.reader

import com.novelreader.data.local.db.entity.BookmarkEntity
import com.novelreader.data.local.preferences.ReaderConfig
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist

enum class ChapterTransition { NONE, FROM_RIGHT, FROM_LEFT, FROM_TOP, FROM_BOTTOM }

fun chapterTransitionFor(direction: String, axis: String): ChapterTransition = when {
    direction == "prev" && axis == "v" -> ChapterTransition.FROM_TOP
    direction == "prev" -> ChapterTransition.FROM_LEFT
    direction == "next" && axis == "v" -> ChapterTransition.FROM_BOTTOM
    else -> ChapterTransition.FROM_RIGHT
}

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

fun themeVars(config: ReaderConfig): Map<String, String> = when (config.theme) {
    "dark" -> mapOf(
        "bgColor" to "#0a0a0f",
        "textColor" to "#e0e0e0",
        "accentColor" to "#90caf9",
        "linkColor" to "#64b5f6"
    )
    "sepia" -> mapOf(
        "bgColor" to "#f4e4c1",
        "textColor" to "#5b4636",
        "accentColor" to "#8d6e63",
        "linkColor" to "#6d4c41"
    )
    "gray" -> mapOf(
        "bgColor" to "#2d2d2d",
        "textColor" to "#d0d0d0",
        "accentColor" to "#90a4ae",
        "linkColor" to "#81d4fa"
    )
    else -> mapOf(
        "bgColor" to "#f5f0e8",
        "textColor" to "#333333",
        "accentColor" to "#1a237e",
        "linkColor" to "#1565c0"
    )
}

fun buildReaderHtml(
    content: String,
    config: ReaderConfig,
    transition: ChapterTransition = ChapterTransition.NONE,
    chapterTitle: String = ""
): String {
    val sanitized = Jsoup.clean(content, READER_SAFELIST)
    val finalContent = stripJunkContent(sanitized, chapterTitle)
    val nonce = buildNonce()
    val titleHtml = chapterTitle.trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "<h1 class=\"chapter-title\">${TextNode(it).outerHtml()}</h1>" }
        ?: ""

    val themeCss = themeVars(config).entries.joinToString("\n            ") { (k, v) ->
        val cssVar = k.replace(Regex("([A-Z])")) { "-${it.value.lowercase()}" }
        "--$cssVar: $v;"
    }
    val css = """
        :root {
            $themeCss
            --font-family: '${escapeCssString(config.fontFamily)}', Georgia, serif;
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
            -webkit-user-select: none;
            user-select: none;
            -webkit-touch-callout: none;
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
        .chapter-title {
            margin: 0 0 1.2em 0;
            font-size: 1.15em;
            line-height: 1.35;
        }
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
        #content.enter-from-right { animation: enterFromRight 0.4s cubic-bezier(0.22, 0.61, 0.36, 1); }
        #content.enter-from-left { animation: enterFromLeft 0.4s cubic-bezier(0.22, 0.61, 0.36, 1); }
        #content.enter-from-top { animation: enterFromTop 0.4s cubic-bezier(0.22, 0.61, 0.36, 1); }
        #content.enter-from-bottom { animation: enterFromBottom 0.4s cubic-bezier(0.22, 0.61, 0.36, 1); }
        @keyframes enterFromRight {
            from { opacity: 0; transform: translateX(80px); }
            to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes enterFromLeft {
            from { opacity: 0; transform: translateX(-80px); }
            to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes enterFromTop {
            from { opacity: 0; transform: translateY(-80px); }
            to   { opacity: 1; transform: translateY(0); }
        }
        @keyframes enterFromBottom {
            from { opacity: 0; transform: translateY(80px); }
            to   { opacity: 1; transform: translateY(0); }
        }
    """.trimIndent()

    val autoScrollJs = """
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
        ${if (config.autoScrollSpeed > 0f) "setTimeout(startAutoScroll, 500);" else ""}
    """.trimIndent()

    val contentClass = if (transition == ChapterTransition.NONE) {
        ""
    } else {
        " class=\"enter-${transitionName(transition)}\""
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="Content-Security-Policy"
                content="default-src 'none';
                         style-src 'nonce-$nonce';
                         script-src 'nonce-$nonce';
                         img-src data:;
                         font-src 'self' data:;
                         connect-src 'none';
                         object-src 'none';
                         base-uri 'none';
                         form-action 'none';
                         frame-src 'none';
                         frame-ancestors 'none';">
            <style nonce="$nonce">$css</style>
            <script nonce="$nonce">
            document.addEventListener('selectstart', function(e) { e.preventDefault(); });

            var _swipeDir = '${config.swipeDirection}';
            function isAtStart() { return window.scrollY <= 20; }
            function isAtEnd() {
                var remaining = document.body.scrollHeight - window.scrollY - window.innerHeight;
                return remaining <= 20;
            }
            (function() {
                var _ts = {x:0, y:0, t:0};
                document.addEventListener('touchstart', function(e) {
                    var t = e.touches[0]; _ts = {x: t.clientX, y: t.clientY, t: Date.now()};
                });
                document.addEventListener('touchend', function(e) {
                    var dx = e.changedTouches[0].clientX - _ts.x;
                    var dy = e.changedTouches[0].clientY - _ts.y;
                    var dt = Date.now() - _ts.t;
                    if (dt > 500) return;
                    var dir = null;
                    if ((_swipeDir === 'vertical' || _swipeDir === 'both') &&
                        Math.abs(dy) > 60 && Math.abs(dy) > Math.abs(dx) * 1.5) {
                        if (dy < 0 && isAtEnd()) { dir = 'next'; }
                        else if (dy > 0 && isAtStart()) { dir = 'prev'; }
                    }
                    if (dir === null &&
                        (_swipeDir === 'horizontal' || _swipeDir === 'both') &&
                        Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
                        dir = dx < 0 ? 'next' : 'prev';
                    }
                    if (dir !== null) {
                        try { Android.onSwipe(dir, Math.abs(dx) > Math.abs(dy) ? 'h' : 'v'); } catch(e) {}
                    } else if (Math.abs(dx) < 24 && Math.abs(dy) < 24) {
                        try { Android.onTap(); } catch(e) {}
                    }
                });
            })();

            function applyConfig(cfg) {
                _swipeDir = cfg.swipeDirection;
                var root = document.documentElement.style;
                root.setProperty('--bg-color', cfg.bgColor);
                root.setProperty('--text-color', cfg.textColor);
                root.setProperty('--accent-color', cfg.accentColor);
                root.setProperty('--link-color', cfg.linkColor);
                root.setProperty('--font-family', "'" + cfg.fontFamily + "', Georgia, serif");
                root.setProperty('--font-size', cfg.fontSize + 'px');
                root.setProperty('--line-height', cfg.lineHeight);
                setAutoScrollSpeed(cfg.autoScrollSpeed);
            }
            function setAutoScrollSpeed(speed) {
                _asSpeed = speed;
                if (speed <= 0) { stopAutoScroll(); }
                else if (!_asRunning) { startAutoScroll(); }
            }
            function applyBookmarks(positionsJson) {
                var content = document.getElementById('content');
                if (!content) return;
                var paras = content.querySelectorAll('p');
                if (!paras.length) return;
                var existing = content.querySelectorAll('.bookmark-indicator');
                for (var i = existing.length - 1; i >= 0; i--) existing[i].remove();
                paras.forEach(function(p){ p.classList.remove('bookmarked'); });
                var positions = JSON.parse(positionsJson);
                var used = {};
                positions.forEach(function(pos) {
                    var idx = Math.floor((pos / 1000) * paras.length);
                    if (idx < 0) idx = 0;
                    if (idx >= paras.length) idx = paras.length - 1;
                    if (used[idx]) return;
                    used[idx] = true;
                    var span = document.createElement('span');
                    span.className = 'bookmark-indicator';
                    span.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>';
                    paras[idx].parentNode.insertBefore(span, paras[idx]);
                    paras[idx].classList.add('bookmarked');
                });
            }

            $autoScrollJs
            </script>
        </head>
        <body>
            <div id="content"$contentClass>$titleHtml$finalContent</div>
        </body>
        </html>
    """.trimIndent()
}

private fun buildNonce(): String = java.util.UUID.randomUUID().toString().replace("-", "")

private fun escapeCssString(value: String): String = buildString {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '"' -> append("\\\"")
            '<' -> append("\\3c ")
            '>' -> append("\\3e ")
            '\n' -> append("\\a ")
            '\r' -> append("\\d ")
            '\u0000' -> append("\\0 ")
            else -> append(ch)
        }
    }
}

private fun transitionName(transition: ChapterTransition): String = when (transition) {
    ChapterTransition.FROM_RIGHT -> "from-right"
    ChapterTransition.FROM_LEFT -> "from-left"
    ChapterTransition.FROM_TOP -> "from-top"
    ChapterTransition.FROM_BOTTOM -> "from-bottom"
    ChapterTransition.NONE -> ""
}

private fun stripJunkContent(html: String, chapterTitle: String = ""): String {
    val doc = Jsoup.parseBodyFragment(html)

    stripHeadingDuplicatingTitle(doc, chapterTitle)

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

private fun stripHeadingDuplicatingTitle(doc: Document, chapterTitle: String) {
    val heading = doc.body().children().firstOrNull { child ->
        child.tagName() in HEADING_TAGS && headingDuplicatesTitle(child.text(), chapterTitle)
    } ?: return
    heading.remove()
}

private val TITLE_SEPARATORS = listOf(":", "-", "–", "—", "|", "•", "·", ",", ".")

private fun headingDuplicatesTitle(headingText: String, chapterTitle: String): Boolean {
    val heading = headingText.trim().replace(WHITESPACE, " ").lowercase()
    val title = chapterTitle.trim().replace(WHITESPACE, " ").lowercase()
    if (title.isEmpty() || heading.isEmpty()) return false
    if (title.length > 20 && heading.contains(title)) return true
    if (!heading.startsWith(title)) return false
    val remainder = heading.substring(title.length).trimStart()
    return remainder.isEmpty() || TITLE_SEPARATORS.any { remainder.startsWith(it) }
}

private val WHITESPACE = Regex("\\s+")

private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

fun buildJs(code: String, params: Map<String, Any> = emptyMap()): String {
    val args = params.entries.joinToString(",") { (k, v) -> "\"$k\":${jsonLiteral(v)}" }
    val argsLiteral = if (args.isEmpty()) "{}" else "{$args}"
    return """
        (function(args) {
            $code
        })($argsLiteral)
    """.trimIndent()
}

private fun jsonLiteral(value: Any?): String = when (value) {
    null -> "null"
    is JSONObject -> value.toString()
    is JSONArray -> value.toString()
    is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> JSONObject(value).toString()
    is Iterable<*> -> JSONArray(value).toString()
    is Array<*> -> JSONArray(value).toString()
    else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
}

fun applyConfigJs(config: ReaderConfig): String {
    val map = themeVars(config)
    val payload = map + mapOf(
        "fontFamily" to escapeCssString(config.fontFamily),
        "fontSize" to config.fontSize,
        "lineHeight" to config.lineHeight,
        "autoScrollSpeed" to config.autoScrollSpeed,
        "swipeDirection" to config.swipeDirection
    )
    return buildJs(
        code = "applyConfig(args.args);",
        params = mapOf("args" to payload)
    )
}

fun applyBookmarksJs(bookmarks: List<BookmarkEntity>): String {
    val positions = bookmarks.map { it.scrollPosition }
    return buildJs(
        code = "applyBookmarks(args.args);",
        params = mapOf("args" to JSONArray(positions).toString())
    )
}

fun bookmarkCaptureRatioJs(): String = buildJs(
    "var max = document.body.scrollHeight - window.innerHeight;" +
        " var ratio = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;" +
        " return ratio.toString();"
)

fun scrollRestoreJs(ratio: Float, completionToken: Int? = null): String = buildJs(
    code = "requestAnimationFrame(function() {" +
        " var max = document.body.scrollHeight - window.innerHeight;" +
        " window.scrollTo(0, max * args.ratio);" +
        (completionToken?.let { " try { Android.onScrollRestoreComplete($it); } catch (e) {}" } ?: "") +
        " });",
    params = mapOf("ratio" to ratio)
)

fun searchHighlightJs(query: String, restoreRatio: Float, completionToken: Int? = null): String = buildJs(
    code = """
        setTimeout(function() {
            (function(q) {
                var c = document.getElementById('content');
                if (!c) return;
                var w = document.createTreeWalker(c, NodeFilter.SHOW_TEXT);
                var n;
                var found = false;
                while (n = w.nextNode()) {
                    var i = n.nodeValue.toLowerCase().indexOf(q.toLowerCase());
                    if (i >= 0) {
                        var r = document.createRange();
                        r.setStart(n, i);
                        r.setEnd(n, i + q.length);
                        var m = document.createElement('mark');
                        m.className = 'search-highlight';
                        try {
                            r.surroundContents(m);
                            var top = m.getBoundingClientRect().top + window.scrollY - 80;
                            window.scrollTo({ top: top, behavior: 'smooth' });
                        } catch (e) {}
                        found = true;
                        return;
                    }
                }
                if (!found) {
                    var max = document.body.scrollHeight - window.innerHeight;
                    window.scrollTo(0, max * args.restoreRatio);
                }
            })(args.query);
            ${completionToken?.let { "try { Android.onScrollRestoreComplete($it); } catch (e) {}" } ?: ""}
        }, 1000);
    """.trimIndent(),
    params = mapOf("query" to query, "restoreRatio" to restoreRatio)
)
