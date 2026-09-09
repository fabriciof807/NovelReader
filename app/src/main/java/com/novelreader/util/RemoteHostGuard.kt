package com.novelreader.util

object RemoteHostGuard {

    private val BLOCKED_SUFFIXES = listOf(".local", ".internal", ".home.arpa")

    fun isAllowed(host: String): Boolean {
        val normalized = normalize(host)
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return false
        if (BLOCKED_SUFFIXES.any { normalized.endsWith(it) }) return false
        if (normalized.contains(':')) return isAllowedIpv6(normalized)
        if (normalized.all { it.isDigit() || it == '.' }) return isAllowedIpv4(normalized)
        return true
    }

    private fun normalize(host: String): String {
        val trimmed = host.trim().lowercase().trimEnd('.')
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOf(']')
            return if (end > 0) trimmed.substring(1, end) else trimmed.removePrefix("[")
        }
        if (trimmed.count { it == ':' } > 1) return trimmed
        return trimmed.substringBefore(':')
    }

    private fun isAllowedIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val first = octets[0]
        val second = octets[1]
        return when {
            first == 0 -> false
            first == 10 -> false
            first == 127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 168 -> false
            first == 100 && second in 64..127 -> false
            first in 224..255 -> false
            else -> true
        }
    }

    private fun isAllowedIpv6(value: String): Boolean {
        if (value == "::" || value == "::1") return false
        val firstGroup = value.substringBefore(':')
        if (firstGroup.length >= 2) {
            val prefix = firstGroup.take(2)
            if (prefix == "fc" || prefix == "fd") return false
            if (prefix == "fe" && firstGroup.length >= 3) {
                val third = firstGroup[2]
                if (third in '8'..'b') return false
            }
        }
        return true
    }
}
