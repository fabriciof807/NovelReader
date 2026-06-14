package com.novelreader.data.parser

import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MhtParser @Inject constructor() {

    fun isMhtFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mht") || lower.endsWith(".mhtml")
    }

    fun extractHtml(raw: String): String? {
        return try {
            extractHtmlFromBytes(raw.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    fun extractSubject(raw: String): String? {
        return try {
            raw.lines()
                .firstOrNull { it.startsWith("Subject", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun extractContentLocation(raw: String): String? {
        return try {
            val boundary = extractBoundary(raw) ?: return null
            val parts = splitByBoundary(raw, boundary)
            for (part in parts) {
                val headers = extractHeaders(part)
                if (headers["content-type"]?.contains("text/html") == true) {
                    return headers["content-location"]
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractHtmlFromBytes(bytes: ByteArray): String? {
        val raw = bytes.toString(Charsets.UTF_8)

        val boundary = extractBoundary(raw) ?: return null

        val parts = splitByBoundary(raw, boundary)

        for (part in parts) {
            val headers = extractHeaders(part)
            val contentType = headers["content-type"] ?: continue

            if (contentType.contains("text/html", ignoreCase = true)) {
                val encoding = headers["content-transfer-encoding"]?.lowercase() ?: "7bit"
                val body = extractBody(part)

                return when {
                    encoding == "quoted-printable" -> decodeQuotedPrintable(body)
                    encoding == "base64" -> String(Base64.getDecoder().decode(body), Charsets.UTF_8)
                    else -> body
                }
            }
        }

        return null
    }

    private fun extractBoundary(raw: String): String? {
        val lines = raw.lines()
        var fullContentType = ""
        var inContentType = false

        for (line in lines) {
            val trimmed = line.trimEnd()
            if (trimmed.startsWith("Content-Type", ignoreCase = true)) {
                fullContentType = trimmed
                inContentType = true
            } else if (inContentType) {
                if (trimmed.startsWith(" ", ignoreCase = true) ||
                    trimmed.startsWith("\t", ignoreCase = true)
                ) {
                    fullContentType += trimmed.trimStart()
                } else {
                    inContentType = false
                    break
                }
            }
        }

        val boundaryParam = fullContentType
            .substringAfter("boundary=", "")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")

        return if (boundaryParam.isNotEmpty()) boundaryParam else null
    }

    private fun splitByBoundary(raw: String, boundary: String): List<String> {
        val delimiter = "--$boundary"
        val parts = raw.split(delimiter)
        return parts
            .filter { !it.startsWith("--") && it.isNotBlank() }
            .map { it.trim() }
    }

    private fun extractHeaders(part: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val lines = part.lines()

        for (line in lines) {
            if (line.isBlank()) break
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                headers.merge(key, value) { existing, new -> "$existing $new" }
            }
        }

        return headers
    }

    private fun extractBody(part: String): String {
        val lines = part.lines()
        val bodyStart = lines.indexOfFirst { it.isBlank() }
        if (bodyStart < 0 || bodyStart >= lines.size - 1) return ""

        return lines.drop(bodyStart + 1).joinToString("\n")
    }

    private fun decodeQuotedPrintable(text: String): String {
        val output = ByteArrayOutputStream()
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        var i = 0

        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF

            when {
                b == '='.code.toByte().toInt() && i + 2 < bytes.size -> {
                    if (bytes[i + 1] == '\r'.code.toByte() || bytes[i + 1] == '\n'.code.toByte()) {
                        i += if (bytes[i + 1] == '\r'.code.toByte() && i + 2 < bytes.size && bytes[i + 2] == '\n'.code.toByte()) 3 else 2
                    } else {
                        val hex = String(bytes, i + 1, 2, Charsets.US_ASCII)
                        try {
                            output.write(hex.toInt(16))
                        } catch (_: NumberFormatException) {
                            output.write(b)
                        }
                        i += 3
                    }
                }
                b == '_'.code.toByte().toInt() -> {
                    output.write(' '.code.toByte().toInt())
                    i++
                }
                else -> {
                    output.write(b)
                    i++
                }
            }
        }

        return output.toString(Charsets.UTF_8)
    }
}
