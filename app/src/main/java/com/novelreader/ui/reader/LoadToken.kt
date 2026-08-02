package com.novelreader.ui.reader

class LoadToken {
    private var current: Int? = null

    fun next(): Int = ((current ?: 0) + 1).also { current = it }

    fun baseUrl(token: Int): String = "$BASE_URL$token/"

    fun shouldAccept(url: String?): Boolean {
        val observed = url
            ?.takeIf { it.startsWith(BASE_URL) }
            ?.removePrefix(BASE_URL)
            ?.substringBefore('/')
            ?.toIntOrNull()
        return observed != null && url == baseUrl(observed) && observed == current
    }

    private companion object {
        const val BASE_URL = "https://reader.local/load/"
    }
}
