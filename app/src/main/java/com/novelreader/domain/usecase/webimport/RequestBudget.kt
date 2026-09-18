package com.novelreader.domain.usecase.webimport

class RequestBudget(private val limit: Int) {
    init {
        require(limit >= 0)
    }

    var used: Int = 0
        private set

    val remaining: Int
        get() = (limit - used).coerceAtLeast(0)

    fun tryConsume(): Boolean {
        if (used >= limit) return false
        used++
        return true
    }
}
