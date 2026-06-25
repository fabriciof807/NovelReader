package com.novelreader.ui.reader

class LoadToken {
    private var current: Int? = null

    fun next(): Int {
        val issued = (current ?: 0) + 1
        current = issued
        return issued
    }

    fun shouldAccept(observed: Int): Boolean = observed == current
}
