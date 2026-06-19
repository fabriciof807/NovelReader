package com.novelreader.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object HtmlSanitizer {

    private val DANGEROUS_TAGS = "script, style, iframe, form, button, noscript, object, embed, link, meta, base, applet, frame, frameset, svg, foreignObject, math, video, audio, details, dialog, template, xmp, marquee, input, textarea, select, image, use"

    fun sanitizeElement(root: Element) {
        root.select("a").unwrap()
        root.select("[style]").removeAttr("style")
        root.select(DANGEROUS_TAGS).remove()
        root.select("img[src^=\"javascript:\"]").remove()
        root.select("img[src^=\"data:\"]").remove()
        root.select("img").removeAttr("srcset")
        root.select("[href^=\"javascript:\"]").removeAttr("href")
        root.select("[src^=\"javascript:\"]").removeAttr("src")
        root.select("*").forEach { el ->
            el.attributes().toList().forEach { attr ->
                if (attr.key.lowercase().startsWith("on")) {
                    el.removeAttr(attr.key)
                }
            }
        }
    }

    fun sanitizeHtml(html: String): String {
        val doc = Jsoup.parse(html)
        sanitizeElement(doc.body())
        return doc.body().html()
    }
}