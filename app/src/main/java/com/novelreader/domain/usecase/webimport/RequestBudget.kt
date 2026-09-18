package com.novelreader.domain.usecase.webimport

class RequestBudget(private val capacity: Int) {
    private var consumed = 0

    fun tryConsume(): Boolean {
        if (consumed >= capacity) return false
        consumed++
        return true
    }
}
